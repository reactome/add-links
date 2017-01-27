package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class UniprotFileProcessor extends GlobbedFileProcessor<Map<String,List<String>>>
{
	
	/**
	 * Sets the globbing pattern to match against for this instance of UniprotFileProcessor.
	 * This is necessary because UniProt files will have the species ID and the Reference Database ID in their files names,
	 * but those values can't be set in Spring because they might not be known until runtime (in cases where new ReferenceDatabase
	 * need to be created in the database, for example). So, set a file pattern name to match.
	 * You should still include the *name* of the database you want to lookup, such as "/tmp/addlinks-downloaded-files/uniprot-mappings/uniprot_mapping_Uniprot_To_PDB."
	 * If this is set, the UniprotFileRetriever will process all UniProt-to-PDB mappings, with names like uniprot_mapping_Uniprot_To_PDB.68323.2.txt and also
	 * uniprot_mapping_Uniprot_To_PDB.48887.2.txt. The mapping returned by this object will contain a map keyed by species, referencing maps keyed by UniProt ID,
	 * referening Lists of other identifiers (since it might not be a 1:1 mapping). 
	 * @param glob
	 */

	private static final Pattern pattern = Pattern.compile("[^.]*\\.(\\d*)\\.\\d*\\.txt");
	private static final Logger logger = LogManager.getLogger();
	@Override
	protected Map<String, Map<String,List<String>>> getIdMappingsFromFilesMatchingGlob()
	{
		//The returned structure looks like this:
		/*
		 * ROOT MAP
		 *   |
		 *   |
		 *   +--Species 1 (Key)
		 *   |    |
		 *   |    +--UniProt ID 1 (Key)
		 *   |    |     | (List)
		 *   |    |     +- Other DB ID 1
		 *   |    |     +- Other DB ID 2
		 *   |    |     +- Other DB ID 3
		 *   |    |
		 *   |    +--UniProt ID 2 (Key)
		 *   |          | (List)
		 *   |          +- Other DB ID 1
		 *   |          +- Other DB ID 2
		 *   |          +- Other DB ID 3
		 *   |
		 *   +--Species 2
		 *   |    |
		 *   |    +--UniProt ID 1 (Key)
		 *   |    |     | (List)
		 *   |    |     +- Other DB ID 1
		 *   |    |     +- Other DB ID 2
		 *   |    |     +- Other DB ID 3
		 *   ...
		 */
		
		//Map<String,Map<String,List<String>>> mappings = new HashMap<String, Map<String,List<String>>>();
		this.mappings = new HashMap<String, Map<String,List<String>>>();

		// Uniprot filenames are generated on the fly, so all we can get is the general pattern. 
		// From there, we have to process all files that match the pattern. 
		PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + this.fileGlob + "*.txt");
		PathMatcher matcherNotMapped = FileSystems.getDefault().getPathMatcher("glob:" + this.fileGlob + "*.notMapped.txt");
		
		try
		{
			if (Files.exists(this.pathToFile)){
				Files.walkFileTree(this.pathToFile, new SimpleFileVisitor<Path>(){
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
					{
						// Extract the species ID from the file name.
						// If the file name is uniprot_mapping_Uniprot_To_Wormbase.68323.2.txt
						// then we want "68323". Regexp: [^.]*\.(\d)*\.\d*\.txt
						Matcher patternMatcher = pattern.matcher(file.getFileName().toString());
						if (patternMatcher.matches())
						{
							if (!matcherNotMapped.matches(file) && matcher.matches(file))
							{
								String speciesId = patternMatcher.group(1);
								if (mappings.containsKey(speciesId))
								{
									logger.warn("You already have an entry for {}. You should only have ONE file for each species for each refDB. If you have more, something may have gone wrong...", speciesId);
								}
								else
								{
									Map<String,List<String>> submappings = new HashMap<String, List<String>>();
									mappings.put(speciesId, submappings);
								}
								logger.debug("Processing file: {}",file.getFileName());
								//Process the file.
								Files.readAllLines(file).stream().filter(p -> !p.equals("From\tTo")).forEach( line ->
								{
									String[] parts = line.split("\\t");
									String uniProtId = parts[0];
									String otherId = parts[1];
									if (mappings.get(speciesId).containsKey(uniProtId))
									{
										List<String> otherIds = mappings.get(speciesId).get(uniProtId);
										otherIds.add(otherId);
										mappings.get(speciesId).put(uniProtId, otherIds);
									}
									else
									{
										List<String> otherIds = new ArrayList<String>();
										otherIds.add(otherId);
										mappings.get(speciesId).put(uniProtId, otherIds);
									}
								});
								//mappings.put(speciesId, submappings);
								// logger.info("# Uniprot ID for species {} is: {}", speciesId,mappings.get(speciesId).keySet().size());
							}
							
						}
						return FileVisitResult.CONTINUE;
					}
					
					@Override
					public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException
					{
						e.printStackTrace();
						return FileVisitResult.CONTINUE;
					}
				});
			}
			else
			{
				logger.warn("The path \"{}\" does not exist.", this.pathToFile);
			}
			
		} catch (IOException e1)
		{
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		logger.info("File processor summary for files matching {}",this.fileGlob);
		mappings.keySet().stream().forEach( key -> 
		{
			logger.info("\tspecies {} has {} mappings",key, mappings.get(key).size());
		});
		return mappings;
	}

}
