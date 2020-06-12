Use this API to get notifications (pull) and to have notifications automatically sent (push).

Notifications are created by other HMRC APIs in response to events such as asynchronous API requests. If the HMRC 
API you are using supports Push Pull Notifications it will be stated on its documentation page.

## Push notifications

When subscribing to an HMRC API that supports Push Pull Notifications, you will be able to enter a Push/Callback URL.
When a notification is created it will be automatically sent by a POST request to the Push/Callback URL.

For example, if the Push/Callback URL has been set to `https://www.example.com/push`, when a notification is created
a POST request will be sent to `https://www.example.com/push` with a body similar to:

`{
    "notificationId": "1ed5f407-8096-40d1-87ef-9a2a103eeb85",
    "boxId": "50dca3fc-c37c-4f03-b719-63571333624c",
    "messageContentType": "application/json",
    "message": "{\"key\":\"value\"}",
    "status": "RECEIVED",
    "createdDateTime": "2020-06-01T10:20:23.160+0000"
}
`

For details about the structure of this JSON object see
<a href="#_get-a-list-of-notifications_get_accordion">Get a list of notifications</a>

## Pull notifications

Regardless of whether a Push/Callback URL has been setup, notifications can be retrieved by polling 
<a href="#_get-a-list-of-notifications_get_accordion">Get a list of notifications</a>. To avoid breaking our rate
limits we would suggest you call the endpoint no more than once very 10 seconds.

To use this endpoint you will need a box identifier. This will be made available to you via the HMRC API that you
are using.

## Processing notifications

The details of how to process notifications is bespoke to each HMRC API that creates notifications. However, it is 
expected that within the message of a notification there would be information (e.g. a correlation or request identifier)
that will allow the notification to be identified.
