package core.actors

import akka.actor.{Actor, ActorLogging}
import core.cache.Redis
import core.model.{SimpleTrip, Trip}
import core.util.{AppLogger, FutureHelper, GeoHelper}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * Created by v962867 on 10/23/15.
 */
case class GeoIndexFinishedTripMsg(trip: Trip)

class GeoIndexFinishedTripActor extends Actor with ActorLogging with GeoHelper with FutureHelper with AppLogger {

  def receive = {
    case GeoIndexFinishedTripMsg(trip) =>

      // get position list
      val listPos = getFutureValue(Redis.getTripPositions(trip.tripId))
      val geoGroup = listPos.groupBy(pos => geoHash(pos.lat, pos.lng))

      // introduce parallelism here
      val futureList: List[Future[Long]] = geoGroup.map { case (hashKey, listPos) =>
        Redis.addToGeoHash(hashKey, SimpleTrip(trip.tripId, trip.fare.getOrElse(0)), false)}.toList

      val futureOfList = Future.sequence(futureList)

      futureOfList onComplete {
        case Success(x) => logger.info(s"index all pos successfully: $trip $x")
        case Failure(ex) => logger.error(s"index all pos failed: $trip", ex)
      }

      // block here to wait for all threads finished
      getFutureValue(futureOfList)

    case _ => logger.info("unexpected message")
  }

}
