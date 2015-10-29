package core.util

import org.slf4j.{LoggerFactory, Logger}

/**
 * Created by v962867 on 10/28/15.
 */
trait CacheLogger {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)
}
