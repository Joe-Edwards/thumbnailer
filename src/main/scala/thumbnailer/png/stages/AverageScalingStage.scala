package thumbnailer.png.stages

import java.nio.ByteBuffer

import akka.stream.scaladsl.Flow
import akka.stream.stage.{SyncDirective, Context, PushStage}
import akka.util.ByteString
import thumbnailer.png.ScalingLogic
import thumbnailer.png.datatypes.{ColourType, Filter, Ihdr, Line}
import thumbnailer.utils.ByteUtils

class AverageScalingStage(widthScale: Int, heightScale: Int, bytesPerPixel: Int) extends PushStage[Line, Line] with ByteUtils {

  var lastLines: List[ByteString] = Nil
  
  override def onPush(line: Line, ctx: Context[Line]): SyncDirective = {
    require(line.filter == Filter.NoFilter, s"Cannot average filtered data")
    if (lastLines.length < (heightScale - 1)) {
      lastLines =  line.bytes :: lastLines
      ctx.pull()
    } else {
      val directive = ctx.push(line.copy(bytes = average(line.bytes)))
      lastLines = Nil
      directive
    }
  }

  def average(data: ByteString): ByteString = {
    require(lastLines.forall(_.length == data.length), s"Length mismatch in inputs")

    def scaleIndex(i: Int) = {
      val offset = i % bytesPerPixel
      (((i / bytesPerPixel) / widthScale) * bytesPerPixel) + offset
    }

    val buffer = ByteBuffer.allocate(scaleIndex(data.length))

    data.indices.foreach { i =>
      val pixelNumber = (i / bytesPerPixel) + 1 // count from 1 so we round the length down

      if (pixelNumber % widthScale == 0) {
        // Indices in each row that we're averaging
        val indices = Range.inclusive(i - ((widthScale - 1) * bytesPerPixel), i, bytesPerPixel)

        val bytesToAverage = indices.flatMap { j =>
          // The current row and all the previous ones
          data(j) :: lastLines.map(bs => bs(j))
        }

        buffer.put(scaleIndex(i), average(bytesToAverage:_*))
      }
    }

    ByteString(buffer)
  }
}

object AverageScalingStage {
  class Logic(widthScale: Int, heightScale: Int) extends ScalingLogic {
    override def resizeIhdr(originalIhdr: Ihdr): Ihdr =
      originalIhdr.copy(width = originalIhdr.width / widthScale, height = originalIhdr.height / heightScale)

    override def resizeLines(originalIhdr: Ihdr): Flow[Line, Line, _] = {
      require(originalIhdr.colourType != ColourType.Palette, s"Cannot average a paletted image")
      Flow[Line].transform(() =>
        new AverageScalingStage(widthScale, heightScale, originalIhdr.bytesPerPixel))
    }
  }
}
