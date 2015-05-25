package thumbnailer.png.stages

import akka.actor.ActorSystem
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.specs2.mutable.Specification

class CrcAppenderSpec extends Specification {

  implicit val system = ActorSystem()
  implicit val materializer = ActorFlowMaterializer()
  implicit val executionContext = system.dispatcher

  "CRC Appender" should {
    "append the CRC of a data stream" in {
      // The data and CRC of a valid IHDR
      val data = ByteString('I', 'H', 'D', 'R', 0, 0, 0, 8, 0, 0, 0, 8, 8, 2, 0, 0, 0)
      val expectedCrc = ByteString(0x4B, 0x6D, 0x29, 0xDC)

      val result = Source.single(data).transform(() => new CrcAppender).runFold(List[ByteString]())(_ :+ _)

      result must beEqualTo(List(data, expectedCrc)).await()
    }

    "append another CRC of a data stream" in {
      // The data and CRC of a valid pHYs
      val data = ByteString(0x70, 0x48, 0x59, 0x73, 0x00, 0x00, 0x0E, 0xC3, 0x00, 0x00, 0x0E, 0xC3, 0x01)
      val expectedCrc = ByteString(0xC7, 0x6F, 0xA8, 0x64)

      val result = Source.single(data).transform(() => new CrcAppender).runFold(List[ByteString]())(_ :+ _)

      result must beEqualTo(List(data, expectedCrc)).await()
    }
  }
}
