package com.zarbosoft.coroutines;

import com.zarbosoft.coroutinescore.SuspendExecution;

import java.util.concurrent.ExecutorService;

/**
 * Prioritizes a single write, releasing batches of queued reads in between
 */
public class WRCriticalSection extends RWCriticalSection {
	public <R> R read(final ExecutorService executor, SuspendableSupplier<R> method) throws SuspendExecution {
		lock.lock();
		if (!writeQueue.isEmpty()) {
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
			CriticalSection.Waiting next;
			if (state == STATE_UNLOCKED) {
				next = writeQueue.poll();
				if (next != null) {
					state = STATE_WRITING;
				}
			} else next = null;
			lock.unlock();
			if (state == STATE_WRITING) {
				submit(next);
			}
		}
	}
}
