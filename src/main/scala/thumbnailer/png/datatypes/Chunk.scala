package thumbnailer.png.datatypes

import akka.stream.scaladsl.Source
import akka.util.ByteString

case class Chunk(header: ChunkHeader, data: Source[ByteString, _]) {
  def bytes: Source[ByteString, _] = {
    Source.single(header.bytes) ++ data ++ Source.single(ByteString(0x00,0x00,0x00,0x00)) // Calculate the CRC properly!!
  }

  def isMandatory = header.name.charAt(0).isUpper
}
