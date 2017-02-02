package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class GlobbedFileProcessor<T> extends FileProcessor<T>
{
	private static final Logger logger = LogManager.getLogger();
	
	protected Map<String, T> mappings;
	
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
	
	public Map<String, T> getIdMappingsFromFile()
	{
		return this.getIdMappingsFromFilesMatchingGlob();
	}
	
	protected abstract Map<String, T> getIdMappingsFromFilesMatchingGlob();
	
	public abstract class ReactomeMappingFileVisitor extends SimpleFileVisitor<Path>
	{
		private PathMatcher matcher;
		
		private Map<String, T> mapping;
		
		protected abstract void addFileToMapping(Path file, Map<String, T> mapping);
		
		@Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
		{
			logger.debug("Checking for file: {}", file);
            if (this.matcher.matches(file))
            {
            	logger.debug("File {} matches the matcher", file);
            	// users must override addFileToMapping for specific implementations.
                addFileToMapping(file, this.mapping);
            }
            else
            {
            	logger.error("File {} does not match pattern.", file);
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
