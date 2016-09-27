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

public class UniprotFileProcessor extends FileProcessor
{
	private String fileGlob;
	
	/**
	 * Sets the globbing pattern to match against for this instance of UniprotFileProcessor
	 * @param glob
	 */
	public void setFileGlob(String glob)
	{
		this.fileGlob = glob;
	}
	
	public String getFileGlob()
	{
		return this.fileGlob;
	}
	private static final Pattern pattern = Pattern.compile("[^.]*\\.(\\d*)\\.\\d*\\.txt");
	private static final Logger logger = LogManager.getLogger();
	@Override
	public Map<String, ?> getIdMappingsFromFile()
	{
		//The returned structure looks like this:
		/*
		 * ROOT MAP
		 *   |
		 *   |
		 *   +--Species 1 (Key)
		 *   |    |
		 *   |    +- Ref DB 1 (Key)
		 *   |    |     |  
		 *   |    |     | (List)
		 *   |    |     +- Other DB ID 1
		 *   |    |     +- Other DB ID 2
		 *   |    |     +- Other DB ID 3
		 *   |    |   
		 *   |    +- Ref DB 2
		 *   |    |
		 *   |    +- Ref DB 3
		 *   |
		 *   +--Species 1
		 *        |
		 *        +- Ref DB 1
		 *        |
		 *        +- Ref DB 2
		 *        |
		 *        +- Ref DB 3
		 *   ...
		 */
		
		Map<String,Map<String,List<String>>> mappings = new HashMap<String, Map<String,List<String>>>();

		// Uniprot filenames are generated on the fly, so all we can get is the general pattern. 
		// From there, we have to process all files that match the pattern. 
		PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + this.fileGlob + "*.txt");
		PathMatcher matcherNotMapped = FileSystems.getDefault().getPathMatcher("glob:" + this.fileGlob + "*.notMapped.txt");
		
		try
		{
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
							logger.info("Processing file: {}",file.getFileName());
							//Process the file.
							Files.readAllLines(file).stream().filter(p -> !p.equals("From\tTo")).forEach( line ->
							{
								String[] parts = line.split("\\t");
								String uniProtId = parts[0];
								String otherId = parts[1];
								if (mappings.containsKey(uniProtId))
								{
									List<String> otherIds = mappings.get(speciesId).get(uniProtId);
									otherIds.add(otherId);
									mappings.get(speciesId).put(uniProtId, otherIds);
								}
								else
								{
									List<String> otherIds = new ArrayList<String>();
									otherIds.add(otherId);
									mappings.get(speciesId).put(otherId, otherIds);
								}
							});
							//mappings.put(speciesId, submappings);
							logger.info("# Uniprot ID for species {} is: {}", speciesId,mappings.get(speciesId).keySet().size());
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
			
		} catch (IOException e1)
		{
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		return mappings;
	}

}
