package thumbnailer.png.stages

import akka.stream.stage.{PushStage, SyncDirective, Context}
import akka.util.ByteString
import thumbnailer.png.datatypes.Filter.{SubFilter, PaethFilter}
import thumbnailer.png.datatypes.{Line, Filter}
import thumbnailer.utils.ByteUtils

class FilteringStage(bytesPerPixel: Int, singleFilter: Option[Filter] = None) extends PushStage[Line, Line] {

  var previousLineData: Option[ByteString] = None

  override def onPush(line: Line, ctx: Context[Line]): SyncDirective = {
    require(line.filter == Filter.NoFilter, s"Cannot filter a line unless it has no filter")
    val (chosenFilter, filteredData) = singleFilter match {
      case Some(filter) => tryFilter(filter, line.bytes)
      case None => tryAllFilters(line.bytes)
    }

    previousLineData = Some(line.bytes)
    ctx.push(Line(chosenFilter, filteredData))
  }

  def tryFilter(filter: Filter, data: ByteString): (Filter, ByteString) = {
    val filteredData = filter.filterData(
      data,
      previousLineData.getOrElse(ByteString(new Array[Byte](data.length))),
      bytesPerPixel)
    (filter, filteredData)
  }

  /**
   * Try all the filters and select the one minimizing the sum of the absolute values of bytes.
   *
   * (This is a reasonably good heuristic for finding the best filter)
   */
  def tryAllFilters(data: ByteString): (Filter, ByteString) = {
    List(Filter.NoFilter, Filter.SubFilter, Filter.UpFilter, Filter.AverageFilter, Filter.PaethFilter)
      .map(tryFilter(_, data))
      .minBy(_._2.map(b => Math.abs(b.toInt)).sum)
  }
}
