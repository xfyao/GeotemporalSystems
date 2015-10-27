package core.business

import akka.actor.{ActorSystem, Props}
import core.actors._
import core.cache._
import core.model.{Event, Position, Trip}
import core.util.FutureHelper._
import core.util.GeoHelper
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Created by v962867 on 10/23/15.
 */
object Controller {

  val logger = LoggerFactory.getLogger(this.getClass)
  val system = ActorSystem("GeoSystem")
  val geoIndexFinishedTripActor = system.actorOf(Props[GeoIndexFinishedTripActor], "GeoIndexFinishedTripActor")
  val buildStartStopGeoCacheActor = system.actorOf(Props[BuildStartStopGeoCacheActor], "BuildStartStopGeoCacheActor")

  /* the receiver of incoming events */
  def addEvent(event: Event) : Unit = {

    logger.info(s"receive event: $event")

    val updatedTripInfoOpt = event.event match {
      case "begin" =>
        Some(eventToTrip(event))
      case "update" | "end" =>
        CountHash.get(event.tripId) match {
          case None =>
            // the trip could be lost due to server reboot etc. Therefore still check caching server and try to recover
            fetchAndMerge(event)
          case Some(trip) =>
            // for update event, if position is not changed since last time, we don't update
            (event.event == "end" || (event.lat != trip.curPos.lat || event.lng != trip.curPos.lng)) match {
              case true =>
                Some(updatedTrip(trip, event))
              case false => None
            }
        }
      case _ =>
        logger.info("wrong type of event, do nothing!");
        None
    }

    logger.info(s"updated trip info: $updatedTripInfoOpt")

    // update cache now if necessary
    updatedTripInfoOpt.map{ trip =>
      val geoHash = GeoHelper.geoHash(trip.curPos.lat, trip.curPos.lng)
      val posIndexFuture = for {
        retSetTrip <- Redis.setTrip(trip)
        retSetPosList <- Redis.addTripPosition(trip.tripId, trip.curPos)
      } yield (retSetPosList)

      // blocking for futures to finish
      val posIndex = getFutureValue(posIndexFuture)

      // geohash index the finished trip data asynchronously
      if (event.event == "end") {
        logger.info(s"ack actors for finished trip: $trip")
        geoIndexFinishedTripActor ! GeoIndexFinishedTripMsg(trip)
        buildStartStopGeoCacheActor ! BuildStartStopGeoCacheMsg(trip)
      }

      // update the count hash and do it in blocking because of low overhead
      updateCountHash(trip)
    }
  }

  private def fetchAndMerge(event: Event): Option[Trip] = {

    logger.info("try to fetch existing trip and update it with new event: $event")

    getFutureValue(Redis.getTrip(event.tripId)) match {
      case None =>
        Some(eventToTrip(event))
      case Some(trip) =>
        Some(updatedTrip(trip, event))
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
