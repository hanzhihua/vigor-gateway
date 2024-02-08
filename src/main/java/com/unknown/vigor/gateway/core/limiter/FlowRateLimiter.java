package com.unknown.vigor.gateway.core.limiter;

import com.unknown.vigor.gateway.core.conf.AbstractProxyConf;

import java.util.concurrent.atomic.AtomicLong;

public class FlowRateLimiter {
    private long maxBytes;
    private AtomicLong currentBytes;

    public FlowRateLimiter(long maxBytes) {
        this.maxBytes = maxBytes;
        this.currentBytes = new AtomicLong(0);
    }

    public boolean incr(long bytes) {
        if (currentBytes.addAndGet(bytes) > maxBytes) {
            currentBytes.addAndGet(-1 * bytes);
            return false;
        }
        return true;
    }

    public void decr(long bytes) {
        currentBytes.addAndGet(-1 * bytes);
    }

    public long value() {
        return currentBytes.longValue();
    }

    private static FlowRateLimiter flowRateLimiter;

    public static FlowRateLimiter getSingleton(AbstractProxyConf conf) {
        if (flowRateLimiter == null) {
            synchronized (FlowRateLimiter.class) {
                if (flowRateLimiter == null) {
                    flowRateLimiter = new FlowRateLimiter(conf.getSinkQueueTotalMaxBytes());
                }
            }
        }
        return flowRateLimiter;
    }
}
