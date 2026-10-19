# Payment gateway

An API based payment gateway to offer a way for merchants to collect payment from their shoppers 

Tech stack used:
- **Language and build:** Java 17, Gradle
- **Framework:** Spring Boot with Spring MVC using api-key interceptor 
- **API:** REST over JSON (Jackson), documented with OpenAPI / Swagger UI (springdoc 2.2.0)
- **Validation:** Jakarta Bean Validation (Hibernate Validator) for the request rules
- **Bank integration:** Spring `RestTemplate`, with timeouts and a small retry loop
- **Storage:** in-memory repository (the provided test double), no database
- **Observability:** Spring Boot Actuator and Micrometer, exported to Prometheus and shown in Grafana
- **Testing:** JUnit 5, Mockito, Spring MockMvc, `MockRestServiceServer` (stubs the bank over HTTP),

# Design considerations and choices:
- REST because We have CRUD, we do a POST, and GET. Every call has all clients session context . Uses JSON instead of XML. Other comparisons
  - GraphQL, user is not extracting a lot of data from single endpoint
  - Websocket: No two way communication like a web chat
  - Webhook: request isn't server initiated or dependent

- Storing only last 4 card number and discarding the rest and the CVV

- We only persist when the Bank gives a 200 (authorised or declined)

- REST is stateless , so we wouldn't have session Ids/session timeouts

- We would only retry when the bank gives 503 since a failed payment is still better than double payment in other cases. for 503, we can re-try with 2s timeout , with exponential backoff

- Currencies picked:  GBP, EUR, USD

- For observability we add 1 metric to count processed payments 
 


### Assumptions:
- We would assume that expiry in current month is not valid
- 1 payment per payment_Id
- Authentication uses API key in the headers
- Card blocking : Certain blocked or black listed users should not be allowed to make a payment is out of scope
- Concurrency is sacrificed because we don't have db Locking , just a map
- Amount is in minor units hence capped due to integer limit
- Any merchant with payment id can retrieve details, even if that's not the payment they made. In future, we should store merchant id against the details
- 2 separate requests will be genuine 2 payment requests instead of blocking the 2nd one
- No retries to the bank simulator as a payment could go out twice unless it was a 503

# Requirement and codes:

Given requirements
- Validating the request
- Store card information
- forward payment request
- accepting responses to and from the bank

A merchant should be able to process a payment through the payment gateway and receive one of the following types of response:
- 200 from bank: *Authorized* - the payment was authorized
  - Merchant sees 200 with JSON response field Status as Authorized 
- 200 from bank: *Declined* - the payment was declined
  - Merchant sees 200 with JSON response field Status as declined
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
  - Security: Uses api-key as security, set in application.properties

-  1 Client to forward request to mock brand
   - PostPaymentRequest to POST "/payments" with base url as : http://localhost:8080
   - Retry and timeout : Exponential backoff with 2s timeout, max 3 retries


----------------
# Gateway request validations:
-  Validation rules :
  - Card details present, length 14-19, 0-9 only , remove whitespaces 
  - Card number should only be numeric
  - Expiry month between 1-12
  - Card not expired, 4 digit long year `yyyy` current month is considered expired
  - Currency is one of 3 valid codes and is 3 character code
  - CVV 3-4 chars,only numeric
  - Currency in EUR , GBP or USD
  - Amount an integer of at-least value 1

- Return all annotations validation errors together followed by expiry check. Returns 400.

# Gateway Response

- Payment Id :  UUID
- Status: Authorized, Declined, Rejected
- Last four digits of the card
- Amount : **represent in minor currency unit, $0.01 would be supplied as 1



------
## Future improvements
Things to consider later:
- Throttling when calling Banking application
- DB locking or making it transactional + Outbox
- Idempotency with Merchant_id 
- Observability by adding more metrics, and tags
- Adding alerts when there is an error or lag increases
- Any merchant with payment id can retrieve details, even if that's not the payment they made. In future, we should store merchant id against the details


Concurrency handling:
- How many users are expected to use the platform ? Consider Outbox 
- The merchant sends an Idempotency-Key header, scoped per merchant.
- The gateway atomically inserts the key as "in progress". use,putIfAbsent.
- The first request proceeds to the bank and stores its result under that key.
- A later request with the same key:
  - returns the stored result if the first has finished, or
  - returns 409 Conflict straight away if the first is still in flight.

