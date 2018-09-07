package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ComplexPortalFileProcessor extends FileProcessor<List<String>>
{
	public ComplexPortalFileProcessor(String processorName)
	{
		super(processorName);
	}
	
	public ComplexPortalFileProcessor()
	{
		super(null);
	}
	
	@Override
	public Map<String, List<String>> getIdMappingsFromFile()
	{
		// Key is the ComplexPortal ID, value is a list of (possibly) multiple Uniprot IDs and ReactomeIDs.
		Map<String, List<String>> complexPortalToUniprotAndReactome = new HashMap<String,List<String>>(); 
		try
		{
			Files.readAllLines(this.pathToFile).stream()
				.filter(line -> !line.startsWith("#"))
				.forEach(line ->
			{
				String[] parts = line.split("\\t");
				
				// Get the ID for ComplexPortal.
				String complexPortalID = parts[0];
				// The UniprotIDs will be in the 5th column.
				String uniprots = parts[4];
				// There could be multiple Uniprot identifiers so we need to extract them all.
				// Remove the stoichiometry information (in parenthesis after each Uniprot ID) and then split on "|".
				String[] uniprotIDs = uniprots.replaceAll("\\(\\d*\\)", "").split("\\|");
				// Cross-references will be in the 9th column, and may include Reactome identifiers.
				String xrefs = parts[8];
				// There could me multiple Reactome identifiers, so we need to extract them all.
				List<String> reactomeIDs = Arrays.stream(xrefs.split("\\|"))
												.filter(s -> s.startsWith("reactome"))
												.map(s -> s.replace("reactome:", "").replaceAll("\\(.*\\)",""))
												.collect(Collectors.toList());
				// Update the mapping.
				if (complexPortalToUniprotAndReactome.containsKey(complexPortalID))
				{
					complexPortalToUniprotAndReactome.get(complexPortalID).addAll(Arrays.asList(uniprotIDs));
					complexPortalToUniprotAndReactome.get(complexPortalID).addAll(reactomeIDs);
				}
				else
				{
					complexPortalToUniprotAndReactome.put(complexPortalID, new ArrayList<String>());
					complexPortalToUniprotAndReactome.get(complexPortalID).addAll(Arrays.asList(uniprotIDs));
					complexPortalToUniprotAndReactome.get(complexPortalID).addAll(reactomeIDs);

				}
			});
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		logger.info("Number of ComplexPortal mappings: {}",complexPortalToUniprotAndReactome.size());
		return complexPortalToUniprotAndReactome;
	}

}
