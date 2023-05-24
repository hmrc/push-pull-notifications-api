package uk.gov.hmrc.pushpullnotificationsapi.mocks.connectors

import org.mockito.verification.VerificationMode
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import uk.gov.hmrc.pushpullnotificationsapi.connectors.ThirdPartyApplicationConnector

trait ThirdPartyApplicationConnectorMockModule extends MockitoSugar with ArgumentMatchersSugar {

  trait BaseThirdPartyApplicationConnectorMock {

    def aMock: ThirdPartyApplicationConnector

    def verify = MockitoSugar.verify(aMock)

    def verify(mode: VerificationMode) = MockitoSugar.verify(aMock, mode)

    def verifyZeroInteractions() = MockitoSugar.verifyZeroInteractions(aMock)

  object CreateBox {


  }

  }

  object ThirdPartyApplicationConnectorMock extends BaseThirdPartyApplicationConnectorMock {
    val aMock = mock[ThirdPartyApplicationConnector](withSettings.lenient())
  }

}
