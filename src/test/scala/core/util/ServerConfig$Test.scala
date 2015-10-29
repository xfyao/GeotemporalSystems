package core.util

import org.scalatest.FunSuite

/**
 * Created by v962867 on 10/29/15.
 */
class ServerConfig$Test extends FunSuite {

  test("Read Configure") {
    val ret = ServerConfig.getConfig.getString("akka.loglevel")
    assert(ret != null)
  }

}
