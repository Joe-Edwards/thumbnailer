package thumbnailer.utils

trait ByteUtils {
  def unsign(byte: Byte): Int = if (byte < 0) byte + 256 else byte

  def average(bytes: Byte*): Byte = {
    (bytes.map(unsign).sum / bytes.length).toByte
  }
}

object ByteUtils extends ByteUtils
