package com.zarbosoft.coroutines;

import com.zarbosoft.coroutinescore.SuspendExecution;
import com.zarbosoft.coroutinescore.SuspendableRunnable;
import com.zarbosoft.rendaw.common.Assertion;
import org.slf4j.Logger;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.*;

import static com.zarbosoft.rendaw.common.Common.uncheck;
import static org.slf4j.LoggerFactory.getLogger;

public class Cohelp {
	public static Logger logger = getLogger("cohelp");

	/**
	 * Standard uncaught error resolution.
	 *
	 * @param executor
	 * @param e
	 */
	public static void fatal(final ExecutorService executor, final Throwable e) {
		logger.error("Uncaught error in executor; shutting down", e);
		executor.shutdown();
	}

	/**
	 * Pause coroutine execution for the given time.
	 *
	 * @param executor Coroutine is resumed in this executor after the duration has passed.
	 * @param time     Time to sleep; multiple of unit
	 * @param unit
	 * @throws SuspendExecution
	 */
	public static void sleep(
			final ScheduledExecutorService executor, final int time, final TimeUnit unit
	) throws SuspendExecution {
		final Coroutine coroutine = Coroutine.getActiveCoroutine();
		Coroutine.yieldThen(() -> {
			executor.schedule(new Runnable() {
				@Override
				public void run() {
					try {
						coroutine.process(null);
					} catch (final Throwable e) {
						fatal(executor, e);
					}
				}
			}, time, unit);
		});
	}

	/**
	 * Run synchronous work asynchronously in a coroutine by offloading it to another thread.
	 *
	 * @param executor Coroutine is resumed in this executor after the work is complete.
	 * @param runnable Work to offload.
	 * @param <T>      Work return value.
	 * @return Return value from work.
	 * @throws SuspendExecution
	 */
	public static <T> T unblock(
			final ExecutorService executor, final Blocking<T> runnable
	) throws SuspendExecution {
		final Coroutine self = Coroutine.getActiveCoroutine();
		return Coroutine.yieldThen(() -> {
			executor.submit(() -> {
				try {
					self.process(runnable.run());
				} catch (final Exception e) {
					self.processThrow(uncheck(e));
				}
			});
		});
	}

	/**
	 * Run synchronous work asynchronously in a coroutine by offloading it to another thread.
	 *
	 * @param executor Coroutine is resumed in this executor after the work is complete.
	 * @param runnable Work to offload.
	 * @throws SuspendExecution
	 */
	public static void unblock(
			final ExecutorService executor, final NullaryBlocking runnable
	) throws SuspendExecution {
		final Coroutine self = Coroutine.getActiveCoroutine();
		Coroutine.yieldThen(() -> {
			executor.submit(() -> {
				try {
					runnable.run();
					self.process(null);
				} catch (final Exception e) {
					self.processThrow(uncheck(e));
				}
			});
		});
	}

	/**
	 * Asynchronously wait for a future to complete.
	 *
	 * @param future
	 * @param <T>
	 * @return Result of future.
	 * @throws SuspendExecution
	 */
	public static <T> T unblock(final CompletableFuture<T> future) throws SuspendExecution {
		final Coroutine self = Coroutine.getActiveCoroutine();
		return Coroutine.yieldThen(() -> {
			future.thenAccept(v -> {
				self.process(v);
			}).exceptionally(e -> {
				self.processThrow(uncheck((Exception) e));
				return null;
			});
		});
	}

	/**
	 * Run asynchronous work synchronously.
	 *
	 * @param runnable Asynchronous work
	 * @param <T>
	 * @return Return value of asynchronous work
	 */
	public static <T> T block(final SuspendableSupplier<T> runnable) {
		final CompletableFuture<T> blocker = new CompletableFuture<>();
		new Coroutine(new SuspendableRunnable() {
			@Override
			public void run() throws SuspendExecution {
				try {
					blocker.complete(runnable.get());
				} catch (final Exception e) {
					blocker.completeExceptionally(e);
				}
			}
		}).process(null);
		while (true) {
			try {
				return blocker.get();
			} catch (final InterruptedException e) {
			} catch (final ExecutionException e) {
				throw uncheck((RuntimeException) e.getCause());
			}
		}
	}

	/**
	 * Block a synchronous method on asynchronous work.
	 *
	 * @param runnable Asynchronous work
	 */
	public static void block(final SuspendableRunnable runnable) {
		final CompletableFuture<Void> blocker = new CompletableFuture<>();
		new Coroutine(new SuspendableRunnable() {
			@Override
			public void run() throws SuspendExecution {
				try {
					runnable.run();
					blocker.complete(null);
				} catch (final Exception e) {
					blocker.completeExceptionally(e);
				}
			}
		}).process(null);
		while (true) {
			try {
				blocker.get();
				return;
			} catch (final InterruptedException e) {
			} catch (final ExecutionException e) {
				throw uncheck((RuntimeException) e.getCause());
			}
		}
	}

	private static TimeUnit timeUnit(final ChronoUnit unit) {
		switch (unit) {
			case NANOS:
				return TimeUnit.NANOSECONDS;
			case MICROS:
				return TimeUnit.MICROSECONDS;
			case MILLIS:
				return TimeUnit.MILLISECONDS;
			case SECONDS:
				return TimeUnit.SECONDS;
			case MINUTES:
				return TimeUnit.MINUTES;
			case HOURS:
				return TimeUnit.HOURS;
			case DAYS:
				return TimeUnit.DAYS;
			default:
				throw new Assertion();
		}
	}

	/**
	 * Run an asynchronous method at a fixed interval.  The method will not be invoked multiple times simultaneously if
	 * the method takes longer than the interval to complete.  If an error propagates out of the runnable it is logged
	 * and the executor is shut down.
	 *
	 * @param executor Method is run in this executor.
	 * @param time     Interval, multiple of unit.
	 * @param unit
	 * @param runnable The method to run periodically.
	 */
	public static void timer(
			final ScheduledExecutorService executor,
			final int time,
			final ChronoUnit unit,
			final SuspendableRunnable runnable
	) {
		executor.scheduleAtFixedRate(new Runnable() {
			CompletableFuture<Void> lastDone = null;

			@Override
			public void run() {
				final CompletableFuture<Void> done = this.lastDone;
				if (done != null && !done.isDone())
					return;
				this.lastDone = new CompletableFuture<>();
				try {
					new Coroutine(new SuspendableRunnable() {
						@Override
						public void run() throws SuspendExecution {
							try {
								runnable.run();
							} catch (final Throwable e) {
								fatal(executor, e);
							} finally {
								lastDone.complete(null);
							}
						}
					}).process(null);
				} catch (final Throwable e) {
					fatal(executor, e);
				}
			}
		}, time, time, timeUnit(unit));
	}

	/**
	 * Run an asynchronous method in an executor.
	 *
	 * @param executor Method is run in this executor.
	 * @param runnable Method to run.
	 */
	public static void submit(final ExecutorService executor, final SuspendableRunnable runnable) {
		executor.submit(() -> {
			try {
				new Coroutine(new SuspendableRunnable() {
					@Override
					public void run() throws SuspendExecution {
						try {
							runnable.run();
						} catch (final Throwable e) {
							fatal(executor, e);
						}
					}
				}).process(null);
			} catch (final Throwable e) {
				fatal(executor, e);
			}
		});
	}

}
