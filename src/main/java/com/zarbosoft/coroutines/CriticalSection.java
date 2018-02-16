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
		System.out.format("a0\n");
		final Coroutine coroutine = Coroutine.getActiveCoroutine();
		System.out.format("a1\n");
		return Coroutine.yieldThen(() -> {
			System.out.format("a2\n");
			push(executor, coroutine, arg);
			System.out.format("a3\n");
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
		System.out.format("b0\n");
		if (wasEmpty)
			new Coroutine(new SuspendableRunnable() {
				@Override
				public void run() throws SuspendExecution {
					System.out.format("b1\n");
					callInner();
				}
			}).process(null);
		System.out.format("b2\n");
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
		System.out.format("c0\n");
		final Waiting<A> waiting = pop();
		System.out.format("c1\n");
		if (waiting == null)
			return;
		try {
			System.out.format("c2\n");
			final R out = execute(waiting.arg);
			System.out.format("c3 %s\n", out);
			Cohelp.submit(waiting.executor, () -> {
				System.out.format("c4\n");
				callInner();
				System.out.format("c4.1\n");
			});
			System.out.format("c5\n");
			waiting.coroutine.process(out);
			System.out.format("c6\n");
		} catch (final RuntimeException e) {
			System.out.format("c2 E\n");
			waiting.coroutine.processThrow(e);
			System.out.format("c3 E\n");
		}
		System.out.format("c7\n");
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
