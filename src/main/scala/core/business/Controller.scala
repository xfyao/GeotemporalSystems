package core.business

import akka.actor.{ActorSystem, Props}
import core.actors._
import core.cache._
import core.model.{Event, Position, Trip}
import core.util.AppLogger

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
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

    val updatedTripInfoOptFuture: Future[Option[Trip]] = event.event match {
      case "begin" =>
        Future(Some(eventToTrip(event)))
      case "update" | "end" =>
        CountHash.get(event.tripId) match {
          case None =>
            // the trip could be lost due to server reboot etc. Therefore still check caching server and try to recover
            fetchAndMerge(event)
          case Some(trip) =>
            // for update event, if position is not changed since last time, we don't update
            (event.event == "end" || (event.lat != trip.curPos.lat || event.lng != trip.curPos.lng)) match {
              case true =>
                Future(Some(updatedTrip(trip, event)))
              case false => Future(None)
            }
        }
      case _ =>
        logger.info("wrong type of event, do nothing!");
        Future(None)
    }

    updatedTripInfoOptFuture onComplete {
      case Success(x) => logger.info(s"merged trip: $x")
      case Failure(ex) => logger.error(s"merged trip is failed: $event",ex)
    }

    // helper method to update trip info in KV store if necessary
    def updateTrip(updatedTripInfoOpt: Option[Trip]): Future[Unit] = {
      updatedTripInfoOpt.map { trip =>
        val posIndexFuture = for {
          retSetTrip <- Redis.setTrip(trip)
          retSetPosList <- Redis.addTripPosition(trip.tripId, trip.curPos)
        } yield (retSetPosList)

        posIndexFuture.map { _ =>
          // geohash index the finished trip data asynchronously
          if (event.event == "end") {
            logger.info(s"ack actors for finished trip: $trip")
            geoIndexFinishedTripActor ! GeoIndexFinishedTripMsg(trip)
            buildStartStopGeoCacheActor ! BuildStartStopGeoCacheMsg(trip)
          }

          // update the count hash and do it in blocking way because of low overhead
          updateCountHash(trip)
        }
      }.getOrElse(Future())
    }

    // update cache now if necessary
    for {
      updatedTripInfoOpt <- updatedTripInfoOptFuture
      ret <- updateTrip(updatedTripInfoOpt)
    } yield {
      ret
    }
  }

  private def fetchAndMerge(event: Event): Future[Option[Trip]] = {
    logger.info("try to fetch existing trip and update it with new event: $event")
    Redis.getTrip(event.tripId) map {
      case None =>
        Some(eventToTrip(event))
      case Some(trip) => trip.status match {
        case "end" => None // it handles the event out of sync situation, e.g., "end" event comes before "update" event
        case _ => Some (updatedTrip (trip, event) )
      }
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

  def getOccuringCount = CountHash.size

}
