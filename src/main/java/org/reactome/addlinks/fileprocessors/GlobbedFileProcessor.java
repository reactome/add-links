package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class GlobbedFileProcessor<T> extends FileProcessor<T>
{
	//private static final Logger logger = LogManager.getLogger();
	
	public GlobbedFileProcessor()
	{
	}
	
	public GlobbedFileProcessor(String processorName)
	{
		super(processorName);
	}

	private Map<String, T> mappings;
	
	protected String fileGlob;
	
	protected SimpleFileVisitor<Path> globFileVisitor;
	
	public void setFileGlob(String glob)
	{
		this.fileGlob = glob;
	}
	
	public String getFileGlob()
	{
		return this.fileGlob;
	}
	
	/**
	 * Gets the mappings from a file.
	 * BUT it calls getIdMappingsFromFilesMatchingGlob to do this, which means that the mapping returned could
	 * actually have multiple files as its sources.
	 */
	@Override
	public Map<String, T> getIdMappingsFromFile()
	{
		return this.getIdMappingsFromFilesMatchingGlob();
	}
	
	protected Pattern pattern = null;
	
	/**
	 * This function will process a file and update a mapping object.
	 * @param file - The file to process.
	 * @param mapping - The mapping object to update.
	 */
	abstract protected void processFile(Path file, Map<String, T> mapping);
	
	/**
	 * Process files that match a file glob.
	 * @return the mapping of all files that matched the glob.
	 */
	protected Map<String, T> getIdMappingsFromFilesMatchingGlob()
	{
		ReactomeMappingFileVisitor visitor = new ReactomeMappingFileVisitor() {
			@Override
			protected void addFileToMapping(Path file, Map<String, T> mapping)
			{
				Matcher patternMatcher = pattern.matcher(file.getFileName().toString());
				if (patternMatcher.matches())
				{
					// Call the processFile function - this must be implemented in the subclasses.
					processFile(file, mapping);
				}
			}
		};
		this.mappings = new HashMap<String, T>();
		visitor.setMapping(this.mappings);
		visitor.setMatcher(FileSystems.getDefault().getPathMatcher("glob:" + this.fileGlob));
		this.globFileVisitor = visitor;
		
		try
		{
			Files.walkFileTree(this.pathToFile, this.globFileVisitor);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		
		return visitor.getMapping();
	}
	
	/**
	 * A FileVisitor for processing Reactome files using GlobbedFileProcessor.
	 * @author sshorser
	 *
	 */
	public abstract class ReactomeMappingFileVisitor extends SimpleFileVisitor<Path>
	{
		private PathMatcher matcher;
		
		protected Map<String, T> mapping;
		
		/**
		 * Add the contents of a file to a mapping object.
		 * @param file
		 * @param mapping
		 */
		protected abstract void addFileToMapping(Path file, Map<String, T> mapping);
		
		/**
		 * Visit files. Calls abstract addFileToMapping when a files is found to match the glob.
		 */
		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
		{
			logger.trace("Checking for file: {}", file);
			if (this.matcher.matches(file))
			{
				logger.debug("File {} matches the matcher", file);
				// users must override addFileToMapping for specific implementations.
				// The function addFileToMapping should *modify* the mapping argument passed in to it.
				addFileToMapping(file, this.mapping);
			}
			else
			{
				logger.trace("File {} does not match pattern.", file);
			}
			return FileVisitResult.CONTINUE;
		}

		public void setMatcher(PathMatcher matcher)
		{
			this.matcher = matcher;
		}

		public Map<String, T> getMapping()
		{
			return this.mapping;
		}

		public void setMapping(Map<String, T> mapping)
		{
			this.mapping = mapping;
		}
	}
}
