package org.reactome.addlinks.test.referencecreators;

import static org.junit.Assert.*;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.gk.model.ReactomeJavaConstants;
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
	
	// For PDB
	@Autowired
	UPMappedIdentifiersReferenceCreator upMappedPDBRefCreator;
	
	@Autowired
	UniprotFileRetreiver UniProtToPDB;
	
	@Autowired
	UniprotFileProcessor UniprotToPDBFileProcessor;
	
	// For RefSeqPeptide
	@Autowired
	UPMappedIdentifiersReferenceCreator upMappedRefSeqPeptideRefCreator;
	
	@Autowired
	UniprotFileRetreiver UniProtToRefSeqPeptide;
	
	@Autowired
	UniprotFileProcessor UniprotToRefSeqPeptideFileProcessor;
	
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
		objectCache.getByRefDbAndSpecies(refDBID, speciesDBID, className).stream().forEach(instance -> {
			try
			{
				identifiersList.append( instance.getAttributeValue(ReactomeJavaConstants.identifier) + "\n" );
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		});
		
		UniProtToWormbase.setFetchDestination(UniProtToRefSeqPeptide.getFetchDestination().replace(".txt","." + speciesDBID + "." + refDBID + ".txt"));
		
		assertTrue(identifiersList.length()>0);
		String identifiers = identifiersList.toString();//.replace("UniProt:", "").replaceAll("\\[[a-zA-Z0-9\\:]*\\] ", "");
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
		objectCache.getByRefDbAndSpecies(refDBID, speciesDBID, className).stream().forEach(instance -> {
			try
			{
				identifiersList.append( instance.getAttributeValue(ReactomeJavaConstants.identifier) + "\n" );
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		});
		
		UniProtToOMIM.setFetchDestination(UniProtToRefSeqPeptide.getFetchDestination().replace(".txt","." + speciesDBID + "." + refDBID + ".txt"));
		
		assertTrue(identifiersList.length()>0);
		String identifiers = identifiersList.toString();//.replace("UniProt:", "").replaceAll("\\[[a-zA-Z0-9\\:]*\\] ", "");

		System.out.print(identifiers);
		BufferedInputStream inStream = new BufferedInputStream(new ByteArrayInputStream(identifiers.getBytes()));
		UniProtToOMIM.setDataInputStream(inStream);
		
		
		UniProtToOMIM.fetchData();
		
		Map<String,Map<String,List<String>>> mappings = (Map<String, Map<String, List<String>>>) UniprotToOMIMFileProcessor.getIdMappingsFromFile();
		assertTrue(mappings.keySet().size() > 0);
		
		upMappedOMIMRefCreator.setTestMode(true);
		upMappedOMIMRefCreator.createIdentifiers(123456, Paths.get(UniProtToOMIM.getFetchDestination()));
	}
	
	@Test
	public void testUPRefCreatorPDB() throws Exception
	{
		// Need a list of identifiers.
		String refDb = "UniProt";
		String species = "Canis familiaris";
		String className = "ReferenceGeneProduct";
		String refDBID = objectCache.getRefDbNamesToIds().get(refDb).get(0);
		String speciesDBID = objectCache.getSpeciesNamesToIds().get(species).get(0);
		
		System.out.println(refDb + " " + refDBID + " ; " + species + " " + speciesDBID);
		StringBuilder identifiersList = new StringBuilder();
		objectCache.getByRefDbAndSpecies(refDBID, speciesDBID, className).stream().forEach(instance -> {
			try
			{
				identifiersList.append( instance.getAttributeValue(ReactomeJavaConstants.identifier) + "\n" );
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		});
		
		UniProtToPDB.setFetchDestination(UniProtToRefSeqPeptide.getFetchDestination().replace(".txt","." + speciesDBID + "." + refDBID + ".txt"));
		
		assertTrue(identifiersList.length()>0);
		String identifiers = identifiersList.toString();//.replace("UniProt:", "").replaceAll("\\[[a-zA-Z0-9\\:]*\\] ", "");

		System.out.print(identifiers);
		BufferedInputStream inStream = new BufferedInputStream(new ByteArrayInputStream(identifiers.getBytes()));
		UniProtToPDB.setDataInputStream(inStream);
		
		UniProtToPDB.fetchData();
		
		Map<String,Map<String,List<String>>> mappings = (Map<String, Map<String, List<String>>>) UniprotToPDBFileProcessor.getIdMappingsFromFile();
		assertTrue(mappings.keySet().size() > 0);
		
		upMappedPDBRefCreator.setTestMode(true);
		upMappedPDBRefCreator.createIdentifiers(123456, Paths.get(UniProtToPDB.getFetchDestination()));
	}
	
	@Test
	public void testUPRefCreatorRefSeqPeptide() throws Exception
	{
		// Need a list of identifiers.
		String refDb = "UniProt";
		String species = "Homo sapiens";
		String className = "ReferenceGeneProduct";
		String refDBID = objectCache.getRefDbNamesToIds().get(refDb).get(0);
		String speciesDBID = objectCache.getSpeciesNamesToIds().get(species).get(0);
		
		System.out.println(refDb + " " + refDBID + " ; " + species + " " + speciesDBID);
		StringBuilder identifiersList = new StringBuilder();
		objectCache.getByRefDbAndSpecies(refDBID, speciesDBID, className).stream().forEach(instance -> {
			try
			{
				identifiersList.append( instance.getAttributeValue(ReactomeJavaConstants.identifier) + "\n" );
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		});
		
		UniProtToRefSeqPeptide.setFetchDestination(UniProtToRefSeqPeptide.getFetchDestination().replace(".txt","." + speciesDBID + "." + refDBID + ".txt"));
		
		assertTrue(identifiersList.length()>0);
		String identifiers = identifiersList.toString();//.replace("UniProt:", "").replaceAll("\\[[a-zA-Z0-9\\:]*\\] ", "");
		System.out.print(identifiers);
		BufferedInputStream inStream = new BufferedInputStream(new ByteArrayInputStream(identifiers.getBytes()));
		UniProtToRefSeqPeptide.setDataInputStream(inStream);
		
		UniProtToRefSeqPeptide.fetchData();
		
		Map<String,Map<String,List<String>>> mappings = (Map<String, Map<String, List<String>>>) UniprotToRefSeqPeptideFileProcessor.getIdMappingsFromFile();
		assertTrue(mappings.keySet().size() > 0);
		
		upMappedRefSeqPeptideRefCreator.setTestMode(true);
		upMappedRefSeqPeptideRefCreator.createIdentifiers(123456, Paths.get(UniProtToRefSeqPeptide.getFetchDestination()));
	}
}
