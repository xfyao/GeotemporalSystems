package core.util

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps


/**
 * Created by v962867 on 10/24/15.
 */
trait FutureHelper {

  def getFutureValue[T](f: Future[T]): T = Await.result[T](f, 5 seconds)
}
