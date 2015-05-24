package thumbnailer.png.stages

import akka.stream.stage._
import akka.util.ByteString

class ClumpingStage(minimumSize: Int) extends PushPullStage[ByteString, ByteString] {

  var buffer: ByteString = ByteString()

  override def onUpstreamFinish(ctx: Context[ByteString]): TerminationDirective = ctx.absorbTermination()

  override def onPush(elem: ByteString, ctx: Context[ByteString]): SyncDirective = {
    buffer = buffer ++ elem
    if (buffer.length < minimumSize) {
      ctx.pull()
    } else {
      val res = ctx.push(buffer)
      buffer = ByteString()
      res
    }
  }

  override def onPull(ctx: Context[ByteString]): SyncDirective = {
    if (ctx.isFinishing) {
      if (buffer.isEmpty) {
        ctx.finish()
      } else {
        ctx.pushAndFinish(buffer)
      }
    } else {
      ctx.pull()
    }
  }
}
