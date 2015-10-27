package core.business

import core.cache.Redis
import core.model.{Position, Event}
import core.util.{GeoHelper, FutureHelper}
import net.liftweb.json._
import org.scalatest.FunSuite

/**
 * Created by v962867 on 10/23/15.
 */
class Controller$Test extends FunSuite with GeoHelper with FutureHelper {

  implicit val formats = DefaultFormats

  test("test json parser") {

    val jevent = "\n{\n\"event\": \"update\",\n\"tripId\": 432,\n\"lat\": 37.79947,\n\"lng\": " +
      "-122.511635,\n\"epoch\": 1392864673040\n}"
    val event = parse(jevent).extract[Event]
    assert(event.event == "update")
    assert(event.lat == 37.79947)
    assert(event.tripId == 432)
    assert(event.epoch == 1392864673040L)
  }

  test("test event to trip") {

    val event = Event(event="update", tripId=432, lat=37.79947, lng = -122.511635, fare = None, epoch = 1392864673040L)
    val trip = Controller.eventToTrip(event)
    assert(trip.status == event.event)
    assert(trip.curPos.lat == event.lat)
    assert(trip.curPos.lng == event.lng)
    assert(trip.tripId == event.tripId)
    assert(trip.lastUpdate == event.epoch)
  }

  test("test update trip with event") {

    val event1 = Event(event="begin", tripId=432, lat=37.79947, lng = -122.511635, fare = None, epoch = 1392864673040L)
    val trip1 = Controller.eventToTrip(event1)

    val event2 = Event(event="update", tripId=432, lat=37.79957, lng = -122.511655, fare = None, epoch = 1392864673440L)
    val trip2 = Controller.updatedTrip(trip1, event2)

    val event3 = Event(event="end", tripId=432, lat=37.79967, lng = -122.511675, fare = None, epoch = 1392864673540L)
    val trip3 = Controller.updatedTrip(trip2, event3)

    assert(trip3.status == event3.event)
    assert(trip3.curPos.lat == event3.lat)
    assert(trip3.curPos.lng == event3.lng)
    assert(trip3.tripId == event3.tripId)
    assert(trip3.lastUpdate == event3.epoch)
    assert(trip3.fare == event3.fare)
  }


  test("test GeoHash functions") {
    val geoHashAny = geoHash(updatePos.lat, updatePos.lng)
    assert(geoHashAny == "9q8zh")
  }

  val tripId = 432
  val beginPos = Position(37.79947, -122.511635)
  val updatePos = Position(37.79987, -122.511835)
  val endPos = Position(37.89947, -122.512635)

  test("test add event - begin") {

    val event = Event(event="begin", tripId=tripId, lat=beginPos.lat, lng =beginPos.lng, fare = None,
      epoch = 1392864673040L)

    Controller.addEvent(event)

    // for actors to finish work
    Thread.sleep(1000)

    // verify trip cache
    val tripOpt = getFutureValue(Redis.getTrip(tripId))

    assert(tripOpt != None)

    tripOpt.map { trip =>
      assert(trip.lastUpdate == event.epoch)
      assert(trip.curPos.lat == event.lat)
      assert(trip.startPos.get.lng == event.lng)
      assert(trip.stopPos == None)
      assert(trip.fare == None)
    }

    // verify trip pos cache
    val tPosSeq = getFutureValue(Redis.getTripPositions(tripId))

    val pos = tPosSeq.head

    assert(pos.lat == event.lat)
    assert(pos.lng == event.lng)
  }

