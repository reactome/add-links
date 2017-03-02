package org.reactome.addlinks.fileprocessors;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reactome.addlinks.CustomLoggable;

public abstract class FileProcessor<T> implements CustomLoggable
{
	protected Logger logger = LogManager.getLogger();
	
	protected Path pathToFile;
	
	protected String processorName;
	
	public void setProcessorName(String processorName)
	{
		this.processorName = processorName;
	}
	
	public void setPath(Path p)
	{
		this.pathToFile = p;
	}
	
	public FileProcessor(String processorName)
	{
		this.setProcessorName(processorName);
		if (this.processorName == null || this.processorName.trim().equals(""))
		{
			logger.warn("No processorName was set, so this FileProcessor will not use its own log file.");
		}
		else
		{
			this.logger = this.createLogger("logs/" + processorName.replace(".log","-retriever.log") + ".log", "RollingRandomAccessFile", processorName, this.getClass().getName(), true, Level.DEBUG);
		}

	}
	
	/**
	 * Returns a mapping based on what is in the file.
	 * This assumes that the file contains a mapping, and in this context, it probably does.
	 * @return
	 */
	public abstract Map<String, T> getIdMappingsFromFile();
	
		/**
	 * Unzips a file.
	 * @param p - The path to the file.
	 * @return The directory where the files are unzipped.
	 * @throws Exception
	 */
	public String unzipFile(Path p) throws Exception 
	{
		String fileName = null ;
		logger.debug("Unzipping {}",p);

		String extension = p.toString().substring(p.toString().lastIndexOf(".")).toLowerCase();
		String outFileName = p.toString().replace(extension, "");
		
		logger.debug("unzipping as {}",extension);
		if (!extension.equals(".gz") && !(extension.equals(".zip")))
		{
			logger.error("The file extension {} for the file {} is not supported by this function.",extension, p.getFileName());
			throw new Exception("Unsupported file extension was received by unzip function: " + extension);
		}

		// If the extension is .gz or .gzip, use GZIPInputStream to read the file,
		// otherwise, use ZipInputStream.
		try (InflaterInputStream inflaterStream = (extension.equals(".gz") || extension.equals(".gzip"))
										? new GZIPInputStream(new FileInputStream(p.toFile()))
										: new ZipInputStream(new FileInputStream(p.toFile()));
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
				if (!Files.exists(Paths.get(outFileName)))
				{
					Files.createDirectory(Paths.get(outFileName));
				}
				ZipEntry entry;
				int i=0;
				while( (entry = ((ZipInputStream)inflaterStream).getNextEntry()) != null )
				{
					i++;
					dataWriter.accept(inflaterStream, outFileName+"/"+entry.getName());
				}
				logger.info("Extracted {} files from zip archive.",i);
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
}
