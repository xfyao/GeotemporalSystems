package core.business

import core.cache.{AllPosIdGen, Redis}
import core.model.{GeoRec, SimpleTrip, Event, Position}
import core.util.{FutureHelper, GeoHelper}
import org.scalatest.{PrivateMethodTester, FunSuite}

import scala.concurrent.Future

/**
 * Created by v962867 on 10/24/15.
 */
class QueryHandler$Test extends FunSuite with PrivateMethodTester with GeoHelper with FutureHelper {

  test("test get longest common prefix") {

    val getCommonGeoHashPrefix = PrivateMethod[String]('getCommonGeoHashPrefix)

    val ret = QueryHandler invokePrivate getCommonGeoHashPrefix(List("9q8d4", "9q9d4", "9q764"))
    assert(ret == "9q")

    val ret2 = QueryHandler invokePrivate getCommonGeoHashPrefix(List("9q8d4", "8q9d4", "6q764"))
    assert(ret2 == "")

  }

  test("test auto scale geo index") {

    val bottomLeft = Position(34.79947, -124.511635)
    val topRight = Position(37.79947, -124.511635)

    val trip = SimpleTrip(123, 0.0)

    // distance 379km
    val geoHashBL = geoHash(bottomLeft.lat, bottomLeft.lng)
    val geoHashTR = geoHash(topRight.lat, topRight.lng)

    Redis.addToGeoHash(geoHashBL, trip, AllPosIdGen)
    Redis.addToGeoHash(geoHashTR, trip, AllPosIdGen)

    val geoHashes1 = getGeoHashListInRec(GeoRec(bottomLeft, topRight))
    println("geohash 1: " + geoHashes1)
    assert(geoHashes1.head.length == 3)

    val getIndexedGeohashes = PrivateMethod[Future[Seq[String]]]('getIndexedGeohashes)

    val geoHashes2 = getFutureValue(QueryHandler invokePrivate getIndexedGeohashes(geoHashes1, AllPosIdGen))
    println("geohash 2: " + geoHashes2)
    assert(geoHashes2.head.length == characterPrecision)

  }

  val event1 = Event(event="begin", tripId=910, lat=48.61, lng = -4.331, fare = None, epoch = 1392864673040L)
  val event2 = Event(event="update", tripId=910, lat=48.612, lng = -4.333, fare = None, epoch = 1392864673140L)
  val event3 = Event(event="end", tripId=910, lat=48.614, lng = -4.363, fare = Some(12.5), epoch = 1392864673240L)

  Controller.addEvent(event1)
  Thread.sleep(500)
  Controller.addEvent(event2)
  Thread.sleep(500)
  Controller.addEvent(event3)
  Thread.sleep(500)

  test("test counting - in the georec - both AllPos and StartAndStop") {

    val bottomLeft = Position(48.6, -4.39)
    val topRight = Position(48.6996, -4.301)

    val countAll = getFutureValue(QueryHandler.countAllPosInGeoRec(GeoRec(bottomLeft, topRight)))

    assert(countAll.totalCount == 1)

    val countSS = getFutureValue(QueryHandler.countStartAndStopInGeoRec(GeoRec(bottomLeft, topRight)))

    assert(countSS.totalCount == 1)

    assert(countSS.totalFare == event3.fare.get)

  }

  test("test counting - out of the geohash box and the georec - both AllPos and StartAndStop") {

    val bottomLeft = Position(48.62, -4.39)
    val topRight = Position(48.6996, -4.301)

    val countAll = getFutureValue(QueryHandler.countAllPosInGeoRec(GeoRec(bottomLeft, topRight)))

    assert(countAll.totalCount == 0)

    val countSS = getFutureValue(QueryHandler.countStartAndStopInGeoRec(GeoRec(bottomLeft, topRight)))

    assert(countSS.totalCount == 0)

  }

  test("test counting - in the geohash box but out of the georec - both AllPos and StartAndStop") {

    val bottomLeft = Position(48.65, -4.39)
    val topRight = Position(48.6996, -4.301)

    val countAll = getFutureValue(QueryHandler.countAllPosInGeoRec(GeoRec(bottomLeft, topRight)))

    assert(countAll.totalCount == 0)

    val countSS = getFutureValue(QueryHandler.countStartAndStopInGeoRec(GeoRec(bottomLeft, topRight)))

    assert(countSS.totalCount == 0)
  }

  test("test counting - pass the georec but start/stop outside") {

    //lat=48.612, lng=-4.333

    val bottomLeft = Position(48.612, -4.34)
    val topRight = Position(48.613, -4.330)

    val countAll = getFutureValue(QueryHandler.countAllPosInGeoRec(GeoRec(bottomLeft, topRight)))

    assert(countAll.totalCount == 1)

    val countSS = getFutureValue(QueryHandler.countStartAndStopInGeoRec(GeoRec(bottomLeft, topRight)))

    assert(countSS.totalCount == 0)
  }


  test("test counting - in the georec - both AllPos and StartAndStop - multiple trips") {

    val event12 = Event(event="begin", tripId=920, lat=48.61, lng = -4.331, fare = None, epoch = 1392864673040L)
    val event22 = Event(event="update", tripId=920, lat=48.612, lng = -4.333, fare = None, epoch = 1392864673140L)
    val event32 = Event(event="end", tripId=920, lat=48.614, lng = -4.363, fare = Some(45.5), epoch = 1392864673240L)

    Controller.addEvent(event12)
    Controller.addEvent(event22)
    Controller.addEvent(event32)

    Thread.sleep(1000)

    val bottomLeft = Position(48.6, -4.39)
    val topRight = Position(48.6996, -4.301)

    val countAll = getFutureValue(QueryHandler.countAllPosInGeoRec(GeoRec(bottomLeft, topRight)))

    assert(countAll.totalCount == 2)

    val countSS = getFutureValue(QueryHandler.countStartAndStopInGeoRec(GeoRec(bottomLeft, topRight)))

    assert(countSS.totalCount == 2)

    assert(countSS.totalFare == event3.fare.get + event32.fare.get)
  }

  test("test counting occuring trips") {

    val event1 = Event(event="begin", tripId=930, lat=37.79947, lng = -122.511635, fare = None, epoch = 1392864673040L)

    Controller.addEvent(event1)

    val count1 = QueryHandler.countOccuringTrips()

    assert(count1 == Controller.getOccuringCount)

    val event2 = Event(event="update", tripId=930, lat=37.79947, lng = -122.511635, fare = None, epoch = 1392864673040L)

    Controller.addEvent(event2)

    val count2 = QueryHandler.countOccuringTrips()

    assert(count2 == Controller.getOccuringCount)

    val event3 = Event(event="end", tripId=930, lat=37.79947, lng = -122.511635, fare = None, epoch = 1392864673040L)

    Controller.addEvent(event3)

    val count3 = QueryHandler.countOccuringTrips()

    assert(count3 == Controller.getOccuringCount)
  }
}
