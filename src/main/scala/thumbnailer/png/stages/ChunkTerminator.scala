package thumbnailer.png.stages

import akka.stream.stage.{Context, PushStage, SyncDirective}
import akka.util.ByteString
import thumbnailer.png.datatypes._

/**
 * Terminates a stream of [[ChunkGrouperOutput]]s once it sees a [[ChunkEnd]]
 *
 * This is required as [[akka.stream.scaladsl.Flow#GroupBy]] cannot terminate its substreams.
 */
class ChunkTerminator extends PushStage[ChunkGrouperOutput, ByteString] {
  override def onPush(part: ChunkGrouperOutput, ctx: Context[ByteString]): SyncDirective = {
    part match {
      case header: ChunkHeader =>
        ctx.pull()
      case data: ChunkData =>
        ctx.push(data.bytes)
      case e: ChunkEnd =>
        ctx.finish()
    }
  }
}
