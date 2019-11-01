package org.reactome.addlinks.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicStatusLine;
import org.gk.persistence.MySQLAdaptor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.reactome.addlinks.dataretrieval.KEGGFileRetriever;

@PrepareForTest({HttpClients.class})
@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"javax.management.*"})
public class TestKEGGRetrieverErrIT
{
	
	KEGGFileRetriever KEGGRetriever = new KEGGFileRetriever();
	
	/**
	 * We will test getting *only* 403 errors from KEGG
	 * @throws Exception
	 */
	@Test
	public void testKEGGFileRetrievalErr() throws Exception
	{
		final String pathToKeggFiles = "/tmp/addlinks-downloaded-files/kegg_entries/";
		final String pathToUniProtFiles = "/tmp/addlinks-downloaded-files/uniprot-mappings/";
		final String speciesDBID = "48887";
		
		// Set up the mock-objects to fake the 403 responses.
		CloseableHttpResponse mockGetResponse = Mockito.mock(CloseableHttpResponse.class);
		StatusLine fourOhThreeStatus = new BasicStatusLine(new ProtocolVersion("HTTP",2,0), 403, "TESTING!");
		Mockito.when(mockGetResponse.getStatusLine()).thenReturn(fourOhThreeStatus);
		
		CloseableHttpClient mockGetClient = Mockito.mock(CloseableHttpClient.class);
		PowerMockito.mockStatic(HttpClients.class);
		Mockito.when(HttpClients.createDefault()).thenReturn(mockGetClient);
		Mockito.when(mockGetClient.execute( org.mockito.ArgumentMatchers.any() )).thenReturn(mockGetResponse);
		
		KEGGRetriever.setAdapter(null); // don't need a real database connection for this, just a dummy object.
		KEGGRetriever.setFetchDestination(pathToKeggFiles + "kegg_entries."+speciesDBID+".txt");
		KEGGRetriever.setDataURL(new URI("http://rest.kegg.jp/get/"));
		List<Path> uniprotToKEGGFiles = new ArrayList<Path>();
		uniprotToKEGGFiles.add(Paths.get(pathToUniProtFiles + "uniprot_mapping_Uniprot_To_KEGG."+speciesDBID+".2.txt"));
		KEGGRetriever.setUniprotToKEGGFiles(uniprotToKEGGFiles);
		KEGGRetriever.setMaxAge(Duration.ofSeconds(1));
		KEGGRetriever.setNumRetries(2);
		KEGGRetriever.fetchData();
		
		// The file should exist...
		assertTrue(Files.exists(Paths.get(pathToKeggFiles + "kegg_entries."+speciesDBID+".txt")));
		// AND it should be EMPTY!
		assertEquals(0, Files.size(Paths.get(pathToKeggFiles + "kegg_entries."+speciesDBID+".txt")));
	}
}
