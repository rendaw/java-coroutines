package com.zarbosoft.coroutines;

import com.zarbosoft.coroutinescore.SuspendExecution;
import com.zarbosoft.rendaw.common.Common;
import org.junit.Test;

import java.util.*;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class TestRWCriticalSection {
	private final TestCriticalSection.Gate gate;
	private final ManualExecutor executor;
	private final RWCriticalSection critical;

	public TestRWCriticalSection() {
		this.gate = new TestCriticalSection.Gate();
		this.executor = new ManualExecutor();
		this.critical = new RWCriticalSection();
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

	public boolean invokeTryUniqueWrite(int arg) throws SuspendExecution {
		return critical.tryUniqueWrite(executor, () -> {
			assertThat(arg, greaterThan(4));
			gate.stop(arg);
		});
	}

	@Test
	public void testOneReader() {
		final Coroutine coroutine1 = new Coroutine(() -> {
			assertThat(invokeRead(13), equalTo(26));
		});
		coroutine1.process();
		gate.start(13);
		assertTrue(coroutine1.isFinished());
	}

	@Test
	public void testOneReaderNoSuspend() {
		final Coroutine coroutine1 = new Coroutine(() -> {
			assertThat(critical.read(executor, () -> 26), equalTo(26));
		});
		coroutine1.process();
		assertTrue(coroutine1.isFinished());
	}

	@Test
	public void testSequentialReaders() {
		Common.Mutable<Integer> value = new Common.Mutable<>(0);
		final Coroutine coroutine1 = new Coroutine(() -> {
			assertThat(invokeRead(13), equalTo(26));
			assertThat(invokeRead(14), equalTo(28));
			value.value = 3;
		});
		coroutine1.process();
		gate.start(13);
		gate.start(14);
		assertThat(value.value, equalTo(3));
		assertTrue(coroutine1.isFinished());
	}

	@Test
	public void testParallelReaders() {
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
	public void testOneWriter() {
		Common.Mutable<Integer> value = new Common.Mutable<>(0);
		final Coroutine coroutine1 = new Coroutine(() -> {
			assertThat(invokeWrite(13), equalTo(26));
			value.value = 3;
		});
		coroutine1.process();
		gate.start(13);
		assertThat(value.value, equalTo(3));
		assertTrue(coroutine1.isFinished());
	}

	@Test
	public void testOneWriterNoSuspend() {
		final Coroutine coroutine1 = new Coroutine(() -> {
			assertThat(critical.write(executor, () -> 26), equalTo(26));
		});
		coroutine1.process();
		assertTrue(coroutine1.isFinished());
	}

	@Test
	public void testSequentialWriters() {
		List<Integer> value = new ArrayList<>();
		final Coroutine coroutine1 = new Coroutine(() -> {
			assertThat(invokeWrite(13), equalTo(26));
			value.add(3);
		});
		final Coroutine coroutine2 = new Coroutine(() -> {
			assertThat(invokeWrite(14), equalTo(28));
			value.add(4);
		});
		coroutine1.process();
		gate.start(13);
		coroutine2.process();
		gate.start(14);
		assertTrue(coroutine1.isFinished());
		assertTrue(coroutine2.isFinished());
		assertThat(value, equalTo(Arrays.asList(3, 4)));
	}

	@Test
	public void testBlockingWriters() {
		List<Integer> value = new ArrayList<>();
		final Coroutine coroutine1 = new Coroutine(() -> {
			assertThat(invokeWrite(13), equalTo(26));
			value.add(3);
		});
		final Coroutine coroutine2 = new Coroutine(() -> {
			assertThat(invokeWrite(14), equalTo(28));
			value.add(4);
		});
		coroutine1.process();
		coroutine2.process();
		gate.start(13);
		gate.start(14);
		assertTrue(coroutine1.isFinished());
		assertTrue(coroutine2.isFinished());
		assertThat(value, equalTo(Arrays.asList(3, 4)));
	}

	@Test(expected = TestCriticalSection.NotAtGateFailure.class)
	public void testBlockingWritersBlocked() {
		List<Integer> value = new ArrayList<>();
		final Coroutine coroutine1 = new Coroutine(() -> {
			assertThat(invokeWrite(13), equalTo(26));
			value.add(3);
		});
		final Coroutine coroutine2 = new Coroutine(() -> {
			assertThat(invokeWrite(14), equalTo(28));
			value.add(4);
		});
		coroutine1.process();
		coroutine2.process();
		gate.start(14);
	}

	@Test
	public void testTryWritePass() {
		final Coroutine coroutine1 = new Coroutine(() -> {
			assertThat(invokeTryUniqueWrite(8), is(true));
		});
		coroutine1.process();
		gate.start(8);
		assertTrue(coroutine1.isFinished());
	}

	@Test
	public void testWriteTryWriteBlocked() {
		List<Integer> value = new ArrayList<>();
		final Coroutine coroutine1 = new Coroutine(() -> {
			assertThat(invokeTryUniqueWrite(7), is(true));
			value.add(3);
		});
		final Coroutine coroutine2 = new Coroutine(() -> {
			assertThat(invokeTryUniqueWrite(9), is(false));
			value.add(4);
		});
		coroutine1.process();
		coroutine2.process();
		assertTrue(coroutine2.isFinished());
		gate.start(7);
		assertTrue(coroutine1.isFinished());
		assertThat(value, equalTo(Arrays.asList(4, 3)));
	}

	@Test
	public void testReadBlockWrite() {
		List<Integer> value = new ArrayList<>();
		final Coroutine coroutine1 = new Coroutine(() -> {
			assertThat(invokeRead(13), equalTo(26));
			value.add(3);
		});
		final Coroutine coroutine2 = new Coroutine(() -> {
			assertThat(invokeWrite(14), equalTo(28));
			value.add(4);
		});
		coroutine1.process();
		coroutine2.process();
		gate.start(13);
		gate.start(14);
		assertThat(value, equalTo(Arrays.asList(3, 4)));
		assertTrue(coroutine1.isFinished());
		assertTrue(coroutine2.isFinished());
	}

	@Test(expected = TestCriticalSection.NotAtGateFailure.class)
	public void testReadBlockWriteBlocked() {
		List<Integer> value = new ArrayList<>();
		final Coroutine coroutine1 = new Coroutine(() -> {
			assertThat(invokeRead(13), equalTo(26));
			value.add(3);
		});
		final Coroutine coroutine2 = new Coroutine(() -> {
			assertThat(invokeWrite(14), equalTo(28));
			value.add(4);
		});
		coroutine1.process();
		coroutine2.process();
		gate.start(14);
	}

	@Test
	public void testReadTryWritePass() {
		List<Integer> value = new ArrayList<>();
		final Coroutine coroutine1 = new Coroutine(() -> {
			assertThat(invokeRead(13), equalTo(26));
			value.add(3);
		});
		final Coroutine coroutine2 = new Coroutine(() -> {
			assertThat(invokeTryUniqueWrite(14), is(true));
			value.add(4);
		});
		coroutine1.process();
		coroutine2.process();
		gate.start(13);
		gate.start(14);
		assertTrue(coroutine1.isFinished());
		assertTrue(coroutine2.isFinished());
		assertThat(value, equalTo(Arrays.asList(3, 4)));
	}

	@Test
	public void testReadTryWriteBlocked() {
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
			assertThat(invokeTryUniqueWrite(15), is(false));
			value.add(5);
		});
		coroutine1.process();
		coroutine2.process();
		coroutine3.process();
		assertTrue(coroutine3.isFinished());
		gate.start(13);
		assertTrue(coroutine1.isFinished());
		gate.start(14);
		assertTrue(coroutine2.isFinished());
		assertThat(value, equalTo(Arrays.asList(5, 3, 4)));
	}

	/**
	 * Additional reads will run while a read is in progress even if write blocked.
	 */
	@Test
	public void testAdditionalReadBlockWrite() {
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
		gate.start(15);
		gate.start(14);
		assertThat(value, equalTo(Arrays.asList(3, 5, 4)));
		assertTrue(coroutine1.isFinished());
		assertTrue(coroutine2.isFinished());
		assertTrue(coroutine3.isFinished());
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
		coroutine2.process();
		coroutine1.process();
		gate.start(14);
		gate.start(13);
		assertThat(value, equalTo(Arrays.asList(4, 3)));
		assertTrue(coroutine1.isFinished());
		assertTrue(coroutine2.isFinished());
	}

	@Test(expected = TestCriticalSection.NotAtGateFailure.class)
	public void testWriteBlockReadBlocked() {
		List<Integer> value = new ArrayList<>();
		final Coroutine coroutine1 = new Coroutine(() -> {
			assertThat(invokeRead(13), equalTo(26));
			value.add(3);
		});
		final Coroutine coroutine2 = new Coroutine(() -> {
			assertThat(invokeWrite(14), equalTo(28));
			value.add(4);
		});
		coroutine2.process();
		coroutine1.process();
		gate.start(13);
	}

	@Test
	public void testReadBetweenWrites() {
		List<Integer> value = new ArrayList<>();
		final Coroutine coroutine1 = new Coroutine(() -> {
			assertThat(invokeWrite(13), equalTo(26));
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
		gate.start(15);
		gate.start(14);
		assertThat(value, equalTo(Arrays.asList(3, 5, 4)));
		assertTrue(coroutine1.isFinished());
		assertTrue(coroutine2.isFinished());
		assertTrue(coroutine3.isFinished());
	}

	/**
	 * Move to additional reads block write state after initial write finishes.
	 */
	@Test
	public void testTransitionReadBlockWrite() {
		List<Integer> value = new ArrayList<>();
		final Coroutine coroutine1 = new Coroutine(() -> {
			assertThat(invokeWrite(13), equalTo(26));
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
		final Coroutine coroutine4 = new Coroutine(() -> {
			assertThat(invokeRead(16), equalTo(32));
			value.add(6);
		});
		coroutine1.process();
		coroutine2.process();
		coroutine3.process();
		gate.start(13);
		coroutine4.process();
		gate.start(16);
		gate.start(15);
		gate.start(14);
		assertThat(value, equalTo(Arrays.asList(3, 6, 5, 4)));
		assertTrue(coroutine1.isFinished());
		assertTrue(coroutine2.isFinished());
		assertTrue(coroutine3.isFinished());
		assertTrue(coroutine4.isFinished());
	}
}
