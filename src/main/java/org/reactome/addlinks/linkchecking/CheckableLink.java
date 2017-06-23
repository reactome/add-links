package org.reactome.addlinks.linkchecking;

import java.net.URI;

class CheckableLink
{
	private URI uri;
	private String searchKeyword;
	
	public CheckableLink(URI uri, String searchKeyword)
	{
		this.uri = uri;
		this.searchKeyword = searchKeyword;
	}
	
	public URI getURI()
	{
		return this.uri;
	}
	
	public String getSearchKeyword()
	{
		return this.searchKeyword;
	}
}