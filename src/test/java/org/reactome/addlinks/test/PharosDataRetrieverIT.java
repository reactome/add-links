package org.reactome.addlinks.test;

import static org.junit.Assert.fail;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;

import org.junit.Test;
import org.reactome.addlinks.dataretrieval.PharosDataRetriever;
import org.reactome.addlinks.dataretrieval.PharosLigandDataRetriever;
import org.reactome.addlinks.dataretrieval.PharosTargetsDataRetriever;

/**
 * Integration tests for the Pharos data retriever - will make calls to live Pharos webservice.
 * @author sshorser
 *
 */
public class PharosDataRetrieverIT
{

	@Test
	public void testPharosTargetsDataRetriever() throws URISyntaxException
	{
		PharosDataRetriever retriever = new PharosTargetsDataRetriever();
		retriever.setDataURL(new URI("https://ncatsidg-dev.appspot.com/graphql"));
		retriever.setFetchDestination("/tmp/pharos_targets_data.txt");
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

	@Test
	public void testPharosLigandsDataRetriever() throws URISyntaxException
	{
		PharosDataRetriever retriever = new PharosLigandDataRetriever();
		retriever.setDataURL(new URI("https://ncatsidg-dev.appspot.com/graphql"));
		retriever.setFetchDestination("/tmp/pharos_ligand_data.txt");
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
