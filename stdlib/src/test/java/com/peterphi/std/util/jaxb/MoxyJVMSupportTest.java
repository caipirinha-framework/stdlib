package com.peterphi.std.util.jaxb;

import org.eclipse.persistence.asm.EclipseLinkASMClassWriter;
import org.junit.Test;

import java.text.MessageFormat;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class MoxyJVMSupportTest
{
	/**
	 * Throws an exception from logger method if MOXy complains about not fully supporting the current JVM Version
	 */
	private static final Handler handler = new Handler()
	{
		@Override
		public void publish(final LogRecord record)
		{
			if (record.getMessage().contains("is not fully supported yet"))
				throw new RuntimeException("EclipseLink MOXy is complaining that this Java version is not fully supported: " +
				                           MessageFormat.format(record.getMessage(), record.getParameters()));
		}


		@Override
		public void flush()
		{

		}


		@Override
		public void close()
		{

		}
	};


	@Test
	public void testMoxySupportsCurrentJVM()
	{

		final Logger logger = Logger.getLogger(EclipseLinkASMClassWriter.class.getName());
		logger.addHandler(handler);

		try
		{
			// Construct the class
			new EclipseLinkASMClassWriter();
		}
		finally
		{
			logger.removeHandler(handler);
		}
	}
}
