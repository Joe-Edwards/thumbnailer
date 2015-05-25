package thumbnailer

import java.io.File
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.stream.ActorFlowMaterializer
import akka.stream.io.{SynchronousFileSink, SynchronousFileSource}
import thumbnailer.png.PngFlows._
import thumbnailer.png.stages.{AverageScalingStage, DownsamplingStage}

import scala.concurrent.{Future, Await}
import scala.concurrent.duration._

object App {
  def main(args: Array[String]): Unit = {

    val usageText = "--in <input file> [--out <output file>] [--scalewidth <width scaling>] [--scaleheight <height scaling>]"

    def usage(): Nothing = { println(usageText); System.exit(1); throw new Exception() }

    def parseOptions(args: List[String] = args.toList, map: Map[String, String] = Map()): Map[String, String] = {
      args match {
        case Nil => map
        case "--in" :: file :: tail => parseOptions(tail, map + ("i" -> file))
        case "--out" :: file :: tail => parseOptions(tail, map + ("o" -> file))
        case "--scalewidth" :: widthScale :: tail => parseOptions(tail, map + ("w" -> widthScale))
        case "--scaleheight" :: heightScale :: tail => parseOptions(tail, map + ("h" -> heightScale))
        case _ => usage()
      }
    }

    val options = parseOptions()

    val inFile = new File(options.getOrElse("i", usage()))
    val outFile = new File(options.getOrElse("o", "output.png"))
    val scaleWidth = options.getOrElse("w", "2").toInt
    val scaleHeight = options.getOrElse("h", "2").toInt

    print("Thumbnailing file...")

    val startTime = System.currentTimeMillis()

    implicit val system = ActorSystem()
    implicit val materializer = ActorFlowMaterializer()
    implicit val executionContext = system.dispatcher

    val inputSource = SynchronousFileSource(inFile)
    val outputSink = SynchronousFileSink(outFile)

    val resizeLogic = new AverageScalingStage.Logic(scaleWidth, scaleHeight)

    val (inputLengthFuture, outputLengthFuture) = inputSource
      .via(chunker)
      .via(mandatoryChunkFilter)
      .resize(resizeLogic)
      .via(deChunker)
      .toMat(outputSink)((_, _))
      .run()

    val Seq(inputLength, outputLength) = Await.result(Future.sequence(Seq(inputLengthFuture, outputLengthFuture)), 60.seconds)

    val endTime = System.currentTimeMillis()
    val timeTaken = FiniteDuration(endTime - startTime, TimeUnit.MILLISECONDS)

    println(" Complete")
    println(s"Read $inputLength bytes from ${inFile.getAbsolutePath}")
    println(s"Wrote $outputLength bytes to ${outFile.getAbsolutePath}")
    println(s"Took $timeTaken")
    system.shutdown()
  }
}
