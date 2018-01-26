package com.zarbosoft.coroutines;

import com.zarbosoft.coroutinescore.SuspendExecution;

/**
 * Like Supplier but can suspend.
 *
 * @param <T>
 */
@FunctionalInterface
public interface SuspendableSupplier<T> {
	T get() throws SuspendExecution;
}
