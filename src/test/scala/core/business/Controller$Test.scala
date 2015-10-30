package core.business

import core.cache.{AllPosIdGen, StartStopIdGen, Redis}
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

  test("test trip local cache and occuring trip counter (1) in sync begin-update-end")  {

    val tripId = 462

    val event = Event(event="start", tripId=tripId, lat=31.79947, lng = -122.511635, fare = None, epoch = 1392864673040L)

    val trip = Controller.eventToTrip(event)

    Controller.addEvent(event)

    // for actors to finish work
    Thread.sleep(1000)

    assert(Controller.cacheContains(trip) == true)

    val event2 = Event(event="update", tripId=tripId, lat=31.79957, lng = -122.511655, fare = None, epoch = 1392864673040L)

    Controller.addEvent(event2)

    // for actors to finish work
    Thread.sleep(1000)

    assert(Controller.cacheContains(trip) == true)

    val event3 = Event(event="end", tripId=tripId, lat=31.79967, lng = -122.511665, fare = None, epoch = 1392864673040L)

    Controller.addEvent(event3)

    // for actors to finish work
    Thread.sleep(1000)

    assert(Controller.cacheContains(trip) == false)

  }


  test("test trip local cache and occuring trip counter (2) out of sync begin-end-update")  {
    val tripId = 555
    val event = Event(event="start", tripId=tripId, lat=31.79947, lng = -122.511635, fare = None, epoch = 1392864673040L)
    val trip = Controller.eventToTrip(event)

    Controller.addEvent(event)

    // for actors to finish work
    Thread.sleep(1000)

    assert(Controller.cacheContains(trip) == true)

    val event3 = Event(event="end", tripId=tripId, lat=31.79967, lng = -122.511665, fare = None, epoch = 1392864673040L)

    Controller.addEvent(event3)

    // for actors to finish work
    Thread.sleep(1000)

    assert(Controller.cacheContains(trip) == false)


    val event2 = Event(event="update", tripId=tripId, lat=31.79957, lng = -122.511655, fare = None, epoch = 1392864673040L)

    Controller.addEvent(event2)

    // for actors to finish work
    Thread.sleep(1000)

    assert(Controller.cacheContains(trip) == false)

  }

  test("test trip local cache and occuring trip counter (3) out of sync update-begin-end")  {
    val tripId = 488

    val event2 = Event(event="update", tripId=tripId, lat=31.79957, lng = -122.511655, fare = None, epoch = 1392864673040L)

    val trip = Controller.eventToTrip(event2)

    Controller.addEvent(event2)

    // for actors to finish work
    Thread.sleep(1000)

    assert(Controller.cacheContains(trip) == true)

    val event = Event(event="start", tripId=tripId, lat=31.79947, lng = -122.511635, fare = None, epoch = 1392864673040L)

    Controller.addEvent(event)

    // for actors to finish work
    Thread.sleep(1000)

    assert(Controller.cacheContains(trip) == true)

    val event3 = Event(event="end", tripId=tripId, lat=31.79967, lng = -122.511665, fare = None, epoch = 1392864673040L)

    Controller.addEvent(event3)

    // for actors to finish work
    Thread.sleep(1000)

    assert(Controller.cacheContains(trip) == false)
  }

  val tripId = 432
  val beginPos = Position(37.79947, -122.511635)
  val updatePos = Position(37.79987, -122.511835)
  val endPos = Position(37.89947, -122.512635)

  test("test in sync events Step 1: begin") {

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

  test("test in sync events Step 2: begin - update") {

    val event = Event(event="update", tripId=tripId, lat=updatePos.lat, lng =updatePos.lng, fare = None,
      epoch = 1392864678040L)

    Controller.addEvent(event)

    // for actors to finish work
    Thread.sleep(1000)

    // we update local cache, positions but not trip
    val tripOpt = getFutureValue(Redis.getTrip(tripId))
    val tripLocal = getFutureValue(Controller.getTripInLocalOrRemoteCache(tripId)).get

    assert(tripOpt != None)

    tripOpt.map { trip =>
      assert(tripLocal.lastUpdate == event.epoch)
      assert(tripLocal.curPos.lat == event.lat)
      assert(trip.lastUpdate != event.epoch)
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
    // we should have no update at all except local cache
    val event2 = Event(event="update", tripId=tripId, lat=updatePos.lat, lng=updatePos.lng, fare = None,
      epoch = 1392864679040L)

    Controller.addEvent(event2)

    // for actors to finish work
    Thread.sleep(1000)

    // verify trip cache
    val tripOpt2 = getFutureValue(Redis.getTrip(tripId))
    val tripLocal2 = getFutureValue(Controller.getTripInLocalOrRemoteCache(tripId)).get

    assert(tripOpt2 != None)

    tripOpt2.map { trip =>
      assert(tripLocal2.lastUpdate == event2.epoch)
      assert(trip.lastUpdate != event2.epoch)
    }

    val tPosSeq2 = getFutureValue(Redis.getTripPositions(tripId))
    assert(tPosSeq2.size == tPosSeq.size)
  }

  test("test in sync events Step 3: begin - update - end") {

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

    val tripPositionsSeq = getFutureValue(Redis.readFromGeoHash(geoHashAny, AllPosIdGen))
    assert(tripPositionsSeq.size >= 1)
    assert(tripPositionsSeq.head.tripId == tripId)

    // verify the geocache for start/stop
    // start point
    val geoHashBegin = geoHash(beginPos.lat, beginPos.lng)
    println("geoHashBegin = " + geoHashBegin)

    val tripStartSeq = getFutureValue(Redis.readFromGeoHash(geoHashBegin, StartStopIdGen))
    assert(tripStartSeq.filter(_.tripId == tripId).size == 1)

    // end point
    val geoHashEnd = geoHash(endPos.lat, endPos.lng).toString
    println("geoHashEnd = " + geoHashEnd)

    val tripStopSeq = getFutureValue(Redis.readFromGeoHash(geoHashEnd, StartStopIdGen))
    assert(tripStopSeq.filter(_.tripId == tripId).size == 1)

  }

  val tripId2 = 678

  test("test out of sync events (1) Step 1: update ") {

    // Update event
    val event = Event(event="update", tripId=tripId2, lat=updatePos.lat, lng =updatePos.lng, fare = None,
      epoch = 1392864673040L)

    Controller.addEvent(event)

    // for actors to finish work
    Thread.sleep(1000)

    // verify trip cache
    val tripOpt = getFutureValue(Redis.getTrip(tripId2))

    assert(tripOpt != None)

    tripOpt.map { trip =>
      assert(trip.lastUpdate == event.epoch)
      assert(trip.curPos.lat == event.lat)
      assert(trip.startPos == None)
      assert(trip.stopPos == None)
      assert(trip.fare == None)
    }

    // verify trip pos cache
    val tPosSeq = getFutureValue(Redis.getTripPositions(tripId2))

    val pos = tPosSeq.head

    assert(pos.lat == event.lat)
    assert(pos.lng == event.lng)

    // verify geoindex

    val geohash = geoHash(event.lat, event.lng)
    val geohashSeq = getFutureValue(Redis.readFromGeoHash(geohash, AllPosIdGen))

    assert(geohashSeq.filter(_.tripId == tripId2).size == 0)

  }


  test("test out of sync events (1) Step 2: update - begin") {

    // begin event
    val event = Event(event="begin", tripId=tripId2, lat=beginPos.lat, lng =beginPos.lng, fare = None,
      epoch = 1392864673020L)

    Controller.addEvent(event)

    // for actors to finish work
    Thread.sleep(1000)

    // verify trip cache
    val tripOpt = getFutureValue(Redis.getTrip(tripId2))

    assert(tripOpt != None)

    tripOpt.map { trip =>
      assert(trip.lastUpdate == event.epoch)
      assert(trip.curPos.lat == event.lat)
      assert(trip.startPos != None)
      assert(trip.startPos.get.lat == event.lat)
      assert(trip.stopPos == None)
      assert(trip.fare == None)
    }

    // verify trip pos cache
    val tPosSeq = getFutureValue(Redis.getTripPositions(tripId2))

    val pos = tPosSeq.head

    assert(pos.lat == event.lat)
    assert(pos.lng == event.lng)

    // verify geoindex

    val geohash = geoHash(event.lat, event.lng)
    val geohashSeq = getFutureValue(Redis.readFromGeoHash(geohash, AllPosIdGen))

    assert(geohashSeq.filter(_.tripId == tripId2).size == 0)

  }

  test("test out of sync events (1) Step 3: update - begin - end") {

    // Update event
    val event = Event(event="end", tripId=tripId2, lat=endPos.lat, lng =endPos.lng, fare = None,
      epoch = 1392864675020L)

    Controller.addEvent(event)

    // for actors to finish work
    Thread.sleep(1000)

    // verify trip cache
    val tripOpt = getFutureValue(Redis.getTrip(tripId2))

    assert(tripOpt != None)

    tripOpt.map { trip =>
      assert(trip.lastUpdate == event.epoch)
      assert(trip.curPos.lat == event.lat)
      assert(trip.startPos != None)
      assert(trip.startPos.get.lat == beginPos.lat)
      assert(trip.stopPos != None)
      assert(trip.stopPos.get.lat == event.lat)
      assert(trip.fare == None)
    }

    // verify trip pos cache
    val tPosSeq = getFutureValue(Redis.getTripPositions(tripId2))

    val pos = tPosSeq.head

    assert(pos.lat == event.lat)
    assert(pos.lng == event.lng)

    // verify geoindex

    val geohash1 = geoHash(beginPos.lat, beginPos.lng)
    val geohashSeq1 = getFutureValue(Redis.readFromGeoHash(geohash1, AllPosIdGen))
    val geohashSeq1_ss = getFutureValue(Redis.readFromGeoHash(geohash1, StartStopIdGen))

    val geohash2 = geoHash(updatePos.lat, updatePos.lng)
    val geohashSeq2 = getFutureValue(Redis.readFromGeoHash(geohash2, AllPosIdGen))

    val geohash3 = geoHash(endPos.lat, endPos.lng)
    val geohashSeq3 = getFutureValue(Redis.readFromGeoHash(geohash3, AllPosIdGen))
    val geohashSeq3_ss = getFutureValue(Redis.readFromGeoHash(geohash3, StartStopIdGen))

    assert(geohashSeq1.filter(_.tripId == tripId2).size == 1)
    assert(geohashSeq2.filter(_.tripId == tripId2).size == 1)
    assert(geohashSeq3.filter(_.tripId == tripId2).size == 1)

    assert(geohashSeq1_ss.filter(_.tripId == tripId2).size == 1)
    assert(geohashSeq3_ss.filter(_.tripId == tripId2).size == 1)

  }


  val tripId3 = 876

  test("test out of sync events (2) step 1: begin ") {

    // begin event
    val event = Event(event="begin", tripId=tripId3, lat=beginPos.lat, lng =beginPos.lng, fare = None,
      epoch = 1392864673040L)

    Controller.addEvent(event)

    // for actors to finish work
    Thread.sleep(1000)

    // verify trip cache
    val tripOpt = getFutureValue(Redis.getTrip(tripId3))

    assert(tripOpt != None)

    tripOpt.map { trip =>
      assert(trip.lastUpdate == event.epoch)
      assert(trip.curPos.lat == event.lat)
      assert(trip.startPos != None)
      assert(trip.startPos.get.lat == event.lat)
      assert(trip.stopPos == None)
      assert(trip.fare == None)
    }

    // verify trip pos cache
    val tPosSeq = getFutureValue(Redis.getTripPositions(tripId3))

    val pos = tPosSeq.head

    assert(pos.lat == event.lat)
    assert(pos.lng == event.lng)

    // verify geoindex
    val geohash = geoHash(event.lat, event.lng)
    val geohashSeq = getFutureValue(Redis.readFromGeoHash(geohash, AllPosIdGen))

    assert(geohashSeq.filter(_.tripId == tripId3).size == 0)

  }

  test("test out of sync events (2) step 2: begin - end") {

    // Update event

    val event = Event(event="end", tripId=tripId3, lat=endPos.lat, lng =endPos.lng, fare = None,
      epoch = 1392864675020L)

    Controller.addEvent(event)

    // for actors to finish work
    Thread.sleep(1000)

    // verify trip cache
    val tripOpt = getFutureValue(Redis.getTrip(tripId3))

    assert(tripOpt != None)

    tripOpt.map { trip =>
      assert(trip.lastUpdate == event.epoch)
      assert(trip.curPos.lat == event.lat)
      assert(trip.startPos != None)
      assert(trip.startPos.get.lat == beginPos.lat)
      assert(trip.stopPos != None)
      assert(trip.stopPos.get.lat == event.lat)
      assert(trip.fare == None)
    }

    // verify trip pos cache
    val tPosSeq = getFutureValue(Redis.getTripPositions(tripId3))

    val pos = tPosSeq.head

    assert(pos.lat == event.lat)
    assert(pos.lng == event.lng)

    // verify geoindex
    val geohash1 = geoHash(beginPos.lat, beginPos.lng)
    val geohashSeq1 = getFutureValue(Redis.readFromGeoHash(geohash1, AllPosIdGen))
    val geohashSeq1_ss = getFutureValue(Redis.readFromGeoHash(geohash1, StartStopIdGen))

    val geohash2 = geoHash(updatePos.lat, updatePos.lng)
    val geohashSeq2 = getFutureValue(Redis.readFromGeoHash(geohash2, AllPosIdGen))

    val geohash3 = geoHash(endPos.lat, endPos.lng)
    val geohashSeq3 = getFutureValue(Redis.readFromGeoHash(geohash3, AllPosIdGen))
    val geohashSeq3_ss = getFutureValue(Redis.readFromGeoHash(geohash3, StartStopIdGen))

    //geohash1 9q8zh
    //geohash2 9q8zh
    //geohash3 9q8zs
    assert(geohashSeq1.filter(_.tripId == tripId3).size == 1)
    assert(geohashSeq2.filter(_.tripId == tripId3).size == 1)
    assert(geohashSeq3.filter(_.tripId == tripId3).size == 1)

    assert(geohashSeq1_ss.filter(_.tripId == tripId3).size == 1)
    assert(geohashSeq3_ss.filter(_.tripId == tripId3).size == 1)

  }


  test("test out of sync events (2) Step 3: begin - end - update") {

    // Update event

    val event = Event(event="update", tripId=tripId3, lat=updatePos.lat, lng =updatePos.lng, fare = None,
      epoch = 1392864674020L)

    Controller.addEvent(event)

    // for actors to finish work
    Thread.sleep(1000)

    // verify trip cache
    val tripOpt = getFutureValue(Redis.getTrip(tripId3))

    assert(tripOpt != None)

    tripOpt.map { trip =>
      assert(trip.lastUpdate != event.epoch)
      assert(trip.curPos.lat != event.lat)
      assert(trip.startPos != None)
      assert(trip.startPos.get.lat == beginPos.lat)
      assert(trip.stopPos != None)
      assert(trip.stopPos.get.lat == endPos.lat)
      assert(trip.fare == None)
    }

    // verify trip pos cache
    val tPosSeq = getFutureValue(Redis.getTripPositions(tripId3))

    val pos = tPosSeq.head

    assert(pos.lat == event.lat)
    assert(pos.lng == event.lng)

    // verify geoindex
    val geohash1 = geoHash(beginPos.lat, beginPos.lng)
    val geohashSeq1 = getFutureValue(Redis.readFromGeoHash(geohash1, AllPosIdGen))
    val geohashSeq1_ss = getFutureValue(Redis.readFromGeoHash(geohash1, StartStopIdGen))

    val geohash2 = geoHash(updatePos.lat, updatePos.lng)
    val geohashSeq2 = getFutureValue(Redis.readFromGeoHash(geohash2, AllPosIdGen))

    val geohash3 = geoHash(endPos.lat, endPos.lng)
    val geohashSeq3 = getFutureValue(Redis.readFromGeoHash(geohash3, AllPosIdGen))
    val geohashSeq3_ss = getFutureValue(Redis.readFromGeoHash(geohash3, StartStopIdGen))

    assert(geohashSeq1.filter(_.tripId == tripId3).size == 2)
    assert(geohashSeq2.filter(_.tripId == tripId3).size == 2)
    assert(geohashSeq3.filter(_.tripId == tripId3).size == 1)

    assert(geohashSeq1_ss.filter(_.tripId == tripId3).size == 1)
    assert(geohashSeq3_ss.filter(_.tripId == tripId3).size == 1)

  }


}
