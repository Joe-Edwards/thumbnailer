package thumbnailer

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, Route}
import akka.pattern.after
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import thumbnailer.png.PngFlows._
import thumbnailer.png.stages.AverageScalingStage

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
      pathPrefix("resize") {
        post {
          entity(as[Multipart.FormData]) { formdata =>
            onSuccess(parseFormdata(formdata)) { case (widthscale, heightscale, file) =>
              complete(resizeToEntity(file, widthscale, heightscale))
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

  def parseFormdata(formData: Multipart.FormData): Future[(Int, Int, Source[ByteString, _])] = {
    formData.parts.prefixAndTail(3).runWith(Sink.head).flatMap {
      case (Seq(widthpart, heightPart, filePart), tail) =>
        for {
          width <- parseIntPart("widthscale", widthpart)
          height <- parseIntPart("heightscale", heightPart)
        } yield {
          (width, height, parseFilePart("in", filePart))
        }
      case _ => throw new Exception("Invalid form data")
    }
  }

  def parseIntPart(expectedName: String, part: Multipart.FormData.BodyPart): Future[Int] = {
    if (part.name != expectedName)
      throw new Exception("Invalid form data")
    else
      part.toStrict(2.seconds).map(_.entity.data.utf8String.toInt)
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

  def resizeToEntity(source: Source[ByteString, _], widthscale: Int, heightScale: Int): HttpEntity.Chunked = {
    val resizeLogic = new AverageScalingStage.Logic(widthscale, heightScale)

    HttpEntity.Chunked.fromData(MediaTypes.`image/png`, source.resizePngData(resizeLogic))
  }
}
