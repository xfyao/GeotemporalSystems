package core.business

import akka.actor.{ActorSystem, Props}
import core.actors._
import core.cache._
import core.model.{Event, Position, Trip}
import core.util.AppLogger
import core.util.ThreadPool.ec

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * Created by v962867 on 10/23/15.
 */
object Controller extends AppLogger {

  val system = ActorSystem("GeoSystem")
  val geoIndexFinishedTripActor = system.actorOf(Props[GeoIndexFinishedTripActor], "GeoIndexFinishedTripActor")
  val buildStartStopGeoCacheActor = system.actorOf(Props[BuildStartStopGeoCacheActor], "BuildStartStopGeoCacheActor")

  /* the receiver of incoming events */
  def addEvent(event: Event) : Future[Unit] = {

    logger.info(s"receive event: $event")

    val eventPos = Position(event.lat, event.lng)

    // The triple will indicate if we need to update: local cache, remote cache for trip and remote cache for position
    def updatedTripInfoOpt(tripFromCache: Option[Trip]): (Option[Trip], Option[Trip], Option[Trip]) = {

      tripFromCache match {

        case None =>
          // very first time to receive event
          val firstTrip = eventToTrip(event)
          (Some(firstTrip), Some(firstTrip), Some(firstTrip))

        case Some(trip) =>

          // it needs to handle event out of sync cases
          val posChanged = event.lat != trip.curPos.lat || event.lng != trip.curPos.lng
          val eventPos = Position(event.lat, event.lng)
          val newTrip = updatedTrip(trip, event)

          val forLocalCache = trip.status match {
            case "end" =>
              None
            case _ =>
              Some(newTrip)
          }

          val forTripInfo = event.event match {
            case "update" =>
              None
            case "begin" | "end" =>
              Some(newTrip)
          }

          val forPositionInfo = posChanged match {
            case true =>
              Some(newTrip)
            case false =>
              None
          }

          (forLocalCache, forTripInfo, forPositionInfo)
      }
    }

    // helper method to update trip info in KV store if necessary
    def updateTrip(updatedTripInfoOpt: (Option[Trip], Option[Trip], Option[Trip])): Future[Unit] = {

      updatedTripInfoOpt._1.map {
        updateCountHash(_)
      }

      val f2 = updatedTripInfoOpt._2.map {
        Redis.setTrip(_)
      }.getOrElse(Future(""))

      val f3 = updatedTripInfoOpt._3.map { trip =>
        Redis.addTripPosition(trip.tripId, trip.curPos)
      } getOrElse(Future(0))

      for {
        x <- f2
        y <- f3
      } yield {
        // do nothing and return Unit now
      }
    }

    for {
      tripFromCache <- getTripInLocalOrRemoteCache(event.tripId)
      updatedTripInfoTriple = updatedTripInfoOpt(tripFromCache)
      updateResult <- updateTrip(updatedTripInfoTriple)
    } yield {
      // after they all done, we need to trigger actors to move data for finished trip
      val tripOpt = updatedTripInfoTriple._3
      val tripFinishedBefore = tripFromCache.map(_.status == "end").getOrElse(false)
      val tripFinishedNow = tripOpt.map(_.status == "end").getOrElse(false)

      // for finished before, we need to run actor again to index the out of sync data
      // for finished now, it is also the time to run actor to index the data
      tripOpt map { trip =>
        tripFinishedNow match {
          case true =>
            geoIndexFinishedTripActor ! GeoIndexFinishedTripMsg(trip)
            buildStartStopGeoCacheActor ! BuildStartStopGeoCacheMsg(trip)
            logger.info(s"actors for finished trip are triggered $trip")
          case false =>
            tripFinishedBefore match {
              case true =>
                geoIndexFinishedTripActor ! GeoIndexCurrentPositionMsg(trip)
                logger.info(s"actor to index out of sync pos is triggered $trip")
              case false => //nothing
            }
        }
      }

      logger.info("add event is done")
    }
  }

  def updatedTrip(origTrip: Trip, event: Event): Trip = {

    val pos = Position(event.lat, event.lng)

    event.event match {
      case "begin" =>
        Trip(tripId = event.tripId, status = event.event, startPos = Some(pos),
          curPos = pos, fare = None, lastUpdate = event.epoch)
      case "end" =>
        Trip(tripId = event.tripId, status = event.event, startPos = origTrip.startPos,
          stopPos = Some(pos), curPos = pos, fare = event.fare, lastUpdate = event.epoch)
      case _ =>
        Trip(tripId = event.tripId, status = event.event, startPos = origTrip.startPos,
          curPos = pos, fare = None, lastUpdate = event.epoch)
    }
  }

  def eventToTrip(event: Event): Trip = {

    val pos = Position(event.lat, event.lng)

    event.event match {
      case "begin" =>
        Trip(tripId = event.tripId, status = event.event, startPos = Some(pos),
          curPos = pos, fare = None, lastUpdate = event.epoch)
      case "end" =>
        Trip(tripId = event.tripId, status = event.event, stopPos = Some(pos),
          curPos = pos,fare = event.fare, lastUpdate = event.epoch)
      case _ =>
        Trip(tripId = event.tripId, status = event.event, curPos = pos, fare = None, lastUpdate = event.epoch)
    }
  }

  /** the in-memory cache to hold lastest trip info, which is used to count ocurring trips and filter duplicate events
    * the count will be aggregated quickly if the sever is rebooted.
    */
  private val CountHash = mutable.HashMap[Int, Trip]()

  def updateCountHash(trip: Trip): Unit = {
    trip.status match {
      case "end" =>
        CountHash.remove(trip.tripId)
      case _ =>
        CountHash.put(trip.tripId, trip)
    }
  }

  def cacheContains(trip: Trip): Boolean = {
    CountHash.contains(trip.tripId)
  }

  def getTripInLocalOrRemoteCache(tripId: Int): Future[Option[Trip]] = {
    CountHash.get(tripId) match {
      case None =>
        val tripFuture = Redis.getTrip(tripId)
        tripFuture.map { tripOpt =>
          tripOpt.map { trip =>
            trip.status match {
              case "end" =>
              // for ended trip, don't fill cache anymore
              case _ =>
                CountHash.put(trip.tripId, trip)
            }
          }
          tripOpt
        }
      case Some(trip) =>
        Future(Some(trip))
    }
  }

  def getOccuringCount = CountHash.size

}
