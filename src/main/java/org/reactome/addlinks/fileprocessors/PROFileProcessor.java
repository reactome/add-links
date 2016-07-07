package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

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
	private Path pathToFile;
	
	@Override
	public void setPath(Path p)
	{
		this.pathToFile = p;
	}

	@Override
	public Map<String,String> getIdMappingsFromFile()
	{
		Map<String,String> mappings = new HashMap<String,String>();
		
		try
		{
			//We filter to only process UniProtKB: because that's the way the old Perl code did it. It ignored UniProtKB_VAR
			Files.lines(this.pathToFile).filter(p -> p.contains("UniProtKB:")).forEach( line ->
			{
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
		logger.debug("Number of UniProt IDs in mapping: {}",mappings.keySet().size());
		return mappings;
	}
}
