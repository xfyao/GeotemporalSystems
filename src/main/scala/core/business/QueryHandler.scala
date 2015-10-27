package core.business

import core.cache._
import core.model.{Position, TripsSummary}
import core.util.{AppLogger, FutureHelper, GeoHelper}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * Created by v962867 on 10/23/15.
 */
object QueryHandler extends GeoHelper with FutureHelper with AppLogger {

  /** scan the geohash index to find trips in this geo rec
    *
    * (1) for boundary geohashes, the positions of each trips have to be examined to make sure it is in the geo rec
    * (2) for inner geohashes, just aggregating trips is enough
    * (3) if the trip has been examined, skip it
    * (4) parallel processing of multiple geohashes is enabled by scala futures
    */
  private def scanGeoHashAreas(listGeoHash: List[String], resultHash: mutable.HashMap[Int, Double], isBoundary: Boolean,
                               isForStartAndStop: Boolean, bottomLeft: Position, topRight: Position): Unit = {

    // HashSet to store local not passed positions to avoid duplicated processing as well
    val nonResultHash = mutable.HashSet[Int]()

    // introduce parallelism here; each future is to check one GeoHash Index
    val futureList: List[Future[Unit]] = listGeoHash.map {geoHash =>
      Redis.readFromGeoHash(geoHash, isForStartAndStop).map { tripSeq =>
        isBoundary match {
          case false =>
            println(s"checking inner geohash: $geoHash $tripSeq $isForStartAndStop")
            tripSeq.foreach ( trip =>
              resultHash.put(trip.tripId, trip.fare)
            )
          case true =>
            println(s"checking boundary geohash: $geoHash $tripSeq $isForStartAndStop")
            tripSeq.filter(trip =>
              // filter trips by (1) not processed yet and (2) has position in the geo rec
              resultHash.contains(trip.tripId) || nonResultHash.contains(trip.tripId) match {
                case true => false
                case false =>
                  // fetch positions for this trip
                  val positions = isForStartAndStop match {
                    case false => getFutureValue(Redis.getTripPositions(trip.tripId))
                    case true =>
                      val tripOpt = getFutureValue(Redis.getTrip(trip.tripId))
                      var listPos = List[Position]()
                      tripOpt.map{trip =>
                        trip.startPos.map(v =>listPos +:= v)
                        trip.stopPos.map(v =>listPos +:= v)}
                      listPos
                  }
                  val inRec = hasPositionInRec(positions.toList, bottomLeft, topRight)
                  inRec match {
                    case false => nonResultHash.add(trip.tripId)
                    case true =>
                  }
                  inRec
              }).foreach( trip =>
              resultHash.put(trip.tripId, trip.fare))
        }
      }}

    val futureOfList = Future.sequence(futureList)

    futureOfList onComplete {
      case Success(x) => logger.info(s"scanning geohashes is done: $listGeoHash")
      case Failure(ex) => logger.error(s"scanning geohashes is failed: $listGeoHash",ex)
    }

    // blocking here for all threads finished
    getFutureValue(futureOfList)
  }

  private def longestCommonPrefix(s1:String, s2:String): String = {
    val maxSize = scala.math.min(s1.length, s2.length)
    var i:Int = 0;
    while ( i < maxSize && s1(i)== s2(i)) i += 1;
    s1.take(i);
  }

  private def getCommonGeoHashPrefix(geoHashList: List[String]): String = {
    geoHashList.foldRight(geoHashList(0))((a,b) => longestCommonPrefix(a, b))
  }

  private def getIndexedGeohashes(geoHashList: List[String], isForStartAndStop: Boolean): List[String] = {

    val precision = geoHashList.headOption.map(_.length).getOrElse(characterPrecision)

    precision match {

      case x if x < characterPrecision =>
        val futureList: List[Future[Seq[String]]] = geoHashList.map{ geoHash =>
          Redis.getGeoIndexKeys(geoHash, isForStartAndStop)
        }
        val futureOfList: Future[List[Seq[String]]] = Future.sequence(futureList)

        getFutureValue(futureOfList).map(hashSeq => hashSeq.toList).flatten
      case _ =>
        geoHashList
    }
  }

  private def countFinishedTripsInGeoRec(bottomLeft: Position, topRight: Position,
                                         isForStartAndStop: Boolean): TripsSummary = {

    val geoHashList = getGeoHashListInRec(bottomLeft, topRight)
    val boundaryGeoHashList = getBoundaryGeoHashListInRec(bottomLeft, topRight, geoHashList)
    val innerGeoHashList = geoHashList.filterNot(boundaryGeoHashList.contains(_))

    logger.info(s"check geoHashList: \n full - $geoHashList \n boundary - $boundaryGeoHashList " +
      s"\n inner - $innerGeoHashList ")

    // the geohash returned is scaled automatically based on the size of georec. For geohash with precision below 6,
    // we need to use "keys" command in Redis to find all smaller GeoHashes indexed in the particular geohash.
    // It can be done in parallelly.
    val updatedBoundaryGeoHashList = getIndexedGeohashes(boundaryGeoHashList, isForStartAndStop)
    val updatedInnerGeoHashList = getIndexedGeohashes(innerGeoHashList, isForStartAndStop)

    // the hash to store and aggregate processed trips to avoid dupliated processing
    val tripResultHash = mutable.HashMap[Int, Double]()

    // To further optermize the performance, check the inner part of geohash grid first,
    // where it is guaranted all trips are in the geo rec
    scanGeoHashAreas(listGeoHash = updatedInnerGeoHashList, resultHash = tripResultHash, isBoundary = false,
      isForStartAndStop = isForStartAndStop, bottomLeft = bottomLeft, topRight = topRight)

    // scan the boundary geohash areas and pass in the previous scan result to avoid dupliated processing
    scanGeoHashAreas(listGeoHash = updatedBoundaryGeoHashList, resultHash = tripResultHash, isBoundary = true,
      isForStartAndStop = isForStartAndStop, bottomLeft = bottomLeft, topRight = topRight)

    val totalFare = tripResultHash.foldRight(0.0)((a,b) => a._2 + b)
    val totalCount = tripResultHash.size

    TripsSummary(totalCount, totalFare)
  }

  /* count all trips occured in this geo rec */
  def countAllPosInGeoRec(bottomLeft: Position, topRight: Position): TripsSummary = {

    countFinishedTripsInGeoRec(bottomLeft, topRight, false)
  }

  /* count all trips with start and/or postion in this geo rec */
  def countStartAndStopInGeoRec(bottomLeft: Position, topRight: Position): TripsSummary = {

    countFinishedTripsInGeoRec(bottomLeft, topRight, true)
  }

  /* count all occuring trips */
  def countOccuringTrips(): Int = {

    Controller.getOccuringCount
  }
}