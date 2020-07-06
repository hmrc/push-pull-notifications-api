
# push-pull-notifications-api

This API allows notifications to be sent (pushed) to software developers or allows the software developer to get (pull) 
notifications. Notifications are create by other HMRC services. 

An example use case is for asynchronous API requests.
1. API X defines an *api-subscription-field* of type PPNS (see https://confluence.tools.tax.service.gov.uk/pages/viewpage.action?pageId=182540862)
1. Software developer subscribes to API X in Developer Hub and can optionally add an endpoint where notifications 
    will be pushed to. This automatically creates a PPNS box called [API_CONTEXT]##[API_VERSION]##[API_SUBSCRIPTION_FIELD_NAME]
    , Eg `hello/world##1.0##callbackUrl` 
1. Software developer makes an API request to API X
1. API X gets the client ID from either the `X-Client-Id` header or from an _auth_ retrieval.
1. API X makes a request to `GET /box` using the inferred box name and client ID to retrieve the box ID.
1. API X generates a correlation/request ID.
1. API X sends a response 202 HTTP status to the software developer with a body containing the box ID and 
    the correlation/request ID.
1. API X starts their asynchronous process, saving the state of this with the correlation/request ID and PPNS box ID.
1. API X complete their asynchronous process, retrieving correlation/request ID and PPNS box ID from state.
1. API X creates a _message_ which must contain the correlation/request ID, and POSTs it to PPNS using the box ID.
1. If the software developer has set the api-subscription-field, the notification is POSTed to their endpoint
    * The API consumer receives the notification and matches the correlation/request ID in the notification with the 
      correlation/request ID they got from the initial API request, they extract the message and process it accordingly.
1. If the API consumer chooses to call the PPNS get-notifications endpoint using the box ID, they can retrieve a 
    list of notifications.
    * The API consumer iterates over each notification and matches the correlation/request ID in the notification 
      with the correlation/request ID they got from the initial API request, they extract the message and process it accordingly.

## Terminology

| Term | Description |
| --- | --- |
| _box_ | A container for notifications |
| _notification_ | A wrapper for a message. Notifications expire after 30 days |
| _message_ | XML or JSON that is being communicated from an API producer to an API consumer |

## `GET /box`
Return the details of a box

### Query parameters
| Name | Description |
| --- | --- |
| `boxName` (required) | The name of the box to get. URL encoded. For example ```BOX%202``` |
| `clientId` (required)| The Developer Hub Client ID that owns the box |

### Response
HTTP Status: `200`
```
{
    "boxId": "1c5b9365-18a6-55a5-99c9-83a091ac7f26",
    "boxName":"BOX 2",
    "boxCreator":{
        "clientId": "X5ZasuQLH0xqKooV_IEw6yjQNfEa"
    },
    "subscribers":[
        {
            "subscribedDateTime": "2020-06-01T10:27:33.613+0000",
            "callBackUrl": "https://www.example.com/callback",
            "subscriptionType": "API_PUSH_SUBSCRIBER",
            "subscriberId": "6cdef122-55a6-55a5-99c9-83a091ac7f86"
        }
    ]
}
```
| Name | Description |
| --- | --- |
| `boxId` | Identifier for a box
| `boxName` | Box name 
| `boxCreator.clientId` | Developer Hub Application Client ID, that created and has access to this box
| `subscribers` | A list of subscribers to this box |
| `subscribers.subscribedDateTime` | ISO-8601 UTC date and time that the subscription was created |
| `subscribers.callBackUrl` | The URL of the endpoint where push notifications will be sent |
| `subscribers.subscriptionType` | The type of subscriber. Currently only `API_PUSH_SUBSCRIBER` is supported |
| `subscribers.subscriberId` | Unique identifier for the subscriber |

### Error scenarios
| Scenario | HTTP Status | Code |
| --- | --- | --- |
| Missing or incorrect query parameter | `400` | `BAD_REQUEST`
| Box does not exist | `404` | `BOX_NOT_FOUND`

## `PUT /box`
Create a new box

This endpoint is restricted, only a whitelist of services can access it.

### Request headers
| Name | Description |
| --- | --- |
| `Content-Type` | Either `application/json` or `text/json` 
| `User-Agent` | User agent that identifies the calling service 

### Request
```
{
    "boxName": "Box 1", 
    "clientId": "X5ZasuQLH0xqKooV_IEw6yjQNfEa"
}
```
| Name | Description |
| --- | --- |
| `boxName` | The name of the box to create |
| `clientId` | The Developer Hub Client ID that can access this box |

### Response
HTTP Status: `201` if the box is created
```
{
    "boxId": "1c5b9365-18a6-55a5-99c9-83a091ac7f26" 
}
```

HTTP Status: `200` if the box already exists
```
{
    "boxId": "1c5b9365-18a6-55a5-99c9-83a091ac7f26" 
}
```

### Error scenarios
| Scenario | HTTP Status | Code |
| --- | --- | --- |
| `boxName` or `clientId` missing from request body | `400` | `INVALID_REQUEST_PAYLOAD`
| Access denied, service is not whitelisted | `403` | `FORBIDDEN`

## `PUT /box/:boxId/subscribers`

Set the subscribers for a box. The _callbackUrl_ in the subscriber is the endpoint where push notifications will be 
sent to.

### Path parameters
| Name | Description |
| --- | --- |
| `boxId` | Unique identifier for a box. Eg `1c5b9365-18a6-55a5-99c9-83a091ac7f26` |

### Request headers
| Name | Description |
| --- | --- |
| `Content-type` | Must be `application/json` 

### Request
```
{
    "subscribers":[
        {
            "subscriberType": "API_PUSH_SUBSCRIBER",
            "callBackUrl": "https://www.example.com/callback"
        }
    ]
}
```
| Name | Description |
| --- | --- |
| `subscriberType` | The type of subscriber. Currently only `API_PUSH_SUBSCRIBER` is supported |
| `callBackUrl` | The URL of the endpoint where push notifications will be sent |

### Response
HTTP Status: `200`
```
{
    "boxId": "1c5b9365-18a6-55a5-99c9-83a091ac7f26",
    "boxName":"BOX 2",
    "boxCreator":{
        "clientId": "X5ZasuQLH0xqKooV_IEw6yjQNfEa"
    },
    "subscribers":[
        {
            "subscribedDateTime": "2020-06-01T10:27:33.613+0000",
            "callBackUrl": "https://www.example.com/callback"
            "subscriptionType": "API_PUSH_SUBSCRIBER",
            "subscriberId": "6cdef122-55a6-55a5-99c9-83a091ac7f86"
        }
    ]
}
```
| Name | Description |
| --- | --- |
| `boxId` | Identifier for a box
| `boxName` | Box name 
| `boxCreator.clientId` | Developer Hub Application Client ID, that created and has access to this box.
| `subscribers` | A list of subscribers to this box |
| `subscribers.subscribedDateTime` | ISO-8601 UTC date and time that the subscription was created. |
| `subscribers.callBackUrl` | The URL of the endpoint where push notifications will be sent |
| `subscribers.subscriptionType` | The type of subscriber. Currently only `API_PUSH_SUBSCRIBER` is supported |
| `subscribers.subscriberId` | Unique identifier for the subscriber |

### Error scenarios
| Scenario | HTTP Status | Code |
| --- | --- | --- |
| Box ID is not a UUID | `400` | `BAD_REQUEST`
| `subscriberType` or `callBackUrl` is missing or invalid in request body | `400` | `INVALID_REQUEST_PAYLOAD`
| Box does not exist | `404` | `BOX_NOT_FOUND`

## `POST /box/:boxId/notifications`

Create a notification

This endpoint is restricted, only a whitelist of services can access it.

### Path parameters
| Name | Description |
| --- | --- |
| `boxId` | Unique identifier for a box. Eg `1c5b9365-18a6-55a5-99c9-83a091ac7f26` |

### Request headers
| Name | Description |
| --- | --- |
| `Content-Type` | Either `application/json` or `application/xml` |
| `User-Agent` | User agent that identifies the calling service | 

### Request
The request body can be any JSON or XML (must match the `Content-type` header). The request body is the notification 
message that can be retrieved.

Example 1:
```
{
    "foo": "bar", 
}
```

Example 2:
```
<foo>bar</foo>
```

### Response
HTTP Status: `201`
```
{
    "notificationId": "1c5b9365-18a6-55a5-99c9-83a091ac7f26"
}
```

### Error scenarios
| Scenario | HTTP Status | Code |
| --- | --- | --- |
| Box ID is not a UUID | `400` | `BAD_REQUEST`
| Request body does not match the Content-Type header | `400` | `INVALID_REQUEST_PAYLOAD` |
| Access denied, service is not whitelisted | `403` | `FORBIDDEN`
| Box does not exist | `404` | `BOX_NOT_FOUND`

## `GET /box/:boxId/notifications`

**This endpoint is exposed through the API Platform see documentation on https://developer.qa.tax.service.gov.uk/**

Get a list of notifications that have been sent to a box

### Path parameters
| Name | Description |
| --- | --- |
| `boxId` | Unique identifier for a box. Eg `1c5b9365-18a6-55a5-99c9-83a091ac7f26` |

### Query parameters
| Name | Description |
| --- | --- |
| `status` (optional) | Only return notifications with this status. One of `RECEIVED` |
| `fromDate` (optional)| Only return notifications created after this UTC datetime. ISO-8601 format. Eg `2020-06-03T14:20:54.987` |
| `toDate` (optional)| Only return notifications created before this UTC datetime. ISO-8601 format. Eg `2020-06-03T14:20:54.123` |

### Request headers
| Name | Description |
| --- | --- |
| `Authorization` | A valid _auth_ bearer token |
| `Accept` | Standard API Platform Accept header. `application/vnd.hmrc.1.0+json` | 

### Response
HTTP Status: `200`
```
[
    {
        "notificationId":"4e57c65a-f687-442c-b695-f635d5d2e856",
        "boxId":"7fe732c9-af27-4a94-973d-5c60d0a133d8",
        "messageContentType":"application/json",
        "message":"{\"test\": \"hello\"}",
        "status":"RECEIVED",
        "createdDateTime":"2020-06-03T14:14:54.108+0000"
    },
    {
        "notificationId":"583b6394-49ea-4a8e-8d66-c3a8f4b920a3",
        "boxId":"7fe732c9-af27-4a94-973d-5c60d0a133d8",
        "messageContentType":"application/xml",
        "message":"<root>XXX</root>",
        "status":"RECEIVED",
        "createdDateTime":"2020-06-03T14:29:10.049+0000"
    }
]
```
| Name | Description |
| --- | --- |
| `notificationId` | Unique identifier for a notification |
| `boxId` | Unique identified for a box that the notification was sent to |
| `messageContentType` | Content type of the message, either `application/json` or `application/xml` |
| `message` | The notification message. JSON or XML as defined by messageContentType. If this is JSON then it will have been escaped. For details about the structure of this data consult the documentation for the HMRC API that created the notification
| `status` | Status of the notification. `RECEIVED`, `READ`, `UNKNOWN` |
| `createdDateTime` | ISO-8601 UTC date and time that the notification was created|

### Error scenarios
| Scenario | HTTP Status | Code |
| --- | --- | --- |
| Box ID is not a UUID | `400` | `BAD_REQUEST`
| Query parameters are invalid | `400` | `INVALID_REQUEST_PAYLOAD` |
| Access denied, The Developer Hub Client ID in the _auth_ bearer token does not have access to this box | `403` | `FORBIDDEN`
| Box does not exist | `404` | `BOX_NOT_FOUND`
| The accept header is missing or invalid | `406` | `ACCEPT_HEADER_INVALID`

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
