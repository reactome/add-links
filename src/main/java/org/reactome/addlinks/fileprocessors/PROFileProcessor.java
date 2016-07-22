package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/* PRO files look like this:
PR:000000005	UniProtKB:P37173
PR:000000005	UniProtKB:P38438
PR:000000005	UniProtKB:Q62312
PR:000000005	UniProtKB:Q90999
PR:000000009	UniProtKB:Q16671
PR:000000009	UniProtKB:Q62893
PR:000000009	UniProtKB:Q8K592
PR:000000010	UniProtKB:O57472
PR:000000010	UniProtKB:Q24025
PR:000000010	UniProtKB:Q63148
*/
public class PROFileProcessor extends FileProcessor
{
	private static final Logger logger = LogManager.getLogger();
	
	@Override
	public Map<String,String> getIdMappingsFromFile()
	{
		Map<String,String> mappings = new HashMap<String,String>();
		AtomicInteger lineCount = new AtomicInteger(0);
		try
		{
			//We filter to only process UniProtKB: because that's the way the old Perl code did it. It ignored UniProtKB_VAR
			Files.lines(this.pathToFile).filter(p -> p.contains("UniProtKB:")).sequential().forEach( line ->
			{
				lineCount.set(lineCount.get()+1);
				//the UniProt ID
				String key = line.substring(line.indexOf("UniProtKB:"));
				// the PRO ID
				String value = line.substring(line.indexOf("PR:"), line.indexOf("\t"));
				mappings.put(key, value);
			} );
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		logger.debug("Number of UniProt IDs in mapping: {}; number of lines processed: {}",mappings.keySet().size(),lineCount.get());
		return mappings;
	}
}
