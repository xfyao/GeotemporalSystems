package core.util

import ch.hsr.geohash.GeoHash
import ch.hsr.geohash.util.{BoundingBoxGeoHashIterator, TwoGeoHashBoundingBox}
import core.model.Position
import org.slf4j.LoggerFactory

/**
 * Created by v962867 on 10/24/15.
 */
object GeoHelper {

  val logger = LoggerFactory.getLogger(this.getClass)

  def characterPrecision: Int = 5

  def geoHash(lat: Double, lng: Double): String = {

    GeoHash.withCharacterPrecision(lat, lng, characterPrecision).toBase32
  }

  def geoHashObj(lat: Double, lng: Double): GeoHash = {

    GeoHash.withCharacterPrecision(lat, lng, characterPrecision)
  }

  def geoHash(lat: Double, lng: Double, precision: Int): String = {

    GeoHash.withCharacterPrecision(lat, lng, precision).toBase32
  }

  def geoHashObj(lat: Double, lng: Double, precision: Int): GeoHash = {

    GeoHash.withCharacterPrecision(lat, lng, precision)
  }

  /* helper method to check position in the geo rec */
  def isInRec(pos: Position, bottomLeft: Position, topRight: Position): Boolean = {

    pos.lat >= bottomLeft.lat &&
      pos.lat <= topRight.lat &&
      pos.lng >= bottomLeft.lng &&
      pos.lng <= topRight.lng
  }

  /* helper method to check at lest one position of a list in the geo rec */
  def hasPositionInRec(positions: List[Position], bottomLeft: Position, topRight: Position): Boolean = {

    for (i <- 0 to positions.size - 1) {
      val e = positions(i)
      if(isInRec(Position(e.lat,e.lng), bottomLeft, topRight)) return true
    }

    false
  }

  /* caculate geohash list of the geo rec */
  def getGeoHashListInRec(bottomLeft: Position, topRight: Position): List[String] = {

    //based on distance (km) to dynamically choose the geohash precision
    val dist = GeoHelper.distance(bottomLeft, topRight)

    val precision = dist match {
      case x if x < 80 => GeoHelper.characterPrecision
      case x if x < 200 => 4
      case x if x < 800 => 3
      case x if x < 2000 => 2
      case _ => 1
    }

    val geoHashBL = GeoHelper.geoHashObj(bottomLeft.lat, bottomLeft.lng, precision)
    val geoHashTR = GeoHelper.geoHashObj(topRight.lat, topRight.lng, precision)

    val boundBox = new TwoGeoHashBoundingBox(geoHashBL, geoHashTR)
    val boundingBoxIter = new BoundingBoxGeoHashIterator(boundBox)

    // get geohashs in the box
    var geoHashList = List[String]()

    while (boundingBoxIter.hasNext) {
      geoHashList :+= boundingBoxIter.next().toBase32
    }

    geoHashList
  }

  /* find the boundary geohash list from all geohashes of this geo rec */
  def getBoundaryGeoHashListInRec(bottomLeft: Position, topRight: Position, allGeoHashList: List[String]) = {

    var ret = List[String]()

    val geoHashBL = GeoHelper.geoHashObj(bottomLeft.lat, bottomLeft.lng)

    val geoHashTR = GeoHelper.geoHashObj(topRight.lat, topRight.lng)

    var current = geoHashBL.getNorthernNeighbour

    // go Northern until out of rec
    while(allGeoHashList.contains(current.toBase32)) {
      ret :+= current.toBase32
      current = current.getNorthernNeighbour
    }

    // go back one and continue go Eastern
    current = current.getSouthernNeighbour.getEasternNeighbour
    while(allGeoHashList.contains(current.toBase32)) {
      ret :+= current.toBase32
      current = current.getEasternNeighbour
    }

    // go back one and continue go Southern
    current = current.getWesternNeighbour
    logger.debug(s"it shoud be the top right one, check ${current.toBase32} ?= ${geoHashTR.toBase32}")
    current = current.getSouthernNeighbour
    while(allGeoHashList.contains(current.toBase32)) {
      ret :+= current.toBase32
      current = current.getSouthernNeighbour
    }

    // go back one and continue go Western
    current = current.getNorthernNeighbour.getWesternNeighbour
    while(allGeoHashList.contains(current.toBase32)) {
      ret :+= current.toBase32
      current = current.getWesternNeighbour
    }

    current = current.getEasternNeighbour
    logger.debug(s"it shoud be the bottom left one, check ${current.toBase32} ?= ${geoHashBL.toBase32}")

    // if boundary list is empty, then the whole list is boundary list instead
    ret.size match {
      case 0 => allGeoHashList
      case _ => ret
    }
  }

  /* it returns kilometer */
  def distance(p1: Position, p2: Position): Double = {

    val theta = p1.lng - p2.lng
    var dist = Math.sin(deg2rad(p1.lat)) * Math.sin(deg2rad(p2.lat)) +
      Math.cos(deg2rad(p1.lat)) * Math.cos(deg2rad(p2.lat)) * Math.cos(deg2rad(theta))
    dist = Math.acos(dist)
    dist = rad2deg(dist)
    dist = dist * 60 * 1.1515
    dist = dist * 1.609344
    dist
  }

  def deg2rad(deg: Double): Double = {
    deg * Math.PI / 180.0
  }

  def rad2deg(rad: Double): Double = {
    rad * 180 / Math.PI
  }
}
