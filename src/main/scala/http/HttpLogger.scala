package http

import org.slf4j.{LoggerFactory, Logger}

/**
 * Created by v962867 on 10/27/15.
 */
trait HttpLogger {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)
}
