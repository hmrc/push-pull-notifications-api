
# push-pull-notifications-api

This API allows notifications to be sent (pushed) to software developers or allows the software developer to get (pull) 
notifications. Notifications are create by other HMRC services. 

An example use case is for asynchronous API requests. For example:
1. Software developer makes an API request to API X (API X is any API on the HMRC API Platform).
1. API X sends a response 202 HTTP status to the software developer with a body containing a *Topic Identifier* and 
    a *Request/Correlation Identifier*.
1. Software developer polls the get notifications endpoint using the *Topic Identifier*, looking for a notification that
    contains the *Request/Correlation Identifier*.
1. API X starts the asynchronous processing of the request.
1. API X completes the asynchronous processing of the request. API X sends the response as a notification message to 
    this service using the *Topic Identifier* that was sent to the end user.
1. Software developer is still polling get notifications endpoint, and finds a match for the *Request/Correlation 
    Identifier* and can process the contents of the notification message. 

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").

## `GET /topics`
Return the details of a topic

```
curl https://push-pull-notifications-api.protected.mdtp/topics?topicName=TOPIC%201&clientId=client_id_1
```

### Query parameters
| Name | Description |
| --- | --- |
| `topicName` (required) | The name of the topic to get. URL encoded. For example ```Topic%201``` |
| `clientId` (required)| The Developer Hub Client ID that owns the topic |

### Response
HTTP Status: `200`
```
{
    "topicId":"1c5b9365-18a6-55a5-99c9-83a091ac7f26",
    "topicName":"TOPIC 2",
    "topicCreator":{
        "clientId":"client_id_2"
    },
    "subscribers":[]
}
```
### Error scenarios
| Scenario | HTTP Status | Body |
| --- | --- | --- |
| Missing query parameter | `400` | `{"statusCode":400,"message":"bad request"}`

### Suggested improvements:
* `Accept` header isn't being checked
* 400 response body return standard `{"code": "XX", "message": "ZZ"}` format
   

## `PUT /topics`
Create a new topic

This endpoint is restricted, only a whitelist of services can access it.

### Request headers
| Name | Description |
| --- | --- |
| `Content-type` | Either `application/json` or `text/json` 
| `User-agent` | User agent that identifies the calling service 

### Request
```
{
    "topicName": "Topic 1", 
    "clientId": "client_id_1"
}
```
| Name | Description |
| --- | --- |
| `topicName` | The name of the topic to create |
| `clientId` | The Developer Hub Client ID that can access this topic

### Response
HTTP Status: `201` if topic is created
```
1c5b9365-18a6-55a5-99c9-83a091ac7f26
```

HTTP Status: `200` if topic already exists
```
1c5b9365-18a6-55a5-99c9-83a091ac7f26
```

### Error scenarios
| Scenario | HTTP Status | Response |
| --- | --- | --- |
| Access denied | `400` | NONE
| Content-type header is missing | `415` | `{"statusCode":415,"message":"Expecting text/json or application/json body"}`
| `topicName` or `clientId` missing from request body | `422` | `{"code":"INVALID_REQUEST_PAYLOAD","message":{"obj.topicName":[{"msg":["error.path.missing"],"args":[]}]}}`

### Suggested improvements:
* `Accept` header isn't being checked
* When access is denied return a 403, and return standard `{"code": "XX", "message": "ZZ"}` format 
* 201 response body JSON rather than text.
* Validate the Client ID, verify that it exists in third-party-application.
* Validate topicName. Eg Only allow Alphanumeric, dash and underscore.
* 422 response body "message" as a sentence, Eg "topicName is missing"
* 415 response body return standard `{"code": "XX", "message": "ZZ"}` format

## `PUT /topics/:topicId/subscribers`

TODO


## `POST /notifications/topics/:topicId`

Send a notification to a topic

### Request headers
| Name | Description |
| --- | --- |
| `Content-type` | Either `application/json` or `application/xml` 

