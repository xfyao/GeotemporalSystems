package core.business

import redis.RedisClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Created by v962867 on 10/25/15.
 */
object Simulator {

  implicit val akkaSystem = akka.actor.ActorSystem()

  def main( args:Array[String] ):Unit = {

    val redis = RedisClient()

    val r = scala.util.Random
    val tripId = r.nextInt(500)
    val status = tripId % 3 match {
      case 1 => "begin"
      case 2 => "update"
      case _ => "end"
    }
    val msg = "\n{\n\"event\": \"update\",\n\"tripId\": " + tripId + ",\n\"lat\": 37.79947,\n\"lng\": -122.511635,\n\"epoch\": 1392864673040\n}"
    akkaSystem.scheduler.schedule(10 milliseconds, 10 milliseconds)(redis.publish("geoevent", msg))
    akkaSystem.scheduler.scheduleOnce(20 seconds)(akkaSystem.shutdown())
  }
}
