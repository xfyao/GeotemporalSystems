package http

import akka.actor.Props
import core.actors.SubscribeActor
import core.business.{Controller, QueryHandler}
import core.model.{GeoRec, Event, Position, TripsSummary}
import core.util.ServerConfig
import org.json4s._
import org.json4s.native.Serialization.write
import spray.httpx.Json4sSupport
import spray.routing.HttpService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

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
  val channelName = ServerConfig.getConfig.getString("redis.geochannel")
  val channels = Seq(channelName)
  val patterns = Seq()

  def onConnectStatus(connected: Boolean) = {
    logger.info("connected: " + connected)
  }

  // create SubscribeActor instance
  actorRefFactory.actorOf(Props(classOf[SubscribeActor], channels, patterns, onConnectStatus _))

  val route = {
    path("countTrips" / Segment / Segment) { (bottomLeft, topRight) =>
      get {
        val bl = bottomLeft.split(",")
        val tr = topRight.split(",")
        logger.info(s"countTrips Request: $bottomLeft $topRight")

        val geoRec = GeoRec(Position(bl(0).toDouble, bl(1).toDouble), Position(tr(0).toDouble, tr(1).toDouble))
        onComplete[TripsSummary](QueryHandler.countAllPosInGeoRec(geoRec)) {
          case Success(tripsSummary) =>
            logger.info(s"countTrips Return: $tripsSummary")
            complete(Return("ok", None, Some("{\"count\":" + tripsSummary.totalCount + "}")))
          case Failure(ex) =>
            logger.error("countTrips internal error", ex)
            complete(Return("error", Some(ex.getMessage), None))
        }
      }
    } ~ path("countTripsForStartAndStop" / Segment / Segment) { (bottomLeft, topRight) =>
      get {
        val bl = bottomLeft.split(",")
        val tr = topRight.split(",")
        logger.info(s"countTripsForStartAndStop Request: $bottomLeft $topRight")

        val geoRec = GeoRec(Position(bl(0).toDouble, bl(1).toDouble), Position(tr(0).toDouble, tr(1).toDouble))
        onComplete[TripsSummary](QueryHandler.countStartAndStopInGeoRec(geoRec)) {
          case Success(tripsSummary) =>
            logger.info(s"countTripsForStartAndStop Return: $tripsSummary")
            complete(Return("ok", None, Some(write(tripsSummary))))
          case Failure(ex) =>
            logger.error("countTripsForStartAndStop internal error", ex)
            complete(Return("error", Some(ex.getMessage), None))
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
          val e = event.extract[Event]
          onComplete(Controller.addEvent(e)) {
            case Success(x) =>
              complete(Return("ok", None, None))
            case Failure(ex) =>
              logger.error("addEvent internal error", ex)
              complete(Return("error", Some(ex.getMessage), None))
          }
        }
      }
    }
  }
}
