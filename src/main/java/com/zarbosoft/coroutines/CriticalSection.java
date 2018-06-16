package com.zarbosoft.coroutines;

import com.zarbosoft.coroutinescore.SuspendExecution;
import com.zarbosoft.coroutinescore.SuspendableRunnable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutorService;

/**
 * Create a critical section around a method that will stop coroutines rather than blocking them.
 *
 * @param <A> Wrapped method's single argument.
 * @param <R> Wrapped method's return value.
 */
public abstract class CriticalSection<A, R> {
	private final Deque<Object> queue = new ArrayDeque<>(); // Doubles as marker

	/**
	 * The method to turn into a critical section.
	 *
	 * @param arg
	 * @return
	 * @throws SuspendExecution
	 */
	protected abstract R execute(A arg) throws SuspendExecution;

	/**
	 * Run the method if no other coroutine is currently executing it. Otherwise suspend, and resume when the other
	 * coroutine has finished.
	 *
	 * @param executor Worker to resume coroutine on if this suspends.
	 * @param arg      Wrapped method's argument.
	 * @return Wrapped method's return.
	 * @throws SuspendExecution
	 */
	public R call(final ExecutorService executor, final A arg) throws SuspendExecution {
		final Coroutine coroutine = Coroutine.getActiveCoroutine();
		return Coroutine.yieldThen(() -> {
			push(executor, coroutine, arg);
		});
	}

	private void push(final ExecutorService executor, final Coroutine coroutine, final A arg) {
		final boolean wasEmpty;
		synchronized (queue) {
			wasEmpty = queue.isEmpty();
			queue.addFirst(new Waiting<>(executor, coroutine, arg));
			if (wasEmpty) {
				queue.addLast(queue);
			}
		}
		if (wasEmpty)
			new Coroutine(new SuspendableRunnable() {
				@Override
				public void run() throws SuspendExecution {
					callInner();
				}
			}).process(null);
	}

	private Waiting<A> pop() {
		synchronized (queue) {
			final Object temp = queue.removeFirst();
			if (temp == queue)
				return null;
			return (Waiting<A>) temp;
		}
	}

	private void callInner() throws SuspendExecution {
		final Waiting<A> waiting = pop();
		if (waiting == null)
			return;
		try {
			final R out = execute(waiting.arg);
			Cohelp.submit(waiting.executor, () -> {
				callInner();
			});
			waiting.coroutine.process(out);
		} catch (final RuntimeException e) {
			waiting.coroutine.processThrow(e);
		}
	}

	private static class Waiting<A> {
		public final ExecutorService executor;
		public final Coroutine coroutine;
		public final A arg;

		private Waiting(final ExecutorService executor, final Coroutine coroutine, final A arg) {
			this.executor = executor;
			this.coroutine = coroutine;
			this.arg = arg;
		}
	}
}
