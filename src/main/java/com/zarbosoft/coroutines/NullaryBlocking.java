package com.zarbosoft.coroutines;

/**
 * Like runnable but can raise a checked exception.
 */
@FunctionalInterface
public interface NullaryBlocking {
	void run() throws Exception;
}
