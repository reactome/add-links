package org.reactome.addlinks.linkchecking;

import java.net.URI;
import java.time.Duration;

/**
 * This class stores the results of checking a link. Including:
 * - URI that was checked
 * - keyword that was searched for in the respone.
 * - response status code.
 * - whether or not the keyword was *actually* in the response
 * - response time (only for the most recent *successful* connection that gets a response)
 * - number of retries.
 * - ReferenceDatabase (DB_ID and name)
 * - DB_ID of object that contains the link.
 * 
 * @author sshorser
 *
 */
public class LinkCheckInfo
{
	CheckableLink linkData;
	int statusCode;
	boolean keywordFound;
	Duration responseTime;
	int numRetries;
	String referenceDatabaseDBID;
	String referenceDatabaseName;
	String identifierDBID;

	public int getStatusCode()
	{
		return this.statusCode;
	}

	public void setStatusCode(int statusCode)
	{
		this.statusCode = statusCode;
	}

	public boolean isKeywordFound()
	{
		return this.keywordFound;
	}

	public void setKeywordFound(boolean keywordFound)
	{
		this.keywordFound = keywordFound;
	}

	public Duration getResponseTime()
	{
		return this.responseTime;
	}

	public void setResponseTime(Duration responseTime)
	{
		this.responseTime = responseTime;
	}

	public int getNumRetries()
	{
		return this.numRetries;
	}

	public void setNumRetries(int numRetries)
	{
		this.numRetries = numRetries;
	}

	public String getReferenceDatabaseDBID()
	{
		return this.referenceDatabaseDBID;
	}

	public void setReferenceDatabaseDBID(String referenceDatabaseDBID)
	{
		this.referenceDatabaseDBID = referenceDatabaseDBID;
	}

	public String getReferenceDatabaseName()
	{
		return this.referenceDatabaseName;
	}

	public void setReferenceDatabaseName(String referenceDatabaseName)
	{
		this.referenceDatabaseName = referenceDatabaseName;
	}

	public String getIdentifierDBID()
	{
		return this.identifierDBID;
	}

	public void setIdentifierDBID(String identifierDBID)
	{
		this.identifierDBID = identifierDBID;
	}

	public URI getURI()
	{
		return this.linkData.getURI();
	}

	public String getSearchKeyword()
	{
		return this.linkData.getSearchKeyword();
	}

	public void setLinkData(URI uri, String keyword)
	{
		this.linkData = new CheckableLink(uri, keyword);
	}
}
