## Synopsis

A system shows how to index, process and utilize the geo-temporal information in a scalable way.

## Software Stack

Scala: a programming language, v2.11.7

Akka Actor: a toolkit to implement concurrent and distributed system which is used to index data asynchronously in this project, v2.3.6

Spray: lightweight http server to provide web service, v1.3.1

Redis: in-memory data structure store, which is used as database, cache and message broker, v2.8

GeoHash: a latitude/longitude geocode system used to index geo-temporal information in KV store

JMeter: a test automation framework to provide load testing, v1.0

## Installation

Redis: http://redis.io/download. The default configuration is used.

SBT: http://www.scala-sbt.org

Steps:

(1) start Redis: redis-2.8.23/src/redis-server

(2) start application in SBT environment: yourproject/sbt run

(3) (optional) use jmeter script tools/geotest.jmx to populate trip data to system

## Tests

This system accepts the geo-temporal events by http post and Redis message sub/pub. The messages need to be posted to
http://localhost:8081/addEvent or published to Redis "geoevent" channel.

The messages are in JSON format and have three types: begin, update and end. Examples are below:

a trip begins:

{
"event": "begin",
"tripId": 432,
"lat": 37.79947,
"lng": -122.511635,
"epoch": 1392864673040
}

update during trip:

{
"event": "update",
"tripId": 432,
"lat": 37.79947,
"lng": -122.511635,
"epoch": 1392864673040
}

when the trip ends:

{
"event": "end",
"tripId": 432,
"lat": 37.79947,
"lng": -122.511635,
"epoch": 1392864673040,
"fare": 4.5
}

Query APIs are supported as below. And the sample postman script can be found at tools/Geo.json.postman_collection

(1) count finished trips in geo rec queried. Example:

Query:

http://localhost:8081/countTrips/37.708206,-122.993353/37.808206,-122.493353

Return:

{
  "code": "ok",
  "ret": "{\"count\":32}"
}


(2) count finished trips and total fare which start or stop in the geo rec queried. Example:

Query:

http://localhost:8081/countTripsForStartAndStop/31.59947,-126.711635/40.90047,-110.001635

Return:

{
  "code": "ok",
  "ret": "{\"totalCount\":824,\"totalFare\":32954.5}"
}

(3) count all occuring trips. Example:

Query:

http://localhost:8081/countOcurringTrips

Return:

{
  "code": "ok",
  "ret": "{\"count\":5}"
}


## License

TBD
