package org.reactome.addlinks.fileprocessors.pharos;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.reactome.addlinks.fileprocessors.FileProcessor;

public class PharosLigandsFileProcessor extends FileProcessor<String>
{
	@Override
	public Map<String, String> getIdMappingsFromFile()
	{
		Map<String, String> mapping = new HashMap<>();

		try(BufferedReader reader = new BufferedReader(new FileReader(this.pathToFile.toFile())))
		{
			String line = reader.readLine();
			while (line != null)
			{
				String[] parts = line.split("\t");
				// first value is Guide to Pharmacology identifier, second value is ligid (Pharos ligand identifier).
				mapping.put(parts[0], parts[1]);
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
