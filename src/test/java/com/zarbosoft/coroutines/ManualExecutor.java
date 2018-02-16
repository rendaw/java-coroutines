package com.zarbosoft.coroutines;

import com.zarbosoft.rendaw.common.Assertion;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.*;

public class ManualExecutor implements ExecutorService {
	public Deque<Runnable> queue = new ArrayDeque<>();

	@Override
	public void shutdown() {
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
