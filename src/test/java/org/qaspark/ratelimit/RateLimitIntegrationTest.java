
package org.qaspark.ratelimit;

import io.restassured.RestAssured;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "rate-limit.request-limit=10",
                "rate-limit.refill-duration=5s"
        }
)
public class RateLimitIntegrationTest
        extends AbstractTestNGSpringContextTests {

    @LocalServerPort
    private int port;

    @BeforeMethod
    public void setUp() {
        RestAssured.port = port;
    }

    @Test
    public void shouldEnforceRequestLimit() {

        String clientId = "LIMIT_TEST_CLIENT";

        // First 10 requests must succeed.
        for (int i = 1; i <= 10; i++) {
            given()
                    .header("X-Client-Id", clientId)
                    .when()
                    .get("/api/resource")
                    .then()
                    .statusCode(200);
        }

        // 11th request must be rejected immediately.
        var response =
                given()
                        .header("X-Client-Id", clientId)
                        .when()
                        .get("/api/resource")
                        .then()
                        .statusCode(429)
                        .extract()
                        .response();

        String retryAfter = response.getHeader("Retry-After");

        Assert.assertNotNull(
                retryAfter,
                "Retry-After header must be present"
        );

        Assert.assertTrue(
                Integer.parseInt(retryAfter) >= 1,
                "Retry-After must be at least one second"
        );

        Assert.assertEquals(
                response.jsonPath().getString("message"),
                "Rate limit exceeded"
        );
    }

    @Test
    public void shouldAllowDifferentClient() {

        given()
                .header("X-Client-Id", "INDEPENDENT_CLIENT")
                .when()
                .get("/api/resource")
                .then()
                .statusCode(200);
    }

    @Test
    public void shouldAllowRequestsAfterRefillDuration()
            throws InterruptedException {

        String clientId = "REFILL_TEST_CLIENT";

        // Consume all tokens.
        for (int i = 1; i <= 10; i++) {
            given()
                    .header("X-Client-Id", clientId)
                    .when()
                    .get("/api/resource")
                    .then()
                    .statusCode(200);
        }

        // Verify rate limit.
        given()
                .header("X-Client-Id", clientId)
                .when()
                .get("/api/resource")
                .then()
                .statusCode(429);

        // Wait beyond the configured refill interval.
        Thread.sleep(6000);

        // Verify that the same client can access the API again.
        given()
                .header("X-Client-Id", clientId)
                .when()
                .get("/api/resource")
                .then()
                .statusCode(200);
    }


    @Test
    public void shouldEnforceRateLimitUnderConcurrentRequests()
            throws Exception {

        final int totalRequests = 20;
        final String clientId = "CONCURRENT_TEST_CLIENT";

        ExecutorService executor =
                Executors.newFixedThreadPool(totalRequests);

        CountDownLatch ready = new CountDownLatch(totalRequests);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Integer>> responses = new ArrayList<>();

        try {
            // Submit all requests before releasing them.
            for (int i = 0; i < totalRequests; i++) {

                Future<Integer> response = executor.submit(() -> {

                    ready.countDown();

                    // Wait until all workers are ready.
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Timed out waiting for concurrent test start"
                        );
                    }

                    return given()
                            .header("X-Client-Id", clientId)
                            .when()
                            .get("/api/resource")
                            .then()
                            .extract()
                            .statusCode();
                });

                responses.add(response);
            }

            // Ensure all workers are ready before starting.
            Assert.assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "Not all workers became ready"
            );

            // Release all workers together.
            start.countDown();

            int successCount = 0;
            int rejectedCount = 0;

            for (Future<Integer> response : responses) {

                int statusCode = response.get(15, TimeUnit.SECONDS);

                if (statusCode == 200) {
                    successCount++;
                } else if (statusCode == 429) {
                    rejectedCount++;
                } else {
                    Assert.fail(
                            "Unexpected HTTP status code: " + statusCode
                    );
                }
            }

            System.out.println("Concurrent request results:");
            System.out.println("Total requests: " + totalRequests);
            System.out.println("HTTP 200: " + successCount);
            System.out.println("HTTP 429: " + rejectedCount);

            Assert.assertEquals(
                    successCount,
                    10,
                    "Exactly 10 requests should succeed"
            );

            Assert.assertEquals(
                    rejectedCount,
                    10,
                    "Exactly 10 requests should be rejected"
            );

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

}
