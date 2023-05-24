package uk.gov.hmrc.pushpullnotificationsapi.mocks

import org.mockito.stubbing.ScalaOngoingStubbing
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId
import uk.gov.hmrc.pushpullnotificationsapi.models.{Box, BoxCreatedResult, BoxId, CreateBoxResult, Subscriber, SubscriberContainer}
import uk.gov.hmrc.pushpullnotificationsapi.repository.BoxRepository

import scala.concurrent.Future
import scala.concurrent.Future.successful

trait BoxRepositoryMockModule extends MockitoSugar with ArgumentMatchersSugar {

  trait BaseBoxRepositoryMock {
    def aMock: BoxRepository


    object CreateBox {
      def succeedsWithCreated(box: Box): ScalaOngoingStubbing[Future[CreateBoxResult]] ={
        when(aMock.createBox(*[Box])(*)).thenReturn(successful(BoxCreatedResult(box)))
      }
    }

    object GetBoxByNameAndClientId {

      def returnsNone(): ScalaOngoingStubbing[Future[Option[Box]]] = {
        when(aMock.getBoxByNameAndClientId(*, *[ClientId])).thenReturn(successful(None))
      }


      def succeedsWithOptionalBox(boxName: String, clientId: ClientId, optionalBox: Option[Box]): ScalaOngoingStubbing[Future[Option[Box]]] = {
        when(aMock.getBoxByNameAndClientId(eqTo(boxName), eqTo(clientId))).thenReturn(successful(optionalBox))
      }
    }

    object UpdateSubscriber {

      def succeedsForBoxId(boxId: BoxId, optionalBox: Option[Box]): ScalaOngoingStubbing[Future[Option[Box]]] = {
        when(aMock.updateSubscriber(eqTo(boxId), *)).thenReturn(successful(optionalBox))
      }
    }
  }

  object BoxRepositoryMock extends BaseBoxRepositoryMock {
    val aMock = mock[BoxRepository]
  }
}
