# Payment gateway


# Design considerations and choices:
- REST because We have CRUD, we do a POST, and GET. Every call has all clients session context . Uses JSON instead of XML. Other comparisons
  - GRaphQl, user is not extracting a lot of data from single endpoint
  - Websocket: No two way communitcaiton like a web chat
  - Webhook: request isnt server initiated or dependent

- Storing only last 4 card number and discarding the rest and the CVV

- We only persist when the Bank gives a 200 (authorised or declined)

- REST is stateless , so we wouldn't have session Ids/session timeouts

- We would only retry when the bank gives 503 since a failed payment is still better than double payment in other cases. for 503, we can re-try with 2s timeout , with exponential backoff

- Currencies picked:  GBP, EUR, USD
 


### Assumptions:
- No authentication , in future we would use API keys
- We would assume that expiry in current month is not valid
- 1 payment per payment_Id
- Authentication is out of scope , assuming anyone can make a payment
- Card blocking : Certain blocked or black listed users should not be allowed to make a payment, we could store their last 4 digits of the case with expiry for future use
- Concurrency is sacrificed because we don't have db Locking , just a map
- Amount is in minor units hence capped due to integer limit
- Any merchant with payment id can retrieve details, even if that's not the payment they made. In future, we should store merchant id against the details
- 2 separate requests will be genuine 2 payment requests instead of blocking the 2nd one
- No retries to the bank simulator as a payment could go out twice unless it was a 503


#  Gateway
- Validating the request
- Store card information
- forward payment request
- accepting responses to and from the bank


# Requirement and codes:
A merchant should be able to process a payment through the payment gateway and receive one of the following types of response:
- 200 from bank: *Authorized* - the payment was authorized
  - Merchant sees 200 with " "Payment was successful of amount ...  " message
- 200 from bank: *Declined* - the payment was declined
  - Merchant sees 200 with "Payment has been declined" message
- non-200 from bank: malformatted/server unavailable
  - Merchant sees 502 : Payment could not be processed
  - For Bank 400 : Some validation on our side was missed, throw error + log
- *Rejected* : 400 - No payment could be created as invalid information 

For getting payment details (2nd requirement):
- 200 on GET success
- 404 on payment id not found 
- 400 or malformatted 


----------------

# Controller and clients
- 1 controller to handle incoming requests from 3rd party
  - POST request to handle payments with card, forward to bank
    - `POST "/payment"`
    - Request body: Given PostPaymentRequest
    - Response: Given PostPaymentResponse
  - GET to get payment details (2nd requirement)
    - `GET "/payment/{id}"` - given
    - Response - given GetPaymentResponse

-  1 Client to forward request to mock brand
   - PostPaymentRequest to POST "/payments" with base url as : http://localhost:8080


----------------
# Gateway request validations:
-  Validation rules :
  - Card details present, length 14-19, 0-9 only , trim whitespaces 
  - Card number should only be numeric
  - Expiry month between 1-12
  - Card not expired, 4 digit long year `dd/yyyy` current month is considered expired
  - Currency is one of 3 valid codes and is 3 character code
  - CVV 3-4 chars,only numeric
  - Amount in EUR , GBP or USD
  - 
- Return first validation errors found in response with code 400

# Gateway Response

- Payment Id :  GUID / UUID
- Status: authorized, Declined, rejected
- Last four digits of the case
- Amount : **represent in minor currency unit, $0.01 would be supplied as 1

# 2nd requirement
- Request:
  - Payment Id
- Response:
  - same as above 

------

Things to consider later:
- Throttling
- DB locking or making it transactional
- Generate an open api spec
- can think of extra admin points
- Observability and logging

Concurrency handling:
- How many users are expected to use the platform ?
- The merchant sends an Idempotency-Key header, scoped per merchant.
- The gateway atomically inserts the key as "in progress". use,putIfAbsent.
- The first request proceeds to the bank and stores its result under that key.
- A later request with the same key:
  - returns the stored result if the first has finished, or
  - returns 409 Conflict straight away if the first is still in flight.