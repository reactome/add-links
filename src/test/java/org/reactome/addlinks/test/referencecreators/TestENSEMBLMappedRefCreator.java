package org.reactome.addlinks.test.referencecreators;

import static org.junit.Assert.assertTrue;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactome.addlinks.dataretrieval.ensembl.EnsemblFileRetriever;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.fileprocessors.ensembl.EnsemblFileAggregator;
import org.reactome.addlinks.fileprocessors.ensembl.EnsemblFileProcessor;
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
	ENSMappedIdentifiersReferenceCreator ensMappedEntrezGeneRefCreator;
	
	@Autowired
	EnsemblFileRetriever ENSEMBLToEntrezGene;
	
	@Autowired
	EnsemblFileProcessor ENSEMBLToEntrezGeneFileProcessor;

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
	public void testENSRefCreator() throws Exception
	{
		//String refDb = "ENSEMBL";
		String refDb = "ENSEMBL_Rattus norvegicus_PROTEIN";
		String species = "Rattus norvegicus";
		String className = "ReferenceGeneProduct";
		List<String> refDBID = objectCache.getRefDbNamesToIds().get(refDb);
		//String refDBID = "8925713";
		String speciesDBID = objectCache.getSpeciesNamesToIds().get(species).get(0);
		for (String ensemblDBID : refDBID)
		{
			List<String> identifiers = getIdentifiersList(ensemblDBID, species, className);
			System.out.println("# identifiers: " + identifiers.size());
			assertTrue(identifiers.size()>0);
			System.out.println(identifiers);
			ENSEMBLToEntrezGene.setFetchDestination(ENSEMBLToEntrezGene.getFetchDestination().replace(".xml","." + speciesDBID + ".xml").replace("]","").replace("[", ""));
			ENSEMBLToEntrezGene.setIdentifiers(identifiers);
			ENSEMBLToEntrezGene.setSpecies(species.toLowerCase().replace(" ", "_"));
			ENSEMBLToEntrezGene.fetchData();
			
			ENSEMBLToEntrezGeneFileProcessor.setPath(Paths.get(ENSEMBLToEntrezGene.getFetchDestination()));
			//@SuppressWarnings("unchecked")
			Map<String,Map<String,List<String>>> mappings = (Map<String,Map<String,List<String>>>) ENSEMBLToEntrezGeneFileProcessor.getIdMappingsFromFile();
			assertTrue(mappings.keySet().size() > 0);
			ensMappedEntrezGeneRefCreator.setTestMode(true);


			List<GKInstance> sourceReferences = objectCache.getByRefDb(refDBID.get(0), ReactomeJavaConstants.ReferenceGeneProduct);
			ensMappedEntrezGeneRefCreator.createIdentifiers(123456, mappings, sourceReferences );
		}
	}
}
