package org.reactome.addlinks.dataretrieval.executor;

import java.util.List;
import java.util.Map;

import org.reactome.release.common.dataretrieval.FileRetriever;

public class SimpleFileRetrieverExecutor extends AbstractFileRetrieverExecutor
{
	public SimpleFileRetrieverExecutor(Map<String, FileRetriever> retrievers, List<String> retrieverFilter)
	{
		super(retrievers, retrieverFilter);
	}

	@Override
	public Boolean call() throws Exception
	{
		fileRetrievers.keySet().stream()
			.parallel()
			.filter(k -> !k.equals("KEGGRetriever"))
			.forEach(k ->
			{
				// KEGGRetreiver is special: it depends on the result of the uniprotToKegg retriever as an input, so we can't execute it here.
				if (fileRetrieverFilter.contains(k))
				{
					FileRetriever retriever = fileRetrievers.get(k);
					logger.info("Executing downloader: {}",k);
					try
					{
						retriever.fetchData();
						logger.info("Completed downloader: {}",k);
					}
					catch (Exception e)
					{

						//TODO: The decision to continue after a failure should be a configurable option.
						logger.warn("Exception caught while processing {}, message is: \"{}\". Will continue with next file retriever.",k,e.getMessage());
						e.printStackTrace();
					}
				}
				else
				{
					logger.info("Skipping {}",k);
				}
			});
		return true;
	}

}
