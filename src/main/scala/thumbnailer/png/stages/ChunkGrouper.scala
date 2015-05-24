package thumbnailer.png.stages

import akka.stream.stage._
import akka.util.ByteString
import thumbnailer.png.datatypes._
import thumbnailer.stage.BufferedState

/**
 * Split a PNG data stream into chunk parts
 *
 * - Verifies the PNG header but does not pass it on
 * - Completes once it sees an IEND chunk header (any subsequent data is ignored)
 */
class ChunkGrouper extends StatefulStage[ByteString, ChunkGrouperOutput] {

  type Ctx = Context[ChunkGrouperOutput]

  // Initially we look for a valid PNG header
  override def initial: StageState[ByteString, ChunkGrouperOutput] = readingPngHeader()

  // Don't finish until we're ready
  override def onUpstreamFinish(ctx: Ctx): TerminationDirective = ctx.absorbTermination()

  def becomeAndPull(state: StageState[ByteString, ChunkGrouperOutput], ctx: Ctx): SyncDirective = {
    become(state)
    state.onPull(ctx)
  }

  def readingPngHeader(): BufferedState[ChunkGrouperOutput] = new BufferedState[ChunkGrouperOutput](ByteString()) {
    override def process(ctx: Ctx): SyncDirective = {
      waitForBytes(ctx, 8) { headerBytes =>
        if (headerBytes == ChunkGrouper.PngHeader) {
          becomeAndPull(readingChunkHeader(buffer), ctx)
        } else {
          ctx.fail(new Exception("Invalid PNG Header"))
        }
      }
    }
  }

  def readingChunkHeader(buffer: ByteString): BufferedState[ChunkGrouperOutput] = new BufferedState[ChunkGrouperOutput](buffer) {
    override def process(ctx: Ctx): SyncDirective = {
      waitForBytes(ctx, 8) { headerBytes =>
        val header = ChunkHeader(headerBytes.slice(4, 8).utf8String, headerBytes.asByteBuffer.getInt(0))

        if (header.isEnd)
          becomeAndPull(readingChunkData(buffer, header), ctx)
        else {
          become(readingChunkData(buffer, header))
          ctx.push(header)
        }
      }
    }
  }

  def readingChunkData(buffer: ByteString, header: ChunkHeader): BufferedState[ChunkGrouperOutput] = new BufferedState[ChunkGrouperOutput](buffer) {
    var bytesToForward = header.length

    override def process(ctx: Ctx): SyncDirective = {
      if (buffer.isEmpty) {
        pullIfPossible(ctx)
      } else if (buffer.length >= bytesToForward) {
        val bytes = buffer.take(bytesToForward)
        buffer = buffer.drop(bytesToForward)

        if (header.isEnd) {
          becomeAndPull(readingChunkEnd(buffer, header), ctx)
        } else {
          become(readingChunkEnd(buffer, header))
          ctx.push(ChunkData(header, bytes))
        }
      } else {
        bytesToForward -= buffer.length
        val bytes = buffer
        buffer = ByteString()

        if (header.isEnd)
          pullIfPossible(ctx)
        else
          ctx.push(ChunkData(header, bytes))
      }
    }
  }

  def readingChunkEnd(buffer: ByteString, header: ChunkHeader): BufferedState[ChunkGrouperOutput] = new BufferedState[ChunkGrouperOutput](buffer) {
    override def process(ctx: Context[ChunkGrouperOutput]): SyncDirective = {
      waitForBytes(ctx, 4) { crc =>
        if (header.isEnd) {
          becomeAndPull(endingStream(), ctx)
        } else {
          become(readingChunkHeader(buffer))
          ctx.push(ChunkEnd(header, crc))
        }
      }
    }
  }

  def endingStream(): StageState[ByteString, ChunkGrouperOutput] = new StageState[ByteString, ChunkGrouperOutput] {
    override def onPush(elem: ByteString, ctx: Ctx): SyncDirective = {
      ctx.fail(new Exception("Unexpected data after end chunk: " + elem))
    }

    override def onPull(ctx: Ctx): SyncDirective = {
      if (ctx.isFinishing) {
        ctx.finish()
      } else {
        ctx.pull()
      }
    }
  }
}

object ChunkGrouper {
  val PngHeader = ByteString(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
}
