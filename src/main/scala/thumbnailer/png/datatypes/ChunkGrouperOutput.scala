package thumbnailer.png.datatypes

import java.nio.ByteBuffer

import akka.util.ByteString

sealed trait ChunkGrouperOutput {
  def header: ChunkHeader
}

case class ChunkHeader(name: String, length: Int) extends ChunkGrouperOutput {

  def lengthBytes = ByteString(ByteBuffer.allocate(4).putInt(length).array())
  def nameBytes =  ByteString(name.getBytes)

  def isEnd = name == "IEND"

  override def header: ChunkHeader = this
}

case class ChunkData(header: ChunkHeader, bytes: ByteString) extends ChunkGrouperOutput

case class ChunkEnd(header: ChunkHeader, crc: ByteString) extends ChunkGrouperOutput
