package org.reactome.addlinks.referencecreators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaAttribute;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.fileprocessors.KEGGFileProcessor.KEGGKeys;
import org.reactome.addlinks.kegg.KEGGReferenceDatabaseGenerator;
import org.reactome.addlinks.kegg.KEGGSpeciesCache;

public class KEGGReferenceCreator extends SimpleReferenceCreator<List<Map<KEGGKeys, String>>>
{
	public KEGGReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
	}
	
	public KEGGReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB, String refCreatorName)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, refCreatorName);
	}

	@Override
	public void createIdentifiers(long personID, Map<String, List<Map<KEGGKeys, String>>> mappings, List<GKInstance> sourceReferences) throws Exception
	{
		ReferenceObjectCache objectCache = new ReferenceObjectCache(adapter, true);
		int sourceIdentifiersWithNoMapping = 0;
		int sourceIdentifiersWithNewIdentifier = 0;
		int sourceIdentifiersWithExistingIdentifier = 0;
		// TODO: rewrite the Reference Creator to process sourceReferences in parallel and then build a list of thingsToCreate, which will be inserted serially.
		for (GKInstance sourceReference : sourceReferences)
		{
			String sourceReferenceIdentifier = (String) sourceReference.getAttributeValue(ReactomeJavaConstants.identifier);
			
			// It's possible that we could get a list of things from some third-party that contains mappings for multiple species.
			// So we need to get the species for EACH thing we iterate on. I worry this will slow it down, but  it needs to be done
			// if we want new identifiers to have the same species of the thing which they refer to.
			Long speciesID = null;
			@SuppressWarnings("unchecked")
			GKSchemaAttribute speciesAttribute = ((Collection<GKSchemaAttribute>) sourceReference.getSchemaAttributes()).stream().filter(a -> a.getName().equals(ReactomeJavaConstants.species)).findFirst().orElse(null);
			if (speciesAttribute!=null)
			{
				GKInstance speciesInst = (GKInstance) sourceReference.getAttributeValue(ReactomeJavaConstants.species);
				if (speciesInst != null)
				{
					speciesID = new Long(speciesInst.getDBID());
				}
			}

			// Check if the source UniProt Identifier is in the mapping.
			if (mappings.containsKey(sourceReferenceIdentifier))
			{
				@SuppressWarnings("unchecked")
				Collection<GKInstance> xrefs = (Collection<GKInstance>) sourceReference.getAttributeValuesList(referringAttributeName);
				boolean xrefAlreadyExists = false;
				
				List<Map<KEGGKeys, String>> keggMaps = mappings.get(sourceReferenceIdentifier);
				
				for (Map<KEGGKeys, String> keggData : keggMaps)
				{
					// Use the KEGG Identifier (from the NAME line). If there is none, use the KEGG gene id, example: "hsa:12345"
					String keggIdentifier = keggData.get(KEGGKeys.KEGG_IDENTIFIER);
					String keggGeneIdentifier =  keggData.get(KEGGKeys.KEGG_GENE_ID);
					// If keggIdentifier AND keggGeneIdentifier have no value, throw an exception! Can't insert null/empty identifiers.
					if ( (keggIdentifier == null || keggIdentifier.trim().equals(""))
						&& (keggGeneIdentifier == null || keggGeneIdentifier.trim().equals("")) )
					{
						throw new Exception("KEGG Identifier cannot be NULL or empty!");
					}
					
					//String keggGeneIdentifier = keggData.get(KEGGKeys.KEGG_SPECIES) + ":" + keggData.get(KEGGKeys.KEGG_GENE_ID);
					// No longer need to include the species code here because it will be a part of the URL. See in KEGGReferenceDatabaseGenerator.generateSpeciesSpecificReferenceDatabases
					// That's where we create the URLs with the species code built-in to the URL.
					//
					// If the data we extracted already begins with a species code such as "hsa:" then we can remove it because
					// the ReferenceDatabase will contain a species code prefix in its accessUrl. If we *don't*
					// remove it, we'll end up with URLs like: "http://www.genome.jp/dbget-bin/www_bget?hsa:hsa:2309" and that is not valid
					keggGeneIdentifier = KEGGSpeciesCache.pruneKEGGSpeciesCode(keggGeneIdentifier);
					// If the original KEGG_IDENTIFIER key didn't have a value, use the KEGG GENE ID.
					if (keggIdentifier == null || keggIdentifier.trim().equals("") )
					{
						keggIdentifier = keggGeneIdentifier;
					}
					keggIdentifier = KEGGSpeciesCache.pruneKEGGSpeciesCode(keggIdentifier);
					StringBuilder xrefsSb = new StringBuilder();
					for (GKInstance xref : xrefs)
					{
						xrefsSb.append(" ").append(xref.getAttributeValue(ReactomeJavaConstants.identifier).toString());
						// We won't add a cross-reference if it already exists
						if (xref.getAttributeValue(ReactomeJavaConstants.identifier).toString().equals( keggIdentifier ))
						{
							xrefAlreadyExists = true;
							// Break out of the xrefs loop - we found an existing cross-reference that matches so there's no point 
							// in letting the loop run longer.
							// TODO: rewrite into a while-loop condition (I don't like breaks that much).
							break;
						}
					}
					logger.trace("xrefs:{}",xrefsSb.toString());
					if (!xrefAlreadyExists)
					{
						String keggDefinition = keggData.get(KEGGKeys.KEGG_DEFINITION);
						sourceIdentifiersWithNewIdentifier++;
						// Also need to add the keggDefinition and keggGeneID as "name" attributes.
						List<String> names = new ArrayList<String>(3);
						names.add(keggDefinition);
						names.add(keggGeneIdentifier);
						
						if (! keggIdentifier.equals(keggGeneIdentifier))
						{
							names.add(keggIdentifier);
						}
						Map<String,List<String>> extraAttributes = new HashMap<String,List<String>>(1);
						extraAttributes.put(ReactomeJavaConstants.name, names);
						logger.trace("For {}, creating new KEGG xref: {}",sourceReference.getDisplayName(), keggIdentifier);
						if (!this.testMode)
						{
							String targetDB = this.targetRefDB;
							if (keggIdentifier.startsWith("vg:"))
							{
								targetDB = "KEGG Gene (Viruses)";
								keggIdentifier = keggIdentifier.replaceFirst("vg:", "");
							}
							else if (keggIdentifier.startsWith("ad:"))
							{
								targetDB = "KEGG Gene (Addendum)";
								keggIdentifier = keggIdentifier.replaceFirst("ad:", "");
							}
							else
							{
								targetDB = KEGGReferenceDatabaseGenerator.generateKeggDBName(objectCache, String.valueOf(speciesID));
							}
							if (targetDB == null)
							{
								// targetDB = this.targetRefDB;
								logger.error("No KEGG DB Name could be obtained for this identifier: {}. Cross-reference will not be created", keggIdentifier);
							}
							if (!this.testMode && targetDB != null)
							{
								this.refCreator.createIdentifier(keggIdentifier, String.valueOf(sourceReference.getDBID()),targetDB, personID, this.getClass().getName(), speciesID, extraAttributes);
							}
						}
					}
					else
					{
						sourceIdentifiersWithExistingIdentifier++;
					}
				}
			}
			else
			{
				sourceIdentifiersWithNoMapping++;
			}
		}
		logger.info("{} reference creation summary: \n"
				+ "\t# {} IDs with a new {} identifier (a new {} reference was created): {};\n"
				+ "\t# {} identifiers which already had the same {} reference (nothing new was created): {};\n"
				+ "\t# {} identifiers not in the {} mapping file (no new {} reference was created for them): {} ",
				this.targetRefDB,
				this.sourceRefDB, this.targetRefDB, this.targetRefDB, sourceIdentifiersWithNewIdentifier,
				this.sourceRefDB, this.targetRefDB, sourceIdentifiersWithExistingIdentifier,
				this.sourceRefDB, this.targetRefDB, this.targetRefDB, sourceIdentifiersWithNoMapping);
	}

//	/**
//	 * Strips out the species code prefix from a KEGG identifier string.
//	 * @param identifier - the identifier string to prune
//	 * @return The identifier, minus any species code prefix that might have been there.
//	 */
//	private String pruneKEGGSpeciesCode(String identifier)
//	{
//		if (identifier != null && identifier.contains(":"))
//		{
//			String[] parts = identifier.split(":");
//			// Species code prefix will be the left-most part, if you split on ":".
//			// There could be *other* parts (such as "si" in "dre:si:ch73-368j24.13"), but the species code is what matters here.
//			String prefix = parts[0];
//			// remove the species code, IF it's in the list of known KEGG species codes.
//			if (KEGGSpeciesCache.getKeggSpeciesCodes().contains(prefix))
//			{
//				identifier = identifier.replaceFirst(prefix + ":", "");
//			}
//		}
//		return identifier;
//	}
}
