package org.reactome.addlinks.brenda;

import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.rpc.ServiceException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.persistence.MySQLAdaptor;
import org.reactome.addlinks.dataretrieval.brenda.BRENDASoapClient;
import org.reactome.addlinks.db.ReferenceDatabaseCreator;
import org.reactome.addlinks.db.ReferenceObjectCache;

public final class BRENDASpeciesCache
{
	private static final Logger logger = LogManager.getLogger();
	private static List<String> brendaSpeciesCache;
	
	private BRENDASpeciesCache()
	{
		//private constructor to prevent instantiation.
	}
	
	/**
	 * Gets a list of species names that are known to both BRENDA and Reactome.
	 * @return
	 */
	public static List<String> getCache()
	{
		return BRENDASpeciesCache.brendaSpeciesCache;
	}
	
	/**
	 * Build a cache of species names.
	 * @param client - BRENDASoapClient object to connect to the BRENDA web service.
	 * @param speciesURL - The URL to query.
	 * @param objectCache
	 * @param dbAdapter
	 */
	public static void buildCache(BRENDASoapClient client, String speciesURL, ReferenceObjectCache objectCache, MySQLAdaptor dbAdapter)
	{
		BRENDASpeciesCache.brendaSpeciesCache = new ArrayList<String>();
		String speciesResult;
		try
		{
			speciesResult = client.callBrendaService(speciesURL, "getOrganismsFromOrganism", "");
			logger.info("size of result (# characters): {}", speciesResult.length());
		}
		catch (MalformedURLException | NoSuchAlgorithmException | RemoteException |ServiceException e)
		{
			logger.error("Exception caught while trying to get BRENDA species list: {}",e.getMessage());
			e.printStackTrace();
			throw new Error(e);
		}
		catch (Exception e)
		{
			logger.error("Exception caught while trying to get BRENDA species list: {}",e.getMessage());
			e.printStackTrace();
			throw new Error(e);
		}
		//Normalize the list.
		List<String> brendaSpecies = Arrays.asList(speciesResult.split("!")).stream().map(species -> species.replace("'", "").replaceAll("\"", "").trim().toUpperCase() ).collect(Collectors.toList());
		logger.info("{} species known to BRENDA, {} species names in cache from database", brendaSpecies.size(), objectCache.getListOfSpeciesNames().size());

		ReferenceDatabaseCreator refDBcreator = new ReferenceDatabaseCreator(dbAdapter);
		BRENDAReferenceDatabaseGenerator.setDBCreator(refDBcreator);
		for (String speciesName : objectCache.getListOfSpeciesNames().stream().sorted().collect(Collectors.toList() ) )
		{
			if (brendaSpecies.contains(speciesName.trim().toUpperCase()))
			{
				brendaSpeciesCache.add(speciesName);
				// Create the BRENDA ReferenceDatabase now that we know we've got a legit species name we can use.
				BRENDAReferenceDatabaseGenerator.createReferenceDatabase(speciesName);
			}
		}
	}
}
