/**
 * 
 */
package org.reactome.addlinks.test;

import static org.junit.Assert.*;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Random;

import org.junit.Test;
import org.reactome.addlinks.dataretrieval.DataRetriever;
import org.reactome.addlinks.dataretrieval.FileRetriever;

/**
 * @author sshorser
 *
 */
public class TestFileRetriever {

	/**
	 * Test method for {@link org.reactome.addlinks.dataretrieval.FileRetriever#fetchData()}.
	 * @throws Exception 
	 */
	@Test
	public void testFetchData() throws Exception {
		DataRetriever retriever = new FileRetriever();
		//retrieve google - it should be pretty easy.
		URI uri = new URI("http://www.google.com");
		retriever.setDataURL(uri);
		String dest = "/tmp/testFetchData_"+ String.valueOf((new Random()).nextInt());
		retriever.setFetchDestination(dest);
		Duration age = Duration.of(1,ChronoUnit.SECONDS);
		retriever.setMaxAge(age);
		
		retriever.fetchData();
		assertTrue(Files.exists(Paths.get(dest)));
		
		//Sleep for two seconds, and then re-download because the file is stale
		Thread.sleep(Duration.of(2,ChronoUnit.SECONDS).toMillis());
		retriever.fetchData();
		assertTrue(Files.exists(Paths.get(dest)));
		//now set a longer maxAge.
		age = Duration.of(100,ChronoUnit.SECONDS);
		retriever.setMaxAge(age);
		// this time, the file will not be stale (because maxAge is larger) so nothing will be downloaded.
		retriever.fetchData();
		//check that the file exists.
		assertTrue(Files.exists(Paths.get(dest)));
	}

}
