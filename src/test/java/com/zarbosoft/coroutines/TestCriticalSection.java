package com.zarbosoft.coroutines;

import com.zarbosoft.coroutinescore.SuspendExecution;
import com.zarbosoft.rendaw.common.Common;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class TestCriticalSection {
	private final TestCriticalSection.Gate gate;
	private final ManualExecutor executor;
	private final CriticalSection critical;

	public TestCriticalSection() {
		this.gate = new TestCriticalSection.Gate();
		this.executor = new ManualExecutor();
		this.critical = new CriticalSection();
	}

	public int invoke(int arg) throws SuspendExecution {
		return (Integer) critical.call(executor, () -> {
			assertThat(arg, greaterThan(4));
			gate.stop(arg);
			return arg * 2;
		});
	}

	@Test
	public void testCriticalSectionSingle() {
		final Coroutine coroutine1 = new Coroutine(() -> {
			assertThat(invoke(13), equalTo(26));
		});
		coroutine1.process();
		gate.start(13);
		assertTrue(coroutine1.isFinished());
	}

	@Test
	public void testCriticalSectionSingleNoSuspend() {
		final Coroutine coroutine1 = new Coroutine(() -> {
			assertThat(critical.call(executor, () -> 26), equalTo(26));
		});
		coroutine1.process();
		assertTrue(coroutine1.isFinished());
	}

	@Test
	public void testCriticalSectionSequential() {
		final Coroutine coroutine1 = new Coroutine(() -> {
			assertThat(invoke(13), equalTo(26));
			assertThat(invoke(14), equalTo(28));
		});
		coroutine1.process();
		gate.start(13);
		gate.start(14);
		assertTrue(coroutine1.isFinished());
	}

	@Test
	public void testCriticalSectionNoContention() {
		final Coroutine coroutine1 = new Coroutine(() -> {
			assertThat(invoke(7), equalTo(14));
		});
		final Coroutine coroutine2 = new Coroutine(() -> {
			assertThat(invoke(8), equalTo(16));
		});
		coroutine1.process();
		gate.start(7);
		assertTrue(coroutine1.isFinished());
		coroutine2.process();
		gate.start(8);
		assertTrue(coroutine2.isFinished());
	}

	@Test
	public void testCriticalSectionContention() {
		final Coroutine coroutine1 = new Coroutine(() -> {
			assertThat(invoke(7), equalTo(14));
		});
		final Coroutine coroutine2 = new Coroutine(() -> {
			assertThat(invoke(8), equalTo(16));
		});
		coroutine1.process();
		coroutine2.process();
		gate.start(7);
		assertTrue(coroutine1.isFinished());
		assertTrue(!coroutine2.isFinished());
		gate.start(8);
		assertTrue(coroutine2.isFinished());
	}

	static class Gate {
		Map<Integer, Coroutine> gates = new HashMap<>();

		public void stop(int id) throws SuspendExecution {
			System.out.format("Arrived at gate %s\n", id);
			if (gates.containsKey(id))
				throw new AssertionError();
			gates.put(id, Coroutine.getActiveCoroutine());
			Coroutine.yield();
		}

		public void start(int id) {
			System.out.format("Leaving gate %s\n", id);
			Coroutine coroutine = gates.remove(id);
			if (coroutine == null)
				throw new NotAtGateFailure();
			coroutine.process();
		}
	}

	static class NotAtGateFailure extends RuntimeException {

	}
}
