package com.peterphi.std.guice.hibernate.module;

import com.google.inject.Inject;
import com.peterphi.std.guice.database.annotation.Transactional;
import com.peterphi.std.guice.testing.GuiceUnit;
import com.peterphi.std.guice.testing.com.peterphi.std.guice.testing.annotations.GuiceConfig;
import org.junit.Test;
import org.junit.runner.RunWith;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

@RunWith(GuiceUnit.class)
@GuiceConfig(config = "hibernate-tests-in-memory-hsqldb.properties",
		            classPackages = TransactionHelperTest.class)
public class TransactionHelperTest
{
	@Inject
	TransactionHelper txutils;

	private final AtomicInteger commits = new AtomicInteger(0);
	private final AtomicInteger rollbacks = new AtomicInteger(0);


	@Test
	public void testCommitActionRuns()
	{
		assertEquals(0, commits.get());
		assertEquals(0, rollbacks.get());

		// Execute TX without failing
		doTransaction(false);

		assertEquals(1, commits.get());
		assertEquals(0, rollbacks.get());
	}


	@Test(expected = KillTransaction.class)
	public void testRollbackActionRuns()
	{
		try
		{
			assertEquals(0, commits.get());
			assertEquals(0, rollbacks.get());

			// Execute TX without failing
			doTransaction(true);
		}
		catch (KillTransaction e)
		{
			// Should increment rollback counter
			assertEquals(0, commits.get());
			assertEquals(1, rollbacks.get());

			// Re-throw to allow the test to pass
			throw e;
		}
	}


	@Test(expected = IllegalStateException.class)
	public void testAddCommitActionWithNoTransactionFails()
	{
		txutils.addCommitAction(() -> commits.incrementAndGet());
	}


	@Transactional
	public void doTransaction(boolean fail)
	{
		// Set up commit / rollback actions
		txutils.addCommitAction(() -> commits.incrementAndGet());
		txutils.addRollbackAction(() -> rollbacks.incrementAndGet());

		if (fail)
			throw new KillTransaction();
	}


	private static class KillTransaction extends RuntimeException
	{
	}

	@Entity(name = "Q")
	private static class DummyEntity
	{
		@Id
		@GeneratedValue(strategy = GenerationType.AUTO)
		private Long id;
	}
}
