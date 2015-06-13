package thumbnailer.png

import akka.http.scaladsl.coding.{Deflate, DeflateDecompressor}
import akka.stream.BidiShape
import akka.stream.scaladsl._
import akka.util.ByteString
import thumbnailer.png.datatypes._
import thumbnailer.png.stages._

object PngFlows {

  /**
   * Flow to resize a stream of PNG data
   */
  def resizePng(logic: ScalingLogic): Flow[ByteString, ByteString, _] = {
    chunkingLayer.join(resizeChunks(logic))
  }

  /**
   * Turns a stream of bytes into a stream of chunks (excluding the end chunk)
   */
  val chunker: Flow[ByteString, Chunk, _] = {
    Flow[ByteString]
      .transform(() => new ChunkGrouper)
      .splitWhen(_.isInstanceOf[ChunkHeader])
      .map { _.prefixAndTail(1).map {
        case (Seq(header: ChunkHeader), parts) => Chunk(header, parts.map {
          case ChunkData(bytes) => bytes
          case _ => throw new Exception("Bad chunk grouper output")
        })
        case _ => throw new Exception("Bad chunk grouper output")
      }
    }.flatten(FlattenStrategy.concat)
  }

  /**
   * Turns a stream of chunks into bytes, inserting the necessary PNG header and IEND chunk.
   */
  val deChunker: Flow[Chunk, ByteString, _] = {
    Flow() { implicit b =>
      import FlowGraph.Implicits._

      val header = b.add(Source.single(ChunkGrouper.PngHeader))
      val merge = b.add(MergePreferred[ByteString](1))
      val flow = b.add(Flow[Chunk]
        .concat(Source.single(Chunk(ChunkHeader("IEND", 0), Source.empty)))
        .map(_.bytes)
        .flatten[ByteString](FlattenStrategy.concat))

      header.outlet ~> merge.preferred
      flow.outlet ~> merge.in(0)

      (flow.inlet, merge.out)
    }
  }

  /**
   * Filters any non-mandatory chunks, ensuring that their bytes are still read.
   */
  val mandatoryChunkFilter: Flow[Chunk, Chunk, _] = {
    Flow[Chunk]
      .map {
      case chunk if chunk.isMandatory =>
        Source.single(chunk)
      case chunk =>
        // This slightly odd mapping ensures that all the bytes are read
        chunk.bytes.mapConcat(_ => Nil)
    }
      .flatten(FlattenStrategy.concat)
  }

  /**
   * Transforms between raw PNG data and chunks
   *
   * - Handles the PNG header and end chunk internally
   * - Excludes any non-mandatory chunks
   */
  val chunkingLayer = simpleBidiFlow(chunker via mandatoryChunkFilter, deChunker)

  /**
   * Resize a stream of PNG chunks
   */
  def resizeChunks(logic: ScalingLogic): Flow[Chunk, Chunk, _] = {

    def parseIhdr(chunk: Chunk): Source[Ihdr, _] = chunk match {
      case Chunk(ChunkHeader("IHDR", _), ihdrData) =>
        ihdrData.transform(() => new ByteStringParsingStage[Ihdr](Ihdr.fromBytes))
      case _ => throw new Exception("First chunk must be IHDR")
    }

    Flow[Chunk]
      .prefixAndTail(1)
      .map {
        case (Seq(ihdrChunk), otherChunks) =>
          parseIhdr(ihdrChunk).prefixAndTail(1).map {
            case (Seq(ihdr), _) => (ihdr, otherChunks)
        }
      }
      .flatten(FlattenStrategy.concat)
      .map {
        case (ihdr, otherChunks) =>
          (logic.resizeIhdr(ihdr), otherChunks.via(resizeChunksWithIhdr(logic, ihdr)))
      }
      .map {
        case (ihdr, otherChunks) =>
          Source.single(ihdr.createChunk) ++ otherChunks
    }.flatten(FlattenStrategy.concat)
  }

  private def resizeChunksWithIhdr(logic: ScalingLogic, ihdr: Ihdr) = {
    idatChunksOnly
      .atop(dataChunkLayer)
      .atop(compressionLayer)
      .atop(linifierLayer(ihdr.bytesPerLine))
      .atop(filteringLayer(ihdr.bytesPerPixel))
      .join(logic.resizeLines(ihdr))
  }

  /**
   * Forwards only the IDAT chunks
   */
  private val idatChunksOnly = ConditionalRoute.bidiFlow[Chunk](_.header.name == "IDAT")

  /**
   * Transforms between a stream of IDAT chunks and a stream of raw [[ByteString]]
   */
  private val dataChunkLayer = simpleBidiFlow(
    Flow[Chunk]
      .map(_.data)
      .flatten(FlattenStrategy.concat),
    Flow[ByteString]
      .transform(() => new ClumpingStage(minimumSize = 1024)) // Ensure all chunks > 1KB to avoid wasteful chunking
      .map(b => Chunk(ChunkHeader("IDAT", b.length), Source.single(b))))

  /**
   * Applies deflate decompression/compression to a stream of [[ByteString]]
   */
  private val compressionLayer = simpleBidiFlow(
    Flow[ByteString]
      .mapConcat(_.grouped(1024).toList)
      .transform(() => new DeflateDecompressor()),
    Flow[ByteString]
      .transform(() => new Deflate(_ => true).newEncodeTransformer()))

  /**
   * Transforms between a stream of [[ByteString]] and a stream of [[Line]]
   */
  private def linifierLayer(bytesPerLine: Int) = simpleBidiFlow(
    Flow[ByteString].transform(() => new Linifier(bytesPerLine)),
    Flow[Line].map(_.raw))

  /**
   * Unfilters and filters a stream of [[Line]]
   */
  private def filteringLayer(bytesPerPixel: Int) = simpleBidiFlow(
    Flow[Line].transform(() => new UnfilteringStage(bytesPerPixel)),
    Flow[Line].transform(() => new FilteringStage(bytesPerPixel))) // TODO: Maybe more efficient to just stick with Paeth

  /**
   * Create a simple BidiFlow from two distinct flows
   */
  private def simpleBidiFlow[I1, O1, I2, O2](lhs: Flow[I1, O1, _], rhs: Flow[I2, O2, _]) = {
    BidiFlow() { implicit b =>
      val lhsShape = b.add(lhs)
      val rhsShape = b.add(rhs)
      BidiShape(lhsShape.inlet, lhsShape.outlet, rhsShape.inlet, rhsShape.outlet)
    }
  }
}
