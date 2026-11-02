package com.peterphi.std.guice.hibernate.usertype;

import com.peterphi.std.types.SampleCount;
import com.peterphi.std.types.Timebase;
import org.junit.Test;

import java.sql.Types;

import static org.junit.Assert.assertEquals;

public class SampleCountUserTypeTest
{
	private final SampleCount SC = new SampleCount(124, Timebase.HZ_25);

	private final SampleCountUserType svc = SampleCountUserType.INSTANCE;


	@Test
	public void testSqlTypes() throws Exception
	{
		assertEquals(Types.VARCHAR, svc.getSqlType());
	}


	@Test
	public void testReturnedClass() throws Exception
	{
		assertEquals(SampleCount.class, svc.returnedClass());
	}


	@Test
	public void testIsMutable() throws Exception
	{
		assertEquals(false, svc.isMutable());
	}


	@Test
	public void testDisassemble() throws Exception
	{
		assertEquals("124@25", svc.disassemble(SC));
	}


	@Test
	public void testAssemble() throws Exception
	{
		assertEquals(SC, svc.assemble("124@25", null));
	}
}
