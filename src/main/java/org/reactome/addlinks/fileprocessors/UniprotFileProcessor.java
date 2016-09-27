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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class UniprotFileProcessor extends FileProcessor
{
	private static final Logger logger = LogManager.getLogger();
	@Override
	public Map<String, ?> getIdMappingsFromFile()
	{
		Map<String,List<String>> mappings = new HashMap<String, List<String>>();

		// Uniprot filenames are generated on the fly, so all we can get is the general pattern. 
		// From there, we have to process all files that match the pattern. 
		PathMatcher matcher = FileSystems.getDefault().getPathMatcher(this.pathToFile.toString()+"*.txt");
		PathMatcher matcherNotMapped = FileSystems.getDefault().getPathMatcher(this.pathToFile.toString()+"*.notMapped.txt");
		
		try
		{
			Files.walkFileTree(this.pathToFile.getParent(), new SimpleFileVisitor<Path>(){
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
				{
					logger.info("Processing file: {}",file.getFileName());
					if (!matcherNotMapped.matches(file) && matcher.matches(file))
					{
						//Process the file.
					}
					
					if (matcher.matches(file))
					{
						System.out.println("MATCHES>>"+file);
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
		// Uniprot mapping files are simple: <ID><TAB><OTHER ID>
		try
		{
			Files.readAllLines(this.pathToFile).forEach( line ->
			{
				String[] parts = line.split("\\t");
				String uniProtId = parts[0];
				String otherId = parts[1];
				if (mappings.containsKey(uniProtId))
				{
					List<String> otherIds = mappings.get(uniProtId);
					otherIds.add(otherId);
					mappings.put(uniProtId, otherIds);
				}
				else
				{
					List<String> otherIds = new ArrayList<String>();
					otherIds.add(otherId);
					mappings.put(otherId, otherIds);
				}
			});
		} catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return mappings;
	}

}
