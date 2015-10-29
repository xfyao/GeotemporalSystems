package core.model

import net.liftweb.json.Serialization.write
import net.liftweb.json._


/**
 * Created by v962867 on 10/22/15.
 */

case class TripsSummary(totalCount: Int, totalFare: Double)

case class Event(event: String, tripId: Int, lat: Double, lng: Double, fare: Option[Double], epoch: Long)

case class GeoRec(bottomLeft: Position, topRight: Position)

class StringFormatter[T<: AnyRef] {

  implicit val formats = DefaultFormats

  def serialize(data: T): String = {
    write[T](data)
  }

  def deserialize(str: String)(implicit m: Manifest[T]): T = {
    parse(str).extract[T]
  }
}

case class Trip(tripId: Int, status: String, startPos: Option[Position] = None, stopPos: Option[Position] = None,
                curPos: Position, fare: Option[Double], lastUpdate: Long)

object Trip {

  implicit val stringFormatter = new StringFormatter[Trip]
}

case class Position(lat: Double, lng: Double)

object Position {

  implicit val stringFormatter = new StringFormatter[Position]
}

case class SimpleTrip(tripId: Int, fare: Double)

object SimpleTrip {

  implicit val stringFormatter = new StringFormatter[SimpleTrip]

}


