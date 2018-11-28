package com.zarbosoft.coroutines;

import com.zarbosoft.coroutinescore.SuspendExecution;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class TestWRCriticalSection {
	private final TestCriticalSection.Gate gate;
	private final ManualExecutor executor;
	private final WRCriticalSection critical;

	public TestWRCriticalSection() {
		this.gate = new TestCriticalSection.Gate();
		this.executor = new ManualExecutor();
		this.critical = new WRCriticalSection();
	}

	public int invokeRead(int arg) throws SuspendExecution {
		return (Integer) critical.read(executor, () -> {
			assertThat(arg, greaterThan(4));
			gate.stop(arg);
			return arg * 2;
		});
	}

	public int invokeWrite(int arg) throws SuspendExecution {
		return (Integer) critical.write(executor, () -> {
			assertThat(arg, greaterThan(4));
			gate.stop(arg);
			return arg * 2;
		});
	}

	@Test
	public void testOneRead() {
		final Coroutine coroutine1 = new Coroutine(() -> {
			assertThat(invokeRead(13), equalTo(26));
		});
		coroutine1.process();
		gate.start(13);
		assertTrue(coroutine1.isFinished());
	}

	@Test
	public void testParallelRead() {
		List<Integer> value = new ArrayList<>();
		final Coroutine coroutine1 = new Coroutine(() -> {
			assertThat(invokeRead(13), equalTo(26));
			value.add(3);
		});
		final Coroutine coroutine2 = new Coroutine(() -> {
			assertThat(invokeRead(14), equalTo(28));
			value.add(4);
		});
		coroutine1.process();
		coroutine2.process();
		gate.start(14);
		gate.start(13);
		assertThat(value, equalTo(Arrays.asList(4, 3)));
		assertTrue(coroutine1.isFinished());
		assertTrue(coroutine2.isFinished());
	}

	@Test
	public void testWriteBlockRead() {
		List<Integer> value = new ArrayList<>();
		final Coroutine coroutine1 = new Coroutine(() -> {
			assertThat(invokeRead(13), equalTo(26));
			value.add(3);
		});
		final Coroutine coroutine2 = new Coroutine(() -> {
			assertThat(invokeWrite(14), equalTo(28));
			value.add(4);
		});
		final Coroutine coroutine3 = new Coroutine(() -> {
			assertThat(invokeRead(15), equalTo(30));
			value.add(5);
		});
		coroutine1.process();
		coroutine2.process();
		coroutine3.process();
		gate.start(13);
		gate.start(14);
		gate.start(15);
		assertThat(value, equalTo(Arrays.asList(3, 4, 5)));
		assertTrue(coroutine1.isFinished());
		assertTrue(coroutine2.isFinished());
		assertTrue(coroutine3.isFinished());
	}

	@Test
	public void testReleaseReadBetweenWrite() {
		List<Integer> value = new ArrayList<>();
		final Coroutine coroutine1 = new Coroutine(() -> {
			assertThat(invokeRead(13), equalTo(26));
			value.add(3);
		});
		final Coroutine coroutine2 = new Coroutine(() -> {
			assertThat(invokeWrite(14), equalTo(28));
			value.add(4);
		});
		final Coroutine coroutine3 = new Coroutine(() -> {
			assertThat(invokeWrite(15), equalTo(30));
			value.add(5);
		});
		final Coroutine coroutine4 = new Coroutine(() -> {
			assertThat(invokeRead(16), equalTo(32));
			value.add(6);
		});
		coroutine1.process();
		coroutine2.process();
		coroutine3.process();
		coroutine4.process();
		gate.start(13);
		assertTrue(coroutine1.isFinished());
		gate.start(14);
		assertTrue(coroutine2.isFinished());
		gate.start(16);
		assertTrue(coroutine4.isFinished());
		gate.start(15);
		assertTrue(coroutine3.isFinished());
		assertThat(value, equalTo(Arrays.asList(3, 4, 6, 5)));
	}
}
