package com.github.akurilov.coroutines;

import com.github.akurilov.commons.collection.OptLockArrayBuffer;
import com.github.akurilov.commons.collection.OptLockBuffer;
import com.github.akurilov.commons.io.Input;
import com.github.akurilov.commons.io.Output;

import java.io.EOFException;
import java.io.IOException;
import java.rmi.ConnectException;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The exclusive coroutine implementation which tries to transfer the items from the given input to the given output.
 * The items got from the input which may not be transferred to the output w/o blocking are stored to the deferred tasks buffer.
 */
public class TransferCoroutine<T>
extends ExclusiveCoroutineBase
implements Coroutine {

	private final Input<T> input;
	private final Output<T> output;
	private final OptLockBuffer<T> deferredItems;

	private int n;

	public TransferCoroutine(
		final CoroutinesProcessor coroutinesProcessor, final Input<T> input, final Output<T> output,
		final int batchSize
	) {
		this(coroutinesProcessor, new OptLockArrayBuffer<>(batchSize), input, output, batchSize);
	}

	private TransferCoroutine(
		final CoroutinesProcessor coroutinesProcessor, final OptLockBuffer<T> deferredItems, final Input<T> input,
		final Output<T> output, final int batchSize
	) {
		super(coroutinesProcessor, deferredItems);
		this.input = input;
		this.output = output;
		this.deferredItems = new OptLockArrayBuffer<>(batchSize);
	}

	@Override
	protected final void invokeTimedExclusively(final long startTimeNanos) {
		try {

			// 1st try to output all deferred items if any
			n = deferredItems.size();
			if(n > 0) {
				if(n == 1) {
					if(output.put(deferredItems.get(0))) {
						deferredItems.clear();
					}
				} else {
					n = output.put(deferredItems);
					deferredItems.removeRange(0, n);
				}
				// do not work with new items if there were deferred items
				return;
			}

			final List<T> items = input.getAll();
			if(items != null) {
				n = items.size();
				if(n > 0) {
					if(n == 1) {
						final T item = items.get(0);
						if(!output.put(item)) {
							deferredItems.add(item);
						}
					} else {
						final int m;
						if(TIMEOUT_NANOS > System.nanoTime() - startTimeNanos) {
							m = output.put(items);
						} else { // timeout, move all items to the deferred items buffer
							m = 0;
						}
						if(m < n) {
							// not all items was transferred w/o blocking
							// defer the remaining items for a future try
							for(final T item : items.subList(m, n)) {
								deferredItems.add(item);
							}
						}
					}
				}
			}

		} catch(final NoSuchObjectException | ConnectException ignored) {
		} catch(final EOFException e) {
			try {
				close();
			} catch(final IOException ee) {
				ee.printStackTrace(System.err);
			}
		} catch(final RemoteException e) {
			final Throwable cause = e.getCause();
			if(cause instanceof EOFException) {
				try {
					close();
				} catch(final IOException ee) {
					ee.printStackTrace(System.err);
				}
			} else {
				e.printStackTrace(System.err);
			}
		} catch(final IOException e) {
			e.printStackTrace(System.err);
		}
	}

	@Override
	protected final void doClose()
	throws IOException {
		try {
			deferredItems.tryLock(TIMEOUT_NANOS, TimeUnit.NANOSECONDS);
			deferredItems.clear();
		} catch(final InterruptedException e) {
			e.printStackTrace(System.err);
		}
	}
}
