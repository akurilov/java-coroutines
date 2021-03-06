package com.github.akurilov.concurrent.throttle;

import java.util.concurrent.TimeUnit;

import static java.lang.System.nanoTime;

/**
 * A semaphore-like non-blocking throttle which permits at the given rate.
 */
public final class RateThrottle
implements Throttle {

	private final long periodNanos;
	private volatile long startTime = -1;
	private volatile long acquiredCount = 0;

	/**
	 * @param rateLimit The rate (permits/sec) limit for the permits
	 */
	public RateThrottle(final double rateLimit) {
		if(rateLimit <= 0) {
			throw new IllegalArgumentException(
				"Rate limit should be more than 0, but got " + rateLimit
			);
		}
		periodNanos = (long) (TimeUnit.SECONDS.toNanos(1) / rateLimit);
	}

	@Override
	public final boolean tryAcquire() {
		synchronized(this) {
			if(startTime > 0) {
				final long periodCount = (nanoTime() - startTime) / periodNanos;
				if(periodCount > acquiredCount) {
					acquiredCount ++;
					return true;
				} else {
					return false;
				}
			} else {
				startTime = nanoTime();
				acquiredCount ++;
				return true;
			}
		}
	}
	
	@Override
	public final int tryAcquire(final int requiredCount) {
		synchronized(this) {
			if(startTime > 0) {
				final int availableCount = (int) (
					(nanoTime() - startTime) / periodNanos - acquiredCount
				);
				if(availableCount > requiredCount) {
					acquiredCount += requiredCount;
					return requiredCount;
				} else {
					acquiredCount += availableCount;
					return availableCount;
				}
			} else {
				startTime = nanoTime();
				acquiredCount += requiredCount;
				return requiredCount;
			}
		}
	}
}
