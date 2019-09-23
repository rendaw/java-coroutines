package com.zarbosoft.coroutines;

import com.zarbosoft.coroutinescore.SuspendExecution;

import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Create a critical section around a method that will stop coroutines rather than blocking them.
 */
public class CriticalSection {
	private final ReentrantLock lock = new ReentrantLock();
	private boolean locked = false;
	private final ArrayDeque<Waiting> queue = new ArrayDeque<>();

	/**
	 * Run the method if no other coroutine is currently executing it. Otherwise suspend, and resume when the other
	 * coroutine has finished.
	 *
	 * @param executor Worker to resume coroutine on if this suspends.
	 * @return Wrapped method's return.
	 * @throws SuspendExecution
	 */
	public <R> R call(final ExecutorService executor, SuspendableSupplier<R> method) throws SuspendExecution {
		lock.lock();
		if (locked) {
			Coroutine coroutine = Coroutine.getActiveCoroutine();
			return Coroutine.yieldThen(() -> {
				queue.add(new Waiting(executor, coroutine, method));
				lock.unlock();
			});
		}
		locked = true;
		lock.unlock();

		try {
			return method.get();
		} finally {
			iterate();
		}
	}

	private void iterate() throws SuspendExecution {
		lock.lock();
		Waiting next = queue.poll();
		if (next == null) {
			locked = false;
		}
		lock.unlock();
		if (next != null) {
			Cohelp.submit(next.executor, () -> {
				try {
					final Object out = next.method.get();
					iterate();
					next.coroutine.process(out);
				} catch (final RuntimeException e) {
					iterate();
					next.coroutine.processThrow(e);
				}
			});
		}
	}

	static class Waiting {
		public final ExecutorService executor;
		public final Coroutine coroutine;
		public final SuspendableSupplier method;

		public Waiting(final ExecutorService executor, final Coroutine coroutine, final SuspendableSupplier method) {
			this.executor = executor;
			this.coroutine = coroutine;
			this.method = method;
		}
	}
}
