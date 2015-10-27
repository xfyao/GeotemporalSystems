package core.cache

import core.model._
import redis.RedisClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by v962867 on 10/24/15.
 */
object Redis {

  implicit val akkaSystem = akka.actor.ActorSystem()

  val redis = RedisClient()

  /* trip cache key */
  def tripIdStr(tripId: Int): String = s"trip-${tripId}"

  /* trip positions cache key */
  def tripPosIdStr(tripId: Int): String = s"tPos-${tripId}"

  /* geohash index cache key for all positions */
  def posHashId(hashId: String): String = s"pos-${hashId}"

  /* geohask index cache key for start and stop positions */
  def ssHashId(hashId: String): String = s"ss-${hashId}"

  /* convert geohash index to geohash only, eg, ss-9q8g6 to 9q8g6 */
  def reverseGeoHashId(hashKey: String): String = {
    hashKey.split("-")(1)
  }

  def setTrip(trip: Trip): Future[Boolean] = {
    redis.set[Trip](tripIdStr(trip.tripId), trip)
  }

  def getTrip(tripId: Int): Future[Option[Trip]] = {
    redis.get[Trip](tripIdStr(tripId))
  }

  def addTripPosition(tripId: Int, pos: Position): Future[Long] = {
    redis.lpush[Position](tripPosIdStr(tripId), pos)
  }

  def getTripPositions(tripId: Int): Future[Seq[Position]] = {
    redis.lrange[Position](tripPosIdStr(tripId),0,-1)
  }

  def addToGeoHash(hashId: String, tps: SimpleTrip, isForStartAndStop: Boolean): Future[Long] = {
    isForStartAndStop match {
      case true => redis.lpush[SimpleTrip](ssHashId(hashId), tps)
      case false => redis.lpush[SimpleTrip](posHashId(hashId), tps)
    }
  }

  def readFromGeoHash(hashId: String, isForStartAndStop: Boolean): Future[Seq[SimpleTrip]] = {

    val posListFuture = isForStartAndStop match {
      case true => redis.lrange[SimpleTrip](ssHashId(hashId), 0, -1)
      case false => redis.lrange[SimpleTrip](posHashId(hashId), 0, -1)
    }

    return posListFuture
  }

  def getGeoIndexKeys(prefix: String, isForStartAndStop: Boolean): Future[Seq[String]] = {
    isForStartAndStop match {
      case true => redis.keys(ssHashId(prefix) + "*").map(kSeq => kSeq.map(v =>reverseGeoHashId(v)))
      case false => redis.keys(posHashId(prefix) + "*").map(kSeq => kSeq.map(v =>reverseGeoHashId(v)))
    }
  }

  def close = akkaSystem.shutdown()
}
