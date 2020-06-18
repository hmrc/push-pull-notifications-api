Use the Push Pull Notifications API to get notifications (pull) and to send notifications (push) automatically.

Other HMRC APIs create notifications in response to events like asynchronous API requests.

Check if the HMRC API you are using supports Push Pull Notifications in the API documentation.

## Push notifications

After subscribing to an HMRC API that supports Push Pull Notifications, you can enter a Push/Callback URL. 

Created notifications are sent automatically by a POST request to the Push/Callback URL. 

For example, if the Push/Callback URL is set to `https://www.example.com/push` when a notification is created, a POST
request is sent to `https://www.example.com/push` with a body similar to:

`{
    "notificationId": "1ed5f407-8096-40d1-87ef-9a2a103eeb85",
    "boxId": "50dca3fc-c37c-4f03-b719-63571333624c",
    "messageContentType": "application/json",
    "message": "{\"key\":\"value\"}",
    "status": "RECEIVED",
    "createdDateTime": "2020-06-01T10:20:23.160+0000"
}
`

See <a href="#_get-a-list-of-notifications_get_accordion">get a list of notifications</a> for details about the
structure of this JSON object.

## Pull notifications

Regardless of whether a Push/Callback URL is set up, notifications can be retrieved by polling
<a href="#_get-a-list-of-notifications_get_accordion">get a list of notifications</a>.

**Avoid breaking our rate limits and call the endpoint no more than once every 10 seconds.**

You will need a Box Identifier to use this endpoint. A Box Identifier is made available by the HMRC API you are using.

## Processing notifications

The notifications process is different for each HMRC API that creates notifications. 

Notification messages should include information like a correlation or request identifier that allows notifications to
be identified.
