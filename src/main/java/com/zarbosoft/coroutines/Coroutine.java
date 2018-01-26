package com.zarbosoft.coroutines;

import com.zarbosoft.coroutinescore.SuspendExecution;
import com.zarbosoft.coroutinescore.SuspendableRunnable;

/**
 * Create a coroutine.  A coroutine is roughly a method that can be paused at any point and then resumed from that
 * point.  Additionally, any call at any depth in the call tree of the method can suspend.  This is useful for
 * multiplexing multiple tasks in a single thread.
 */
public class Coroutine extends com.zarbosoft.coroutinescore.Coroutine {
	Object inValue = null;
	Runnable runAfter = null;
	private RuntimeException inException = null;

	/**
	 * Creates a coroutine for the provided method. Nothing is run until process is called.
	 *
	 * @param runnable
	 */
	public Coroutine(final SuspendableRunnable runnable) {
		super(runnable);
	}

	public Coroutine(final SuspendableRunnable runnable, final int stackSize) {
		super(runnable, stackSize);
	}

	/**
	 * Alias for Coroutine.process.
	 */
	@Override
	public final void run() {
		process();
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
		super.run();
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
		super.run();
		if (runAfter != null) {
			final Runnable runAfter1 = runAfter;
			runAfter = null;
			runAfter1.run();
		}
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
		final Coroutine self = (Coroutine) com.zarbosoft.coroutinescore.Coroutine.getActiveCoroutine();
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
		return (Coroutine) com.zarbosoft.coroutinescore.Coroutine.getActiveCoroutine();
	}

}
