package com.healthy.security;

import com.healthy.common.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AttemptGuard {

    private final RateLimiter limiter;
    private final SecurityAuditLogger audit;

    public AttemptGuard(RateLimiter limiter, SecurityAuditLogger audit) {
        this.limiter = limiter;
        this.audit = audit;
    }

    public void check(String action, String key, int limit, Duration window, String ip) {
        try {
            limiter.requireBelow(key, limit, window);
        } catch (TooManyRequestsException e) {
            audit.rateLimited(action, ip);
            throw e;
        }
    }

    public void record(String key) {
        limiter.record(key);
    }

    public void clear(String key) {
        limiter.clear(key);
    }
}
