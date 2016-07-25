package org.reactome.addlinks.test;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;

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
	public void testUniProtFileRetriever() throws URISyntaxException
	{	
		System.setProperty("org.apache.commons.logging.Log","org.apache.commons.logging.impl.SimpleLog");
		System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.wire", "DEBUG");
		
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.impl.conn", "DEBUG");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.impl.client", "DEBUG");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.client", "DEBUG");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http", "DEBUG");
		
		UniprotFileRetreiver retriever = new UniprotFileRetreiver();
		retriever.setDataURL(new URI("http://www.uniprot.org/uploadlists/"));
		retriever.setFetchDestination("/tmp/uniprot_xrefs.txt");
		retriever.setMaxAge(Duration.ofSeconds(1));
		
		retriever.downloadData();
	}
}
