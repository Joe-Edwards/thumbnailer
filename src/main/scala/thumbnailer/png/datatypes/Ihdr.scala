package thumbnailer.png.datatypes

import java.nio.ByteBuffer

import akka.util.ByteString

case class Ihdr(
  width: Int,
  height: Int,
  bitDepth: Byte,
  colourType: ColourType,
  compression: Byte,
  filter: Byte,
  interlace: Byte) {

  require(compression == 0x00, s"Only support deflate compression (found $compression)")
  require(filter == 0x00, s"Only support simple filter scheme (found $filter)")
  require(interlace == 0x00, s"Only support simple interlacing (found $interlace)")

  def bytes = {
    val buffer = ByteBuffer
      .allocate(13)
      .putInt(0, width)
      .putInt(4, height)
      .put(8, bitDepth)
      .put(9, colourType.byte)
      .put(10, compression)
      .put(11, filter)
      .put(12, interlace)
    ByteString(buffer)
  }

  def bytesPerPixel = (bitDepth.toInt * colourType.channels) / 8

  def bytesPerLine = bytesPerPixel * width + 1 // add 1 for the filter byte
}

object Ihdr {
  def fromBytes(bytes: ByteString) = {
    val buffer = bytes.asByteBuffer
    Ihdr(
      buffer.getInt(0),
      buffer.getInt(4),
      buffer.get(8),
      ColourType.fromByte(buffer.get(9)),
      buffer.get(10),
      buffer.get(11),
      buffer.get(12))
  }
}
