package com.zarbosoft.coroutines;

/**
 * Like Supplier, but can raise a checked exception.
 *
 * @param <T>
 */
@FunctionalInterface
public interface Blocking<T> {
	T run() throws Exception;
}
