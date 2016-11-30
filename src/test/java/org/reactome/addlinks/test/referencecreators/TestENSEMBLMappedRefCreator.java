package org.reactome.addlinks.test.referencecreators;

import static org.junit.Assert.assertTrue;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.gk.model.ReactomeJavaConstants;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactome.addlinks.dataretrieval.EnsemblFileRetriever;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.fileprocessors.EnsemblFileProcessor;
import org.reactome.addlinks.referencecreators.ENSMappedIdentifiersReferenceCreator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration("/test-ensembl-mapped-identifiers.xml")
@RunWith(org.springframework.test.context.junit4.SpringJUnit4ClassRunner.class)
public class TestENSEMBLMappedRefCreator
{
	@Autowired
	ReferenceObjectCache objectCache;
	
	// For Wormbase
	@Autowired
	ENSMappedIdentifiersReferenceCreator ensMappedWormbaseRefCreator;
	
	@Autowired
	EnsemblFileRetriever ENSEMBLToWormbase;
	
	@Autowired
	EnsemblFileProcessor ENSEMBLToWormbaseFileProcessor;

	@BeforeClass
	public static void setup()
	{
		System.setProperty("org.apache.commons.logging.Log","org.apache.commons.logging.impl.SimpleLog");
		System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.wire", "DEBUG");
		
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.impl.conn", "DEBUG");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.impl.client", "DEBUG");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.client", "DEBUG");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http", "DEBUG");
	}
	
	private List<String> getIdentifiersList(String refDbId, String species, String className)
	{
		// Need a list of identifiers.
		//String refDBID = objectCache.getRefDbNamesToIds().get(refDbId).get(0);
		String speciesDBID = objectCache.getSpeciesNamesToIds().get(species).get(0);
		System.out.println(refDbId + " " + refDbId + " ; " + species + " " + speciesDBID);
		List<String> identifiersList = new ArrayList<String>();
		objectCache.getByRefDbAndSpecies(refDbId, speciesDBID, className).stream().forEach(instance -> {
			try
			{
				identifiersList.add( (String)instance.getAttributeValue(ReactomeJavaConstants.identifier)  );
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		});
		//String identifiers = identifiersList.toString();
		return identifiersList;
	}
	
	@Test
	public void testENSRefCreatorWormbase() throws Exception
	{
		//String refDb = "ENSEMBL";
		String refDb = "ENSEMBL_Caenorhabditis elegans_PROTEIN";
		String species = "Caenorhabditis elegans";
		String className = "ReferenceGeneProduct";
		List<String> refDBID = objectCache.getRefDbNamesToIds().get(refDb);
		//String refDBID = "8925713";
		String speciesDBID = objectCache.getSpeciesNamesToIds().get(species).get(0);
		for (String ensemblDBID : refDBID)
		{
			// The DB ID for worms in ENSEMBL ("ENSEMBL_Caenorhabditis elegans_GENE")
			if (ensemblDBID.equals("8925712"))
			{
				List<String> identifiers = getIdentifiersList(ensemblDBID, species, className);
				assertTrue(identifiers.size()>0);
				System.out.println(identifiers);
				ENSEMBLToWormbase.setFetchDestination(ENSEMBLToWormbase.getFetchDestination().replace(".txt","." + speciesDBID + "." + refDBID + ".txt"));
				ENSEMBLToWormbase.setIdentifiers(identifiers);
				ENSEMBLToWormbase.setSpecies(species.toLowerCase().replace(" ", "_"));
				ENSEMBLToWormbase.fetchData();
				@SuppressWarnings("unchecked")
				Map<String,Map<String,List<String>>> mappings = (Map<String, Map<String, List<String>>>) ENSEMBLToWormbaseFileProcessor.getIdMappingsFromFile();
				assertTrue(mappings.keySet().size() > 0);
				ensMappedWormbaseRefCreator.setTestMode(true);
				ensMappedWormbaseRefCreator.createIdentifiers(123456, Paths.get(ENSEMBLToWormbase.getFetchDestination()));
			}
		}
	}
}
