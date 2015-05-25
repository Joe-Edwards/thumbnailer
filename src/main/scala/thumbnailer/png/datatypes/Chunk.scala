package thumbnailer.png.datatypes

import akka.stream.scaladsl.Source
import akka.util.ByteString
import thumbnailer.png.stages.CrcAppender

case class Chunk(header: ChunkHeader, data: Source[ByteString, _]) {
  def bytes: Source[ByteString, _] = {
    val crcData = Source.single(header.nameBytes) ++ data
    Source.single(header.lengthBytes) ++ crcData.transform(() => new CrcAppender)
  }

  def isMandatory = header.name.charAt(0).isUpper
}
