package core.util

import org.slf4j.{LoggerFactory, Logger}

/**
 * Created by v962867 on 10/27/15.
 */
trait AppLogger {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)
}
