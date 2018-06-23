package com.zarbosoft.coroutines;

import com.zarbosoft.coroutinescore.SuspendExecution;
import com.zarbosoft.rendaw.common.Assertion;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;

public class ManualExecutor implements ScheduledExecutorService {
	private static class Scheduled {
		final LocalDateTime at;
		final Runnable runnable;
		final Duration repeat;

		private Scheduled(final LocalDateTime at, final Runnable runnable, final Duration repeat) {
			this.at = at;
			this.runnable = runnable;
			this.repeat = repeat;
		}
	}

	public List<Scheduled> scheduled = new ArrayList<>();
	public LocalDateTime now = LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC);

	private void sortScheduled() {
		this.scheduled.sort(new Comparator<Scheduled>() {
			@Override
			public int compare(final Scheduled o1, final Scheduled o2) {
				return o1.at.compareTo(o2.at);
			}
		});
	}

	private <V> ScheduledFuture<V> push(final Scheduled event) {
		this.scheduled.add(event);
		sortScheduled();
		return new ScheduledFuture<V>() {
			@Override
			public long getDelay(final TimeUnit unit) {
				throw new AssertionError();
			}

			@Override
			public int compareTo(final Delayed o) {
				throw new AssertionError();
			}

			@Override
			public boolean cancel(final boolean mayInterruptIfRunning) {
				return scheduled.remove(event);
			}

			@Override
			public boolean isCancelled() {
				return scheduled.contains(event);
			}

			@Override
			public boolean isDone() {
				throw new AssertionError();
			}

			@Override
			public V get() throws InterruptedException, ExecutionException {
				throw new AssertionError();
			}

			@Override
			public V get(
					final long timeout, final TimeUnit unit
			) throws InterruptedException, ExecutionException, TimeoutException {
				throw new AssertionError();
			}
		};
	}

	public void advance(final Duration amount) throws SuspendExecution {
		final Coroutine coroutine = Coroutine.getActiveCoroutine();
		Coroutine.yieldThen(() -> {
			now = now.plus(amount);
			final List<Scheduled> reschedule = new ArrayList<>();
			for (final Scheduled event : scheduled) {
				if (event.at.compareTo(now) <= 0) {
					event.runnable.run();
					if (event.repeat != null)
						reschedule.add(event);
				} else {
					reschedule.add(event);
				}
			}
			scheduled = reschedule;
			sortScheduled();
			submit(() -> {
				try {
					coroutine.process(null);
				} catch (final Throwable e) {
					this.shutdown();
				}
			});
		});
	}

	private ChronoUnit toChronoUnit(final TimeUnit unit) {
		switch (unit) {
			case NANOSECONDS:
			case MICROSECONDS:
				return ChronoUnit.MICROS;
			case MILLISECONDS:
				return ChronoUnit.MILLIS;
			case SECONDS:
				return ChronoUnit.SECONDS;
			case MINUTES:
				return ChronoUnit.MINUTES;
			case HOURS:
				return ChronoUnit.HOURS;
			case DAYS:
				return ChronoUnit.DAYS;
			default:
				throw new AssertionError();
		}
	}

	@Override
	public ScheduledFuture<?> schedule(final Runnable command, final long delay, final TimeUnit unit) {
		return push(new Scheduled(now.plus(delay, toChronoUnit(unit)), command, null));
	}

	@Override
	public <V> ScheduledFuture<V> schedule(final Callable<V> callable, final long delay, final TimeUnit unit) {
		return push(new Scheduled(now.plus(delay, toChronoUnit(unit)), () -> {
			try {
				callable.call();
			} catch (final Exception e) {
				e.printStackTrace();
			}
		}, null));
	}

	@Override
	public ScheduledFuture<?> scheduleAtFixedRate(
			final Runnable command, final long initialDelay, final long period, final TimeUnit unit
	) {
		return push(new Scheduled(now.plus(initialDelay, toChronoUnit(unit)),
				command,
				Duration.of(period, toChronoUnit(unit))
		));
	}

	@Override
	public ScheduledFuture<?> scheduleWithFixedDelay(
			final Runnable command, final long initialDelay, final long delay, final TimeUnit unit
	) {
		return push(new Scheduled(now.plus(initialDelay, toChronoUnit(unit)),
				command,
				Duration.of(delay, toChronoUnit(unit))
		));
	}

	@Override
	public void shutdown() {
		throw new AssertionError("Shutdown");
	}

	@Override
	public List<Runnable> shutdownNow() {
		throw new Assertion();
	}

	@Override
	public boolean isShutdown() {
		throw new Assertion();
	}

	@Override
	public boolean isTerminated() {
		throw new Assertion();
	}

	@Override
	public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
		throw new Assertion();
	}

	@Override
	public <T> Future<T> submit(final Callable<T> task) {
		throw new Assertion();
	}

	@Override
	public <T> Future<T> submit(final Runnable task, final T result) {
		throw new Assertion();
	}

	@Override
	public Future<?> submit(final Runnable task) {
		task.run();
		return null;
	}

	@Override
	public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks) throws InterruptedException {
		throw new Assertion();
	}

	@Override
	public <T> List<Future<T>> invokeAll(
			final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit
	) throws InterruptedException {
		throw new Assertion();
	}

	@Override
	public <T> T invokeAny(final Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
		throw new Assertion();
	}

	@Override
	public <T> T invokeAny(
			final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit
	) throws InterruptedException, ExecutionException, TimeoutException {
		throw new Assertion();
	}

	@Override
	public void execute(final Runnable command) {
		command.run();
	}
}
