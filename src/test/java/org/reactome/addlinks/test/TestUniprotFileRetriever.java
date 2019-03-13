package org.reactome.addlinks.test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.reactome.addlinks.dataretrieval.UniprotFileRetreiver;


@PowerMockIgnore({"javax.management.*","javax.net.ssl.*", "javax.security.*"})
@RunWith(PowerMockRunner.class)
@PrepareForTest({ java.net.URI.class,
				org.apache.commons.net.ftp.FTPClient.class,
				org.reactome.addlinks.dataretrieval.FileRetriever.class,
				org.apache.http.impl.client.HttpClients.class })
public class TestUniprotFileRetriever
{

	@Test
	public void testUniProtFileRetrieverIT() throws URISyntaxException, IOException
	{	
		//Some logging/debugging for monitoring the connection to the server.
		System.setProperty("org.apache.commons.logging.Log","org.apache.commons.logging.impl.SimpleLog");
		System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.wire", "DEBUG");
		
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.impl.conn", "DEBUG");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.impl.client", "DEBUG");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.client", "DEBUG");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http", "DEBUG");
		
		UniprotFileRetreiver retriever = new UniprotFileRetreiver();
		retriever.setDataURL(new URI("https://www.uniprot.org/uploadlists/"));
		FileInputStream inStream = new FileInputStream(this.getClass().getClassLoader().getResource("uniprot_ids.txt").getFile());
		BufferedInputStream bis = new BufferedInputStream(inStream);
		retriever.setDataInputStream(bis);
		String mapFrom=UniprotFileRetreiver.UniprotDB.GeneName.getUniprotName();//"ACC+ID";
		String mapTo=UniprotFileRetreiver.UniprotDB.KEGG.getUniprotName();//"KEGG_ID";
		String pathToFile = "/tmp/uniprot_mapping_service/uniprot_xrefs_"+mapFrom+"_to_"+mapTo+".txt";
		retriever.setFetchDestination(pathToFile);
		retriever.setMaxAge(Duration.ofSeconds(1));
		retriever.setMapFromDb(mapFrom);
		retriever.setMapToDb(mapTo);
		retriever.downloadData();
		
		System.out.println("Contents of "+pathToFile);
		List<String> lines = Files.readAllLines(Paths.get(pathToFile));
		for (String line : lines)
		{
			System.out.println(line);
		}
		
		System.out.println("Contents of "+pathToFile.replace(".txt", ".notMapped.txt"));
		lines = Files.readAllLines(Paths.get(pathToFile.replace(".txt", ".notMapped.txt")));
		for (String line : lines)
		{
			System.out.println(line);
		}
	}
	
	@Test
	public void testUniProtFileRetrieverRetryingIT() throws URISyntaxException, IOException
	{	
		//Some logging/debugging for monitoring the connection to the server.
		System.setProperty("org.apache.commons.logging.Log","org.apache.commons.logging.impl.SimpleLog");
		System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.wire", "DEBUG");
		
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.impl.conn", "DEBUG");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.impl.client", "DEBUG");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.client", "DEBUG");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http", "DEBUG");
		
		UniprotFileRetreiver retriever = new UniprotFileRetreiver();
		retriever.setDataURL(new URI("https://www.uniprot.org/uploadlists/"));
		FileInputStream inStream = new FileInputStream(this.getClass().getClassLoader().getResource("uniprot_ids.txt").getFile());
		BufferedInputStream bis = new BufferedInputStream(inStream);
		retriever.setDataInputStream(bis);
		String mapFrom=UniprotFileRetreiver.UniprotDB.GeneName.getUniprotName();//"ACC+ID";
		String mapTo=UniprotFileRetreiver.UniprotDB.KEGG.getUniprotName();//"KEGG_ID";
		String pathToFile = "/tmp/uniprot_mapping_service/uniprot_xrefs_"+mapFrom+"_to_"+mapTo+".txt";
		retriever.setFetchDestination(pathToFile);
		retriever.setMaxAge(Duration.ofSeconds(1));
		retriever.setMapFromDb(mapFrom);
		retriever.setMapToDb(mapTo);
		
		// Mock the HttpEntity to return NULL the first time, to test retrying.
		// The second time, it will succeed.
		// When it comes to get the "not mapped" values, NULL will be returned again,
		// so we can test running out of retries.
		CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
		//HttpGet httpGet = mock(HttpGet.class);
		CloseableHttpResponse httpResponse = mock(CloseableHttpResponse.class);
		StatusLine statusLine = mock(StatusLine.class);
		HttpEntity mockEntity = mock(HttpEntity.class);

		when(statusLine.getStatusCode()).thenReturn(200);
		when(httpResponse.getStatusLine()).thenReturn(statusLine);
		when(mockEntity.getContent()).thenReturn(new ByteArrayInputStream("blah blah".getBytes(StandardCharsets.UTF_8))).thenReturn(null);
		when(httpResponse.getEntity()).thenReturn(mockEntity);
		when(httpClient.execute( any())).thenReturn(httpResponse).thenReturn(httpResponse);
		PowerMockito.mockStatic(HttpClients.class);
		when(HttpClients.createDefault()).thenCallRealMethod().thenReturn(httpClient);
		
		retriever.downloadData();
		
		System.out.println("Contents of "+pathToFile);
		List<String> lines = Files.readAllLines(Paths.get(pathToFile));
		for (String line : lines)
		{
			System.out.println(line);
		}
		
		System.out.println("Contents of "+pathToFile.replace(".txt", ".notMapped.txt"));
		lines = Files.readAllLines(Paths.get(pathToFile.replace(".txt", ".notMapped.txt")));
		for (String line : lines)
		{
			System.out.println(line);
		}
	}
}
