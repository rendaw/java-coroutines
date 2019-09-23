package com.zarbosoft.coroutines;

import com.zarbosoft.coroutinescore.SuspendExecution;
import com.zarbosoft.coroutinescore.SuspendableRunnable;

import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Prioritizes reads
 */
public class RWCriticalSection {
	final ReentrantLock lock = new ReentrantLock();
	final static int STATE_WRITING = -1;
	final static int STATE_UNLOCKED = 0;
	int state = STATE_UNLOCKED;
	ArrayDeque<CriticalSection.Waiting> readQueue = new ArrayDeque<>();
	final ArrayDeque<CriticalSection.Waiting> writeQueue = new ArrayDeque<>();

	public <R> R read(final ExecutorService executor, SuspendableSupplier<R> method) throws SuspendExecution {
		lock.lock();
		if (state == STATE_WRITING) {
			Coroutine coroutine = Coroutine.getActiveCoroutine();
			return Coroutine.yieldThen(() -> {
				readQueue.add(new CriticalSection.Waiting(executor, coroutine, method));
				lock.unlock();
			});
		}
		state += 1;
		lock.unlock();

		try {
			return method.get();
		} finally {
			lock.lock();
			state -= 1;
			if (state == STATE_UNLOCKED && !writeQueue.isEmpty()) {
				state = STATE_WRITING;
			}
			lock.unlock();
			if (state == STATE_WRITING)
				iterate();
		}
	}

	public <R> R write(final ExecutorService executor, SuspendableSupplier<R> method) throws SuspendExecution {
		lock.lock();
		if (state != STATE_UNLOCKED) {
			Coroutine coroutine = Coroutine.getActiveCoroutine();
			return Coroutine.yieldThen(() -> {
				writeQueue.add(new CriticalSection.Waiting(executor, coroutine, method));
				lock.unlock();
			});
		}
		state = STATE_WRITING;
		lock.unlock();

		try {
			return method.get();
		} finally {
			iterate();
		}
	}

	public boolean tryUniqueWrite(ExecutorService executor, SuspendableRunnable method) throws SuspendExecution {
		lock.lock();
		if (state != STATE_UNLOCKED) {
			if (state != STATE_WRITING && writeQueue.isEmpty()) {
				Coroutine coroutine = Coroutine.getActiveCoroutine();
				Coroutine.yieldThen(() -> {
					writeQueue.add(new CriticalSection.Waiting(executor, coroutine, () -> {
						method.run();
						return null;
					}));
					lock.unlock();
				});
				return true;
			} else {
				lock.unlock();
				return false;
			}
		}
		state = STATE_WRITING;
		lock.unlock();

		try {
			method.run();
			return true;
		} finally {
			iterate();
		}
	}

	void submit(CriticalSection.Waiting next) {
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

	/**
	 * Release pending readers, or else the next writer.
	 *
	 * @throws SuspendExecution
	 */
	void iterate() throws SuspendExecution {
		ArrayDeque<CriticalSection.Waiting> readers;
		ArrayDeque<CriticalSection.Waiting> temp = new ArrayDeque<>();

		lock.lock();
		// Tally new readers, reduce by completed reader
		state = (state == STATE_WRITING ? 0 : state - 1) + readQueue.size();

		// Drain the reader queue for dispatch later here
		readers = readQueue;
		readQueue = temp;

		// If no readers, prep the next writer
		CriticalSection.Waiting writer;
		if (state == 0) {
			writer = writeQueue.poll();
			if (writer != null) {
				state = STATE_WRITING;
			}
		} else
			writer = null;
		lock.unlock();

		if (state == STATE_WRITING)
			submit(writer);
		else
			for (CriticalSection.Waiting reader : readers)
				submit(reader);
	}
}
