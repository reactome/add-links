package org.reactome.addlinks.test.referencecreators;

import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
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
	
	// For RefSeqRNA
	@Autowired
	UPMappedIdentifiersReferenceCreator upMappedRefSeqRNARefCreator;
	
	@Autowired
	UniprotFileRetreiver UniProtToRefSeqRNA;
	
	@Autowired
	UniprotFileProcessor UniprotToRefSeqRNAFileProcessor;
	
	// For ENSEMBLGene
	@Autowired
	UPMappedIdentifiersReferenceCreator upMappedENSEMBLGeneRefCreator;
	
	@Autowired
	UniprotFileRetreiver UniProtToENSEMBLGene;
	
	@Autowired
	UniprotFileProcessor UniprotToENSEMBLGeneFileProcessor;
	
	// For EntrezGene
	@Autowired
	UPMappedIdentifiersReferenceCreator upMappedEntrezGeneRefCreator;
	
	@Autowired
	UniprotFileRetreiver UniProtToEntrezGene;
	
	@Autowired
	UniprotFileProcessor UniprotToEntrezGeneFileProcessor;
	
	// For EntrezGene - from ENSEMBL
	@Autowired
	UPMappedIdentifiersReferenceCreator ENSEMBLToEntrezGeneRefCreator;
	
	@Autowired
	UniprotFileRetreiver ENSEMBLToEntrezGeneRetriever;
	
	@Autowired
	UniprotFileProcessor ENSEMBLToEntrezGeneFileProcessor;
	
	
	// For KEGG
	@Autowired
	UPMappedIdentifiersReferenceCreator upMappedKEGGRefCreator;
	
	@Autowired
	UniprotFileRetreiver UniProtToKEGG;
	
	@Autowired
	UniprotFileProcessor UniprotToKEGGFileProcessor;
	
