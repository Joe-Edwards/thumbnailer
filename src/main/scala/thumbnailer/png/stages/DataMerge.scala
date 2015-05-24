package thumbnailer.png.stages

import akka.stream.scaladsl.FlexiMerge.{ReadAny, MergeLogic}
import akka.stream.{OperationAttributes, FanInShape2}
import akka.stream.scaladsl.FlexiMerge
import thumbnailer.png.datatypes.{ChunkHeader, Chunk}

class DataMerge extends FlexiMerge[Chunk, FanInShape2[Chunk, Chunk, Chunk]](
  new FanInShape2[Chunk, Chunk, Chunk]("DataMerge"),
  OperationAttributes.name("DataMerge")
) {
  override def createMergeLogic(s: FanInShape2[Chunk, Chunk, Chunk]): MergeLogic[Chunk] = new MergeLogic[Chunk] {

    override def initialCompletionHandling: CompletionHandling = CompletionHandling(
      onUpstreamFinish = {
        case (ctx, s.in0) => SameState
        case (ctx, s.in1) => SameState
      },
      onUpstreamFailure = {
        case (ctx, _, cause) => ctx.fail(cause); SameState
      }
    )

    override def initialState: State[_] = State(ReadAny(s)) { (ctx, _, elem) =>
      elem match {
        case Chunk(ChunkHeader("IEND", _), _) =>
        case chunk: Chunk => ctx.emit(chunk)
      }
      SameState
    }
  }
}
