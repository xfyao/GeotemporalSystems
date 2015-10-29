package core.util

import com.typesafe.config.{Config, ConfigFactory}

/**
 * Created by v962867 on 10/29/15.
 */
object ServerConfig {

  private val conf: Config = ConfigFactory.load()

  def getConfig: Config = conf
}
