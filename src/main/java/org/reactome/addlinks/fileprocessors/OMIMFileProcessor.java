package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class OMIMFileProcessor extends UniprotFileProcessor
{

	public OMIMFileProcessor()
	{
		super();
	}
	
	public OMIMFileProcessor(String processorName)
	{
		super(processorName);
	}
	
	/**
	 * Sets the path to the data file from OMIM which will be used to filter out non-genes.
	 * @param p
	 */
	public void setOMIMPath(Path p)
	{
		this.pathToOMIMFile = p;
	}

	private Path pathToOMIMFile;
	
	/**
	 * This file processor will first run the main Uniprot file processor.
	 * Then it will filter those results based on the OMIM data file, and remove anything that is not a gene.
	 * This way, we only create links to OMIM genes, not to OMIM phenotypes.
	 */
	@Override
	protected void processFile(Path file, Map<String, Map<String,List<String>>> mapping)
	{

		super.processFile(file, mapping);
		
		// now process the OMIM file.
		try
		{
			// 2nd column in file has string indicating if this entry is a gene, a phenotype, or something else. We only want genes.
			Set<String> omimGenes = Files.readAllLines(this.pathToOMIMFile).parallelStream()
										.filter( line -> line.split("\t").length >= 2 && (line.split("\t"))[1].equals("gene") )
										.map( line -> line.split("\t")[0] )
										.collect(Collectors.toSet());
			logger.info("{} OMIM IDs are for genes.", omimGenes.size());
			Map<String, Map<String,List<String>>> newMapping = Collections.synchronizedMap(new HashMap<String, Map<String,List<String>>>());
			// now that the main file processor has run, go through the mapping and REMOVE everything that is not a Gene.
			for (String species : mapping.keySet())
			{
				for (String uniprotID : mapping.get(species).keySet())
				{
					for (String omimID : mapping.get(species).get(uniprotID))
					{
						// If the OMIM ID is in the list of genes, add it to the new list.
						if ( omimGenes.contains(omimID) )
						{
							//mapping.get(species).get(uniprotID).remove(omimID);
							if (!newMapping.containsKey(species))
							{
								newMapping.put(species, Collections.synchronizedMap(new HashMap<String,List<String>>()));
							}
							if (!newMapping.get(species).containsKey(uniprotID))
							{
								newMapping.get(species).put(uniprotID, Collections.synchronizedList(new ArrayList<String>()));
							}
							// add the 
							newMapping.get(species).get(uniprotID).add(omimID);
							
							logger.debug("Including OMIM ID: {}", omimID);
						}
						else
						{
							logger.debug("*NOT* including OMIM ID: {}", omimID);
						}
					}
				}
			}
			// assign newMapping to mapping so it can be accessed outside.
			mapping = newMapping;
		}
		catch (IOException e)
		{
			e.printStackTrace();
			logger.error("Error while trying to read OMIM file at {}, error is {}", this.pathToOMIMFile.toString(), e.getMessage());
			throw new Error(e);
		}
	}
}
