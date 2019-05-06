package org.reactome.addlinks.dataretrieval.executor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.schema.InvalidAttributeException;
import org.reactome.addlinks.brenda.BRENDASpeciesCache;
import org.reactome.addlinks.dataretrieval.FileRetriever;
import org.reactome.addlinks.dataretrieval.brenda.BRENDAFileRetriever;
import org.reactome.addlinks.db.ReferenceObjectCache;

public class BrendaFileRetrieverExecutor extends AbstractFileRetrieverExecutor
{
	private static final String RETRIEVER_NAME = "BrendaRetriever";
	private ReferenceObjectCache objectCache;
	
	public BrendaFileRetrieverExecutor(Map<String, ? extends FileRetriever> retrievers, List<String> retrieverFilter, ReferenceObjectCache cache)
	{
		super(retrievers, retrieverFilter);
		this.objectCache = cache;
	}

	@Override
	public Boolean call() throws Exception
	{
		BRENDAFileRetriever brendaRetriever = (BRENDAFileRetriever) this.fileRetrievers.get(RETRIEVER_NAME);

		List<String> identifiers = new ArrayList<String>();
		String originalDestination = brendaRetriever.getFetchDestination();

		if (this.fileRetrieverFilter.contains(RETRIEVER_NAME))
		{
			logger.info("Executing BRENDA file retrievers");
			
			// Loop through the species.
			for (String speciesName : objectCache.getSetOfSpeciesNames().stream().sorted().collect(Collectors.toList() ) )
			{
				String speciesId = objectCache.getSpeciesNamesToIds().get(speciesName).get(0);
				// Check if the species is in the cache of species known to BRENDA.
				if (BRENDASpeciesCache.getCache().contains(speciesName.trim()))
				{
					// A function that maps a GKInstance to its identifier (or NULL if it does not have one).
					Function<GKInstance, ? extends String> mapper = instance -> {
						try
						{
							return (String)instance.getAttributeValue(ReactomeJavaConstants.identifier);
						}
						catch (InvalidAttributeException e)
						{
							logger.error(e);
							e.printStackTrace();
						}
						catch (Exception e)
						{
							logger.error(e);
							e.printStackTrace();
						}
						return null;
					};
					// Get the uniprot identifiers for the current species.
					List<String> uniprotIdentifiers = objectCache.getByRefDbAndSpecies("2", speciesId, ReactomeJavaConstants.ReferenceGeneProduct).stream().map(mapper).collect(Collectors.toList());
					
					logger.info("Processing for Brenda: Species: "+speciesId+"/"+speciesName);
					identifiers.addAll(uniprotIdentifiers);
					
					if (uniprotIdentifiers != null && uniprotIdentifiers.size() > 0)
					{
						// If there are identifiers to operate on, run the file retriever.
						brendaRetriever.setSpeciesName(speciesName);
						brendaRetriever.setIdentifiers(uniprotIdentifiers);
						brendaRetriever.setFetchDestination(originalDestination.replace(".csv","."+speciesName.replace(" ", "_")+".csv"));
						try
						{
							brendaRetriever.fetchData();
						} catch (Exception e)
						{
							logger.error("Error occurred while trying to fetch Brenda data: {}. File may not have been downloaded.", e.getMessage());
							e.printStackTrace();
						}
					}
					else
					{
						logger.debug("No uniprot identifiers for " + speciesName);
					}
				}
				else
				{
					logger.debug("Species " + speciesName + " is not in the list of species known to BRENDA.");
				}
			}
		}
		else
		{
			logger.info("Skipping BrendaRetriever");
		}
		return true;
	}

}
