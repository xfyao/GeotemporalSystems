package core.business

import core.cache._
import core.model._
import core.util.{AppLogger, GeoHelper}

import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * Created by v962867 on 10/23/15.
 */
case class ScanResult(positiveTrips: Seq[SimpleTrip], negativeTrips: Seq[SimpleTrip])

object QueryHandler extends GeoHelper with AppLogger {

  /** scan the geohash index to find trips in this geo rec
    *
    * (1) for boundary geohashes, the positions of each trips have to be examined to make sure it is in the geo rec
    * (2) for inner geohashes, just aggregating trips is enough
    * (3) if the trip has been examined, skip it
    * (4) parallel processing of multiple geohashes is enabled by scala futures
    */
  private def scanBoundaryGeoHashAreas(listGeoHash: Seq[String], prevScanResult: ScanResult, idGen: CacheIdGen,
                                       geoRec: GeoRec): Future[ScanResult] = {

    val resultHash: TrieMap[Int, SimpleTrip] = prevScanResult.positiveTrips.map(t =>
      t.tripId -> t)(collection.breakOut)
    val negativeResultHash: TrieMap[Int, SimpleTrip] = prevScanResult.negativeTrips.map(t =>
      t.tripId -> t)(collection.breakOut)

    // introduce parallelism here; each future is to check one GeoHash Index
    val futureList: Seq[Future[Unit]] = listGeoHash.map { geoHash =>

      // fetch positions for this trip
      def getPositions(trip: SimpleTrip): Future[Seq[Position]] = {
        idGen.name match {
          case AllPosIdGen.name =>
            Redis.getTripPositions(trip.tripId)
          case StartStopIdGen.name =>
            Redis.getTrip(trip.tripId).map { tripOpt =>
              tripOpt.map { trip =>
                trip.startPos.map(List(_)).getOrElse(List[Position]()) :::
                  trip.stopPos.map(List(_)).getOrElse(List[Position]())
              }.getOrElse(List[Position]())
            }
        }
      }

      def examTrip(trip: SimpleTrip): Future[Option[SimpleTrip]] = {
        // filter trips by (1) not processed yet and (2) has position in the geo rec
        resultHash.contains(trip.tripId) || negativeResultHash.contains(trip.tripId) match {
          case true => Future(None)
          case false =>
            // fetch positions for this trip and exame
            getPositions(trip).map { positions =>
              val inRec = hasPositionInRec(positions.toList, geoRec)
              inRec match {
                case false => negativeResultHash.putIfAbsent(trip.tripId, trip); None
                case true => Some(trip)
              }
            }
        }
      }


      def filterTrip(tripSeq: Seq[SimpleTrip]): Future[Seq[Option[SimpleTrip]]] = {
        val all = tripSeq map examTrip
        Future sequence all
      }

      for {
        tripSeq <- Redis.readFromGeoHash(geoHash, idGen)
        filteredTripSeq <- filterTrip(tripSeq)
      } yield {
        filteredTripSeq.foreach {
          case Some(trip) =>
            resultHash.putIfAbsent(trip.tripId, trip)
          case None => // do nothing
        }
      }
    }

    val futureOfList = Future.sequence(futureList)

    futureOfList onComplete {
      case Success(x) => logger.info(s"scanning geohashes is done: $listGeoHash")
      case Failure(ex) => logger.error(s"scanning geohashes is failed: $listGeoHash",ex)
    }

    futureOfList.map(_ => ScanResult(resultHash.values.toSeq, negativeResultHash.values.toSeq))
  }

