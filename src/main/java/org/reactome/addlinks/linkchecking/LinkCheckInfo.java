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
	private CheckableLink linkData;
	private int statusCode;
	private boolean keywordFound;
	private Duration responseTime;
	private int numRetries;
	private String referenceDatabaseDBID;
	private String referenceDatabaseName;
	private String identifierDBID;
	private String identifier;

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
	
	@Override
	public String toString()
	{
		return "[DBID: " + this.getIdentifierDBID() + "; " +
				"Identifier: " + this.getIdentifier() + "; " +
				"URI: " + this.getURI().toString() + "; " +
				"RefDBID: " + this.getReferenceDatabaseDBID() + "; " + 
				"RefDB Name: " + this.getReferenceDatabaseName() + "; " + 
				"# of Retries: " + this.getNumRetries() + "; " +
				"Keyword: " + this.getSearchKeyword() + "; " +
				"Keyword Found: " + this.isKeywordFound() + "; " +
				"Response Time: " + this.getResponseTime() + "; " +
				"Status code: " + this.getStatusCode() +
				"]";
	}

	public String getIdentifier()
	{
		return this.identifier;
	}

	public void setIdentifier(String identifier)
	{
		this.identifier = identifier;
	}
}