------

# Running and testing it

**You need:** Java 17 and Docker (with `docker compose`). Gradle is included (`./gradlew`).

## 1. Start the bank simulator, Prometheus and Grafana

```bash
docker compose up -d
```

| Service | URL | What it is |
|---|---|---|
| Bank simulator | http://localhost:8080 | Mountebank, the mock acquiring bank (the gateway calls it, you don't) |
| Prometheus | http://localhost:9090 | Scrapes the gateway's metrics |
| Grafana | http://localhost:3000 | Metrics dashboard, opens on "Payment Gateway" (no login to view) |

## 2. Start the gateway

```bash
./gradlew bootRun
```

It listens on **http://localhost:8090**. Leave it running; logs print in that terminal. Stop it with `Ctrl+C`.

Unit and integration tests need no Docker (the bank is stubbed): `./gradlew test`

## 3. Try the API

**Swagger UI:** http://localhost:8090/swagger-ui/index.html. Click **Authorize**, enter the API key `test-merchant-key`, then use "Try it out".

Or use curl. Every call to `/payment` needs the header `X-API-Key: test-merchant-key`.

The simulator decides the outcome from the **last digit of the card number**:

| Last digit | Bank says | Gateway returns |
|---|---|---|
| 1, 3, 5, 7, 9 | authorised | `200`, status `Authorized` |
| 2, 4, 6, 8 | declined | `200`, status `Declined` |
| 0 | 503 unavailable | `502` after 3 attempts (about 0.7s with backoff) |

**Authorised payment**
```bash
curl -i -X POST http://localhost:8090/payment \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: test-merchant-key' \
  -d '{"card_number":"2222405343248871","expiry_month":12,"expiry_year":2099,"currency":"GBP","amount":1050,"cvv":"012"}'
```
Returns `200` with `id`, `status`, `cardNumberLastFour` (`"8871"`), expiry, currency and amount. The full card number and CVV are never returned or stored. `amount` is in minor units: `1050` is 10.50.

**Declined payment** (card ends in an even digit)
```bash
curl -i -X POST http://localhost:8090/payment \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: test-merchant-key' \
  -d '{"card_number":"2222405343248872","expiry_month":12,"expiry_year":2099,"currency":"USD","amount":500,"cvv":"123"}'
```

**Retrieve a payment**, using the `id` from a response above
```bash
curl -i http://localhost:8090/payment/<id> -H 'X-API-Key: test-merchant-key'
```

**Things that should fail** (each uses the same headers; only the body or URL changes)

| Try this | Expect |
|---|---|
| Body `{}` | `400`, `Payment rejected`, all six fields listed |
| `"card_number":"1234567890123"` (13 digits) and `"currency":"JPY"` together | `400`, both errors listed at once |
| `"expiry_month":1,"expiry_year":2020` (expired) | `400`, `expiry_month: expiry date must be in the future` |
| The current month and year | `400`, same error (the current month counts as expired) |
| `"amount":10.5` or `"cvv":123` (wrong JSON type) | `400`, `Request body is missing or malformed` |
| Card ending in `0` | `502`, `The bank is unavailable, please try again in a few minutes`. The app log shows the retries |
| No `X-API-Key` header, or a wrong one | `401`, `Missing or invalid API key` |
| `GET /payment/00000000-0000-0000-0000-000000000000` | `404`, `Payment not found` |
| `GET /payment/not-a-uuid` | `400`, `Invalid payment id` |

Example of a rejected request:
```bash
curl -i -X POST http://localhost:8090/payment \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: test-merchant-key' \
  -d '{"card_number":"1234567890123","expiry_month":12,"expiry_year":2099,"currency":"JPY","amount":100,"cvv":"123"}'
```

## 4. See the metric

The gateway counts processed payments in `payments.processed`, tagged by `status` (`AUTHORIZED` or `DECLINED`). Send a few payments from step 3, then open:

- **Grafana:** http://localhost:3000. Two panels: total payments by status and payments per minute. It refreshes every 5 seconds
- **Prometheus:** http://localhost:9090, query `payments_processed_total`
- **Raw:** http://localhost:8090/actuator/prometheus or http://localhost:8090/actuator/metrics/payments.processed

Payments rejected by validation or failed by the bank are not counted, because no payment is created.

## 5. Stop everything

`Ctrl+C` in the gateway terminal, then `docker compose down`.

----------------