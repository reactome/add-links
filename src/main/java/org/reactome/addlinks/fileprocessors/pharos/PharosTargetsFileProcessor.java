package org.reactome.addlinks.fileprocessors.pharos;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.reactome.addlinks.fileprocessors.FileProcessor;

public class PharosTargetsFileProcessor extends FileProcessor<String>
{

	/**
	 * Pharos Target mappings are very simple - they identify targets with UniProt identifiers. The data downloaded for targets for Pharos is also just a list of UniProt identifiers.
	 */
	@Override
	public Map<String, String> getIdMappingsFromFile()
	{
		Map<String, String> mapping = new HashMap<>();

		try(BufferedReader reader = new BufferedReader(new FileReader(this.pathToFile.toFile())))
		{
			String line = reader.readLine();
			while (line != null)
			{
				mapping.put(line, line);
			}
		}
		catch (FileNotFoundException e)
		{
			logger.error("File {} could not be found, file processor has failed.", this.pathToFile.toString());
		}
		catch (IOException e)
		{
			logger.error("Error with file! File processing has probably failed.", e);
		}

		return mapping;
	}

}
