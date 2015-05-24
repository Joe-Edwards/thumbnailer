package thumbnailer.png.stages

import akka.stream.stage.{Context, PushStage, SyncDirective}
import akka.util.ByteString
import thumbnailer.png.datatypes.{Filter, Line}

class UnfilteringStage(bytesPerPixel: Int) extends PushStage[Line, Line] {

  var previousLineData: Option[ByteString] = None

  override def onPush(line: Line, ctx: Context[Line]): SyncDirective = {
    val unfilteredData = line.filter.unfilterData(
      line.bytes,
      previousLineData.getOrElse(ByteString(new Array[Byte](line.bytes.length))),
      bytesPerPixel)
    previousLineData = Some(unfilteredData)
    ctx.push(Line(Filter.NoFilter, unfilteredData))
  }
}
