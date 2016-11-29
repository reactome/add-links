package org.reactome.addlinks.test.referencecreators;

import static org.junit.Assert.*;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactome.addlinks.dataretrieval.UniprotFileRetreiver;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.fileprocessors.UniprotFileProcessor;
import org.reactome.addlinks.referencecreators.UPMappedIdentifiersReferenceCreator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

/**
 * This is an *integration* test of UniProt-mapped identifiers Reference Creator and also of UniProt file retrieval and processing.
 * @author sshorser
 *
 */
@ContextConfiguration("/test-application-context.xml")
@RunWith(org.springframework.test.context.junit4.SpringJUnit4ClassRunner.class)
public class TestUniProtMappedRefCreator
{
	@Autowired
	ReferenceObjectCache objectCache;
	
	// For Wormbase
	@Autowired
	UPMappedIdentifiersReferenceCreator upMappedWormbaseRefCreator;
	
	@Autowired
	UniprotFileRetreiver UniProtToWormbase;
	
	@Autowired
	UniprotFileProcessor UniprotToWormbaseFileProcessor;

	// For OMIM
	@Autowired
	UPMappedIdentifiersReferenceCreator upMappedOMIMRefCreator;
	
	@Autowired
	UniprotFileRetreiver UniProtToOMIM;
	
	@Autowired
	UniprotFileProcessor UniprotToOMIMFileProcessor;
	
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
	
	@Test
	public void testUPRefCreatorWormbase() throws Exception
	{

		// Need a list of identifiers.
		
		String refDb = "UniProt";
		String species = "Caenorhabditis elegans";
		String className = "ReferenceGeneProduct";
		String refDBID = objectCache.getRefDbNamesToIds().get(refDb).get(0);
		String speciesDBID = objectCache.getSpeciesNamesToIds().get(species).get(0);
		
		System.out.println(refDb + " " + refDBID + " ; " + species + " " + speciesDBID);
		StringBuilder identifiersList = new StringBuilder();
		objectCache.getByRefDbAndSpecies(refDBID, speciesDBID, className).stream().forEach(dbid -> {
			identifiersList.append( dbid + "\n" );
		});
		
		UniProtToWormbase.setFetchDestination(UniProtToWormbase.getFetchDestination().replace(".txt","." + speciesDBID + "." + refDBID + ".txt"));
		
		assertTrue(identifiersList.length()>0);
		String identifiers = identifiersList.toString().replace("UniProt:", "").replaceAll("\\[[a-zA-Z0-9\\:]*\\] ", "");
		System.out.print(identifiers);
		BufferedInputStream inStream = new BufferedInputStream(new ByteArrayInputStream(identifiers.getBytes()));
		UniProtToWormbase.setDataInputStream(inStream);
		
		
		UniProtToWormbase.fetchData();
		
		Map<String,Map<String,List<String>>> mappings = (Map<String, Map<String, List<String>>>) UniprotToWormbaseFileProcessor.getIdMappingsFromFile();
		assertTrue(mappings.keySet().size() > 0);
		
		upMappedWormbaseRefCreator.setTestMode(true);
		upMappedWormbaseRefCreator.createIdentifiers(123456, Paths.get(UniProtToWormbase.getFetchDestination()));
	}
	
	@Test
	public void testUPRefCreatorOMIM() throws Exception
	{

		// Need a list of identifiers.
		
		String refDb = "UniProt";
		String species = "Homo sapiens";
		String className = "ReferenceGeneProduct";
		String refDBID = objectCache.getRefDbNamesToIds().get(refDb).get(0);
		String speciesDBID = objectCache.getSpeciesNamesToIds().get(species).get(0);
		
		System.out.println(refDb + " " + refDBID + " ; " + species + " " + speciesDBID);
		StringBuilder identifiersList = new StringBuilder();
		objectCache.getByRefDbAndSpecies(refDBID, speciesDBID, className).stream().forEach(dbid -> {
			identifiersList.append( dbid + "\n" );
		});
		
		UniProtToOMIM.setFetchDestination(UniProtToOMIM.getFetchDestination().replace(".txt","." + speciesDBID + "." + refDBID + ".txt"));
		
		assertTrue(identifiersList.length()>0);
		String identifiers = identifiersList.toString().replace("UniProt:", "").replaceAll("\\[[a-zA-Z0-9\\:]*\\] ", "");
		System.out.print(identifiers);
		BufferedInputStream inStream = new BufferedInputStream(new ByteArrayInputStream(identifiers.getBytes()));
		UniProtToOMIM.setDataInputStream(inStream);
		
		
		UniProtToOMIM.fetchData();
		
		Map<String,Map<String,List<String>>> mappings = (Map<String, Map<String, List<String>>>) UniprotToOMIMFileProcessor.getIdMappingsFromFile();
		assertTrue(mappings.keySet().size() > 0);
		
		upMappedOMIMRefCreator.setTestMode(true);
		upMappedOMIMRefCreator.createIdentifiers(123456, Paths.get(UniProtToOMIM.getFetchDestination()));
	}
}
