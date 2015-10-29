package core.cache

import core.model._
import core.util.CacheLogger
import redis.clients.jedis.{JedisPool, JedisPoolConfig}

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by v962867 on 10/24/15.
 */
trait CacheIdGen {

  val name: String

  def genId(hashId: String): String

  /* convert geohash index to geohash only, eg, ss-9q8g6 to 9q8g6 */
  def reverseGeoHashId(hashKey: String): String = {
    hashKey.split("-")(1)
  }
}

object AllPosIdGen extends CacheIdGen {

  val name = "AllPos"

  /* geohash index cache key for all positions */
  def genId(hashId: String): String = s"pos-${hashId}"
}

object StartStopIdGen extends CacheIdGen {

  val name = "StartStop"

  /* geohash index cache key for all positions */
  def genId(hashId: String): String = s"ss-${hashId}"
}


object Redis extends CacheLogger {

  val poolConfig = new JedisPoolConfig()
  poolConfig.setMaxTotal(128)
  val pool = new JedisPool(poolConfig, "localhost");

  /* trip cache key */
  def tripIdStr(tripId: Int): String = s"trip-${tripId}"

  /* trip positions cache key */
  def tripPosIdStr(tripId: Int): String = s"tPos-${tripId}"

  /* The plan is to find a good nonblocking lib to replace this java Redis client. Therefore Future is used in APIs */
  def setTrip(trip: Trip): Future[String] = {
    Future {
      val jedis = pool.getResource
      try {
        jedis.set(tripIdStr(trip.tripId), Trip.stringFormatter.serialize(trip))
      } catch {
        case ex: Throwable =>
          logger.error("setTrip", ex)
          "exception"
      } finally {
        pool.returnResourceObject(jedis)
      }
    }
  }

  def getTrip(tripId: Int): Future[Option[Trip]] = {
    Future {
      val jedis = pool.getResource
      try {
        val ret = jedis.get(tripIdStr(tripId))
        ret match {
          case null => None
          case _ => Some(Trip.stringFormatter.deserialize(ret))
        }
      } catch {
        case ex: Throwable =>
          logger.error("getTrip", ex)
          None
      } finally {
        pool.returnResourceObject(jedis)
      }
    }
  }

  def addTripPosition(tripId: Int, pos: Position): Future[Long] = {

    Future {
      val jedis = pool.getResource
      try {
        jedis.lpush(tripPosIdStr(tripId), Position.stringFormatter.serialize(pos))
      } catch {
        case ex: Throwable =>
          logger.error("addTripPosition", ex)
          -1
      } finally {
        pool.returnResourceObject(jedis)
      }
    }
  }

  def getTripPositions(tripId: Int): Future[Seq[Position]] = {

    Future {
      val jedis = pool.getResource
      try {
        val ret = jedis.lrange(tripPosIdStr(tripId), 0, -1)
        ret.toList.map{ str =>
          Position.stringFormatter.deserialize(str)
        }
      } catch {
        case ex: Throwable =>
          logger.error("getTripPositions", ex)
          List()
      } finally {
        pool.returnResourceObject(jedis)
      }
    }
  }

  def addToGeoHash[T <: CacheIdGen](hashId: String, tps: SimpleTrip, idGen: T): Future[Long] = {

    Future {
      val jedis = pool.getResource
      try {
        jedis.lpush(idGen.genId(hashId), SimpleTrip.stringFormatter.serialize(tps))
      } catch {
        case ex: Throwable =>
          logger.error("addToGeoHash", ex)
          -1
      } finally {
        pool.returnResourceObject(jedis)
      }
    }
  }

  def readFromGeoHash[T <: CacheIdGen](hashId: String, idGen: T): Future[Seq[SimpleTrip]] = {
    Future {
      val jedis = pool.getResource
      try {
        val ret = jedis.lrange(idGen.genId(hashId), 0, -1)
        ret.toList.map { str =>
          SimpleTrip.stringFormatter.deserialize(str)
        }
      } catch {
        case ex: Throwable =>
          logger.error("readFromGeoHash", ex)
          List()
      } finally {
        pool.returnResourceObject(jedis)
      }
    }
  }

  def getGeoIndexKeys[T <: CacheIdGen](prefix: String, idGen: T): Future[Seq[String]] = {
    Future {
      val jedis = pool.getResource
      try {
        val ret = jedis.keys(idGen.genId(prefix) + "*")
        ret.toList.map(v =>
          idGen.reverseGeoHashId(v))
      } catch {
        case ex: Throwable =>
          logger.error("getGeoIndexKeys", ex)
          List()
      } finally {
        pool.returnResourceObject(jedis)
      }
    }
  }
}
