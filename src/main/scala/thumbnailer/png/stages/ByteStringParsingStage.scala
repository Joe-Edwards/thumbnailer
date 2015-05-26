package thumbnailer.png.stages

import akka.stream.stage.{TerminationDirective, SyncDirective, Context, PushPullStage}
import akka.util.ByteString

/**
 * Stage that reads in a stream of ByteStrings and parses the result.
 *
 * Only emits a single element (the result of the parse).
 */
class ByteStringParsingStage[T](parse: ByteString => T) extends PushPullStage[ByteString, T] {
  
  var buffer = ByteString()
  
  override def onUpstreamFinish(ctx: Context[T]): TerminationDirective = ctx.absorbTermination()
  
  override def onPush(elem: ByteString, ctx: Context[T]): SyncDirective = {
    buffer ++= elem
    ctx.pull()
  }

  override def onPull(ctx: Context[T]): SyncDirective = {
    if (ctx.isFinishing) {
      ctx.pushAndFinish(parse(buffer))
    } else {
      ctx.pull()
    }
  }
}
