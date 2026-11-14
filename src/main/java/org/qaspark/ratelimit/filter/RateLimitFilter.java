
package org.qaspark.ratelimit.filter;

import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.qaspark.ratelimit.service.RateLimitService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    public RateLimitFilter(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String clientId = request.getHeader("X-Client-Id");

        if (clientId == null || clientId.isBlank()) {
            clientId = request.getRemoteAddr();
        }

        ConsumptionProbe probe = rateLimitService.consumeRequest(clientId);

        if (!probe.isConsumed()) {

            long retryAfterSeconds = Math.max(
                    1L,
                    (probe.getNanosToWaitForRefill() + 999_999_999L)
                            / 1_000_000_000L
            );

            response.setStatus(429);
            response.setHeader(
                    "Retry-After",
                    String.valueOf(retryAfterSeconds)
            );
            response.setContentType("application/json");

            response.getWriter().write(
                    "{\"message\":\"Rate limit exceeded\"}"
            );

            return;
        }

        filterChain.doFilter(request, response);
    }
}
