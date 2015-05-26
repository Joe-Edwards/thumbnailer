package thumbnailer.png.stages

import akka.actor.ActorSystem
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.specs2.mutable.Specification
import thumbnailer.png.datatypes.{ChunkData, ChunkGrouperOutput, ChunkHeader}

import scala.concurrent.Await
import scala.concurrent.duration._

class ChunkGrouperSpec extends Specification {

  implicit val timeout = 10.seconds

  implicit val system = ActorSystem()
  implicit val materializer = ActorFlowMaterializer()
  implicit val executionContext = system.dispatcher

  val PngHeaderSource = Source.single(ChunkGrouper.PngHeader)
  val EndChunkSource = Source.single(ByteString(0x00,0x00,0x00,0x00,'I','E','N','D',0xAE,0x42,0x60,0x82))

  def runGrouperWithStandard(bytes: ByteString*): List[ChunkGrouperOutput] = {
    val source = PngHeaderSource ++ Source(bytes.toList) ++ EndChunkSource
    runGrouper(source)
  }

  def runGrouper(source: Source[ByteString, _]): List[ChunkGrouperOutput] = {
    Await.result(source.transform(() => new ChunkGrouper).runFold(List[ChunkGrouperOutput]())(_ :+ _), timeout)
  }

  "A Chunk Grouper stage" should {
    "strip off a valid PNG header and valid End Chunk" in {
      val source = PngHeaderSource ++ EndChunkSource

      runGrouper(source) must beEmpty
    }

    "fail an invalid PNG header" in {
      val source = EndChunkSource

      runGrouper(source) must throwAn[Exception]("Invalid PNG Header")
    }

    "fail a missing End chunk" in {
      val source = PngHeaderSource

      runGrouper(source) must throwAn[Exception]
    }

    "parse a correctly formed chunk" in {
      val chunkName = "Test"
      val chunkLength = ByteString(0x00, 0x00, 0x00, 0x06)
      val chunkData = ByteString(0x01, 0x02, 0x03, 0x04, 0x05, 0x06)
      val chunkCrc = ByteString(0x00, 0x00, 0x00, 0x00)

      val result = runGrouperWithStandard(chunkLength, ByteString(chunkName), chunkData, chunkCrc)

      result must beEqualTo(List(
        ChunkHeader(chunkName, chunkData.length),
        ChunkData(chunkData)))
    }

    "fail a chunk with a bad name" in {
      val badData = ByteString(0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01)

      runGrouperWithStandard(badData) must throwAn[Exception]("Bad chunk name")
    }
  }
}
