package org.reactome.addlinks.ensembl;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.MySQLAdaptor.AttributeQueryRequest;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.InvalidClassException;
import org.reactome.addlinks.CustomLoggable;
import org.reactome.addlinks.dataretrieval.ensembl.EnsemblBatchLookup;
import org.reactome.addlinks.dataretrieval.ensembl.EnsemblFileRetriever;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.fileprocessors.ensembl.EnsemblBatchLookupFileProcessor;

public class EnsemblFileRetrieverExecutor implements CustomLoggable
{
	private Logger logger;
	
	private EnsemblBatchLookup ensemblBatchLookup;
	private Map<String, EnsemblFileRetriever> ensemblFileRetrievers;
	private Map<String, EnsemblFileRetriever> ensemblFileRetrieversNonCore;
	private ReferenceObjectCache objectCache;

	private MySQLAdaptor dbAdapter;
	
	public EnsemblFileRetrieverExecutor()
	{
		this.logger = this.createLogger("EnsemblFileRetrieverExecutor", "RollingRandomAccessFile", this.getClass().getName(), true, Level.DEBUG);
	}
	
	public void execute() throws Exception
	{
		// Getting cross-references from ENSEMBL requires first getting doing a batch mapping from ENSP to ENST, then batch mapping ENST to ENSG.
		// Then, individual xref lookups on ENSG.
		// To do the batch lookups, you will need the species, and also the species-specific ENSEMBL db_id.
		// To do the xref lookup, you will need the target database.
		
		// Ok, here's what to do: 
		// 1) Find all ReferenceDatabase objects whose name is LIKE 'ENSEMBL_%_PROTEIN' and accessUrl is LIKE '%www.ensembl.org%'
		//    (this will capture "core" databases. We'll handle the ones that are for other ENSEMBL databases, such as "plants.ensembl.org" and "fungi.ensembl.org" separately)
		// 2) do batch lookups on everything in the database for that Ensembl ReferenceDatabase.
		// 3) do batch lookups to get ENSG ENSEMBL IDs, where necessary.
		// 4) Use list of ensemblFileRetrievers to do xref lookups.
		// Consider refactoring all of this into a separate class.
		
		String dbName = "ENSEMBL_%_PROTEIN";
		logger.debug("Trying to find database with name {}", dbName);
		String url = "%www.ensembl.org%";
		Set<GKInstance> databases = getRefDatabaseObjects(dbName, url, " LIKE ");
		if (databases.size() > 0)
		{
			logger.debug("Database {} exists ({} matches), now trying to find entities that reference it.", dbName, databases.size());

			List<GKInstance> refGeneProducts = getRefGeneProds(databases);
			
			// generate list of ENSP identifiers. This code would look prettier if getAttributeValue didn't throw Exception ;)
			Map<String, List<String>> refGeneProdsBySpecies = getRefGeneProdsBySpecies(refGeneProducts);
			
			// now, do batch look-ups by species. This will perform Protein-to-Transcript mappings.
			String baseFetchDestination = ensemblBatchLookup.getFetchDestination();
			for (String species : refGeneProdsBySpecies.keySet())
			{
				String speciesName = objectCache.getSpeciesNamesByID().get(species).get(0).replaceAll(" ", "_");
				
				ensemblBatchLookup.setFetchDestination(baseFetchDestination+"ENSP_batch_lookup."+species+".xml");
				ensemblBatchLookup.setSpecies(speciesName);
				ensemblBatchLookup.setIdentifiers(refGeneProdsBySpecies.get(species));
				ensemblBatchLookup.fetchData();
				
				EnsemblBatchLookupFileProcessor enspProcessor = new EnsemblBatchLookupFileProcessor("file-processors/EnsemblBatchLookupFileProcessor");
				enspProcessor.setPath(Paths.get(baseFetchDestination+"ENSP_batch_lookup."+species+".xml"));
				Map<String, String> enspToEnstMap = enspProcessor.getIdMappingsFromFile();
				
				if (!enspToEnstMap.isEmpty())
				{
					ensemblBatchLookup.setFetchDestination(baseFetchDestination+"ENST_batch_lookup."+species+".xml");
					ensemblBatchLookup.setSpecies(speciesName);
					ensemblBatchLookup.setIdentifiers(new ArrayList<String>(enspToEnstMap.values()));
					ensemblBatchLookup.fetchData();
					
					enspProcessor.setPath(Paths.get(baseFetchDestination+"ENST_batch_lookup."+species+".xml"));
					Map<String, String> enstToEnsgMap = enspProcessor.getIdMappingsFromFile();

					if (!enstToEnsgMap.isEmpty())
					{
						List<String> identifiers = new ArrayList<String>(enstToEnsgMap.values());
						// Ok, now we have RefGeneProd for ENSEMBL_%_PROTEIN. Now we can map these identifiers to some external database.
						executeEnsemblFileRetrievers(ensemblFileRetrievers, species, speciesName, identifiers);
					}
					else
					{
						logger.debug("ENST to ENSG mapping is empty. No identifiers to do xref lookup for species {}/{}", species, speciesName);
					}
				}
				else
				{
					logger.debug("ENSP to ENST mapping returned no results for species {}/{}", species, speciesName);
				}
			}
		}
		else
		{
			logger.debug("Could not find a database with name = {} and url like {}", dbName, url);
		}
		
		url = "%www.ensembl.org%";
		databases = getRefDatabaseObjects(dbName, url, " NOT LIKE ");
		logger.debug("{} databases found for non-core ENSEMBL databases.", databases.size());
		if (databases.size() > 0)
		{
			// These don't need multiple steps - rest.ensemblgenomes.org can translate them immediately.
			List<GKInstance> refGeneProducts = getRefGeneProds(databases);
			
			Map<String, List<String>> refGeneProdsBySpecies = getRefGeneProdsBySpecies(refGeneProducts);
			for (String species : refGeneProdsBySpecies.keySet())
			{
				String speciesName = objectCache.getSpeciesNamesByID().get(species).get(0).replaceAll(" ", "_");
				executeEnsemblFileRetrievers(ensemblFileRetrieversNonCore, species, speciesName, refGeneProdsBySpecies.get(species));
			}
		}
		else
		{
			logger.debug("Could not find a database with name = {} and url NOT LIKE {}", dbName, url);
		}
	}

