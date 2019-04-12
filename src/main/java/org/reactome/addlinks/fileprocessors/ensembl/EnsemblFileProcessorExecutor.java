package org.reactome.addlinks.fileprocessors.ensembl;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.fileprocessors.ensembl.EnsemblAggregateFileProcessor.EnsemblAggregateProcessingMode;

public class EnsemblFileProcessorExecutor
{
	private static final String PATH_TO_DOWNLOADED_ENSEMBL_FILES = "/tmp/addlinks-downloaded-files/ensembl/";

	private MySQLAdaptor dbAdapter;
	
	private ReferenceObjectCache objectCache;
	
	public EnsemblFileProcessorExecutor(MySQLAdaptor adaptor, ReferenceObjectCache cache)
	{
		this.dbAdapter = adaptor;
		this.objectCache = cache;
	}
	
	/**
	 * Process ENSEMBL files. ENSEMBL files need special processing - you can't do it in a single step.
	 * This function will actually run EnsemblFileAggregators and EnsemblFileAggregatorProcessors.
	 * These two classes are used to produce an aggregate file containing ALL Ensembl mappings: each line will have the following identifiers:
	 *  - ENSP, ENST, ENSG.
	 *  Each line also contains the Name of an external database that the ENSG maps to, and the identifier value from that external database (or "null" if there was no mapping).
	 * @param dbMappings - this mapping will be updated by this function.
	 * @throws Exception
	 * @throws InvalidAttributeException
	 */
	public void processENSEMBLFiles(Map<String, Map<String, ?>> dbMappings) throws Exception, InvalidAttributeException
	{
		@SuppressWarnings("unchecked")
		Collection<GKInstance> enspDatabases = this.dbAdapter.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceDatabase, ReactomeJavaConstants.name, " LIKE ", "ENSEMBL%PROTEIN");
		Set<String> species = new HashSet<String>();
		for (GKInstance inst : enspDatabases)
		{	
			List<GKInstance> refGeneProds = this.objectCache.getByRefDb(inst.getDBID().toString(), "ReferenceGeneProduct");
			for (GKInstance refGeneProd : refGeneProds)
			{
				species.add(((GKInstance)refGeneProd.getAttributeValue(ReactomeJavaConstants.species)).getDBID().toString());
			}
		}
		
		for (String speciesID : species)
		{
			List<String> dbNames = new ArrayList<String>(Arrays.asList("EntrezGene", "Wormbase"));
			EnsemblFileAggregator ensemblAggregator = new EnsemblFileAggregator(speciesID, dbNames, PATH_TO_DOWNLOADED_ENSEMBL_FILES);
			ensemblAggregator.createAggregateFile();
			
			EnsemblAggregateFileProcessor aggregateProcessor = new EnsemblAggregateFileProcessor("file-processors/EnsemblAggregateFileProcessor");
			aggregateProcessor.setPath(Paths.get(PATH_TO_DOWNLOADED_ENSEMBL_FILES+"ensembl_p2xref_mapping."+speciesID+".csv") );
			aggregateProcessor.setMode(EnsemblAggregateProcessingMode.XREF);
			Map<String, Map<String, List<String>>> xrefMapping = aggregateProcessor.getIdMappingsFromFile();
			dbMappings.put("ENSEMBL_XREF_"+speciesID, xrefMapping);
			
			aggregateProcessor.setMode(EnsemblAggregateProcessingMode.ENSP_TO_ENSG);
			Map<String, Map<String, List<String>>> ensp2EnsgMapping = aggregateProcessor.getIdMappingsFromFile();
			dbMappings.put("ENSEMBL_ENSP_2_ENSG_"+speciesID, ensp2EnsgMapping);
		}
	}
}
