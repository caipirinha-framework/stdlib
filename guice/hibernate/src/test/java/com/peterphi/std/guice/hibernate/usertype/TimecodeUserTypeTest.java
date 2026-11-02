package com.peterphi.std.guice.hibernate.usertype;

import com.peterphi.std.types.Timebase;
import com.peterphi.std.types.Timecode;
import org.junit.Test;

import java.sql.Types;

import static org.junit.Assert.assertEquals;

public class TimecodeUserTypeTest
{
	private final Timecode TC = Timecode.getInstance("01:02:03:04", Timebase.HZ_25);
	private final Timecode TC_DAYS = Timecode.getInstance("01:02:03:04:05", Timebase.HZ_25);

	private final TimecodeUserType svc = TimecodeUserType.INSTANCE;


	@Test
	public void testSqlTypes() throws Exception
	{
		assertEquals(Types.VARCHAR, svc.getSqlType());
	}


	@Test
	public void testReturnedClass() throws Exception
	{
		assertEquals(Timecode.class, svc.returnedClass());
	}


	@Test
	public void testIsMutable() throws Exception
	{
		assertEquals(false, svc.isMutable());
	}


	@Test
	public void testDisassemble() throws Exception
	{
		assertEquals("01:02:03:04@25", svc.disassemble(TC));
	}


	@Test
	public void testAssemble() throws Exception
	{
		assertEquals(TC, svc.assemble("01:02:03:04@25", null));
	}


	@Test
	public void testDisassembleWithDaysPart() throws Exception
	{
		assertEquals("01:02:03:04:05@25", svc.disassemble(TC_DAYS));
	}


	@Test
	public void testAssembleWithDaysPart() throws Exception
	{
		assertEquals(TC_DAYS, svc.assemble("01:02:03:04:05@25", null));
	}
}
