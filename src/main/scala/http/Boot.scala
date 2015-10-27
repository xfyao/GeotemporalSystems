package http

/**
 * Created by v962867 on 10/25/15.
 */

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.io.IO
import spray.can.Http

object Boot extends App with HttpLogger {

  implicit val system = ActorSystem("akka-system")

  /* Use Akka to create our Spray Service */
  val service = system.actorOf(Props[ServerActor], "geoservice")

  /* and bind to Akka's I/O interface */
  IO(Http) ! Http.Bind(service, "0.0.0.0",  8081)

  logger.info("http server is started.")

}

class ServerActor extends Actor with Server with ActorLogging {
  def actorRefFactory = context
  def receive = runRoute(route)
}