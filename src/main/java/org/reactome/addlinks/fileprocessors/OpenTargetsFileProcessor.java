package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Processes TSV files for mapping to OpenTargets.
 * Files will be formatted like this: "${ENSEMBL ID}","${HGNC Gene Symbol}",...other stuff, including ACTUAL UniProt identifiers...
 * Example: "ENSG00000205002","AARD","Q4LEZ3|A5PKU8",56
 * See: https://github.com/opentargets/platform/issues/657#issuecomment-514718929 for details on this format, but the relevant quote
 * is: "Ensembl gene ID, UniProt accessions, HGNC approved symbol and Number of associations"
 * @author sshorser
 *
 */
public class OpenTargetsFileProcessor extends FileProcessor<String>
{
	public OpenTargetsFileProcessor()
	{
		super();
	}

	public OpenTargetsFileProcessor(String processorName)
	{
		super(processorName);
	}

	@Override
	public Map<String, String> getIdMappingsFromFile()
	{
		Map<String, String> uniprotToEnsemblForOpenTargetsMap = new HashMap<>();
		AtomicInteger lineCount = new AtomicInteger(0);
		Path inputFile = Paths.get(this.pathToFile.toAbsolutePath().toString().replace(".gz", ""));
		try
		{
			this.unzipFile(this.pathToFile, true);

			Files.readAllLines(inputFile).forEach( line -> {
				String[] parts = line.split(",");
				lineCount.incrementAndGet();
				if (parts.length >= 2) // Could be more > 2 fields now but we only care about the first two.
				{
					// We create mappings from UniProt -> Ensembl, but the lookup key used is actually the HGNC Gene Symbol
					// TODO: Now that they provide *actual* UniProt identifiers in the file, maybe rewrite this code to use this instead of the HGNC symbol.
					// ...I would have done it now if these changes hadn't been brought to my attention a day or two before AddLinks was scheduled to run a part of a Release.
					String hgncGeneName = parts[1].replaceAll("\"", "");
					String ensemblID = parts[0].replaceAll("\"", "");
					uniprotToEnsemblForOpenTargetsMap.put(hgncGeneName, ensemblID);
				}
			} );
		}
		catch (IOException e) // potentially thrown by Files.readAllLines
		{
			logger.error("Error reading file ({}): {}", inputFile.toString(), e.getMessage());
			e.printStackTrace();
		}
		catch (Exception e) // potentially thrown by this.unzipFile
		{
			logger.error("Error accessing/unzipping file({}): {}", this.pathToFile, e.getMessage());
			e.printStackTrace();
		}
		logger.info("{} lines processed, {} keys added to map.", lineCount.get(), uniprotToEnsemblForOpenTargetsMap.keySet().size());
		return uniprotToEnsemblForOpenTargetsMap;
	}

}
