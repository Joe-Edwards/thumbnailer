package thumbnailer.png

import akka.http.scaladsl.coding.{Deflate, DeflateDecompressor}
import akka.stream.scaladsl._
import akka.util.ByteString
import thumbnailer.png.datatypes._
import thumbnailer.png.stages._

import scala.concurrent.Promise

object PngFlows {

  /**
   * Flow combining the others to resize a PNG
   */
  def resizePng(logic: ScalingLogic): Flow[ByteString, ByteString, _] = {
    Flow[ByteString]
      .via(chunker)
      .via(mandatoryChunkFilter)
      .via(resizeChunks(logic))
      .via(deChunker)
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

  /**
   * Resize a stream of chunks with a known IHDR
   */
  private def resizeChunksWithIhdr(logic: ScalingLogic, ihdr: Ihdr): Flow[Chunk, Chunk, _] = Flow() { implicit b =>
    import FlowGraph.Implicits._

    val ihdrPromise = Promise[Ihdr]()

    val split = b.add(new DataSplit)
    val merge = b.add(Merge[Chunk](2))
    val resize = b.add(dataToChunkTransform(lineToDataTransform(ihdr, logic.resizeLines(ihdr))))

    split.out0      ~>      merge.in(0)
    split.out1 ~> resize ~> merge.in(1)

    (split.in, merge.out)
  }

  /**
   * Turns a raw pixel data transform into a transform on the stream of IDAT chunks
   */
  private def dataToChunkTransform(
    dataTransform: Flow[ByteString, ByteString, _]
  ): Flow[Chunk, Chunk, _] = {
    Flow[Chunk]
      .map(_.data)
      .flatten(FlattenStrategy.concat)
      .via(dataTransform)
      .transform(() => new ClumpingStage(minimumSize = 1024)) // Ensure all chunks > 1KB to avoid wasteful chunking
      .map(b => Chunk(ChunkHeader("IDAT", b.length), Source.single(b)))
  }

  /**
   * Turns a line-by-line transform into a transform on the raw pixel data
   */
  private def lineToDataTransform(
    ihdr: Ihdr,
    dataTransform: Flow[Line, Line, _]
  ): Flow[ByteString, ByteString, _] = {
    var totalSize = 0L
    var totalSize2 = 0L
    Flow[ByteString]
      //.log("compressed", b => {totalSize += b.length; totalSize})(new SimpleLogger())
      .mapConcat(_.grouped(1024).toList) // TODO: Deflate gets overexcited with large pieces
      .transform(() => new DeflateDecompressor())
      //.log("decompressed", b => {totalSize2 += b.length; totalSize2})(new SimpleLogger())
      .transform(() => new Linifier(ihdr.bytesPerLine))
      .transform(() => new UnfilteringStage(ihdr.bytesPerPixel))
      .via(dataTransform)
      .transform(() => new FilteringStage(ihdr.bytesPerPixel))//, singleFilter = Some(Filter.PaethFilter))) // TODO: Maybe more efficient to just stick with Paeth
      .map(_.raw)
      .transform(() => new Deflate(_ => true).newEncodeTransformer())
  }
}
