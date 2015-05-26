package thumbnailer.png.datatypes

import java.nio.ByteBuffer

import akka.util.ByteString

sealed trait ChunkGrouperOutput

case class ChunkHeader(name: String, length: Int) extends ChunkGrouperOutput {

  def lengthBytes = ByteString(ByteBuffer.allocate(4).putInt(length).array())
  def nameBytes =  ByteString(name.getBytes)

  def isEnd = name == "IEND"
}

case class ChunkData(bytes: ByteString) extends ChunkGrouperOutput
