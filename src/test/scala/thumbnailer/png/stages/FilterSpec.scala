package thumbnailer.png.stages

import akka.util.ByteString
import org.specs2.mutable.Specification
import thumbnailer.png.datatypes.Filter
import thumbnailer.png.datatypes.Filter._

class FilterSpec extends Specification {

  def testFilter(filter: Filter, previous: ByteString, raw: ByteString, filtered: ByteString) = {
    filter.getClass.getSimpleName should {
      "filter data correctly" in {
        filter.filterData(raw, previous, 1) mustEqual filtered
      }

      "unfilter data correctly" in {
        filter.unfilterData(filtered, previous, 1) mustEqual raw
      }
    }
  }

  testFilter(
    NoFilter,
    previous = ByteString(0x01, 0x02, 0x03, 0x04, 0x05),
    raw =      ByteString(0x01, 0x02, 0x03, 0x04, 0x05),
    filtered = ByteString(0x01, 0x02, 0x03, 0x04, 0x05))

  testFilter(
    SubFilter,
    previous = ByteString(0x01, 0x02, 0x03, 0x04, 0x05),
    raw =      ByteString(0x10, 0x10, 0x0F, 0x0F, 0x0F),
    filtered = ByteString(0x10, 0x00, 0xFF, 0x00, 0x00))

  testFilter(
    UpFilter,
    previous = ByteString(0x00, 0x20, 0x10, 0x00, 0x00),
    raw =      ByteString(0x10, 0x20, 0x0F, 0x00, 0x00),
    filtered = ByteString(0x10, 0x00, 0xFF, 0x00, 0x00))

  testFilter(
    AverageFilter,
    previous = ByteString(0x00, 0x20, 0x10, 0x00, 0xFF),
    raw =      ByteString(0x10, 0x18, 0x13, 0x09, 0x04),
    filtered = ByteString(0x10, 0x00, 0xFF, 0x00, 0x80))

  testFilter(
    PaethFilter,
    previous = ByteString(0x10, 0x10, 0x1A, 0x05, 0x05),
    raw =      ByteString(0x01, 0x05, 0x1A, 0xFF, 0x01),
    filtered = ByteString(0xF1, 0x04, 0x0A, 0xFA, 0x02))
}
