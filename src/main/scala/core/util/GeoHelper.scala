package core.util

import ch.hsr.geohash.GeoHash
import ch.hsr.geohash.util.{BoundingBoxGeoHashIterator, TwoGeoHashBoundingBox}
import core.model.{GeoRec, Position}

/**
 * Created by v962867 on 10/24/15.
 */
trait GeoHelper {

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
  def isInRec(pos: Position, geoRec: GeoRec): Boolean = {

    pos.lat >= geoRec.bottomLeft.lat &&
      pos.lat <= geoRec.topRight.lat &&
      pos.lng >= geoRec.bottomLeft.lng &&
      pos.lng <= geoRec.topRight.lng
  }

  /* helper method to check at lest one position of a list in the geo rec */
  def hasPositionInRec(positions: List[Position], geoRec: GeoRec): Boolean = {

    for (i <- 0 to positions.size - 1) {
      val e = positions(i)
      if(isInRec(Position(e.lat,e.lng), geoRec)) return true
    }

    false
  }

  /* caculate geohash list of the geo rec */
  def getGeoHashListInRec(geoRec: GeoRec): List[String] = {

    //based on distance (km) to dynamically choose the geohash precision
    val dist = distance(geoRec.bottomLeft, geoRec.topRight)

    val precision = dist match {
      case x if x < 80 => characterPrecision
      case x if x < 200 => 4
      case x if x < 800 => 3
      case x if x < 2000 => 2
      case _ => 1
    }

    val geoHashBL = geoHashObj(geoRec.bottomLeft.lat, geoRec.bottomLeft.lng, precision)
    val geoHashTR = geoHashObj(geoRec.topRight.lat, geoRec.topRight.lng, precision)

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
  def getBoundaryGeoHashListInRec(geoRec: GeoRec, allGeoHashList: List[String]) = {

    var ret = List[String]()

    val geoHashBL = geoHashObj(geoRec.bottomLeft.lat, geoRec.bottomLeft.lng)

    val geoHashTR = geoHashObj(geoRec.topRight.lat, geoRec.topRight.lng)

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
