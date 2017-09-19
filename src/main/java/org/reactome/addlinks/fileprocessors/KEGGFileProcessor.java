package org.reactome.addlinks.fileprocessors;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KEGGFileProcessor extends GlobbedFileProcessor<List<Map<KEGGFileProcessor.KEGGKeys, String>>>
{
	public enum KEGGKeys
	{
		KEGG_IDENTIFIER,
		KEGG_GENE_ID,
		KEGG_SPECIES,
		KEGG_DEFINITION,
		EC_NUMBERS
	}
	
	private static final Pattern ecPattern = Pattern.compile("(.*)\\[EC:([0-9\\-\\. ]*)\\]");
	
	public KEGGFileProcessor(String processorName)
	{
		super(processorName);
		this.pattern = Pattern.compile("kegg_entries.[0-9]+\\.[0-9]+\\.txt");
	}
	
	public KEGGFileProcessor()
	{
		super();
		this.pattern = Pattern.compile("kegg_entries.[0-9]+\\.[0-9]+\\.txt");
	}

	/**
	 * Returns UniProt-to-KEGG mappings.
	 * @param file 
	 * 
	 */
	@Override
	protected void processFile(Path path, Map<String, List<Map<KEGGFileProcessor.KEGGKeys, String>>> mapping)
	{
		int lineNumber = 0;
		int entryLineNumber = 0;
		int entryCount = 0;
		int duplicateEntryCount = 0;
		try
		{
			logger.debug("Processing: {}", path.toString());
			try (FileReader fr = new FileReader(path.toFile());
					BufferedReader br = new BufferedReader(fr);)
			{
				String line;
				
				String keggGeneID = null;
				String keggSpeciesCode = null;
				String keggDefinition = null;
				String keggIdentifier = null;
				String ecNumber = null;
				String[] uniProtIDs = null;
				boolean watchingForUniprotID = false;
				while ((line = br.readLine()) != null)
				{
					lineNumber++;
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
					
					if (!line.equals("///"))
					{
						String[] parts = line.split("\\s+");
						if (!watchingForUniprotID)
						{
							switch (parts[0].trim())
							{
								case "ENTRY":
									entryLineNumber = lineNumber;
									entryCount ++;
									keggGeneID = parts[1].trim();
									break;
								case "ORGANISM":
									keggSpeciesCode = parts[1].trim();
									break;
								case "DEFINITION":
									keggDefinition = line.replaceFirst("DEFINITION +", "");
									break;
								case "NAME":
									//Extract the first String from the NAME line (after "NAME").
									keggIdentifier = Arrays.asList(line.replaceAll("NAME +", "").split(",")).get(0).trim();
									break;
								case "ORTHOLOGY":
									// This could actually be several EC numbers separated by a space character.
									Matcher matcher = ecPattern.matcher(line); 
									if (matcher.matches() && matcher.groupCount() > 0)
									{
										ecNumber = matcher.group(2);
									}
									break;
								case "DBLINKS":
									// The UniProt ID could be on the FIRST line of DBLINKS (well... I haven't seen it but there's no reason to think it's impossible).
									if (line.contains("UniProt:"))
									{
										uniProtIDs = line.replaceAll("UniProt:", "").trim().split("\\s+");
										watchingForUniprotID = false;
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
								uniProtIDs = line.replaceAll("UniProt:", "").trim().split("\\s+");
								watchingForUniprotID = false;
							}
						}
					}
					// When we get the "///" line, it means "End Of Record" and we need to add what we have to the map and then reset everything.
					else
					{
						// Flip the flag on watchingForUniprotID back to false, since we've reached the end of a record.
						// This is necessasry, in case one of the Records doesn't have any UniProt dblinks.
						watchingForUniprotID = false;
						if (keggIdentifier == null)
						{
							// We can create a link to KEGG using the value from the ENTRY record, if there was no NAME record present.
							keggIdentifier = keggGeneID;
						}

						if (uniProtIDs!=null &&  uniProtIDs.length > 0)
						{
							Map<KEGGKeys,String> keggValues = new HashMap<KEGGKeys,String>(5);
							keggValues.put(KEGGKeys.KEGG_IDENTIFIER, keggIdentifier);
							keggValues.put(KEGGKeys.KEGG_GENE_ID, keggGeneID);
							keggValues.put(KEGGKeys.KEGG_SPECIES, keggSpeciesCode);
							keggValues.put(KEGGKeys.KEGG_DEFINITION, keggDefinition);
							keggValues.put(KEGGKeys.EC_NUMBERS, ecNumber);
							
							if ( (keggValues.get(KEGGKeys.KEGG_IDENTIFIER) == null || keggValues.get(KEGGKeys.KEGG_IDENTIFIER).trim().equals(""))
									&& (keggValues.get(KEGGKeys.KEGG_GENE_ID) == null || keggValues.get(KEGGKeys.KEGG_GENE_ID).trim().equals("")))
							{
								logger.error("For UniProts {}: KEGG_IDENTIFIER and KEGG_GENE_ID are both NULL/Empty. This is not allowed! See ENTRY starting at line {}. KEGG values map for this entry: {}", uniProtIDs.toString(), entryLineNumber, keggValues.toString());
							}
							else
							{
								for (String uniProtID : uniProtIDs)
								{
									logger.trace("UniProt ID {} maps to {}", uniProtID, keggValues.toString());
									if (!mapping.containsKey(uniProtID))
									{
										List<Map<KEGGFileProcessor.KEGGKeys, String>> keggList = new ArrayList<Map<KEGGFileProcessor.KEGGKeys, String>>();
										keggList.add(keggValues);
										mapping.put(uniProtID, keggList);
									}
									else
									{
										//we should check for dupliates...
										boolean isDuplicate = false;
										for (Map<KEGGKeys, String> preExistingKeggValueRecord : mapping.get(uniProtID))
										{
											String identifier = keggValues.get(KEGGKeys.KEGG_IDENTIFIER);
											String otherIdentifier = preExistingKeggValueRecord.get(KEGGKeys.KEGG_IDENTIFIER);
											String geneID = keggValues.get(KEGGKeys.KEGG_GENE_ID);
											String otherGeneId = preExistingKeggValueRecord.get(KEGGKeys.KEGG_GENE_ID);
											
											// currentCompareValue and preexistingCompareValue should never actually be "" because
											// that situation should be handled up above. The logic prevents such keggValue maps from
											// ever being inserted into the main map structure.
											String currentCompareValue = identifier != null && !identifier.trim().equals("")
																			? identifier
																			: (geneID != null && !geneID.trim().equals("")
																				? geneID
																				: "");  

											String preexistingCompareValue = otherIdentifier != null && !otherIdentifier.trim().equals("")
																			? otherIdentifier
																			: (otherGeneId != null && !otherGeneId.trim().equals("")
																				? otherGeneId
																				: "");

											if (currentCompareValue.equals(preexistingCompareValue))
											{
												isDuplicate = true;
												duplicateEntryCount ++;
												logger.warn("Duplicate mapping for {} to {} - will not be added to results.", uniProtID, keggValues.get(KEGGKeys.KEGG_IDENTIFIER));
											}
											
//											if (keggValues.get(KEGGKeys.KEGG_IDENTIFIER).equals(preExistingKeggValueRecord.get(KEGGKeys.KEGG_IDENTIFIER)))
//											{
//												isDuplicate = true;
//												duplicateEntryCount ++;
//												logger.warn("Duplicate mapping for {} to {} - will not be added to results.", uniProtID, keggValues.get(KEGGKeys.KEGG_IDENTIFIER));
//											}
										}
										if (!isDuplicate)
										{
											mapping.get(uniProtID).add(keggValues);
										}
									}
									//Now reset all the variables.
									keggDefinition = null;
									keggGeneID = null;
									keggSpeciesCode = null;
									keggIdentifier = null;
									ecNumber = null;
								}
							}
						}
						else
						{
							logger.error("Processing a KEGG entry and no UniProt ID was found! "
									+ "This is very strange since the KEGG IDs we looked up are known to UniProt. "
									+ "Perhaps KEGG does not know the corresponding UniProt IDs? "
									+ "Data that we have at this point: KEGG Gene ID: {} ; KEGG Species code: {} ; KEGG Definition: {} ; KEGG Identifier: {} ; EC Numbers from KEGG: {}",
									keggGeneID, keggSpeciesCode, keggDefinition, keggGeneID, ecNumber);
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
		logger.info("Processed {} lines for file {}. Found {} entries: {} were duplicates. Added {} UniProt keys to the mapping.", lineNumber, path.toString(), entryCount, duplicateEntryCount, mapping.keySet().size());
	}



}
