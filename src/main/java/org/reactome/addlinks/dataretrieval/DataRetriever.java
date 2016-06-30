package org.reactome.addlinks.dataretrieval;

import java.net.URI;
import java.time.Duration;



public interface DataRetriever {

	public void fetchData() throws Exception;
	public void setFetchDestination(String destination);
	public void setDataURL(URI uri);
	public void setMaxAge(Duration age);
}
