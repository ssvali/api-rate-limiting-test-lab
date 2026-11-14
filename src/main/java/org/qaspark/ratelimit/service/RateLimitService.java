
package org.qaspark.ratelimit.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import io.github.bucket4j.ConsumptionProbe;

@Service
public class RateLimitService {

    private final int requestLimit;
    private final Duration refillDuration;

    private final ConcurrentMap<String, Bucket> buckets =
            new ConcurrentHashMap<>();

    public RateLimitService(
            @Value("${rate-limit.request-limit:10}") int requestLimit,
            @Value("${rate-limit.refill-duration:1m}") Duration refillDuration) {

        this.requestLimit = requestLimit;
        this.refillDuration = refillDuration;
    }

    public ConsumptionProbe consumeRequest(String clientId) {

        Bucket bucket = buckets.computeIfAbsent(
                clientId,
                this::createBucket
        );

        return bucket.tryConsumeAndReturnRemaining(1);
    }

    private Bucket createBucket(String clientId) {
        Bandwidth limit = Bandwidth.classic(
                requestLimit,
                Refill.intervally(requestLimit, refillDuration)
        );

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
