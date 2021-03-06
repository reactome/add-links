package org.reactome.addlinks.dataretrieval.executor;

import java.util.List;

import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reactome.release.common.dataretrieval.FileRetriever;

/**
 * An abstract class to contain common functionality of various File Retriever executors.
 * @author sshorser
 *
 */
public abstract class AbstractFileRetrieverExecutor implements Callable<Boolean>
{
	protected static final Logger logger = LogManager.getLogger();
	protected Map<String,? extends FileRetriever> fileRetrievers;
	protected List<String> fileRetrieverFilter;

	public AbstractFileRetrieverExecutor(Map<String,? extends FileRetriever> retrievers, List<String> retrieverFilter)
	{
		this.fileRetrievers = retrievers;
		this.fileRetrieverFilter = retrieverFilter;
	}
}
