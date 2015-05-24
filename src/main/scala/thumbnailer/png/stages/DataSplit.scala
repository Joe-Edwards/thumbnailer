package thumbnailer.png.stages

import akka.stream.scaladsl.FlexiRoute
import akka.stream.scaladsl.FlexiRoute.{DemandFromAll, RouteLogic}
import akka.stream.{FanOutShape2, OperationAttributes}
import thumbnailer.png.datatypes.{Chunk, ChunkHeader}

class DataSplit extends FlexiRoute[Chunk, FanOutShape2[Chunk, Chunk, Chunk]](
    new FanOutShape2[Chunk, Chunk, Chunk]("DataSplit"),
    OperationAttributes.name("DataSplit")) {

  override def createRouteLogic(s: FanOutShape2[Chunk, Chunk, Chunk]): RouteLogic[Chunk] = new RouteLogic[Chunk] {

    override def initialState: State[_] = State(DemandFromAll(s)) { (ctx, _, chunk) =>
      chunk match {
        case Chunk(ChunkHeader("IDAT", _), _) =>
          ctx.emit(s.out1)(chunk)
        case _ =>
          ctx.emit(s.out0)(chunk)
      }
      SameState
    }
  }
}
