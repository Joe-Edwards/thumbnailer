package thumbnailer.png.datatypes

import java.nio.ByteBuffer

import akka.util.ByteString
import thumbnailer.utils.ByteUtils

sealed trait Filter {
  def byte: Byte
  def filterData(data: ByteString, previousLine: ByteString, bpp: Int): ByteString
  def unfilterData(data: ByteString, previousLine: ByteString, bpp: Int): ByteString
}

sealed trait ShiftingFilter extends Filter with ByteUtils {
  def shift(previous: Byte, upper: Byte, upperLeft: Byte): Byte

  override def filterData(data: ByteString, previousLine: ByteString, bpp: Int): ByteString = {
    shiftData(data, previousLine, bpp, inverted = false)
  }

  override def unfilterData(data: ByteString, previousLine: ByteString, bpp: Int): ByteString = {
    shiftData(data, previousLine, bpp, inverted = true)
  }

  def shiftData(data: ByteString, previousLine: ByteString, bpp: Int, inverted: Boolean): ByteString = {
    require(data.length == previousLine.length, s"Previous data (length ${data.length}) doesn't line up with input data (length ${previousLine.length})")

    // Using a byte buffer here is ~20 times faster than trying to use a purely functional approach
    val outputBuffer = ByteBuffer.allocate(data.length)

    data.indices.foreach { i =>
      val previousByte: Byte = if (i < bpp) 0x00 else if (inverted) outputBuffer.get(i - bpp) else data(i - bpp)
      val upperByte: Byte = previousLine(i)
      val upperLeftByte: Byte = if (i < bpp) 0x00 else previousLine(i - bpp)

      val shiftByte = shift(previousByte, upperByte, upperLeftByte)

      if (inverted)
        outputBuffer.put(i, (data(i) + shiftByte).toByte)
      else
        outputBuffer.put(i, (data(i) - shiftByte).toByte)
    }

    ByteString(outputBuffer)
  }
}

object Filter {

  def fromByte(byte: Byte) = byte match {
    case 0x00 => NoFilter
    case 0x01 => SubFilter
    case 0x02 => UpFilter
    case 0x03 => AverageFilter
    case 0x04 => PaethFilter
    case b => throw new Exception("Unknown filtering type " + b)
  }

  /**
   * No filter applied
   */
  case object NoFilter extends Filter {
    override def byte = 0x00
    override def filterData(data: ByteString, previousLine: ByteString, bpp: Int): ByteString = data
    override def unfilterData(data: ByteString, previousLine: ByteString, bpp: Int): ByteString = data
  }

  /**
   * Filter by shifting values by the pixel on the left
   */
  case object SubFilter extends ShiftingFilter {
    override def byte: Byte = 0x01
    override def shift(previous: Byte, upper: Byte, upperLeft: Byte): Byte = previous
}

  /**
   * Filter by shifting values by the pixel above
   */
  case object UpFilter extends ShiftingFilter {
    override def byte: Byte = 0x02
    override def shift(previous: Byte, upper: Byte, upperLeft: Byte): Byte = upper
  }

  /**
   * Filter by shifting values by the average of the pixels on the left and above
   */
  case object AverageFilter extends ShiftingFilter {
    override def byte: Byte = 0x03
    override def shift(previous: Byte, upper: Byte, upperLeft: Byte): Byte = average(previous, upper)
  }

  /**
   * Filter by shifting based on a prediction from the left, upper-left and upper pixels
   */
  case object PaethFilter extends ShiftingFilter {
    override def byte: Byte = 0x04
    override def shift(previous: Byte, upper: Byte, upperLeft: Byte): Byte = paethPredictor(previous, upper, upperLeft)

    private def paethPredictor(a: Byte, b: Byte, c: Byte): Byte = {
      val as = unsign(a)
      val bs = unsign(b)
      val cs = unsign(c)

      val p = as + bs - cs
      val pa = Math.abs(p - as)
      val pb = Math.abs(p - bs)
      val pc = Math.abs(p - cs)

      // return nearest of a,b,c,
      // breaking ties in order a,b,c.
      if ((pa <= pb) && (pa <= pc)) a
      else if (pb <= pc) b
      else c
    }
  }
}
