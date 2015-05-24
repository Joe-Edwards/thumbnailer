package thumbnailer.png

import akka.stream.scaladsl.Flow
import thumbnailer.png.datatypes.{Line, Ihdr}

abstract class ScalingLogic {
  def resizeIhdr(originalIhdr: Ihdr): Ihdr
  def resizeLines(originalIhdr: Ihdr): Flow[Line, Line, _]
}
