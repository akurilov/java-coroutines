package com.github.akurilov.coroutines;

import com.github.akurilov.commons.concurrent.ContextAwareThreadFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The coroutines executor. It's suggested to use a single/global/shared executor instance per
 * application. By default the background coroutines executor is created. The normal coroutines
 * executor with higher scheduling priority may be created using the custom constructor with
 * <i>false</i> argument.
 */
public class CoroutinesProcessor {

	private final static Logger LOG = Logger.getLogger(CoroutinesProcessor.class.getName());

	private final ThreadPoolExecutor executor;
	private final boolean backgroundFlag;
	private final List<StoppableTask> workers = new ArrayList<>();
	private final Queue<Coroutine> coroutines = new ConcurrentLinkedQueue<>();

	public CoroutinesProcessor() {
		this(true);
	}

	public CoroutinesProcessor(final boolean backgroundFlag) {
		final int svcThreadCount = Runtime.getRuntime().availableProcessors();
		executor = new ThreadPoolExecutor(
			svcThreadCount, svcThreadCount, 0, TimeUnit.DAYS, new ArrayBlockingQueue<>(1),
			new ContextAwareThreadFactory("coroutine-processor-", true, null)
		);
		this.backgroundFlag = backgroundFlag;
		for(int i = 0; i < svcThreadCount; i ++) {
			final StoppableTask svcWorkerTask = new CoroutinesProcessorTask(
				coroutines, backgroundFlag
			);
			executor.submit(svcWorkerTask);
			workers.add(svcWorkerTask);
		}
	}

	public void start(final Coroutine coroutine) {
		coroutines.add(coroutine);
	}

	public void stop(final Coroutine coroutine) {
		coroutines.remove(coroutine);
	}

	public void setThreadCount(final int threadCount) {
		final int newThreadCount = threadCount > 0 ?
			threadCount : Runtime.getRuntime().availableProcessors();
		final int oldThreadCount = executor.getCorePoolSize();
		if(newThreadCount != oldThreadCount) {
			executor.setCorePoolSize(newThreadCount);
			executor.setMaximumPoolSize(newThreadCount);
			if(newThreadCount > oldThreadCount) {
				for(int i = oldThreadCount; i < newThreadCount; i ++) {
					final StoppableTask procTask = new CoroutinesProcessorTask(
						coroutines, backgroundFlag
					);
					executor.submit(procTask);
					workers.add(procTask);
				}
			} else { // less, remove some active service worker tasks
				try {
					for(int i = oldThreadCount - 1; i >= newThreadCount; i --) {
						workers.remove(i).close();
					}
				} catch (final Exception e) {
					e.printStackTrace(System.err);
				}
			}
		}
	}

	private static final class CoroutinesProcessorTask
	implements StoppableTask {

		private final Queue<Coroutine> coroutines;
		private final boolean backgroundFlag;

		private volatile boolean stopFlag = false;
		private volatile boolean closeFlag = false;

		private CoroutinesProcessorTask(
			final Queue<Coroutine> coroutines, final boolean backgroundFlag
		) {
			this.coroutines = coroutines;
			this.backgroundFlag = backgroundFlag;
		}

		@Override
		public final void run() {
			if(backgroundFlag) {
				while(!stopFlag) {
					if(coroutines.size() == 0) {
						try {
							Thread.sleep(1);
						} catch(final InterruptedException e) {
							break;
						}
					} else {
						for(final Coroutine nextCoroutine : coroutines) {
							if(!nextCoroutine.isStopped()) {
								try {
									nextCoroutine.run();
								} catch(final Throwable t) {
									LOG.log(
										Level.WARNING, "Coroutine \"" + nextCoroutine + "\" failed",
										t
									);
								}
								LockSupport.parkNanos(1);
							}
						}
					}
				}
			} else {
				while(!stopFlag) {
					if(coroutines.size() == 0) {
						try {
							Thread.sleep(1);
						} catch(final InterruptedException e) {
							break;
						}
					} else {
						for(final Coroutine nextCoroutine : coroutines) {
							if(!nextCoroutine.isStopped()) {
								try {
									nextCoroutine.run();
								} catch(final Throwable t) {
									LOG.log(
										Level.WARNING, "Coroutine \"" + nextCoroutine + "\" failed",
										t
									);
								}
							}
						}
					}
				}
			}
		}

		@Override
		public final void stop() {
			stopFlag = true;
		}

		@Override
		public final boolean isStopped() {
			return stopFlag;
		}

		@Override
		public final void close() {
			stop();
			closeFlag = true;
		}

		@Override
		public final boolean isClosed() {
			return closeFlag;
		}
	}
}
