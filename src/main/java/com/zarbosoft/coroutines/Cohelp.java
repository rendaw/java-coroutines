package com.zarbosoft.coroutines;

import com.zarbosoft.coroutinescore.SuspendExecution;
import com.zarbosoft.coroutinescore.SuspendableRunnable;
import com.zarbosoft.rendaw.common.Assertion;
import org.slf4j.Logger;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

import static com.zarbosoft.rendaw.common.Common.uncheck;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.WEEKS;
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
	 * Block a synchronous method on asynchronous work.  Note: Asynchronous work must happen in a different thread
	 * (coroutines must be resumed by some external process).
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
	 * The first execution of the runnable will be after 1 interval has elapsed from the call to repeat.
	 *
	 * @param executor Method is run in this executor.
	 * @param time     Interval, multiple of unit.
	 * @param unit
	 * @param runnable The method to run periodically.
	 */
	public static void repeat(
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
	 * Defines a schedule to use with scheduleTimer
	 *
	 * @param <T> temporal type (LocalDateTime or ZonedDateTime)
	 */
	public interface Schedule<T extends Temporal> {
		public T now();

		/**
		 * Get the time of an occurance relative to the current time
		 *
		 * @param now    current time
		 * @param offset occurances after now; if 0 the occurance immediately before now
		 * @return moment of the occurance
		 */
		public T get(T now, int offset);
	}

	/**
	 * Run the runnable according to the schedule. Can handle complex schedules
	 * (based around calendar or regional clocks).
	 *
	 * @param executor Method is run in this executor
	 * @param schedule The schedule to run on
	 * @param runnable The method to run
	 * @param <T>
	 */
	public static <T extends Temporal> void calendarRepeat(
			final ScheduledExecutorService executor, final Schedule<T> schedule, final SuspendableRunnable runnable
	) {
		final Runnable inner = new Runnable() {
			T last;

			@Override
			public void run() {
				final T now = schedule.now();
				final List<T> times = Arrays.asList(schedule.get(now, 0), schedule.get(now, 1));
				final Duration scale = Duration.between(times.get(0), times.get(1));
				final Duration epsilon = scale.dividedBy(100);
				for (final T next : times) {
					final Duration until = Duration.between(now, next);
					if (until.abs().minus(epsilon).isNegative() &&
							(last == null || Duration.between(last, now).toMinutes() > 30)) {
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
						last = now;
						break;
					}
					if (until.isNegative())
						continue;
					executor.schedule(this, until.toMillis(), TimeUnit.MILLISECONDS);
					break;
				}
			}
		};
		inner.run();
	}

	public static void scheduleDailyUTC(
			final ScheduledExecutorService executor, final LocalTime time, final SuspendableRunnable runnable
	) {
		calendarRepeat(executor, new Schedule<ZonedDateTime>() {
			@Override
			public ZonedDateTime now() {
				return ZonedDateTime.now(ZoneOffset.UTC);
			}

			@Override
			public ZonedDateTime get(final ZonedDateTime now, int offset) {
				final ZonedDateTime basis = now.toLocalDate().atTime(time).atZone(ZoneOffset.UTC);
				if (basis.isAfter(now)) {
					offset -= 1;
				}
				return basis.plus(offset, DAYS);
			}
		}, runnable);
	}

	public static void scheduleWeeklyUTC(
			final ScheduledExecutorService executor,
			final DayOfWeek day,
			final LocalTime time,
			final SuspendableRunnable runnable
	) {
		calendarRepeat(executor, new Schedule<ZonedDateTime>() {
			@Override
			public ZonedDateTime now() {
				return ZonedDateTime.now(ZoneOffset.UTC);
			}

			@Override
			public ZonedDateTime get(final ZonedDateTime now, int offset) {
				final LocalDate today = now.toLocalDate();
				final ZonedDateTime basis = today
						.minus(today.getDayOfWeek().getValue() - day.getValue(), DAYS)
						.atTime(time)
						.atZone(ZoneOffset.UTC);
				if (basis.isAfter(now)) {
					offset -= 1;
				}
				return basis.plus(offset, WEEKS);
			}
		}, runnable);
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

	/**
	 * Run an asynchronous method in an executor after a period.
	 *
	 * @param executor
	 * @param executor Method is run in this executor.
	 * @param time
	 * @param unit
	 * @param runnable Method to run.
	 * @return
	 */
	public static ScheduledFuture<?> delay(
			final ScheduledExecutorService executor,
			final int time,
			final ChronoUnit unit,
			final SuspendableRunnable runnable
	) {
		return executor.schedule(() -> {
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
		}, time, timeUnit(unit));
	}
}
