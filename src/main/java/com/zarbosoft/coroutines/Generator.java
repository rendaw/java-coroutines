package com.zarbosoft.coroutines;

import com.zarbosoft.coroutinescore.SuspendExecution;
import com.zarbosoft.coroutinescore.SuspendableRunnable;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.zarbosoft.coroutines.Coroutine.yield;

public class Generator<T> {
	private final static Object END = new Object();

	private Generator() {
	}

	private T value;

	public void yieldValue(T value) throws SuspendExecution {
		this.value = value;
		yield();
	}

	public static <T> Stream<T> stream(SuspendableConsumer<Generator<T>> runnable) {
		Generator<T> g = new Generator<T>();
		Coroutine c = new Coroutine(new SuspendableRunnable() {
			@Override
			public void run() throws SuspendExecution {
				runnable.apply(g);
			}
		});
		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(new Iterator() {
			@Override
			public boolean hasNext() {
				return !c.isFinished();
			}

			@Override
			public Object next() {
				c.process();
				if (c.isFinished())
					return END;
				return g.value;
			}
		}, Spliterator.ORDERED), false).filter(v -> v != END);
	}
}
