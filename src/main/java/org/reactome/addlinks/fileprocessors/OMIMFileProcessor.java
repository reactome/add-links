package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
	
	public void setOMIMPath(Path p)
	{
		this.pathToOMIMFile = p;
	}

	private Path pathToOMIMFile;
	
	@Override
	protected void processFile(Path file, Map<String, Map<String,List<String>>> mapping)
	{

		super.processFile(file, mapping);
		
		// now process the OMIM file.
		try
		{
			// 2nd column in file has string indicating if this entry is a gene, a phenotype, or something else. We only want genes.
			Set<String> omimGenes = Files.readAllLines(this.pathToOMIMFile).parallelStream()
											.filter( line -> (line.split("\t"))[1].equals("gene") )
											.collect(Collectors.toSet());
			// now that the main file processor has run, go through the mapping and REMOVE everything that is not a Gene.
			for (String species : mapping.keySet())
			{
				for (String uniprotID : mapping.get(species).keySet())
				{
					for (String omimID : mapping.get(species).get(uniprotID))
					{
						// If the OMIM ID is not in the list of genes, remove it.
						if ( !omimGenes.contains(omimID) )
						{
							mapping.get(species).get(uniprotID).remove(omimID);
							logger.debug("Removing {}", omimID);
						}
					}
				}
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
			logger.error("Error while trying to read OMIM file at {}, error is {}", this.pathToOMIMFile.toString(), e.getMessage());
			throw new Error(e);
		}
	}
}