//	@BeforeClass
//	public static void setup()
//	{
//		System.setProperty("org.apache.commons.logging.Log","org.apache.commons.logging.impl.SimpleLog");
//		System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
//		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.wire", "DEBUG");
//		
//		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.impl.conn", "DEBUG");
//		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.impl.client", "DEBUG");
//		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.client", "DEBUG");
//		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http", "DEBUG");
//	}

	private BufferedInputStream indentifiersAsStream(List<GKInstance> identifiers)
	{
		BufferedInputStream inStream = new BufferedInputStream(new ByteArrayInputStream(
				identifiers.stream().map( inst -> {
					try
					{
						return ((String)inst.getAttributeValue(ReactomeJavaConstants.identifier));
					}
					catch (Exception e)
					{
						e.printStackTrace();
						throw new Error(e);
					}
				} ).reduce( "", (a,b) -> a + "\n" + b ).getBytes()
			));
		return inStream;
	}
	
	private List<GKInstance> getIdentifiersList(String refDb, String species, String className)
	{
		// Need a list of identifiers.
		String refDBID = objectCache.getRefDbNamesToIds().get(refDb).get(0);
		List<GKInstance> identifiers;
		if (species!=null)
		{
			String speciesDBID = objectCache.getSpeciesNamesToIds().get(species).get(0);
			identifiers = objectCache.getByRefDbAndSpecies(refDBID, speciesDBID, className);
			System.out.println(refDb + " " + refDBID + " ; " + species + " " + speciesDBID);
		}
		else
		{
			identifiers = objectCache.getByRefDb(refDBID, className);
			System.out.println(refDb + " " + refDBID + " ; " );
		}
		
		return identifiers;
	}

	private void testReferenceCreation(String refDb, String species, String className, UniprotFileRetreiver retriever, UniprotFileProcessor processor, UPMappedIdentifiersReferenceCreator refCreator) throws Exception, IOException
	{
		String refDBID = objectCache.getRefDbNamesToIds().get(refDb).get(0);
		String speciesDBID = objectCache.getSpeciesNamesToIds().get(species).get(0);
		List<GKInstance> identifiers = getIdentifiersList(refDb, species, className);
		assertTrue(identifiers.size()>0);
		System.out.println("Number of identifiers to look up: " + identifiers.size());
		retriever.setFetchDestination(retriever.getFetchDestination().replace(".txt","." + speciesDBID + "." + refDBID + ".txt"));
		BufferedInputStream inStream = indentifiersAsStream(identifiers);
		retriever.setDataInputStream(inStream);
		System.out.println("Fetching data...");
		retriever.fetchData();
		Map<String,Map<String,List<String>>> mappings = (Map<String, Map<String, List<String>>>) processor.getIdMappingsFromFile();
		assertTrue(mappings.keySet().size() > 0);
		refCreator.setTestMode(true);
		refCreator.createIdentifiers(123456, mappings, identifiers );
	}
	
	@Test
	public void testUPRefCreatorWormbase() throws Exception
	{
		String refDb = "UniProt";
		String species = "Caenorhabditis elegans";
		String className = "ReferenceGeneProduct";
		testReferenceCreation(refDb, species, className, UniProtToWormbase, UniprotToWormbaseFileProcessor, upMappedWormbaseRefCreator);
	}

	@Test
	public void testUPRefCreatorOMIM() throws Exception
	{
		String refDb = "UniProt";
		String species = "Homo sapiens";
		String className = "ReferenceGeneProduct";
		testReferenceCreation(refDb, species, className, UniProtToOMIM, UniprotToOMIMFileProcessor, upMappedOMIMRefCreator);
	}
	
	@Test
	public void testUPRefCreatorPDB() throws Exception
	{
		String refDb = "UniProt";
		String species = "Canis familiaris";
		String className = "ReferenceGeneProduct";
		testReferenceCreation(refDb, species, className, UniProtToPDB, UniprotToPDBFileProcessor, upMappedPDBRefCreator);
	}
	
	@Test
	public void testUPRefCreatorRefSeqPeptide() throws Exception
	{
		String refDb = "UniProt";
		String species = "Homo sapiens";
		String className = "ReferenceGeneProduct";
		testReferenceCreation(refDb, species, className, UniProtToRefSeqPeptide, UniprotToRefSeqPeptideFileProcessor, upMappedRefSeqPeptideRefCreator);
	}
	
	@Test
	public void testUPRefCreatorRefSeqRNA() throws Exception
	{
		String refDb = "UniProt";
		String species = "Homo sapiens";
		String className = "ReferenceGeneProduct";
		testReferenceCreation(refDb, species, className, UniProtToRefSeqRNA, UniprotToRefSeqRNAFileProcessor, upMappedRefSeqRNARefCreator);
	}
	
	@Test
	public void testUPRefCreatorENSEMBLGene() throws Exception
	{
		String refDb = "UniProt";
		String species = "Homo sapiens";
		String className = "ReferenceGeneProduct";
		testReferenceCreation(refDb, species, className, UniProtToENSEMBLGene, UniprotToENSEMBLGeneFileProcessor, upMappedENSEMBLGeneRefCreator);
	}
	
	@Test
	public void testUPRefCreatorEntrezGene() throws Exception
	{
		String refDb = "UniProt";
		String species = "Homo sapiens";
		String className = "ReferenceGeneProduct";
		testReferenceCreation(refDb, species, className, UniProtToEntrezGene, UniprotToEntrezGeneFileProcessor, upMappedEntrezGeneRefCreator);
	}
	
	@Test
	public void testUPRefCreatorKEGG() throws Exception
	{
		String refDb = "UniProt";
		String species = "Xenopus laevis";
		String className = "ReferenceGeneProduct";
		testReferenceCreation(refDb, species, className, UniProtToKEGG, UniprotToKEGGFileProcessor, upMappedKEGGRefCreator);
	}


	
//	// Nope. UniProt won't map non-uniprot input IDs.
//	@Test
//	public void testExperimentalENSEMBLToEntrezGene() throws Exception
//	{
//		String refDb = "ENSEMBL_Canis familiaris_PROTEIN";
//		String species = "Canis familiaris";
//		String className = "ReferenceGeneProduct";
//		String refDBID = objectCache.getRefDbNamesToIds().get(refDb).get(0);
//		String speciesDBID = objectCache.getSpeciesNamesToIds().get(species).get(0);
//		String identifiers = getIdentifiersList(refDb, species, className);
//		assertTrue(identifiers.length()>0);
//		System.out.print(identifiers);
//		ENSEMBLToEntrezGeneRetriever.setFetchDestination(ENSEMBLToEntrezGeneRetriever.getFetchDestination().replace(".txt","." + speciesDBID + "." + refDBID + ".txt"));
//		BufferedInputStream inStream = new BufferedInputStream(new ByteArrayInputStream(identifiers.getBytes()));
//		ENSEMBLToEntrezGeneRetriever.setDataInputStream(inStream);
//		ENSEMBLToEntrezGeneRetriever.fetchData();
//		@SuppressWarnings("unchecked")
//		Map<String,Map<String,List<String>>> mappings = (Map<String, Map<String, List<String>>>) ENSEMBLToEntrezGeneFileProcessor.getIdMappingsFromFile();
//		assertTrue(mappings.keySet().size() > 0);
//		ENSEMBLToEntrezGeneRefCreator.setTestMode(true);
//		ENSEMBLToEntrezGeneRefCreator.createIdentifiers(123456, Paths.get(ENSEMBLToEntrezGeneRetriever.getFetchDestination()));
//	}
}
