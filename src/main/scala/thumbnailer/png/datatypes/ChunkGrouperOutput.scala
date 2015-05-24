package thumbnailer.png.datatypes

import java.nio.ByteBuffer

import akka.util.ByteString

sealed trait ChunkGrouperOutput {
  def header: ChunkHeader
  def bytes: ByteString
}

case class ChunkHeader(name: String, length: Int) extends ChunkGrouperOutput {
  def bytes = ByteString(ByteBuffer.allocate(4).putInt(length).array()) ++ ByteString(name.getBytes)

  def isEnd = name == "IEND"

  override def header: ChunkHeader = this
}

case class ChunkData(header: ChunkHeader, bytes: ByteString) extends ChunkGrouperOutput

case class ChunkEnd(header: ChunkHeader, crc: ByteString) extends ChunkGrouperOutput {
  val bytes = crc
}
