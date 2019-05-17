package org.reactome.addlinks.dataretrieval.executor;

import java.util.List;
import java.util.Map;

import org.reactome.addlinks.dataretrieval.UniprotFileRetriever;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.uniprot.UniProtFileRetrieverExecutor;

public class UniprotFileRetrieverExecutor extends AbstractFileRetrieverExecutor
{

	private int numberOfUniprotDownloadThreads;
	private ReferenceObjectCache objectCache;
	
	public UniprotFileRetrieverExecutor(Map<String, UniprotFileRetriever> retrievers, List<String> retrieverFilter, int numThreads, ReferenceObjectCache cache)
	{
		super(retrievers, retrieverFilter);
		this.numberOfUniprotDownloadThreads = numThreads;
		this.objectCache = cache;
	}

	@Override
	public Boolean call() throws Exception
	{
		logger.info("Executing UniProt file retrievers");
		UniProtFileRetrieverExecutor executor = new UniProtFileRetrieverExecutor();
		executor.setFileRetrieverFilter(fileRetrieverFilter);
		executor.setObjectCache(objectCache);
		executor.setUniprotFileRetrievers((Map<String, UniprotFileRetriever>) this.fileRetrievers);
		executor.setNumberOfUniprotDownloadThreads(numberOfUniprotDownloadThreads);
		executor.execute();
		return true;
	}

}
