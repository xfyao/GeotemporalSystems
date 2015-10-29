package core.actors

import java.net.InetSocketAddress

import core.business.Controller
import core.model.Event
import core.util.ServerConfig
import net.liftweb.json._
import org.slf4j.LoggerFactory
import redis.actors.RedisSubscriberActor
import redis.api.pubsub.{Message, PMessage}

class SubscribeActor(channels: Seq[String] = Nil, patterns: Seq[String] = Nil, onConnectStatus:Boolean=>Unit)
  extends RedisSubscriberActor(new InetSocketAddress(ServerConfig.getConfig.getString("redis.hostname"),
    ServerConfig.getConfig.getInt("redis.port")), channels, patterns, None, onConnectStatus) {

  val logger = LoggerFactory.getLogger(this.getClass)

  implicit val formats = DefaultFormats

  var count = 0

  def onMessage(message: Message) {
    logger.info(s"message received: ${message.data.utf8String}")
    try {
      val event = parse(message.data.utf8String).extract[Event]
      Controller.addEvent(event)
      count += 1
      logger.info("message count = " + count)
    } catch {
      case ex: Throwable => logger.error("message error",ex)
    }
  }

  def onPMessage(pmessage: PMessage) {
    logger.info(s"pattern message received: $pmessage")
  }
}