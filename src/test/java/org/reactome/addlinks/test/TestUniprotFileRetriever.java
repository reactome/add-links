package org.reactome.addlinks.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.reactome.addlinks.dataretrieval.UniprotFileRetreiver;


@PowerMockIgnore({"javax.management.*","javax.net.ssl.*"})
@RunWith(PowerMockRunner.class)
@PrepareForTest({ java.net.URI.class,
				org.apache.commons.net.ftp.FTPClient.class,
				org.reactome.addlinks.dataretrieval.FileRetriever.class,
				org.apache.http.impl.client.HttpClients.class })
public class TestUniprotFileRetriever
{

	@Test
	public void testUniProtFileRetriever() throws URISyntaxException, IOException
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
		retriever.setDataURL(new URI("http://www.uniprot.org/uploadlists/"));
		FileInputStream inStream = new FileInputStream(new File("/home/sshorser/workspaces/reactome/new_add_links/AddLinks/uniprot_ids.txt"));
		retriever.setDataInputStream(inStream);
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
}
