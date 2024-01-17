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

package uk.gov.hmrc.pushpullnotificationsapi.services

import javax.inject.{Inject, Singleton}

import uk.gov.hmrc.crypto._

import uk.gov.hmrc.pushpullnotificationsapi.config.AppConfig

@Singleton
class LocalCrypto @Inject() (appConfig: AppConfig) extends Encrypter with Decrypter {
  implicit val aesCrypto: Encrypter with Decrypter = SymmetricCryptoFactory.aesCrypto(appConfig.mongoEncryptionKey)

  override def encrypt(plain: PlainContent): Crypted = aesCrypto.encrypt(plain)

  override def decryptAsBytes(reversiblyEncrypted: Crypted): PlainBytes = aesCrypto.decryptAsBytes(reversiblyEncrypted)

  override def decrypt(reversiblyEncrypted: Crypted): PlainText = aesCrypto.decrypt(reversiblyEncrypted)
}
