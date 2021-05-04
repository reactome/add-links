package org.reactome.addlinks.test;

import static org.junit.Assert.fail;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;

import org.junit.Test;
import org.reactome.addlinks.dataretrieval.PharosDataRetriever;

/**
 * Integration tests for the Pharos data retriever - will make calls to live Pharos webservice.
 * @author sshorser
 *
 */
public class PharosDataRetrieverIT
{

	@Test
	public void testPharosDataRetriever() throws URISyntaxException
	{
		PharosDataRetriever retriever = new PharosDataRetriever();
		retriever.setDataURL(new URI("https://ncatsidg-dev.appspot.com/graphql"));
		retriever.setFetchDestination("/tmp/pharo_data.txt");
		// we always want to download when testing....
		retriever.setMaxAge(Duration.ofSeconds(0));
		try
		{
			retriever.fetchData();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			fail();
		}
	}

}
