package thumbnailer.png.datatypes

case class ColourType private (byte: Byte, channels: Int)

object ColourType {
  def fromByte(byte: Byte) = byte match {
    case 0x00 => GreyScale
    case 0x02 => RGB
    case 0x03 => Palette
    case 0x04 => GreyScalaAlpha
    case 0x06 => RGBA
    case _ => throw new Exception("Unknown colour type " + byte)
  }

  val GreyScale = ColourType(0x00, 1)
  val RGB = ColourType(0x02, 3)
  val Palette = ColourType(0x03, 1)
  val GreyScalaAlpha = ColourType(0x04, 2)
  val RGBA = ColourType(0x06, 4)
}
