# API Rate Limiting Test Lab

A practical Java Spring Boot project demonstrating API rate limiting and throttling, with automated HTTP integration tests using TestNG and Rest Assured.

The project explores how APIs behave when clients exceed configured request limits, how HTTP 429 responses are handled, and whether rate limits remain enforced under concurrent traffic.

## Objectives

- Implement API rate limiting using Bucket4j.
- Enforce configurable request limits and refill durations.
- Identify clients using the `X-Client-Id` request header.
- Return HTTP 429 when a client exceeds its quota.
- Provide retry guidance through the `Retry-After` response header.
- Validate rate limiting through sequential and concurrent API requests.

## Technology Stack

| Technology | Version / Purpose |
|---|---|
| Java | 17 |
| Spring Boot | 3.5.5 |
| Maven | Build management |
| Bucket4j | 8.10.1 |
| TestNG | 7.11.0 |
| Rest Assured | 5.5.6 |

## How It Works

The application exposes a sample API endpoint:

`GET /api/resource`

A rate-limiting filter intercepts incoming requests and identifies the client using the `X-Client-Id` header. If the header is missing or blank, the filter falls back to the remote IP address.

Bucket4j maintains an in-memory token bucket for each client.

The default configuration allows 10 requests per refill interval.

When the bucket has no available tokens:

- The API returns HTTP 429 Too Many Requests.
- The response includes a `Retry-After` header.
- The response body contains a rate-limit error message.

## Configuration

Rate limits are configurable through `src/main/resources/application.properties`.

```properties
rate-limit.request-limit=10
rate-limit.refill-duration=1m
```

For automated integration testing, the test class overrides the refill duration to five seconds.

The implementation uses interval-based token replenishment, not a rolling-window algorithm.

## API Testing Scenarios

The integration tests validate the following behaviors:

| Scenario | Expected behavior |
|---|---|
| First 10 requests | HTTP 200 |
| 11th request | HTTP 429 |
| Different client | Independent request quota |
| Refill interval elapsed | Client can make requests again |
| 20 concurrent requests | 10 accepted, 10 rejected |
| Rate-limit response | HTTP 429 with error message and Retry-After header |

## Concurrent Request Testing

One of the key experiments sends 20 concurrent HTTP requests using the same client identifier.

Observed result:

| Metric | Result |
|---|---:|
| Total requests | 20 |
| HTTP 200 | 10 |
| HTTP 429 | 10 |

This demonstrates that the configured in-memory token bucket enforced the request limit under the tested concurrent workload.

## Running the Project

### Prerequisites

- Java 17
- Maven

### Clone the repository

```bash
git clone https://github.com/ssvali/api-rate-limiting-test-lab.git
cd api-rate-limiting-test-lab
```

### Run the application

```bash
mvn spring-boot:run
```

### Execute integration tests

```bash
mvn clean test
```

## Project Structure

```text
api-rate-limiting-test-lab/
├── src/
│   ├── main/
│   │   ├── java/org/qaspark/ratelimit/
│   │   │   ├── config/
│   │   │   ├── controller/
│   │   │   │   └── ApiController.java
│   │   │   ├── filter/
│   │   │   │   └── RateLimitFilter.java
│   │   │   ├── service/
│   │   │   │   └── RateLimitService.java
│   │   │   └── RateLimitApplication.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/org/qaspark/ratelimit/
│           └── RateLimitIntegrationTest.java
├── .gitignore
└── pom.xml
```

## Limitations

This is an educational API testing lab, not a production-ready distributed rate-limiting solution.

- Rate-limit buckets are maintained in application memory.
- Limits are not shared across multiple application instances.
- Client identification uses a request header or remote IP fallback.
- The refill strategy uses Bucket4j interval-based replenishment.

These limitations are intentional and keep the experiment focused on API rate-limiting behavior and testing.

## Test Results

Latest verified execution:

```text
Tests run: 4
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

## Purpose

This repository is part of a practical API testing learning series focused on validating behavior beyond successful API responses.

The emphasis is on testing API contracts, boundary conditions, throttling behavior, and concurrency—not simply generating HTTP requests.


## Related Article

**API Rate Limiting Testing: What Happens When Clients Exceed the Limit?**

Read the accompanying article on Medium:

[Read the article](https://medium.com/@sha.jamdar/api-rate-limiting-testing-what-happens-when-clients-exceed-the-limit-b4f021c3fe8d)