  private def scanInnerGeoHashAreas(listGeoHash: Seq[String], prevScanResult: ScanResult, idGen: CacheIdGen,
                                    geoRec: GeoRec): Future[ScanResult] = {

    val resultHash: TrieMap[Int, SimpleTrip] = prevScanResult.positiveTrips.map(t =>
      t.tripId -> t)(collection.breakOut)
    val negativeResultHash: TrieMap[Int, SimpleTrip] = prevScanResult.negativeTrips.map(t =>
      t.tripId -> t)(collection.breakOut)

    // introduce parallelism here; each future is to check one GeoHash Index
    val futureList: Seq[Future[Unit]] = listGeoHash.map { geoHash =>

      for {
        tripSeq <- Redis.readFromGeoHash(geoHash, idGen)
      } yield {
        logger.info(s"checking inner geohash: $geoHash $tripSeq")
        tripSeq.foreach ( trip =>
          resultHash.putIfAbsent(trip.tripId, trip)
        )
      }
    }

    val futureOfList = Future.sequence(futureList)

    futureOfList onComplete {
      case Success(x) => logger.info(s"scanning geohashes is done: $listGeoHash")
      case Failure(ex) => logger.error(s"scanning geohashes is failed: $listGeoHash",ex)
    }

    futureOfList.map(_ => ScanResult(resultHash.values.toSeq, negativeResultHash.values.toSeq))
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

  private def getIndexedGeohashes(geoHashList: Seq[String], idGen: CacheIdGen): Future[Seq[String]] = {

    val precision = geoHashList.headOption.map(_.length).getOrElse(characterPrecision)

    precision match {

      case x if x < characterPrecision =>
        val futureList: Seq[Future[Seq[String]]] = geoHashList.map{ geoHash =>
          Redis.getGeoIndexKeys(geoHash, idGen)
        }
        val futureOfList: Future[Seq[Seq[String]]] = Future.sequence(futureList)
        futureOfList.map(_.flatten)
      case _ =>
        Future(geoHashList)
    }
  }

  private def countFinishedTripsInGeoRec(geoRec: GeoRec, idGen: CacheIdGen): Future[TripsSummary] = {

    val geoHashList = getGeoHashListInRec(geoRec)
    val boundaryGeoHashList = getBoundaryGeoHashListInRec(geoRec, geoHashList)
    val innerGeoHashList = geoHashList.filterNot(boundaryGeoHashList.contains(_))

    logger.info(s"check geoHashList: \n full - $geoHashList \n boundary - $boundaryGeoHashList " +
      s"\n inner - $innerGeoHashList ")

    // the geohash returned is scaled automatically based on the size of georec. For geohash with precision below 6,
    // we need to use "keys" command in Redis to find all smaller GeoHashes indexed in the particular geohash.
    // It can be done in parallelly.
    val updatedBoundaryGeoHashList: Future[Seq[String]] = getIndexedGeohashes(boundaryGeoHashList, idGen)
    val updatedInnerGeoHashList: Future[Seq[String]] = getIndexedGeohashes(innerGeoHashList, idGen)

    for {
      innerList <- updatedInnerGeoHashList
      scanResult <- scanInnerGeoHashAreas(innerList, ScanResult(List(), List()), idGen, geoRec)
      boundaryList <- updatedBoundaryGeoHashList
      scanResult2 <- scanBoundaryGeoHashAreas(boundaryList, scanResult, idGen, geoRec)
    } yield {
      // we should use scala fold operation here. When List size reaches 10k, the stack overflow error will happen
      var total = 0.0
      var count = 0
      for(trip <- scanResult2.positiveTrips) {
        total += trip.fare
        count += 1
      }
      TripsSummary(count, total)
    }
  }

  /* count all trips occured in this geo rec */
  def countAllPosInGeoRec(geoRec: GeoRec): Future[TripsSummary] = {

    countFinishedTripsInGeoRec(geoRec, AllPosIdGen)
  }

  /* count all trips with start and/or postion in this geo rec */
  def countStartAndStopInGeoRec(geoRec: GeoRec): Future[TripsSummary] = {

    countFinishedTripsInGeoRec(geoRec, StartStopIdGen)
  }

  /* count all occuring trips */
  def countOccuringTrips(): Int = {

    Controller.getOccuringCount
  }
}