  test("test add event - update") {

    val event = Event(event="update", tripId=tripId, lat=updatePos.lat, lng =updatePos.lng, fare = None,
      epoch = 1392864678040L)

    Controller.addEvent(event)

    // for actors to finish work
    Thread.sleep(1000)

    // verify trip cache
    val tripOpt = getFutureValue(Redis.getTrip(tripId))
    assert(tripOpt != None)

    tripOpt.map { trip =>
      assert(trip.lastUpdate == event.epoch)
      assert(trip.curPos.lat == event.lat)
      assert(trip.stopPos == None)
      assert(trip.startPos != None)
      assert(trip.startPos.get == beginPos)
      assert(trip.fare == None)
    }

    // verify trip position cache
    val tPosSeq = getFutureValue(Redis.getTripPositions(tripId))

    val pos = tPosSeq.head
    assert(pos.lat == event.lat)
    assert(pos.lng == event.lng)

    // verify no update situation that the lat/lnt are not changed and time stamp is changed
    // we should have update
    val event2 = Event(event="update", tripId=tripId, lat=updatePos.lat, lng=updatePos.lng, fare = None,
      epoch = 1392864679040L)

    Controller.addEvent(event2)

    // for actors to finish work
    Thread.sleep(1000)

    // verify trip cache
    val tripOpt2 = getFutureValue(Redis.getTrip(tripId))

    assert(tripOpt2 != None)

    tripOpt2.map { trip =>
      assert(trip.lastUpdate == event.epoch)
      assert(trip.lastUpdate != event2.epoch)
      assert(trip.curPos.lat == event.lat)
      assert(trip.stopPos == None)
      assert(trip.startPos != None)
      assert(trip.startPos.get == beginPos)
      assert(trip.fare == None)
    }

    val tPosSeq2 = getFutureValue(Redis.getTripPositions(tripId))
    assert(tPosSeq2.size == tPosSeq.size)
  }

  test("test add event - end") {

    val event = Event(event="end", tripId=tripId, lat=endPos.lat, lng =endPos.lng, fare = Some(34.5),
      epoch = 1392864688040L)

    Controller.addEvent(event)

    // for actors to finish work
    Thread.sleep(1000)

    // verify trip cache
    val tripOpt = getFutureValue(Redis.getTrip(tripId))

    assert(tripOpt != None)

    tripOpt.map { trip =>
      assert(trip.lastUpdate == event.epoch)
      assert(trip.curPos.lat == event.lat)
      assert(trip.stopPos != None)
      assert(trip.stopPos.get == trip.curPos)
      assert(trip.startPos != None)
      assert(trip.startPos.get == beginPos)
      assert(trip.fare != None)
      assert(trip.fare.get == event.fare.get)
    }

    // verify trip pos cache
    val tPosSeq = getFutureValue(Redis.getTripPositions(tripId))
    val pos = tPosSeq.head
    assert(pos.lat == event.lat)
    assert(pos.lng == event.lng)

    // verify the geocache for all positions. Choose any position
    val geoHashAny = geoHash(updatePos.lat, updatePos.lng)
    println("geoHashAny = " + geoHashAny)

    val tripPositionsSeq = getFutureValue(Redis.readFromGeoHash(geoHashAny, false))
    assert(tripPositionsSeq.size >= 1)
    assert(tripPositionsSeq.head.tripId == tripId)

    // verify the geocache for start/stop
    // start point
    val geoHashBegin = geoHash(beginPos.lat, beginPos.lng)
    println("geoHashBegin = " + geoHashBegin)

    val tripStartSeq = getFutureValue(Redis.readFromGeoHash(geoHashBegin, true))
    assert(tripStartSeq.size >= 1)
    assert(tripStartSeq.head.tripId == tripId)

    // end point
    val geoHashEnd = geoHash(endPos.lat, endPos.lng).toString
    println("geoHashEnd = " + geoHashEnd)

    val tripStopSeq = getFutureValue(Redis.readFromGeoHash(geoHashEnd, true))
    assert(tripStopSeq.size >= 1)
    assert(tripStopSeq.head.tripId == tripId)

  }

  test("test trip local cache and occuring trip counter")  {

    val event = Event(event="start", tripId=432, lat=37.79947, lng = -122.511635, fare = None, epoch = 1392864673040L)
    val trip = Controller.eventToTrip(event)
    Controller.updateCountHash(trip)
    assert(Controller.getOccuringCount == 1)

    val event2 = Event(event="update", tripId=432, lat=37.79957, lng = -122.511655, fare = None, epoch = 1392864673040L)
    val trip2 = Controller.updatedTrip(trip, event2)
    assert(trip2.status ==  "update")

    Controller.updateCountHash(trip2)
    assert(Controller.getOccuringCount == 1)

    val event3 = Event(event="end", tripId=432, lat=37.79967, lng = -122.511665, fare = None, epoch = 1392864673040L)
    var trip3 = Controller.updatedTrip(trip2, event3)
    assert(trip3.status == "end")

    Controller.updateCountHash(trip3)
    assert(Controller.getOccuringCount == 0)
  }
}
