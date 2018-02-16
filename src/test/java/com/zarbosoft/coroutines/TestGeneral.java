package com.zarbosoft.coroutines;

import org.junit.Test;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class TestGeneral {

	@Test
	public void testBaseline() {
		final Coroutine coroutine = new Coroutine(() -> {
			Coroutine.yield();
		});
		coroutine.process();
		coroutine.process();
		assertTrue(coroutine.isFinished());
	}

	@Test(expected = com.zarbosoft.coroutinescore.Coroutine.Error.class)
	public void testCantRestartFinished() {
		final Coroutine coroutine = new Coroutine(() -> {
		});
		coroutine.process();
		coroutine.process();
	}

	@Test
	public void testInject() {
		final Coroutine coroutine = new Coroutine(() -> {
			assertThat(Coroutine.yield(), equalTo(4));
		});
		coroutine.process();
		coroutine.process(4);
		assertTrue(coroutine.isFinished());
	}

	@Test
	public void testInjectException() {
		class TestError extends RuntimeException {

		}
		final Coroutine coroutine = new Coroutine(() -> {
			try {
				Coroutine.yield();
			} catch (final TestError e) {
				return;
			}
			throw new AssertionError();
		});
		coroutine.process();
		coroutine.processThrow(new TestError());
		assertTrue(coroutine.isFinished());
	}

	@Test
	public void testYieldThen() {
		final Coroutine coroutine = new Coroutine(() -> {
			final Coroutine coroutine1 = Coroutine.getActiveCoroutine();
			Coroutine.yieldThen(() -> {
				coroutine1.process();
			});
		});
		coroutine.process();
		assertTrue(coroutine.isFinished());
	}
}
