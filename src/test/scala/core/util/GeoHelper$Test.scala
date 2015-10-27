package core.util

import core.model.Position
import org.scalatest.FunSuite

/**
 * Created by v962867 on 10/26/15.
 */
class GeoHelper$Test extends FunSuite with GeoHelper {


  test("test if position is in the georec") {

    val ret1 = isInRec(Position(1,2), Position(2,3), Position(3,4))

    assert(ret1 == false)

    val ret2 = isInRec(Position(2.5,3.5), Position(2,3), Position(3,4))

    assert(ret2 == true)
  }

  test("test if at least one of position list is in the georec") {


    val listPos = List(Position(1,2), Position(2,3), Position(3,4))

    val ret1 = hasPositionInRec(listPos, Position(0,0), Position(3,3))

    assert(ret1 == true)

    val ret2 = hasPositionInRec(listPos, Position(4,5), Position(6,7))

    assert(ret2 == false)

  }

  test("test caculate distance between positions") {

    val p1 = Position(37.79947, -122.511635)
    val p2 = Position(34.79947, -124.511635)
    val dis = distance(p1, p2)
    assert(Math.round(dis) == 379)
  }

  test("test get geohash with dynamic precision") {

    val p1 = Position(37.79947, -122.511635)
    val p2 = Position(34.79947, -124.511635)
    val dis = distance(p1, p2)
    assert(Math.round(dis) == 379)

    val geoHashes = getGeoHashListInRec(p2, p1)
    assert(geoHashes.head.length == 3)
  }

  test("test geohash list in a georec") {

    // the distance is about 13 km
    val bottomLeft = Position(48.6, -4.39)
    val topRight = Position(48.6996, -4.301)

    val ret = getGeoHashListInRec(bottomLeft, topRight)

    println("GeoHashes in the rec: " + ret)

    assert(ret.size == 12)
  }

  test("test boundary geohash list in a georec") {

    // the distance is about 13 km
    val bottomLeft = Position(48.6, -4.39)
    val topRight = Position(48.6996, -4.301)

    val ret = getGeoHashListInRec(bottomLeft, topRight)
    println("GeoHashes in the rec: " + ret)

    val ret2 = getBoundaryGeoHashListInRec(bottomLeft, topRight, ret)
    println("Boundary GeoHashes in the rec " + ret2)

    assert(ret2.size == 10)

  }

  test("test boundary geohash list in a georec - just one geohash") {

    // the distance is about 13 km
    val bottomLeft = Position(48.6, -4.39)
    val topRight = Position(48.6, -4.39)

    val ret = getGeoHashListInRec(bottomLeft, topRight)
    println("GeoHashes in the rec: " + ret)

    assert(ret.size == 1)

    val ret2 = getBoundaryGeoHashListInRec(bottomLeft, topRight, ret)
    println("Boundary GeoHashes in the rec " + ret2)

    // no inner geohash in this case
    assert(ret2.size == 1)
  }

}
