package app.pricelog.api.config;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Caps how hard an unauthenticated visitor can read.
 *
 * <p>Reads are free of model cost, but not free of every cost: the database is
 * a Burstable B1ms shared with a production app and this pool is capped at
 * three connections, so an unthrottled crawler on a public URL is felt by
 * something else. A token bucket per address keeps browsing comfortable and
 * scripted hammering not.
 *
 * <p>In memory on purpose. The app runs at most one replica, so there is
 * nothing to share state with, and a restart resetting the buckets is a fair
 * trade against introducing a store just for this.
 */
@Component
public class AnonymousRateLimiter {

    /** Burst allowance: comfortably more than a person tapping around. */
    private static final int CAPACITY = 60;
    private static final double REFILL_PER_SECOND = 1.0;
    /** Buckets idle this long are dropped, so the map cannot grow without end. */
    private static final Duration IDLE_EVICTION = Duration.ofMinutes(30);

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong lastSweep = new AtomicLong(System.nanoTime());

    /** @return true when the caller may proceed */
    public boolean tryAcquire(String client) {
        sweepOccasionally();
        return buckets.computeIfAbsent(client, k -> new Bucket()).tryAcquire();
    }

    private void sweepOccasionally() {
        long now = System.nanoTime();
        long previous = lastSweep.get();
        if (now - previous < Duration.ofMinutes(5).toNanos()) {
            return;
        }
        if (!lastSweep.compareAndSet(previous, now)) {
            return;
        }
        Instant cutoff = Instant.now().minus(IDLE_EVICTION);
        buckets.entrySet().removeIf(e -> e.getValue().lastUsedBefore(cutoff));
    }

    private static final class Bucket {
        private double tokens = CAPACITY;
        private Instant updatedAt = Instant.now();

        synchronized boolean tryAcquire() {
            Instant now = Instant.now();
            double elapsed = Duration.between(updatedAt, now).toMillis() / 1000.0;
            tokens = Math.min(CAPACITY, tokens + elapsed * REFILL_PER_SECOND);
            updatedAt = now;
            if (tokens < 1.0) {
                return false;
            }
            tokens -= 1.0;
            return true;
        }

        synchronized boolean lastUsedBefore(Instant cutoff) {
            return updatedAt.isBefore(cutoff);
        }
    }
}
