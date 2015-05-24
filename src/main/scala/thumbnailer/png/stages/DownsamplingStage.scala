package thumbnailer.png.stages

import java.nio.ByteBuffer

import akka.stream.scaladsl.Flow
import akka.stream.stage._
import akka.util.ByteString
import thumbnailer.png.ScalingLogic
import thumbnailer.png.datatypes.{Ihdr, Filter, Line}

class DownsamplingStage(widthScale: Int, heightScale: Int, bytesPerPixel: Int) extends PushStage[Line, Line] {

  var lineNumber = 0

  override def onPush(line: Line, ctx: Context[Line]): SyncDirective = {
    require(line.filter == Filter.NoFilter, "Data must be unfiltered before it can be resized")
    lineNumber += 1

    if (lineNumber % heightScale == 0) {
      ctx.push(line.copy(bytes = downsample(line.bytes)))
    } else {
      ctx.pull()
    }
  }

  def downsample(data: ByteString): ByteString = {
    def scaleIndex(i: Int) = {
      val offset = i % bytesPerPixel
      (((i / bytesPerPixel) / widthScale) * bytesPerPixel) + offset
    }

    val output = ByteBuffer.allocate(scaleIndex(data.length))

    data.indices.foreach { i =>
      val pixelNumber = (i / bytesPerPixel) + 1 // count from 1 so we round the length down

      if (pixelNumber % widthScale == 0) {
        output.put(scaleIndex(i), data(i))
      }
    }

    ByteString(output)
  }
}

object DownsamplingStage {
  class Logic(widthScale: Int, heightScale: Int) extends ScalingLogic {
    override def resizeIhdr(originalIhdr: Ihdr): Ihdr =
      originalIhdr.copy(width = originalIhdr.width / widthScale, height = originalIhdr.height / heightScale)

    override def resizeLines(originalIhdr: Ihdr): Flow[Line, Line, _] = Flow[Line].transform(() =>
      new DownsamplingStage(widthScale, heightScale, originalIhdr.bytesPerPixel))
  }
}
