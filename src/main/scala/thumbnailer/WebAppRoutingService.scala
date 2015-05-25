package thumbnailer

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, Route}
import akka.pattern.after
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import thumbnailer.png.PngFlows._
import thumbnailer.png.ScalingLogic
import thumbnailer.png.stages.{DownsamplingStage, AverageScalingStage}

import scala.concurrent.Future
import scala.concurrent.duration._

class WebAppRoutingService(implicit system: ActorSystem, materializer: FlowMaterializer) extends Directives {
  import system.dispatcher

  val shutdownTimeout = 5.seconds

  def route: Route = {
    mapRequest(logRequest) {
      pathEndOrSingleSlash {
        getFromResource("home.html")
      } ~
      path("thumbnail.js") {
        getFromResource("thumbnail.js")
      } ~
      path("thumbnailer.css") {
        getFromResource("thumbnailer.css")
      } ~
      pathPrefix("resize") {
        post {
          entity(as[Multipart.FormData]) { formdata =>
            onSuccess(parseFormdata(formdata)) { case (logic, file) =>
              complete(resizeToEntity(logic, file))
            }
          }
        }
      } ~
      pathPrefix("shutdown") {
        after(5.seconds, system.scheduler)(Future.successful(system.shutdown()))
        complete(s"Shutting Down in $shutdownTimeout")
      }
    }
  }

  def parseFormdata(formData: Multipart.FormData): Future[(ScalingLogic, Source[ByteString, _])] = {
    formData.parts.prefixAndTail(4).runWith(Sink.head).flatMap {
      case (Seq(widthpart, heightPart, scaleTypePart, filePart), tail) =>
        for {
          width <- parseIntPart("widthscale", widthpart)
          height <- parseIntPart("heightscale", heightPart)
          scaletype <- parseStringPart("scaletype", scaleTypePart)
        } yield {
          (getLogic(width, height, scaletype), parseFilePart("in", filePart))
        }
      case _ => throw new Exception("Invalid form data")
    }
  }

  def getLogic(width: Int, height: Int, scaleType: String) = scaleType match {
    case "downsample" => new DownsamplingStage.Logic(width, height)
    case "average" => new AverageScalingStage.Logic(width, height)
    case _ => throw new Exception("Unknown scaling type " + scaleType)
  }

  def parseStringPart(expectedName: String, part: Multipart.FormData.BodyPart): Future[String] = {
    if (part.name != expectedName)
      throw new Exception("Invalid form data")
    else
      part.toStrict(2.seconds).map(_.entity.data.utf8String)
  }

  def parseIntPart(expectedName: String, part: Multipart.FormData.BodyPart): Future[Int] = {
    parseStringPart(expectedName, part).map(_.toInt)
  }

  def parseFilePart(expectedName: String, part: Multipart.FormData.BodyPart): Source[ByteString, _] = {
    if ((part.name != expectedName) || part.filename.isEmpty)
      throw new Exception("Invalid form data")
    else
      part.entity.dataBytes
  }

  def logRequest(request: HttpRequest): HttpRequest = {
    println("Got request " + request)
    request
  }

  def resizeToEntity(logic: ScalingLogic, source: Source[ByteString, _]): HttpEntity.Chunked = {
    HttpEntity.Chunked.fromData(MediaTypes.`image/png`, source.resizePngData(logic))
  }
}
