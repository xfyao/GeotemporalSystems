package core.cache

import core.business.Controller
import core.model.{SimpleTrip, SimpleTrip$, Position, Event}
import core.util.FutureHelper
import org.scalatest.FunSuite
import net.liftweb.json._

import scala.util.{Failure, Success}

/**
 * Created by v962867 on 10/24/15.
 */
class Redis$Test extends FunSuite with FutureHelper {

  implicit val formats = DefaultFormats

  test("set/get trip") {

    val tripId = 800

    val jevent = "{\"event\": \"begin\",\n\"tripId\": " + tripId +
      ",\n\"lat\": 37.79947,\n\"lng\": -122.511635,\n\"epoch\": 1392864673040\n}"

    val event = parse(jevent).extract[Event]
    val trip = Controller.eventToTrip(event)
    getFutureValue(Redis.setTrip(trip))

    val tripRetOpt = getFutureValue(Redis.getTrip(tripId))

    assert(tripRetOpt != None)

    tripRetOpt.map { tripRet =>
      assert(trip.tripId == tripRet.tripId)
      assert(trip.curPos == tripRet.curPos)
    }

  }

  test("add/get trip positions") {

    val tripId = 811
    val pos1 = Position(123.0, 234.0)
    val pos2 = Position(123.1, 234.1)

    getFutureValue(Redis.addTripPosition(tripId, pos1))
    getFutureValue(Redis.addTripPosition(tripId, pos2))

    val posSeq = getFutureValue(Redis.getTripPositions(tripId))

    assert(posSeq.size >= 2)
    assert(posSeq.head == pos2)

  }

  test("add/get geocache - all positions") {

    val tripId = 821

    val trip = SimpleTrip(tripId, 0)

    getFutureValue(Redis.addToGeoHash("xyz", trip, false))

    val tripsSeq = getFutureValue(Redis.readFromGeoHash("xyz", false))
    assert(tripsSeq.size >= 1)
    assert(tripsSeq.head.tripId == tripId)
  }

  test("test get geoIndex keys - all positions") {

    val tripId = 821

    val trip = SimpleTrip(tripId, 0)

    getFutureValue(Redis.addToGeoHash("xyz", trip, false))

    val tripsSeq = getFutureValue(Redis.getGeoIndexKeys("x", false))
    assert(tripsSeq.size >= 1)
    assert(tripsSeq.head == "xyz")
  }

  test("add/get geocache - start or stop position") {

    val tripId = 831

    val trip = SimpleTrip(tripId, 0)

    getFutureValue(Redis.addToGeoHash("xyz", trip, true))

    val tripPositionsSeq = getFutureValue(Redis.readFromGeoHash("xyz", true))
    assert(tripPositionsSeq.size >= 1)
    assert(tripPositionsSeq.head.tripId == tripId)
  }

  test("test get geoIndex keys - start or stop positions") {

    val tripId = 821

    val trip = SimpleTrip(tripId, 0)

    getFutureValue(Redis.addToGeoHash("xyz", trip, true))

    val tripsSeq = getFutureValue(Redis.getGeoIndexKeys("x", true))
    assert(tripsSeq.size >= 1)
    assert(tripsSeq.head == "xyz")
  }
}
