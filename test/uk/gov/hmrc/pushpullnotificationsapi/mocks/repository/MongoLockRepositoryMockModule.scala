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

package uk.gov.hmrc.pushpullnotificationsapi.mocks.repository

import scala.concurrent.Future.successful

import org.mockito.verification.VerificationMode
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.mongo.lock.MongoLockRepository

trait MongoLockRepositoryMockModule extends MockitoSugar with ArgumentMatchersSugar {

  trait BaseMongoLockRepositoryMock {
    def aMock: MongoLockRepository

    def verify = MockitoSugar.verify(aMock)

    def verify(mode: VerificationMode) = MockitoSugar.verify(aMock, mode)

    def verifyZeroInteractions() = MockitoSugar.verifyZeroInteractions(aMock)

    object IsLocked {

      def thenTrueTrueFalse() = {
        when(aMock.isLocked(*, *)).thenReturn(successful(true)).andThenAnswer(successful(true)).andThenAnswer(successful(false))
      }

      def theSuccess(returnVal: Boolean) = {
        when(aMock.isLocked(*, *)).thenReturn(successful(returnVal))
      }

    }

    object TakeLock {

      def thenTrueFalse() = {
        when(aMock.takeLock(*, *, *)).thenReturn(successful(true)).andThenAnswer(successful(false))
      }

      def thenSuccess(bool: Boolean) = {
        when(aMock.takeLock(*, *, *)).thenReturn(successful(bool))
      }

    }

    object ReleaseLock {

      def thenSuccess() = {
        when(aMock.releaseLock(*, *)).thenReturn(successful(()))
      }

    }

  }

  object MongoLockRepositoryMock extends BaseMongoLockRepositoryMock {
    val aMock = mock[MongoLockRepository](withSettings.lenient())
  }

}
