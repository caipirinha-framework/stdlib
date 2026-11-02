package com.peterphi.std.guice.database;


import com.peterphi.std.guice.hibernate.dao.HibernateDao;
import com.peterphi.std.guice.restclient.jaxb.webquery.WebQuery;
import com.peterphi.std.util.tracing.Tracing;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang.StringUtils;
import org.unbescape.csv.CsvEscape;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class CSVSearchUtils
{
	private static final int MAX_RESULTS = 500_000;

	private static final String TSV_UNSAFE_STR = "\t\n";
	private static final String TSV_UNSAFE_REPLACEMENTS = "  ";
	private static final char[] TSV_UNSAFE = TSV_UNSAFE_STR.toCharArray();


	/**
	 * @param dao      dao to request projection against
	 * @param query    the user's query
	 * @param pageSize the max number of rows to fetch per transaction
	 * @param header   the header row, expressed as a comma-separated list
	 * @param variant  the variant to use (if null or "csv" then will output an Excel-compatible CSV. Other supported value is <code>tsv</code>, which will output tsv with no headers, unix newlines (and replace tab/newline in values with space char)
	 * @param filename the output filename (optional, if set a Content-Disposition header will be returned with this filename)
	 * @return
	 */
	public static Response process(final HibernateDao<?, ?> dao,
	                               final WebQuery query,
	                               final int pageSize,
	                               final String header,
	                               final String variant,
	                               final String filename)
	{
		@Positive final int wantedRows = Math.min(MAX_RESULTS, query.getLimit() == 0 ? Integer.MAX_VALUE : query.getLimit());

		if ("entity".equals(query.fetch))
			throw new IllegalArgumentException("CSV Projection cannot fetch Entity!");

		// Decode variant
		final boolean emitHeader, useTabs;
		final String newline;
		if (variant == null || variant.equals("csv"))
		{
			emitHeader = true;
			useTabs = false;
			newline = "\r\n";
		}
		else if (variant.equals("tsv"))
		{
			emitHeader = false;
			useTabs = true;
			newline = "\n";
		}
		else
		{
			throw new IllegalArgumentException("Unknown variant! Expected one of: csv,tsv");
		}

		// Special-case a count query
		if (query.isComputeSize())
		{
			final long count = dao.count(query);

			final String str;
			if (emitHeader)
				str = "count" + newline + count;
			else
				str = "" + count;

			return Response.ok(str, "text/csv").build();
		}

		// CSV doesn't contain metadata, so don't bother generating it
		query.computeSize(false);
		query.logSQL(false);
		query.logPerformance(false);

		// If the user wants a lot of results, use a sensible page size
		if (wantedRows > pageSize)
			query.limit(pageSize);
		else
			query.limit(wantedRows);

		// First, check that the query is valid before we start the streaming output
		// We do this by proactively fetching Page 1
		final var firstPage = dao.project(query, false);
		final var onlyHasOnePage = firstPage.getList().size() <= wantedRows;

		final boolean verboseTrace = Tracing.isVerbose();
		final String traceId = Tracing.newOperationId("Start multi-page CSV Streaming Output processing...");

		// Emit row data
		final StreamingOutput streamingOutput = os -> {
			final boolean startedTrace;
			final String rootTrace;
			if (Tracing.getTraceId() == null)
			{
				rootTrace = traceId;
				Tracing.start(rootTrace, verboseTrace);
				startedTrace = true;
			}
			else
			{
				startedTrace = false;
				rootTrace = null;
			}

			try (os)
			{
				try (final var writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), 4096))
				{
					if (emitHeader)
						writer.append(header.replace(',', useTabs ? '\t' : ',')).append(newline);

					final int cols = StringUtils.split(header, ',').length;

					int rowsSoFar = 0;

					do
					{
						final List<Object[]> results;

						if (rowsSoFar == 0)
							results = firstPage.getList(); // N.B. clear this list at the end
						else
							results = dao.project(query, false).getList();

						for (Object[] row : results)
						{
							int col = 0;
							for (Object o : row)
							{
								if (col >= cols)
									break; // Don't emit any extra cols the user did not want

								if (!useTabs)
								{
									// CSV
									if (col != 0)
										writer.append(',');

									if (o != null)
										writer.append(CsvEscape.escapeCsv(o.toString()));
								}
								else
								{
									// TSV
									if (col != 0)
										writer.append('\t');

									if (o != null)
									{
										final String str = o.toString();
										if (!StringUtils.containsAny(str, TSV_UNSAFE))
											writer.append(str);
										else
											writer.append(StringUtils.replaceChars(str, TSV_UNSAFE_STR, TSV_UNSAFE_REPLACEMENTS));
									}
								}

								col++;
							}
							writer.append(newline);
						}

						if (results.isEmpty() || results.size() < query.getLimit())
						{
							// Last page
							break;
						}
						else
						{
							rowsSoFar += results.size();

							// Free up memory from this page
							results.clear();

							// Prepare for next page
							query.offset(query.getOffset() + query.getLimit());

							// Check if the next page is the last page
							final int rowsLeft = wantedRows - rowsSoFar;
							if (rowsLeft < query.getLimit())
								query.limit(rowsLeft);

							// Make sure we finish streaming out this page of results
							writer.flush();
						}
					}
					while (rowsSoFar < wantedRows);
				}
			}
			finally
			{
				if (startedTrace && rootTrace != null)
					Tracing.stop(rootTrace);
			}
		};


		// Special-case the resultset having only one page of results and return without chunking
		final Response.ResponseBuilder builder = Response.ok().type("text/csv");

		if (filename != null)
		{
			builder.header("Content-Disposition", "attachment; filename=\"" + filename.replace('"', '\'') + "\"");
		}
		if (onlyHasOnePage)
		{
			final ByteArrayOutputStream bos = new ByteArrayOutputStream(2048);

			try (bos)
			{
				streamingOutput.write(bos);
			}
			catch (IOException e)
			{
				throw new RuntimeException("Error writing CSV Output: " + e.getMessage(), e);
			}

			builder.header("Content-Length", bos.size()).entity(bos.toInputStream());
		}
		else
		{
			builder.entity(streamingOutput);
		}

		return builder.build();
	}
}
