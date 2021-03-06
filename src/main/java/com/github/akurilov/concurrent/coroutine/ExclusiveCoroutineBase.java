package com.github.akurilov.concurrent.coroutine;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The base class for a coroutine implementation which may not be executed in parallel.
 */
public abstract class ExclusiveCoroutineBase
extends CoroutineBase {

	private final Lock invocationLock;

	protected ExclusiveCoroutineBase(final CoroutinesExecutor executor) {
		this(executor, new ReentrantLock());
	}

	protected ExclusiveCoroutineBase(final CoroutinesExecutor executor, final Lock invocationLock) {
		super(executor);
		this.invocationLock = invocationLock;
	}

	@Override
	protected final void invokeTimed(long startTimeNanos) {
		if(invocationLock.tryLock()) {
			try {
				invokeTimedExclusively(startTimeNanos);
			} finally {
				invocationLock.unlock();
			}
		}
	}

	/**
	 * The method is guaranteed to be executing only in a single thread.
	 * @param startTimeNanos the time when the invocation started
	 */
	protected abstract void invokeTimedExclusively(final long startTimeNanos);
}
