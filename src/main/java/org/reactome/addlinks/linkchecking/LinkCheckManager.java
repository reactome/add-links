package org.reactome.addlinks.linkchecking;

import java.net.URI;
import java.util.List;
import java.util.function.Function;

import org.apache.http.HttpStatus;

public class LinkCheckManager
{


	public void checkLinks(List<CheckableLink> linksData)
	{
		for (CheckableLink ld : linksData)
		{
			LinkChecker checker = new LinkChecker(ld.getURI(), ld.getSearchKeyword());
			
			// checking a link is more than just a true/false - the status code needs
			// to be taken into account too. And we could track other things such as number of 
			// retries, and time to get the response.
			// Maybe we should pass a call-back function to the link-checker 
			// to be called when the link-checker has gotten a response.
			
			try
			{
				checker.checkLink();
			}
			catch (Exception e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
