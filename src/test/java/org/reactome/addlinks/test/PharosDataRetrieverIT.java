package org.reactome.addlinks.test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.time.Duration;

import org.junit.Test;
import org.reactome.addlinks.dataretrieval.pharos.PharosDataRetriever;
import org.reactome.addlinks.dataretrieval.pharos.PharosLigandDataRetriever;
import org.reactome.addlinks.dataretrieval.pharos.PharosTargetsDataRetriever;

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
		String pathToPharosTargetsFile = "/tmp/pharos_targets_data.txt";
		PharosDataRetriever retriever = new PharosTargetsDataRetriever();
		testRetriever(pathToPharosTargetsFile, retriever);
	}

	@Test
	public void testPharosLigandsDataRetriever() throws URISyntaxException
	{
		String pathToPharosLigandsFile = "/tmp/pharos_ligand_data.txt";
		PharosDataRetriever retriever = new PharosLigandDataRetriever();
		testRetriever(pathToPharosLigandsFile, retriever);
	}

	/**
	 * Tests a Pharos retriever.
	 * @param pathToPharosFile Path to output file.
	 * @param retriever A retriever object to test.
	 * @throws URISyntaxException
	 */
	private void testRetriever(String pathToPharosFile, PharosDataRetriever retriever) throws URISyntaxException
	{
		retriever.setDataURL(new URI("https://ncatsidg-dev.appspot.com/graphql"));
		retriever.setFetchDestination(pathToPharosFile);
		// we always want to download when testing....
		retriever.setMaxAge(Duration.ofSeconds(0));
		try
		{
			retriever.fetchData();
			assertTrue(Files.exists(Paths.get(pathToPharosFile), LinkOption.NOFOLLOW_LINKS));
			assertTrue(Files.size(Paths.get(pathToPharosFile)) > 0);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			fail();
		}
	}

}
