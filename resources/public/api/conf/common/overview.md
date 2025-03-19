Use the Push Pull Notifications API to get notifications (pull) and to send notifications (push) automatically.

Other HMRC APIs create notifications in response to events like asynchronous API requests.

Check if the HMRC API you are using supports Push Pull Notifications in the API documentation.

Notifications will be deleted after 30 days.

## Push notifications

You can enter a Push/Callback URL after subscribing to an HMRC API that supports Push Pull Notifications. 

Created notifications are sent automatically by a POST request to the Push/Callback URL. 

For example, if the Push/Callback URL is set to `https://www.example.com/push` when a notification is created, a POST
request is sent to `https://www.example.com/push` with a body similar to:

<pre>
{
    "notificationId": "1ed5f407-8096-40d1-87ef-9a2a103eeb85",
    "boxId": "50dca3fc-c37c-4f03-b719-63571333624c",
    "messageContentType": "application/json",
    "message": "{\"key\":\"value\"}",
    "status": "PENDING",
    "createdDateTime": "2020-06-01T10:20:23.160+0000"
}
</pre>

> We only support PPNS callback URLs on the default HTTPS port 443. You do not need to specify the port in the callback URL.

See [get a list of notifications](1.0/oas/page#tag/push-pull-notification-api/operation/Getalistofnotifications) for details about the
structure of this JSON object.

When a notification is pushed its status will be `PENDING`. If your service responds to this request with an HTTP status code 200, the notification status is updated to `ACKNOWLEDGED`.

If your service responds with a different HTTP status code, the notification status will remain as `PENDING` and the request is retried several times over the next few hours.

If after a few hours, an HTTP status code 200 has not been received, the notification status is updated to `FAILED`.

### Design your application to process duplicate push notifications as a single notification (idempotency)

The push notification system is designed to send ‘At least once’ to guarantee delivery. In most cases this means notifications will be sent once and successfully received.

In rare cases of network disruption, messages may be sent more than once. You should design your application to process duplicate notifications as a single notification. This will prevent errors and provide a consistent outcome for your application and users.

### Validating the callback URL

When you save a callback URL to receive push notifications from an API, the system will try to verify it by sending a GET request to the callback URL with the query parameter “challenge”.

You should return a 200 response with the challenge in the JSON response body. For example: 

<pre>
{
  “challenge”: “challenge_from_query_parameter”
}
</pre>

If we don’t receive a successful response with a matching challenge within 20 seconds, the request to save the callback URL will fail.

You will see an error message to inform you that the callback URL is invalid.
 

## Push Secret

When you subscribe to an API that sends Push notifications, we will generate a push secret you can use to validate the payload signature and confirm notifications come from HMRC.

### Validate the payload signature

Using the push secret, HMRC will generate a signature of the payload for every notification sent to the callback URL. 

That signature will be available in the HTTP header called `X-Hub-Signature`.

When receiving a notification, you should compute the signature of the payload using the push secret and verify that it matches the value in the X-Hub-Signature header.

The signature is calculated using HMAC-SHA1 and converted to hexadecimal format. For example (example in Scala)

<pre>
import java.nio.charset.StandardCharsets.UTF_8
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

def sign(pushSecret: String, payload: String): String = {
 val secretKey = new SecretKeySpec(pushSecret.getBytes(UTF_8), "HmacSHA1")
 val mac = Mac.getInstance("HmacSHA1")
 mac.init(secretKey)
 mac.doFinal(payload.getBytes(UTF_8)).map("%02x".format(_)).mkString
}
</pre>

Using `sample key` as a pushSecret and `{"sample": "payload"}` as a payload, will return the signature `c6cdd3e30021fe66d88d37088fed2566453eb7fb`.

## Pull notifications

Regardless of whether a Push/Callback URL is set up, notifications can be retrieved by polling [get a list of notifications](1.0/oas/page#tag/push-pull-notification-api/operation/Getalistofnotifications)

**Avoid breaking our rate limits and call the endpoint no more than once every 10 seconds.**

You will need a Box Identifier to use this endpoint. A Box Identifier is made available by the HMRC API you are using.

When you have successfully processed the notification, you can update its status to `ACKNOWLEDGED` by calling the 
[acknowledge a list of notifications](1.0/oas/page#tag/push-pull-notification-api/operation/Acknowledgealistofnotifications) endpoint.

## Notification statuses

* `PENDING` means the notification was created but has not been processed
* `FAILED` means the notification was pushed to your Push/Callback URL, but no HTTP status code 200 was returned
* `ACKNOWLEDGED` means the notification was successfully pushed to your Push/Callback URL or you processed the
notification using the [acknowledge a list of notifications](1.0/oas/page#tag/push-pull-notification-api/operation/Acknowledgealistofnotifications) endpoint

## Processing notifications

The way to process notifications is different for each HMRC API. Check the documentation for the HMRC API you are using.

Notification messages should include information like a correlation or request identifier that allows notifications to
be identified.
