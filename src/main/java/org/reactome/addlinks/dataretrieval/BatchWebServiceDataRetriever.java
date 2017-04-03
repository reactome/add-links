package org.reactome.addlinks.dataretrieval;

import java.net.URI;
import java.util.List;
import java.util.function.Supplier;

/**
 * Makes a batch of webservice calls to an endpoint to get data.
 * @author sshorser
 *
 */
public class BatchWebServiceDataRetriever extends FileRetriever {

	private String name;
	private String dataUrl;
	private Supplier<List<String>> sourceForWSCalls;
	
	@Override
	public void fetchData() throws Exception  {
		String originalDest = this.destination;
		List<String> wsArgs = sourceForWSCalls.get();
		logger.debug("Number of ws calls to make: {}",wsArgs.size());
		//For each item in the batch, create a URL for it, get it, save it to file.
		for (String id : wsArgs)
		{
			String url = this.dataUrl.replaceAll("#+ID#+", String.valueOf(id));
			this.setDataURL( new URI(url));
			this.setFetchDestination ( originalDest+"/"+this.name+"_"+id );
			super.fetchData();
		}
	}	

	public void setDataURL(String dataUrl)
	{
		this.dataUrl = dataUrl;
	}
	
	public void setName(String name)
	{
		this.name = name;
	}
	
	public void setInputSupplier(Supplier<List<String>> s)
	{
		this.sourceForWSCalls = s;
	}
}
