package org.reactome.addlinks.test;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.reactome.addlinks.fileprocessors.ensembl.EnsemblAggregateFileProcessor;
import org.reactome.addlinks.fileprocessors.ensembl.EnsemblAggregateFileProcessor.EnsemblAggregateProcessingMode;
import org.reactome.addlinks.fileprocessors.ensembl.EnsemblFileAggregator;


public class TestEnsemblFileAggregatorIT
{

	@Test
	public void testCreateAggregateEnsemblMappingFile() throws IOException
	{
		String speciesID = "68323";// "48892";
		List<String> dbNames = (List) Arrays.asList("EntrezGene", "Wormbase");
		String rootPath = "/tmp/addlinks-downloaded-files/ensembl/";
		EnsemblFileAggregator aggregator = new EnsemblFileAggregator(speciesID, dbNames, rootPath);
		
		aggregator.createAggregateFile();
		
		Path pathToOutfile = Paths.get(rootPath + "ensembl_p2xref_mapping."+speciesID+".csv");
		
		assertTrue(Files.exists(pathToOutfile));
		assertTrue(Files.size(pathToOutfile ) > 0);
		
		EnsemblAggregateFileProcessor aggregateProcessor = new EnsemblAggregateFileProcessor();
		aggregateProcessor.setMode(EnsemblAggregateProcessingMode.XREF);
		aggregateProcessor.setPath(pathToOutfile);
		Map<String, Map<String, String>> mappings = aggregateProcessor.getIdMappingsFromFile();
		assertNotNull(mappings);
		
		aggregateProcessor.setMode(EnsemblAggregateProcessingMode.ENSP_TO_ENSG);
		aggregateProcessor.setPath(pathToOutfile);
		mappings = aggregateProcessor.getIdMappingsFromFile();
		assertNotNull(mappings);
	}
}
