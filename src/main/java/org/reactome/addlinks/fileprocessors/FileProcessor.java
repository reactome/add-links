package org.reactome.addlinks.fileprocessors;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipInputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class FileProcessor
{
	private static final Logger logger = LogManager.getLogger();
	
	public abstract void setPath(Path p);
	
	public abstract Map<String,String> getIdMappingsFromFile();
	
	public List<String> unzipFile(Path p)
	{
		List<String> fileNames = new ArrayList<>();
		logger.debug("Unzipping {}",p);

		String extension = p.toString().substring(p.toString().lastIndexOf("."));
		String outFileName = p.toString().replace(extension, "");
		
		logger.debug("unzipping as {}",extension);
		// If the extension is .gz or .gzip, use GZIPInputStream to read the file,
		// otherwise, use ZipInputStream.
		try (InflaterInputStream gzIn = (extension.equals(".gz") || extension.equals(".gzip"))
										? new GZIPInputStream(new FileInputStream(p.toFile()))
										: new ZipInputStream(new FileInputStream(p.toFile()));
			FileOutputStream outStream = new FileOutputStream( new File(outFileName) );)
		{

			byte[] buffer = new byte[1024];	
			while ( gzIn.read(buffer) > 0)
			{
				outStream.write(buffer);
			}
			fileNames.add(outFileName);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			}

		logger.debug("File was unzipped to {}", outFileName);
		return fileNames;
	}
}
