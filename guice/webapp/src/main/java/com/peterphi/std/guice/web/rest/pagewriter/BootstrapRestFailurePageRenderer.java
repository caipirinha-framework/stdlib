package com.peterphi.std.guice.web.rest.pagewriter;

import com.peterphi.std.guice.restclient.jaxb.ExceptionInfo;
import com.peterphi.std.guice.restclient.jaxb.RestFailure;
import org.apache.commons.lang.StringUtils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

public class BootstrapRestFailurePageRenderer extends BootstrapPageWriter
{
	private final RestFailure failure;

	private List<String> highlightTerms = null;

	private boolean renderStackTrace = false;


	public BootstrapRestFailurePageRenderer(RestFailure failure)
	{
		this.failure = failure;
	}


	public void setHighlightTerms(List<String> terms)
	{
		this.highlightTerms = terms;
	}


	@Override
	protected String getTitle()
	{
		if (failure.exception.detail == null || failure.exception.detail.length() < 1024)
			return failure.exception.shortName + ": " + failure.exception.detail;
		else
			return failure.exception.shortName; // Detail is very long, omit it from the page title
	}


	@Override
	protected void writeCustomHeadContent(StringBuilder sb)
	{
		super.writeCustomHeadContent(sb);

		sb.append("<style>\n");
		sb.append("pre { font-size: 11px; }\n");
		sb.append("body { padding-top: 60px; padding-bottom: 40px; }\n");
		sb.append(".dl-horizontal > dd:after { display: table; content: \"\"; clear: both; }\n");
		sb.append("</style>\n");
	}

	@Override
	protected void writeBodyContent(StringBuilder sb)
	{
		sb.append("<div class='container'>\n");

		appendHeader(sb);

		sb.append("<p>There was a problem processing your request:<br /><code>").append(failure.exception.shortName).append(
				"</code> thrown <code>").append(failure.date).append("</code> with id <code>").append(failure.id).append(
				"</code> and error code <code>").append(failure.errorCode).append("</code> returning HTTP Code <code>").append(
				failure.httpCode).append("</code>.");

		if (failure.source != null && !failure.source.equals("unknown"))
			sb.append(" Source listed as ").append(failure.source);

		sb.append("</p>");

		// Add the exception (and potentially the stack trace)
		appendException(sb, failure.exception);

		sb.append("</div>"); // div class=container
	}


	protected void appendHeader(StringBuilder sb)
	{
		sb.append("<h1>A problem occurred</h1>");
	}


	private void appendException(StringBuilder sb, ExceptionInfo info)
	{
		if (info != null)
		{
			sb.append("<hr />");
			sb.append("<h3 id='exception'>").append(escape(info.shortName)).append("</h3>");

			// Maintain any whitespace in the exception detail (e.g. for guice CreationExceptions)
			sb.append("<p style='white-space: pre-wrap;'>").append(escape(info.detail)).append("</p>");

			if (renderStackTrace)
			{
				appendStacktrace(sb, info);
			}
		}
	}


	private void appendStacktrace(StringBuilder sb, ExceptionInfo info)
	{
		if (!StringUtils.isEmpty(info.stackTrace))
		{
			sb.append("<pre>");
			if (this.highlightTerms != null)
			{
				String[] lines = info.stackTrace.split("\n");

				for (String line : lines)
				{
					appendStacktraceLine(sb, line);
				}
			}
			else
			{
				// Just append the stack trace
				sb.append(escape(info.stackTrace));
			}

			sb.append("</pre>");
		}
	}



	private void appendStacktraceLine(StringBuilder sb, String line)
	{
		// Only consider lines starting in whitespace (stack trace elements looking like " at com.company.x(MyClass.java:123)"
		if (!line.isEmpty() && Character.isWhitespace(line.charAt(0)))
		{
			for (String highlightTerm : highlightTerms)
			{
				if (line.contains(highlightTerm))
				{
					// Highlight line (also, make sure the whitespace is outside the <b> tag so things line up nicely)
					sb.append("\t<b>");
					sb.append(escape(line.substring(1)));
					sb.append("</b>");
					sb.append("<br />");
					return;
				}
			}

			// Line did not meet highlight criteria, mute it (also, make sure the whitespace is outside the <b> tag so things line up nicely)
			sb.append("\t<span class='muted'>");
			sb.append(escape(line.substring(1)));
			sb.append("</span>");
			sb.append("<br />");
		}
		else
		{
			// Line does not start with whitespace, do not highlight or mute
			sb.append(escape(line)).append("<br />");
		}
	}


	public void enableStackTrace()
	{
		this.renderStackTrace = true;
	}
}
