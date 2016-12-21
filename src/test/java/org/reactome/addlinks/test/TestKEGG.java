package org.reactome.addlinks.test;

import static org.junit.Assert.*;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactome.addlinks.dataretrieval.KEGGFileRetriever;
import org.reactome.addlinks.dataretrieval.UniprotFileRetreiver;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.fileprocessors.KEGGFileProcessor;
import org.reactome.addlinks.fileprocessors.UniprotFileProcessor;
import org.reactome.addlinks.fileprocessors.KEGGFileProcessor.KEGGKeys;
import org.reactome.addlinks.referencecreators.KEGGReferenceCreator;
import org.reactome.addlinks.referencecreators.UPMappedIdentifiersReferenceCreator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

/**
 * *INTEGRATION* tests for KEGG code.
 * @author sshorser
 *
 */
@ContextConfiguration("/test-application-context.xml")
@RunWith(org.springframework.test.context.junit4.SpringJUnit4ClassRunner.class)
public class TestKEGG
{
	@Autowired
	ReferenceObjectCache objectCache;

	// For KEGG
	@Autowired
	UPMappedIdentifiersReferenceCreator upMappedKEGGRefCreator;
	
	@Autowired
	UniprotFileRetreiver UniProtToKEGG;
	
	@Autowired
	UniprotFileProcessor UniprotToKEGGFileProcessor;
	
	@Autowired
	MySQLAdaptor dbAdapter;
	
	KEGGFileRetriever KEGGRetriever = new KEGGFileRetriever();
	
	private String getIdentifiersList(String refDb, String species, String className)
	{
		// Need a list of identifiers.
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
		String identifiers = identifiersList.toString();
		return identifiers;
	}
	
	@Test
	public void testKEGGFileRetriever() throws Exception
	{
		String refDb = "UniProt";
		String species = "Xenopus laevis";
		String className = "ReferenceGeneProduct";
		String refDBID = objectCache.getRefDbNamesToIds().get(refDb).get(0);
		String speciesDBID = objectCache.getSpeciesNamesToIds().get(species).get(0);
		String identifiers = getIdentifiersList(refDb, species, className);
		assertTrue(identifiers.length()>0);
		System.out.print(identifiers);
		UniProtToKEGG.setFetchDestination(UniProtToKEGG.getFetchDestination().replace(".txt","." + speciesDBID + "." + refDBID + ".txt"));
		BufferedInputStream inStream = new BufferedInputStream(new ByteArrayInputStream(identifiers.getBytes()));
		UniProtToKEGG.setDataInputStream(inStream);
		UniProtToKEGG.fetchData();
		
		//Now that the Uniprot-to-KEGG lookup is done we have to query against KEGG to get the Entries list.
		KEGGRetriever.setAdapter(dbAdapter);
		KEGGRetriever.setFetchDestination("/tmp/addlinks-downloaded-files/kegg_entries/kegg_entries."+speciesDBID+".txt");
		KEGGRetriever.setDataURL(new URI("http://rest.kegg.jp/get/"));
		List<Path> uniprotToKEGGFiles = new ArrayList<Path>();
		uniprotToKEGGFiles.add(Paths.get(UniProtToKEGG.getFetchDestination()));
		KEGGRetriever.setUniprotToKEGGFiles(uniprotToKEGGFiles);
		KEGGRetriever.setMaxAge(Duration.ofSeconds(1));		
		KEGGRetriever.fetchData();
		assertTrue(Files.exists(Paths.get("/tmp/addlinks-downloaded-files/kegg_entries/kegg_entries."+speciesDBID+".txt")));
		assertTrue(Files.size(Paths.get("/tmp/addlinks-downloaded-files/kegg_entries/kegg_entries."+speciesDBID+".txt")) > 0);
	}

