package com.zarbosoft.coroutines;

import com.zarbosoft.coroutinescore.SuspendExecution;

/**
 * Like Function but can suspend.
 *
 * @param <T>
 * @param <R>
 */
@FunctionalInterface
public interface SuspendableFunction<T, R> {
	R apply(T t) throws SuspendExecution;
}
