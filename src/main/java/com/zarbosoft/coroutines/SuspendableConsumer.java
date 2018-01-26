package com.zarbosoft.coroutines;

import com.zarbosoft.coroutinescore.SuspendExecution;

/**
 * Like Consumer but can suspend.
 *
 * @param <T>
 */
@FunctionalInterface
public interface SuspendableConsumer<T> {
	void apply(T t) throws SuspendExecution;
}
