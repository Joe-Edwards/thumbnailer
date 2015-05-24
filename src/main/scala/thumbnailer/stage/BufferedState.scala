package thumbnailer.stage

import akka.stream.stage.{Context, StageState, SyncDirective}
import akka.util.ByteString

/**
 * State that adds new elements to the buffer and processes on Push or Pull in the same fashion.
 */
abstract class BufferedState[Out](var buffer: ByteString) extends StageState[ByteString, Out] {
  override def onPush(elem: ByteString, ctx: Context[Out]): SyncDirective = {
    buffer = buffer ++ elem
    process(ctx)
  }

  override def onPull(ctx: Context[Out]): SyncDirective = {
    process(ctx)
  }

  /**
   * Method for handling the buffer
   */
  def process(ctx: Context[Out]): SyncDirective

  /**
   * Pull more elements if not finishing
   */
  def pullIfPossible(ctx: Context[Out]): SyncDirective = {
    if (ctx.isFinishing)
      ctx.fail(new Exception("Unexpected upstream termination"))
    else
      ctx.pull()
  }

  /**
   * Keep pulling until the buffer is a certain length, then perform a function on those bytes
   */
  def waitForBytes(ctx: Context[Out], length: Int)(f: ByteString => SyncDirective): SyncDirective = {
    if (buffer.length < length) {
      pullIfPossible(ctx)
    } else {
      val bytes = buffer.take(length)
      buffer = buffer.drop(length)
      f(bytes)
    }
  }
}
