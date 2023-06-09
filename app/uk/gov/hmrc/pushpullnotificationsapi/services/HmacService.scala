/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.pushpullnotificationsapi.services

import java.nio.charset.StandardCharsets.UTF_8
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Singleton

@Singleton
class HmacService {

  private val algorithm = "HmacSHA1"

  /** Generates an HMAC-SHA1 signature for the provided message using the provided key.
    *
    * @param key
    *   The signing key in hexadecimal format
    * @param message
    *   The message to be signed
    * @return
    *   The generated HMAC signature in hexadecimal format
    */
  def sign(key: String, message: String): String = {
    val secretKey = new SecretKeySpec(key.getBytes(UTF_8), algorithm)
    val mac = Mac.getInstance(algorithm)
    mac.init(secretKey)
    mac.doFinal(message.getBytes(UTF_8)).map("%02x".format(_)).mkString
  }
}
