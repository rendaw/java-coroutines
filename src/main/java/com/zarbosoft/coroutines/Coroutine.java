package com.zarbosoft.coroutines;

import com.zarbosoft.coroutinescore.SuspendExecution;
import com.zarbosoft.coroutinescore.SuspendableRunnable;

/**
 * Create a coroutine.  A coroutine is roughly a method that can be paused at any point and then resumed from that
 * point.  Additionally, any call at any depth in the call tree of the method can suspend.  This is useful for
 * multiplexing multiple tasks in a single thread.
 */
public class Coroutine {
	private static class InnerCoroutine extends com.zarbosoft.coroutinescore.Coroutine {
		private final Coroutine outer;

		public InnerCoroutine(final Coroutine outer, final SuspendableRunnable runnable) {
			super(runnable);
			this.outer = outer;
		}

		public InnerCoroutine(final Coroutine outer, final SuspendableRunnable runnable, final int stackSize) {
			super(runnable, stackSize);
			this.outer = outer;
		}
	}

	private final InnerCoroutine inner;
	Object inValue = null;
	Runnable runAfter = null;
	private RuntimeException inException = null;

	/**
	 * Creates a coroutine for the provided method. Nothing is run until process is called.
	 *
	 * @param runnable
	 */
	public Coroutine(final SuspendableRunnable runnable) {
		inner = new InnerCoroutine(this, runnable);
	}

	public Coroutine(final SuspendableRunnable runnable, final int stackSize) {
		inner = new InnerCoroutine(this, runnable, stackSize);
	}

	/**
	 * Start or resume the coroutine.
	 */
	public final void process() {
		process(null);
	}

	/**
	 * Start or resume the coroutine.
	 *
	 * @param value This will be returned from the call to yeild if the coroutine was suspended.  Otherwise, ignored.
	 */
	public final void process(final Object value) {
		inValue = value;
		inner.run();
		if (runAfter != null) {
			final Runnable runAfter1 = runAfter;
			runAfter = null;
			runAfter1.run();
		}
	}

	/**
	 * Resume the coroutine by raising an exception from the suspension point.
	 *
	 * @param exception Exception to raise.
	 */
	public final void processThrow(final RuntimeException exception) {
		inException = exception;
		inner.run();
		if (runAfter != null) {
			final Runnable runAfter1 = runAfter;
			runAfter = null;
			runAfter1.run();
		}
	}

	/**
	 * Suspend the coroutine running in the current thread.
	 *
	 * @throws SuspendExecution
	 */
	public static <T> T yield() throws SuspendExecution {
		com.zarbosoft.coroutinescore.Coroutine.yield();
		final Coroutine self = getActiveCoroutine();
		if (self.inException != null) {
			final RuntimeException e = self.inException;
			self.inException = null;
			throw e;
		}
		return (T) self.inValue;
	}

	/**
	 * Suspend the coroutine and run the method after the suspension is complete.  This is useful when scheduling
	 * the coroutine to be run on another thread which would otherwise cause a race condition (suspension completion vs
	 * resumption start).
	 *
	 * @param runAfter
	 * @param <T>
	 * @return Value supplied to Coroutine.process when resumed.
	 * @throws SuspendExecution
	 */
	public static <T> T yieldThen(final Runnable runAfter) throws SuspendExecution {
		final Coroutine self = getActiveCoroutine();
		self.runAfter = runAfter;
		com.zarbosoft.coroutinescore.Coroutine.yield();
		self.runAfter = null;
		if (self.inException != null) {
			final RuntimeException e = self.inException;
			self.inException = null;
			throw e;
		}
		return (T) self.inValue;
	}

	/**
	 * May only be called while executing a coroutine, from the same thread.
	 *
	 * @return The current coroutine.
	 */
	public static Coroutine getActiveCoroutine() {
		return ((InnerCoroutine) com.zarbosoft.coroutinescore.Coroutine.getActiveCoroutine()).outer;
	}

	/**
	 * @return true if coroutine is finished
	 */
	public boolean isFinished() {
		return inner.getState() == com.zarbosoft.coroutinescore.Coroutine.State.FINISHED;
	}
}
