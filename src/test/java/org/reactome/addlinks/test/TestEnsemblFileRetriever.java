package org.reactome.addlinks.test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.reactome.addlinks.dataretrieval.ensembl.EnsemblFileRetriever;
import org.reactome.addlinks.dataretrieval.ensembl.EnsemblFileRetriever.EnsemblDB;

@PowerMockIgnore({"javax.management.*","javax.net.ssl.*"})
@RunWith(PowerMockRunner.class)
@PrepareForTest({ java.net.URI.class,
				org.apache.commons.net.ftp.FTPClient.class,
				org.reactome.release.common.dataretrieval.FileRetriever.class,
				org.apache.http.impl.client.HttpClients.class })
public class TestEnsemblFileRetriever
{

	@Test
	public void testEnsemblFileRetriever() throws URISyntaxException
	{
		//Some logging/debugging for monitoring the connection to the server.
		System.setProperty("org.apache.commons.logging.Log","org.apache.commons.logging.impl.SimpleLog");
		System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.wire", "DEBUG");

		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.impl.conn", "DEBUG");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.impl.client", "DEBUG");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.client", "DEBUG");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http", "DEBUG");

		EnsemblFileRetriever retriever = new EnsemblFileRetriever();
		retriever.setDataURL(new URI("http://rest.ensembl.org/xrefs/id/"));
		String mapTo = EnsemblDB.EntrezGene.getEnsemblName();

		List<String> identifiers = Arrays.asList("ENSG00000175899", "ENSG00000175890", "ENSG00000166913");
		String species = "homo_sapiens";
		retriever.setFetchDestination("/tmp/test_ensembl_mapping_service/ensembl_mapped_to_"+mapTo+"_for_"+species+".xml");
		retriever.setSpecies(species);
		retriever.setMapToDb(mapTo);
		retriever.setIdentifiers(identifiers);
		retriever.downloadData();
	}
}
