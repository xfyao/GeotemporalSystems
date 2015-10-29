package core.actors

import akka.actor.{Actor, ActorLogging}
import core.cache.{StartStopIdGen, Redis}
import core.model.{SimpleTrip, Trip}
import core.util.{AppLogger, FutureHelper, GeoHelper}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

/**
 * Created by v962867 on 10/23/15.
 */
case class BuildStartStopGeoCacheMsg(trip: Trip)

class BuildStartStopGeoCacheActor extends Actor with ActorLogging with GeoHelper with FutureHelper with AppLogger {

  def receive = {

    case BuildStartStopGeoCacheMsg(trip) =>
      val startFutureOpt = trip.startPos.map { pos =>
        val geoHashStr = geoHash(pos.lat, pos.lng)
        Redis.addToGeoHash(geoHashStr, SimpleTrip(trip.tripId, trip.fare.getOrElse(0)), StartStopIdGen)
      }

      val stopFutureOpt = trip.stopPos.map { pos =>
        val geoHashStr = geoHash(pos.lat, pos.lng)
        Redis.addToGeoHash(geoHashStr, SimpleTrip(trip.tripId, trip.fare.getOrElse(0)), StartStopIdGen)
      }

      // blocking here to wait threads to finish
      startFutureOpt.map { f => f.onComplete {
        case Success(x) => logger.info(s"index start pos successfully: $trip")
        case Failure(ex) => logger.error(s"index start pos failed: $trip", ex)
      }
        getFutureValue(f)
      }

      stopFutureOpt.map { f => f.onComplete {
        case Success(x) => logger.info(s"index start pos successfully: $trip $x")
        case Failure(ex) => logger.error(s"index start pos failed: $trip", ex)
      }
        getFutureValue(f)
      }
    case _ => logger.info("unexpected message")
  }
}

