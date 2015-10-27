package http

import akka.actor.Props
import core.actors.SubscribeActor
import core.business.{Controller, QueryHandler}
import core.model.{Event, Position}
import org.json4s._
import org.json4s.native.Serialization.write
import spray.httpx.Json4sSupport
import spray.routing.HttpService

/**
 * Created by v962867 on 10/25/15.
 */
object Json4sProtocol extends Json4sSupport {
  implicit def json4sFormats: Formats = DefaultFormats
}

case class Return(code: String, msg: Option[String], ret: Option[String])

trait Server extends HttpService with HttpLogger {

  import Json4sProtocol._

  // subscribe to Redis messag
  val channels = Seq("geoevent")
  val patterns = Seq()

  def onConnectStatus(connected: Boolean) = {
    println("connected: " + connected)
  }

  // create SubscribeActor instance
  actorRefFactory.actorOf(Props(classOf[SubscribeActor], channels, patterns, onConnectStatus _))

  val route = {
    path("countTrips" / Segment / Segment) { (bottomLeft, topRight) =>
      get {
        complete {
          try {
            val bl = bottomLeft.split(",")
            val tr = topRight.split(",")
            logger.info(s"countTrips Request: $bottomLeft $topRight")
            val tripsSummary = QueryHandler.countAllPosInGeoRec(Position(bl(0).toDouble, bl(1).toDouble),
              Position(tr(0).toDouble, tr(1).toDouble))
            logger.info(s"countTrips Return: $tripsSummary")
            Return("ok", None, Some("{\"count\":" + tripsSummary.totalCount + "}"))
          } catch {
            case ex: Throwable =>
              logger.error("countTrips internal error", ex)
              Return("error", Some(ex.getMessage), None)
          }
        }
      }
    } ~ path("countTripsForStartAndStop" / Segment / Segment) { (bottomLeft, topRight) =>
      get {
        complete {
          try {
            val bl = bottomLeft.split(",")
            val tr = topRight.split(",")
            logger.info(s"countTripsForStartAndStop Request: $bottomLeft $topRight")
            val ret = QueryHandler.countStartAndStopInGeoRec(Position(bl(0).toDouble, bl(1).toDouble),
              Position(tr(0).toDouble, tr(1).toDouble))
            logger.info(s"countTripsForStartAndStop Return: $ret")
            Return("ok", None, Some(write(ret)))
          } catch {
            case ex: Throwable =>
              logger.error("countTripsForStartAndStop internal error", ex)
              Return("error", Some(ex.getMessage), None)
          }
        }
      }
    } ~ path("countOcurringTrips") {
      get {
        complete {
          try {
            val count = QueryHandler.countOccuringTrips()
            logger.info(s"countOcurringTrips: $count")
            Return("ok", None, Some("{\"count\":" + count + "}"))
          } catch {
            case ex: Throwable =>
              logger.error("countOcurringTrips internal error", ex)
              Return("error", Some(ex.getMessage), None)
          }
        }
      }
    } ~ path("addEvent") {
      post {
        entity(as[JObject]) { event =>
          logger.info(s"addEvent: $event")
          complete {
            try {
              val e = event.extract[Event]
              Controller.addEvent(e)
              Return("ok", None, None)
            } catch {
              case ex: Throwable =>
                logger.error("addEvent internal error", ex)
                Return("error", Some(ex.getMessage), None)
            }
          }
        }
      }
    }
  }
}
