package uk.gov.hmrc.pushpullnotificationsapi.mocks

import org.mockito.verification.VerificationMode
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId
import uk.gov.hmrc.pushpullnotificationsapi.models.{Box, BoxCreateFailedResult, BoxCreatedResult, BoxDeleteAccessDeniedResult, BoxDeleteNotFoundResult, BoxDeleteSuccessfulResult, BoxId, BoxRetrievedResult}
import uk.gov.hmrc.pushpullnotificationsapi.services.BoxService

import scala.concurrent.Future.{failed, successful}

trait BoxServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  trait BaseBoxServiceMock {

    def aMock: BoxService

    def verify = MockitoSugar.verify(aMock)

    def verify(mode: VerificationMode) = MockitoSugar.verify(aMock, mode)

    def verifyZeroInteractions() = MockitoSugar.verifyZeroInteractions(aMock)

  object CreateBox {

    def thenFailsWithException(error: String) = {
      when(aMock.createBox(*[ClientId], *, *)(*, *))
      .thenReturn(failed(new RuntimeException(error)))
    }

    def thenSucceedCreated(box: Box) = {
      when(aMock.createBox(*[ClientId], *, *)(*, *)).thenReturn(successful(BoxCreatedResult(box)))
    }

    def thenSucceedRetrieved(box: Box) = {
      when(aMock.createBox(*[ClientId], *, *)(*, *)).thenReturn(successful(BoxRetrievedResult(box)))
    }

    def thenFailsWithBoxName(boxName: String, clientId: ClientId) = {
      when(aMock.createBox(eqTo(clientId), eqTo(boxName), *)(*, *)).thenReturn(successful(BoxCreateFailedResult(s"Box with name :$boxName already exists for clientId: ${clientId.value} but unable to retrieve")))
    }


    def thenFailsWithCreateFailedResult(error: String) = {
      when(aMock.createBox(*[ClientId], *, *)(*, *)).thenReturn(successful(BoxCreateFailedResult(error)))
    }

    def verifyCalledWith(clientId: ClientId, boxName: String, isClientManaged: Boolean) = {
      verify.createBox(eqTo(clientId), eqTo(boxName), eqTo(isClientManaged))(*, *)
    }
  }

    object DeleteBox {

      def failsNotFound() = {
        when(aMock.deleteBox(*[ClientId], *[BoxId])(*)).thenReturn(successful(BoxDeleteNotFoundResult()))
      }

      def failsAccessDenied() = {
        when(aMock.deleteBox(*[ClientId], *[BoxId])(*)).thenReturn(successful(BoxDeleteAccessDeniedResult()))
      }

      def isSuccessful() = {
        when(aMock.deleteBox(*[ClientId], *[BoxId])(*)).thenReturn(successful(BoxDeleteSuccessfulResult()))
      }

      def verifyCalledWith(clientId: ClientId, boxId: BoxId) = {
        verify.deleteBox(eqTo(clientId), eqTo(boxId))(*)
      }

    }
    //getAllBoxes

    //deleteBox

    //getBoxByNameAndClientId

    //getBoxesByClientId

    //updateCallbackUrl

    //validateBoxOwner

  }

  object BoxServiceMock extends BaseBoxServiceMock {
    val aMock = mock[BoxService](withSettings.lenient())


  }

}