### Path parameters
| Name | Description |
| --- | --- |
| `topicId` | Identifier for a topic 

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
1c5b9365-18a6-55a5-99c9-83a091ac7f26
```

### Error scenarios
| Scenario | HTTP Status | Response |
| --- | --- | --- |
| Request body content type does not match header `Content-type` | `400` | `{"code":"INVALID_REQUEST_PAYLOAD","message":"Content Type not Supported or message syntax is invalid"}`

### Suggested improvements:
* `Accept` header isn't being checked.
* 201 response body JSON rather than text.
* Restrict access to this endpoint same as POST topic. (In fact POST topic doesn't have to be protected, but this one does)


## `GET /notifications/topics/:topicId`

**This endpoint is exposed through the API Platform**

Get a list of notifications that have been sent to a topic

### Path parameters
| Name | Description |
| --- | --- |
| `topicId` | Identifier for a topic 

### Query parameters
| Name | Description |
| --- | --- |
| `status` (optional) | Only return notifications with this status. One of `RECEIVED`, `READ`, `UNKNOWN` |
| `from_date` (optional)| Only return notifications created after this UTC datetime. ISO-8601 format. Eg `2020-06-03T14:20:54.987` |
| `to_date` (optional)| Only return notifications created before this UTC datetime. ISO-8601 format. Eg `2020-06-03T14:20:54.123` |

### Response
HTTP Status: `200`
```
[
    {
        "notificationId":"4e57c65a-f687-442c-b695-f635d5d2e856",
        "topicId":"7fe732c9-af27-4a94-973d-5c60d0a133d8",
        "notificationContentType":"APPLICATION_JSON",
        "message":"{\"topicName\": \"TOPIC 4!\", \"clientId\": \"client!!!!Â£$%^&*() id_4\"}",
        "status":"RECEIVED",
        "createdDateTime":"2020-06-03T14:14:54.108+0000"
    },
    {
        "notificationId":"583b6394-49ea-4a8e-8d66-c3a8f4b920a3",
        "topicId":"7fe732c9-af27-4a94-973d-5c60d0a133d8",
        "notificationContentType":"APPLICATION_JSON",
        "message":"<root>XXX</root>",
        "status":"RECEIVED",
        "createdDateTime":"2020-06-03T14:29:10.049+0000"
    }
]
```
| Name | Description |
| --- | --- |
| `notificationId` | Identifier for a notification
| `topicId` | Identifier for a topic
| `notificationContentType` | The content type of the notification body, either `APPLICATION_JSON` or `APPLICATION_JSON`
| `message` | The notification body
| `status` | The status of the notification. `RECEIVED`, `READ`, `UNKNOWN`
| `createdDateTime` | UTC date and time that the notification was created

### Error scenarios
| Scenario | HTTP Status | Response |
| --- | --- | --- |
| X-Client-ID header is missing (this will soon be updated when auth is implemented) | `400` | NONE
| Access denied (this will soon be updated when auth is implemented) | `404` | `{"code":"INVALID_REQUEST_PAYLOAD","message":"client_id_2 does not match topicCreator"}`
| Topic not found | 404 | NONE

### Suggested improvements:
* `Accept` header isn't being checked
* 400 response body return standard `{"code": "XX", "message": "ZZ"}` format
* 404 access denied response should be 403
* 404 access denied response body shouldn't contain the client ID 
* 404 Topic not found return standard `{"code": "XX", "message": "ZZ"}` format
* 200 response body `APPLICATION_XML` should be `application/xml` and `APPLICATION_JSON` should be `application/json`
* 200 response body when there are no notification is text "no results", this should be any empty []
* If you use an invalid value in `status`, `from_date`, `to_date` query parameter, you get a 404 response. This should 
    be a 400 standard `{"code": "XX", "message": "ZZ"}` format.
* Q. Could we have an additional filter that returns all notifications that were created after a notificationId? (Phase 2)
* Q. Are notification statuses READ and UNKNOWN actually used?