	private void executeEnsemblFileRetrievers(Map<String, EnsemblFileRetriever> retrievers, String species, String speciesName, List<String> identifiers)
	{
		List<Callable<Boolean>> jobs = Collections.synchronizedList(new ArrayList<Callable<Boolean>>());
		
		retrievers.keySet().parallelStream().forEach( ensemblRetrieverName ->
		{
			Callable<Boolean> job = new Callable<Boolean>()
			{
				@Override
				public Boolean call() throws Exception
				{
					logger.info("Executing file retriever: {}; for species {}; for {} identifiers",ensemblRetrieverName, speciesName, identifiers.size());
					EnsemblFileRetriever retriever = retrievers.get(ensemblRetrieverName);
					retriever.setFetchDestination(retriever.getFetchDestination().replaceAll("(\\.*[0-9])*\\.xml", "." + species + ".xml"));
					retriever.setSpecies(speciesName);
					retriever.setIdentifiers(identifiers);
					try
					{
						retriever.fetchData();
						return true;
					}
					catch (Exception e)
					{
						e.printStackTrace();
						return false;
					}
				}
			};
			jobs.add(job);
		});

		logger.info("{} EnsemblFileRetrievers to execute.", jobs.size());
		// ENSEMBL doesn't like > 15 requests per second, so let's keep our pool smaller than that.
		// Don't forget: each EnsemblFileRetriever will execute *n* threads as well, each as big as
		// stream().parallel() will allow, so we should only try to run 2 retrievers at at time.
		ForkJoinPool pool = new ForkJoinPool(2);
		if (pool != null && jobs.size() > 0)
		{
			pool.invokeAll(jobs);
		}

	}

