package org.reactome.addlinks.fileprocessors;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class KEGGFileProcessor extends FileProcessor<String[]>
{
	private static final Logger logger = LogManager.getLogger();
	/**
	 * Returns UniProt-to-KEGG mappings. The KEGG mappings are array of the form
	 * [KEGG_ID, KEGG_NAME, KEGG_IDENTIFIER] The KEGG_IDENTIFIER may not always
	 * be present.
	 * 
	 * @return
	 */
	@Override
	public Map<String, String[]> getIdMappingsFromFile()
	{
		Map<String, String[]> mappings = new HashMap<String, String[]>();

		try
		{
			try (FileReader fr = new FileReader(this.pathToFile.toFile());
					BufferedReader br = new BufferedReader(fr);)
			{
				String line;
				while ((line = br.readLine()) != null)
				{
					// If a line begins with "ENTRY " then it is a new KEGG entry. Extract the Kegg ID from this line.
					// If a line contains "///" then that is the end of the current entry.
					// If a line begins with "ORGANISM" then it contains the KEGG species code, extract it.
					// If a line begins with "DBLINKS" then we need to scan the next few
					// lines begining with whitespace for "UniProt:" to get the UniProt
					// ID this KEGG mapping came from.
					// If a line begins with DEFINITION then we extract the rest of that line for the Reactome name.
					// If a line begins with ORTHOLOGY then we extract the KEGG "identifier" (such as K02087) to use as the Reactome identifier value.
					// ...but if it is not possible to extract this value, then the KEGG code (keggSpeciesCode:keggID) will be acceptable for this.
					// ...The old code seems to suggests extracting the Reactome Identifier value from the ORTHOLOGY line, but not all KEGG entries have this.
					// Example: http://rest.kegg.jp/get/hsa:57488 The Reactome Instance browser shows that the Identifier for this ReferenceDNASequence is "ESYT2"
					// which is the first value of the "NAME" line. So, I'm going to code this to extract from the "NAME" line, not "ORTHOLOGY".
					// Another example: http://rest.kegg.jp/get/xla:380246
					// In this case, there is an ORTHOLOGY line and a NAME line. The value from ORTHOLOGY "K02087" doesn't seem to be a valid KEGG Identifier beacuse
					// the URL http://rest.kegg.jp/get/xla:K02087 does not return anythign, but http://rest.kegg.jp/get/xla:cdk1.S yiels the same data as
					// http://rest.kegg.jp/get/xla:380246 which suggests that "cdk1.S" is a "better" KEGG Identifier than "K02087" 

					// Also, it looks like we will need to extract EC numbers from ORTHOLOGY (if it's there) to create the EC abd BRENDA and IntEnz identifiers.
					
					
					String keggGeneID = null;
					String keggSpeciesCode = null;
					String keggDefinition = null;
					String keggIdentifier = null;
					String uniProtID = null;
					boolean watchingForUniprotID = false;
					String[] parts = line.split(" +");
					
					if (!line.equals("///"))
					{
					
						if (!watchingForUniprotID)
						{
							switch (parts[0])
							{
								case "ENTRY":
									keggGeneID = parts[1];
									break;
								case "ORGANISM":
									keggSpeciesCode = parts[1];
									break;
								case "DEFINITION":
									keggDefinition = line.replaceFirst("DEFINITION +", "");
									break;
								case "NAME":
									//keggIdentifier = line.replaceAll("ORTHOLOGY +", "").
									break;
								case "DBLINKS":
									// The UniProt ID could be on the FIRST line of DBLINKS (well... I haven't seen it but there's no reason to think it's impossible).
									if (line.contains("UniProt:"))
									{
										uniProtID = line.replaceAll("UniProt:", "").trim();
									}
									else
									{
										// First line of DBLINKS was somethign else so turn on the "looking for UniProt" switch.
										watchingForUniprotID = true;
									}
									break;
							}
						}
						else
						{
							// Once UniProt is found, turn off the switch.
							if (line.contains("UniProt:"))
							{
								uniProtID = line.replaceAll("UniProt:", "").trim();
								watchingForUniprotID = false;
							}
						}
					}
					// When we get the "///" line, it means "End Of Record" and we need to add what we have to the map and then reset everything.
					else
					{
						if (uniProtID!=null &&  !uniProtID.equals(""))
						{
							if (!mappings.containsKey(uniProtID))
							{
								String[] keggData = new String[3];
								keggData[0] = keggGeneID;
								//mappings.put(uniProtID, )
							}
						}
						else
						{
							logger.error("Processing a KEGG entry and no UniProt ID was found! "
									+ "This is very strange since the KEGG IDs we looked up are known to UniProt. "
									+ "Perhaps KEGG does not know the corresponding UniProt IDs? "
									+ "Data that we have at this point: KEGG ID: {} ; KEGG Species code: {} ; KEGG Definition: {}",
									keggGeneID, keggSpeciesCode, keggDefinition);
						}
					}
				}
			}
		}
		catch (IOException e)
		{
			logger.error("Error while trying to read a file: {}", e.getMessage());
			e.printStackTrace();
			throw new Error(e);
		}
		return null;
	}

}
