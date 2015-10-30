package core.util

import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

/**
 * Created by v962867 on 10/29/15.
 */
object ThreadPool {
  val poolSize = ServerConfig.getConfig.getInt("app.threadpoolsize")
  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(poolSize))
}
