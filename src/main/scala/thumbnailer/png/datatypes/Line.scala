package thumbnailer.png.datatypes

import akka.util.ByteString

case class Line(filter: Filter, bytes: ByteString) {
  def raw = ByteString(filter.byte) ++ bytes
}
