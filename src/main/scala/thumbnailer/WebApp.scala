package thumbnailer

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorFlowMaterializer

import scala.util.{Failure, Success}

object WebApp {
  def main(args: Array[String]): Unit = {

    implicit val system = ActorSystem()
    implicit val materializer = ActorFlowMaterializer()
    implicit val executionContext = system.dispatcher

    val http = Http()
    val routing = new WebAppRoutingService()

    val bindingFuture = http.bindAndHandleAsync(Route.asyncHandler(routing.route), "0.0.0.0", 8080)

    bindingFuture.onComplete {
      case Success(binding) =>
        println(s"Bound to ${binding.localAddress}")
      case Failure(e) =>
        println(s"Failed to bind")
        throw e
    }
  }
}