	private Map<String, List<String>> getRefGeneProdsBySpecies(List<GKInstance> refGeneProducts)
	{
		Map<String, List<String>> refGeneProdsBySpecies = new HashMap<String, List<String>>();
		refGeneProducts.stream().forEach(instance -> {
			try
			{
				String species = String.valueOf(((GKInstance)instance.getAttributeValue(ReactomeJavaConstants.species)).getDBID());
				if (refGeneProdsBySpecies.get(species) == null)
				{
					refGeneProdsBySpecies.put(species, new ArrayList<String>( Arrays.asList((String)instance.getAttributeValue(ReactomeJavaConstants.identifier) ) ) );
				}
				else
				{
					refGeneProdsBySpecies.get(species).add((String)instance.getAttributeValue(ReactomeJavaConstants.identifier));
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			
		});
		return refGeneProdsBySpecies;
	}

	@SuppressWarnings("unchecked")
	private List<GKInstance> getRefGeneProds(Set<GKInstance> databases) throws InvalidAttributeException, Exception
	{
		List<GKInstance> refGeneProducts = new ArrayList<GKInstance>();
		for (GKInstance database : databases)
		{
			// Filter out anything whose name is simply "ENSEMBL". We want the ENSEMBL_* databases
			for (String name : ((List<String>) database.getAttributeValuesList(ReactomeJavaConstants.name)).stream()
								.filter(n -> !n.toUpperCase().equals("ENSEMBL")).collect(Collectors.toList()) )
			{
				logger.debug("Trying {}", name);
				// Get ReferenceGeneProducts from the cache.
				List<GKInstance> results = objectCache.getByRefDb(String.valueOf(database.getDBID()) , "ReferenceGeneProduct");
				refGeneProducts.addAll(results);
				logger.debug("{} results found in cache", results.size());
				
			}
		}
		logger.debug("{} ReferenceGeneProducts found", refGeneProducts.size());
		return refGeneProducts;
	}

	private Set<GKInstance> getRefDatabaseObjects(String dbName, String url, String operator)
			throws InvalidClassException, InvalidAttributeException, Exception
	{
		List<AttributeQueryRequest> aqrList = new ArrayList<AttributeQueryRequest>();
		AttributeQueryRequest dbNameARQ = dbAdapter.new AttributeQueryRequest("ReferenceDatabase", "name", " LIKE ", dbName);
		AttributeQueryRequest accessUrlARQ = dbAdapter.new AttributeQueryRequest("ReferenceDatabase", "url", operator, url);
		aqrList.add(dbNameARQ);
		aqrList.add(accessUrlARQ);
		@SuppressWarnings("unchecked")
		Set<GKInstance> databases = (Set<GKInstance>) dbAdapter._fetchInstance(aqrList);
		return databases;
	}

	public void setEnsemblBatchLookup(EnsemblBatchLookup ensemblBatchLookup2)
	{
		this.ensemblBatchLookup = ensemblBatchLookup2;
	}

	public void setEnsemblFileRetrievers(Map<String, EnsemblFileRetriever> ensemblFileRetrievers)
	{
		this.ensemblFileRetrievers = ensemblFileRetrievers;
	}

	public void setEnsemblFileRetrieversNonCore(Map<String, EnsemblFileRetriever> ensemblFileRetrieversNonCore)
	{
		this.ensemblFileRetrieversNonCore = ensemblFileRetrieversNonCore;
	}

	public void setObjectCache(ReferenceObjectCache objectCache)
	{
		this.objectCache = objectCache;
	}

	public void setDbAdapter(MySQLAdaptor dbAdapter)
	{
		this.dbAdapter = dbAdapter;
	}

}
