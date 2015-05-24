package thumbnailer.png

import akka.http.scaladsl.coding.{Deflate, DeflateDecompressor}
import akka.stream.FlowMaterializer
import akka.stream.scaladsl._
import akka.util.ByteString
import thumbnailer.png.datatypes._
import thumbnailer.png.stages._

import scala.concurrent.Promise

object PngFlows {

  /**
   * Turns a stream of bytes into a stream of chunks (excluding the end chunk)
   */
  val chunker: Flow[ByteString, Chunk, _] = {
    Flow[ByteString]
      .transform(() => new ChunkGrouper)
      .groupBy(_.header)
      .map {
        case (header, parts) => Chunk(header, parts.transform(() => new ChunkTerminator))
      }
  }

  /**
   * Filters any non-mandatory chunks, ensuring that their bytes are still read.
   */
  def mandatoryChunkFilter(implicit materializer: FlowMaterializer): Flow[Chunk, Chunk, _] = {
    Flow[Chunk]
      .map {
        case chunk if chunk.isMandatory =>
          Some(chunk)
        case chunk =>
          chunk.bytes.runWith(Sink.ignore)
          None
      }
      .collect {
        case Some(chunkWithSource) => chunkWithSource
      }
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

  implicit class SourceWithResize[Mat](val chunks: Source[Chunk, Mat])(implicit fm: FlowMaterializer) {
    def resize(logic: ScalingLogic) = PngFlows.resize(chunks, logic)
  }

  /**
   * Resize a stream of PNG chunks
   *
   * N.B. This will materialize the stream to find the IHDR!
   */
  def resize[Mat](chunkSource: Source[Chunk, Mat], logic: ScalingLogic)(implicit materializer: FlowMaterializer): Source[Chunk, Mat] = {
    import materializer.executionContext

    val (mat, firstChunkAndTail) = chunkSource
      .prefixAndTail(1)
      .toMat(Sink.head)((_, _))
      .run()

    val ihdrAndChunks = firstChunkAndTail
      .flatMap {
        case (Seq(Chunk(ChunkHeader("IHDR", _), ihdrData)), otherChunks) =>
          ihdrData.runFold(ByteString())(_ ++ _).map(Ihdr.fromBytes).map((_, otherChunks))
        case _ => throw new Exception("First chunk must be IHDR")
      }

    val allChunksFuture = ihdrAndChunks.map { case (ihdr, otherChunks) =>
      val newIhdr = logic.resizeIhdr(ihdr)
      Source.single(Chunk(ChunkHeader("IHDR", 13), Source.single(newIhdr.bytes))) ++
        otherChunks.via(resizeChunks(logic, ihdr))
    }

    Source(allChunksFuture).flatten(FlattenStrategy.concat).mapMaterializedValue(_ => mat)
  }

  /**
   * Resize a stream of chunks with a known IHDR
   */
  private def resizeChunks(logic: ScalingLogic, ihdr: Ihdr) = Flow() { implicit b =>
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
    Flow[ByteString]
      .transform(() => new DeflateDecompressor())
      .transform(() => new Linifier(ihdr.bytesPerLine))
      .transform(() => new UnfilteringStage(ihdr.bytesPerPixel))
      .via(dataTransform)
      .transform(() => new FilteringStage(ihdr.bytesPerPixel))//, singleFilter = Some(Filter.PaethFilter))) // TODO: Choose filter intelligently?
      .map(_.raw)
      .transform(() => new Deflate(_ => true).newEncodeTransformer())
  }
}
