package com.lexibridge.operations.security.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class ApiRateLimiterService {

    private static final long WINDOW_MILLIS = 60_000;
    private final int limit;
    private final Map<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    public ApiRateLimiterService(
        @Value("${lexibridge.security.rate-limit.requests-per-minute:60}") int limit
    ) {
        this.limit = limit;
    }

    public boolean allow(String key) {
        Deque<Long> bucket = buckets.computeIfAbsent(key, ignored -> new ConcurrentLinkedDeque<>());
        long now = Instant.now().toEpochMilli();
        long cutoff = now - WINDOW_MILLIS;

        synchronized (bucket) {
            while (!bucket.isEmpty() && bucket.peekFirst() < cutoff) {
                bucket.pollFirst();
            }
            if (bucket.size() >= limit) {
                return false;
            }
            bucket.addLast(now);
            return true;
        }
    }
}
