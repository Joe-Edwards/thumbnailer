package thumbnailer.png.stages

import java.nio.ByteBuffer
import java.util.zip.CRC32

import akka.stream.stage.{TerminationDirective, SyncDirective, Context, PushPullStage}
import akka.util.ByteString

class CrcAppender extends PushPullStage[ByteString, ByteString] {
  
  val crc = new CRC32()

  override def onUpstreamFinish(ctx: Context[ByteString]): TerminationDirective = ctx.absorbTermination()

  override def onPush(elem: ByteString, ctx: Context[ByteString]): SyncDirective = {
    crc.update(elem.asByteBuffer)
    ctx.push(elem)
  }

  override def onPull(ctx: Context[ByteString]): SyncDirective = {
    if (ctx.isFinishing) {
      val crcBytes = ByteBuffer.allocate(4).putInt(0, crc.getValue.toInt)
      ctx.pushAndFinish(ByteString(crcBytes))
    } else {
      ctx.pull()
    }
  }
}
