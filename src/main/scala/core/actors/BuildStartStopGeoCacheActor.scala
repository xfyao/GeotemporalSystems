package core.actors

import akka.actor.{Actor, ActorLogging}
import core.cache.Redis
import core.model.{SimpleTrip, Trip}
import core.util.{FutureHelper, GeoHelper}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

/**
 * Created by v962867 on 10/23/15.
 */
case class BuildStartStopGeoCacheMsg(trip: Trip)

class BuildStartStopGeoCacheActor extends Actor with ActorLogging {

  val logger = LoggerFactory.getLogger(this.getClass)

  def receive = {

    case BuildStartStopGeoCacheMsg(trip) =>
      val startFutureOpt = trip.startPos.map { pos =>
        val geoHash = GeoHelper.geoHash(pos.lat, pos.lng)
        Redis.addToGeoHash(geoHash, SimpleTrip(trip.tripId, trip.fare.getOrElse(0)), true)
      }

      val stopFutureOpt = trip.stopPos.map { pos =>
        val geoHash = GeoHelper.geoHash(pos.lat, pos.lng)
        Redis.addToGeoHash(geoHash, SimpleTrip(trip.tripId, trip.fare.getOrElse(0)), true)
      }

      // blocking here to wait threads to finish
      startFutureOpt.map { f => f.onComplete {
        case Success(x) => logger.info(s"index start pos successfully: $trip")
        case Failure(ex) => logger.error(s"index start pos failed: $trip", ex)
      }
        FutureHelper.getFutureValue(f)
      }

      stopFutureOpt.map { f => f.onComplete {
        case Success(x) => logger.info(s"index start pos successfully: $trip $x")
        case Failure(ex) => logger.error(s"index start pos failed: $trip", ex)
      }
        FutureHelper.getFutureValue(f)
      }
    case _ => logger.info("unexpected message")
  }
}

