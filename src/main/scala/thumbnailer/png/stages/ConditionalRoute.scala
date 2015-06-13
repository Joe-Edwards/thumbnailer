package thumbnailer.png.stages

import akka.stream.scaladsl.{BidiFlow, Merge, FlowGraph, FlexiRoute}
import akka.stream.scaladsl.FlexiRoute.{DemandFromAll, RouteLogic}
import akka.stream.{BidiShape, FanOutShape2, OperationAttributes}
import thumbnailer.png.datatypes.{Chunk, ChunkHeader}

/**
 * FlexiRoute that sends an element to out1 when the condition is true, and out0 when false.
 */
class ConditionalRoute[T](condition: T => Boolean) extends FlexiRoute[T, FanOutShape2[T, T, T]](
    new FanOutShape2[T, T, T]("FilterRoute"),
    OperationAttributes.name("FilterRoute")) {

  override def createRouteLogic(s: FanOutShape2[T, T, T]): RouteLogic[T] = new RouteLogic[T] {

    override def initialState: State[_] = State(DemandFromAll(s)) { (ctx, _, element) =>
      if (condition(element)) {
        ctx.emit(s.out1)(element)
        SameState
      } else {
        ctx.emit(s.out0)(element)
        SameState
      }
    }
  }
}

object ConditionalRoute {

  /**
   * A corresponding [[BidiFlow]] that forwards elements that meet the condition
   * and simply loops back elements that do not.
   */
  def bidiFlow[T](condition: T => Boolean) = BidiFlow() { implicit b =>
    import FlowGraph.Implicits._

    val split = b.add(new ConditionalRoute[T](condition))
    val merge = b.add(Merge[T](2))

    split.out0 ~> merge.in(0)

    BidiShape(split.in, split.out1, merge.in(1), merge.out)
  }
}
