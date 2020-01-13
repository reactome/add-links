package org.reactome.addlinks.fileprocessors;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.reactome.addlinks.CustomLoggable;

public abstract class FileProcessor<T> implements CustomLoggable
{
	protected Logger logger;// = LogManager.getLogger();
	
	protected Path pathToFile;
	
	protected String processorName;
	
	public FileProcessor(){}
	
	public void setProcessorName(String processorName)
	{
		this.processorName = processorName;
	}
	
	/**
	 * Sets the path to the file that will be processed.
	 * @param p
	 */
	public void setPath(Path p)
	{
		this.pathToFile = p;
	}
	
	public FileProcessor(String processorName)
	{
		this.setProcessorName(processorName);
		String logName = null;
		if (this.processorName != null && !this.processorName.trim().equals(""))
		{
			logName = processorName.replace(".log","-processor.log");
		}
		this.logger = this.createLogger( logName , "RollingRandomAccessFile", this.getClass().getName(), true, Level.DEBUG, this.logger, "File Processor");
	}
	
	/**
	 * Returns a mapping based on what is in the file.
	 * This assumes that the file contains a mapping, and in this context, it probably does.
	 * @return
	 */
	public abstract Map<String, T> getIdMappingsFromFile() throws IOException;
	
	/**
	 * Unzips a file.
	 * @param pathToZipfile - the Path to the file to unzip.
	 * @param flattenZipOutput - Flatten the output? This is only relevant for .zip files which could contain multiple files.
	 * If this is <em>false</em>, then a new directory will be created with the same name as <code>p</code>, minus the .zip file extension,
	 * and the contents will be unzipped into directory.
	 * If set to <em>true</em>, then the contents of <code>p</code> will be extracted into the same directory that contains <code>p</code>.
	 * @return The path to the directory that contains the unzipped file(s).
	 * @throws Exception
	 */
	public String unzipFile(Path pathToZipfile, boolean flattenZipOutput) throws Exception
	{
		String fileName = null ;
		logger.debug("Unzipping {}",pathToZipfile);

		String extension = pathToZipfile.toString().substring(pathToZipfile.toString().lastIndexOf(".")).toLowerCase();
		String outFileName = pathToZipfile.toString().replace(extension, "");
		
		logger.debug("unzipping as {}",extension);
		if (!extension.equals(".gz") && !(extension.equals(".zip")))
		{
			logger.error("The file extension {} for the file {} is not supported by this function.",extension, pathToZipfile.getFileName());
			throw new Exception("Unsupported file extension was received by unzip function: " + extension);
		}

		// If the extension is .gz or .gzip, use GZIPInputStream to read the file,
		// otherwise, use ZipInputStream.
		try (InflaterInputStream inflaterStream = (extension.equals(".gz") || extension.equals(".gzip"))
										? new GZIPInputStream(new FileInputStream(pathToZipfile.toFile()))
										: new ZipInputStream(new FileInputStream(pathToZipfile.toFile()));
			)
		{
			//This Consumer will write a zip stream to a file. 
			BiConsumer<InflaterInputStream,String> dataWriter = (inStream,outputFileName) ->
			{
				byte[] buffer = new byte[1024];
				try
				{
					int count = 0;
					try(FileOutputStream outStream = new FileOutputStream( new File(outputFileName)); )
					{
						while ((count = inStream.read(buffer)) > 0)
						{
								outStream.write(buffer,0,count);
						}
					}
				}
				catch (Exception e)
				{
					logger.error("Error writing zip file: {}",e.getMessage());
				}
			};

			//If it's a ZIP file, there could be multiple files but if it's a GZIP file, there is only one file.
			if (inflaterStream instanceof ZipInputStream)
			{
				String prefixToOutput = pathToZipfile.getParent().toAbsolutePath().toString();
				if (!flattenZipOutput)
				{
					Files.createDirectories(Paths.get(outFileName));
					prefixToOutput = outFileName ;
				}
				ZipEntry entry;
				int i=0;
				while( (entry = ((ZipInputStream)inflaterStream).getNextEntry()) != null )
				{
					i++;
					dataWriter.accept(inflaterStream, prefixToOutput+"/"+entry.getName());
				}
				logger.info("Extracted {} files from zip archive.", i);

			}
			else
			{
				dataWriter.accept(inflaterStream, outFileName);
			}

			fileName = outFileName;
		}
		catch (IOException e)
		{
			logger.error("Exception was caught while trying to unzip a file: {}",e.getMessage());
			e.printStackTrace();
		}

		logger.debug("File was unzipped to {}", outFileName);
		//TODO: Re-write to return a list of the full paths of all unzipped files, instead of just the directory where they were unzipped to.
		return fileName;
	}
	
	
	/**
	 * Unzips a file.
	 * @param pathToZipfile - The path to the file.
	 * @return The directory where the files are unzipped.
	 * @throws Exception
	 */
	public String unzipFile(Path pathToZipfile) throws Exception 
	{
		return this.unzipFile(pathToZipfile, false);
	}
}