	@Test
	public void testKEGGFileProcessor() throws Exception
	{
		String refDb = "UniProt";
		String species = "Xenopus laevis";
		String className = "ReferenceGeneProduct";
		String refDBID = objectCache.getRefDbNamesToIds().get(refDb).get(0);
		String speciesDBID = objectCache.getSpeciesNamesToIds().get(species).get(0);
		String identifiers = getIdentifiersList(refDb, species, className);
		assertTrue(identifiers.length()>0);
		System.out.print(identifiers);
		UniProtToKEGG.setFetchDestination(UniProtToKEGG.getFetchDestination().replace(".txt","." + speciesDBID + "." + refDBID + ".txt"));
		BufferedInputStream inStream = new BufferedInputStream(new ByteArrayInputStream(identifiers.getBytes()));
		UniProtToKEGG.setDataInputStream(inStream);
		UniProtToKEGG.fetchData();
		
		//Now that the Uniprot-to-KEGG lookup is done we have to query against KEGG to get the Entries list.
		KEGGRetriever.setAdapter(dbAdapter);
		KEGGRetriever.setFetchDestination("/tmp/addlinks-downloaded-files/kegg_entries/kegg_entries."+speciesDBID+".txt");
		KEGGRetriever.setDataURL(new URI("http://rest.kegg.jp/get/"));
		List<Path> uniprotToKEGGFiles = new ArrayList<Path>();
		uniprotToKEGGFiles.add(Paths.get(UniProtToKEGG.getFetchDestination()));
		KEGGRetriever.setUniprotToKEGGFiles(uniprotToKEGGFiles);
		KEGGRetriever.setMaxAge(Duration.ofSeconds(1));		
		KEGGRetriever.fetchData();
		assertTrue(Files.exists(Paths.get("/tmp/addlinks-downloaded-files/kegg_entries/kegg_entries."+speciesDBID+".txt")));
		assertTrue(Files.size(Paths.get("/tmp/addlinks-downloaded-files/kegg_entries/kegg_entries."+speciesDBID+".txt")) > 0);
		
		// Get the KEGG mappings.
		KEGGFileProcessor keggProcessor = new KEGGFileProcessor();
		keggProcessor.setPath(Paths.get("/tmp/addlinks-downloaded-files/kegg_entries/kegg_entries.txt"));
		Map<String,Map<KEGGKeys,String>> mappings = keggProcessor.getIdMappingsFromFile();
		assertNotNull(mappings);
		assertTrue(mappings.keySet().size()>0);
	}

	
	@Test
	public void testKEGGReferenceCreator() throws Exception
	{
		String refDb = "UniProt";
		String species = "Xenopus laevis";
		String className = "ReferenceGeneProduct";
		String refDBID = objectCache.getRefDbNamesToIds().get(refDb).get(0);
		String speciesDBID = objectCache.getSpeciesNamesToIds().get(species).get(0);
		String identifiers = getIdentifiersList(refDb, species, className);
		assertTrue(identifiers.length()>0);
		System.out.print(identifiers);
		UniProtToKEGG.setFetchDestination(UniProtToKEGG.getFetchDestination().replace(".txt","." + speciesDBID + "." + refDBID + ".txt"));
		BufferedInputStream inStream = new BufferedInputStream(new ByteArrayInputStream(identifiers.getBytes()));
		UniProtToKEGG.setDataInputStream(inStream);
		UniProtToKEGG.fetchData();
		
		//Now that the Uniprot-to-KEGG lookup is done we have to query against KEGG to get the Entries list.
		KEGGRetriever.setAdapter(dbAdapter);
		KEGGRetriever.setFetchDestination("/tmp/addlinks-downloaded-files/kegg_entries/kegg_entries.txt");
		KEGGRetriever.setDataURL(new URI("http://rest.kegg.jp/get/"));
		List<Path> uniprotToKEGGFiles = new ArrayList<Path>();
		uniprotToKEGGFiles.add(Paths.get(UniProtToKEGG.getFetchDestination()));
		KEGGRetriever.setUniprotToKEGGFiles(uniprotToKEGGFiles);
		KEGGRetriever.setMaxAge(Duration.ofSeconds(1));		
		KEGGRetriever.fetchData();
		
		// Get the KEGG mappings.
		KEGGFileProcessor keggProcessor = new KEGGFileProcessor();
		keggProcessor.setPath(Paths.get("/tmp/addlinks-downloaded-files/kegg_entries/kegg_entries.txt"));
		Map<String,Map<KEGGKeys,String>> mappings = keggProcessor.getIdMappingsFromFile();
		assertNotNull(mappings);
		assertTrue(mappings.keySet().size()>0);
		
		String classToCreate = ReactomeJavaConstants.ReferenceDNASequence;
		String classReferring = ReactomeJavaConstants.ReferenceGeneProduct;
		String referringAttribute = ReactomeJavaConstants.crossReference;
		String sourceDB = "UniProt";
		String targetDB = "KEGG";
		KEGGReferenceCreator refCreator = new KEGGReferenceCreator(dbAdapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
		refCreator.setTestMode(true);
		long personID = 123456;

		List<GKInstance> sourceReferences = objectCache.getByRefDbAndSpecies(refDBID, speciesDBID, className);
		refCreator.createIdentifiers(personID , mappings, sourceReferences );
	}
}