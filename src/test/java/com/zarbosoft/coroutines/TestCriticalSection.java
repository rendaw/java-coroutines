package com.zarbosoft.coroutines;

import com.zarbosoft.coroutinescore.SuspendExecution;
import com.zarbosoft.rendaw.common.Common;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class TestCriticalSection {
	@Test
	public void testCriticalSectionSingle() {
		final Common.Mutable<Coroutine> escape = new Common.Mutable<>();
		final ExecutorService executor = new ManualExecutor();
		final CriticalSection<Integer, Integer> critical = new CriticalSection<Integer, Integer>() {
			@Override
			protected Integer execute(final Integer arg) throws SuspendExecution {
				assertThat(arg, greaterThan(4));
				escape.value = Coroutine.getActiveCoroutine();
				Coroutine.yield();
				return arg * 2;
			}
		};

		final Coroutine coroutine1 = new Coroutine(() -> {
			assertThat(critical.call(executor, 13), equalTo(26));
		});
		coroutine1.process();
		escape.value.process();
		assertTrue(coroutine1.isFinished());
	}

	@Test
	public void testCriticalSectionSequential() {
		final List<Coroutine> escape = new ArrayList<>();
		final ExecutorService executor = new ManualExecutor();
		final CriticalSection<Integer, Integer> critical = new CriticalSection<Integer, Integer>() {
			@Override
			protected Integer execute(final Integer arg) throws SuspendExecution {
				escape.add(Coroutine.getActiveCoroutine());
				Coroutine.yield();
				return arg * 2;
			}
		};

		final Coroutine coroutine1 = new Coroutine(() -> {
			assertThat(critical.call(executor, 13), equalTo(26));
			assertThat(critical.call(executor, 14), equalTo(28));
		});
		coroutine1.process();
		escape.get(0).process();
		escape.get(1).process();
		assertTrue(coroutine1.isFinished());
	}

	@Test
	public void testCriticalSectionNoContention() {
		final List<Coroutine> escape = new ArrayList<>();
		final ManualExecutor executor = new ManualExecutor();
		final CriticalSection<Integer, Integer> critical = new CriticalSection<Integer, Integer>() {
			@Override
			protected Integer execute(final Integer arg) throws SuspendExecution {
				assertThat(arg, greaterThan(4));
				escape.add(Coroutine.getActiveCoroutine());
				Coroutine.yield();
				return arg * 2;
			}
		};

		final Coroutine coroutine1 = new Coroutine(() -> {
			assertThat(critical.call(executor, 7), equalTo(14));
		});
		final Coroutine coroutine2 = new Coroutine(() -> {
			assertThat(critical.call(executor, 8), equalTo(16));
		});
		coroutine1.process();
		escape.get(0).process();
		assertTrue(coroutine1.isFinished());
		coroutine2.process();
		escape.get(1).process();
		assertTrue(coroutine2.isFinished());
	}

	@Test
	public void testCriticalSectionContention() {
		final List<Coroutine> escape = new ArrayList<>();
		final ManualExecutor executor = new ManualExecutor();
		final CriticalSection<Integer, Integer> critical = new CriticalSection<Integer, Integer>() {
			@Override
			protected Integer execute(final Integer arg) throws SuspendExecution {
				assertThat(arg, greaterThan(4));
				escape.add(Coroutine.getActiveCoroutine());
				Coroutine.yield();
				return arg * 2;
			}
		};

		final Coroutine coroutine1 = new Coroutine(() -> {
			assertThat(critical.call(executor, 7), equalTo(14));
		});
		final Coroutine coroutine2 = new Coroutine(() -> {
			assertThat(critical.call(executor, 8), equalTo(16));
		});
		coroutine1.process();
		coroutine2.process();
		escape.get(0).process();
		assertTrue(coroutine1.isFinished());
		assertTrue(!coroutine2.isFinished());
		escape.get(1).process();
		assertTrue(coroutine2.isFinished());
	}
}
