package thumbnailer.png.stages

import akka.stream.stage.{Context, PushPullStage, SyncDirective, TerminationDirective}
import akka.util.ByteString
import thumbnailer.png.datatypes.{Filter, Line}

class Linifier(bytesPerLine: Int) extends PushPullStage[ByteString, Line] {

  var buffer: ByteString = ByteString()

  // Control our own termination
  override def onUpstreamFinish(ctx: Context[Line]): TerminationDirective = ctx.absorbTermination()

  override def onPush(elem: ByteString, ctx: Context[Line]): SyncDirective = {
    buffer = buffer ++ elem
    onPull(ctx)
  }

  override def onPull(ctx: Context[Line]): SyncDirective = {

    if (buffer.length >= bytesPerLine) {
      // Enough data to emit a new line
      val line = Line(Filter.fromByte(buffer(0)), buffer.slice(1, bytesPerLine))
      buffer = buffer.drop(bytesPerLine)
      ctx.push(line)
    } else if (ctx.isFinishing) {
      // Finishing without any more lines to emit
      if (buffer.isEmpty) ctx.finish() else ctx.fail(new Exception("Incomplete line data: " + buffer))
    } else {
      // Need more data
      ctx.pull()
    }
  }
}
