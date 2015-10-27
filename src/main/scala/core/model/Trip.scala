package core.model

import akka.util.ByteString
import net.liftweb.json.Serialization.write
import net.liftweb.json._
import redis.ByteStringFormatter


/**
 * Created by v962867 on 10/22/15.
 */

case class TripsSummary(totalCount: Int, totalFare: Double)

case class Event(event: String, tripId: Int, lat: Double, lng: Double, fare: Option[Double], epoch: Long)

case class Trip(tripId: Int, status: String, startPos: Option[Position] = None, stopPos: Option[Position] = None,
                curPos: Position, fare: Option[Double], lastUpdate: Long)


object Trip {

  implicit val byteStringFormatter = new ByteStringFormatter[Trip] {

    implicit val formats = DefaultFormats

    def serialize(data: Trip): ByteString = {
      val jsonString = write(data)
      ByteString(jsonString)
    }

    def deserialize(bs: ByteString): Trip = {
      parse(bs.utf8String).extract[Trip]
    }
  }

}

case class Position(lat: Double, lng: Double)

object Position {

  implicit val byteStringFormatter = new ByteStringFormatter[Position] {

    def serialize(data: Position): ByteString = {
      ByteString(data.lat + "," + data.lng)
    }

    def deserialize(bs: ByteString): Position = {
      val r = bs.utf8String.split(',').toList
      Position(r(0).toDouble, r(1).toDouble)
    }
  }
}

case class SimpleTrip(tripId: Int, fare: Double)


object SimpleTrip {

  implicit val formats = DefaultFormats

  implicit val byteStringFormatter = new ByteStringFormatter[SimpleTrip] {
    def serialize(data: SimpleTrip): ByteString = {
      val jsonString = write(data)
      ByteString(jsonString)
    }

    def deserialize(bs: ByteString): SimpleTrip = {
      parse(bs.utf8String).extract[SimpleTrip]
    }
  }
}


