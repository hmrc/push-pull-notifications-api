/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.thirdpartydelegatedauthority.utils

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

import uk.gov.hmrc.pushpullnotificationsapi.util.ApplicationLogger

object FutureUtils extends ApplicationLogger {
  // Don't use these but the file is not yet in a library
  //
  // def predicate(value: => Boolean)(error: => Throwable): Future[Unit] = {
  //   if (value) Future.successful((): Unit)
  //   else Future.failed(error)
  // }

  // def timeThisFuture[T](f: => Future[T])(implicit file: sourcecode.File, line: sourcecode.Line, enc: sourcecode.Enclosing, ec: ExecutionContext): Future[T] = {
  //   val shortFile = file.value.split(java.io.File.separator).toList.lastOption.getOrElse("?")
  //   timeThisFuture(f, s"$shortFile-${line.value} in ${enc.value}")
  // }

  def timeThisFuture[T](f: => Future[T], msg: => String)(implicit ec: ExecutionContext): Future[T] = {
    val startTime = System.currentTimeMillis()
    val theF = f

    theF.onComplete({ t =>
      val endTime = System.currentTimeMillis()
      val duration = endTime - startTime
      if (duration >= 100) {
        val message = s"Timer @ $msg"
        t match {
          case Success(_) => logger.info(s"$message took $duration ms")
          case Failure(_) => logger.info(s"$message failure took $duration ms")
        }
      }
    })
    theF
  }
}
