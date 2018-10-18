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
					// But... there's also a possibility that we will need to create a new KEGG Reference Database on-the-fly. This could happen
					// if the species names in KEGG don't quite match the names in Reactome. So if the names don't match, why create a Reference Database?
					// Because we got this KEGG identifier as a result of a valid mapping, so it MUST be a valid KEGG identifier that we can map to, 
					// and the problem is in the species. Since we can't modify the species to match the KEGG species, we'll just create a new Reference Database.
					String keggPrefix = KEGGSpeciesCache.extractKEGGSpeciesCode(keggGeneIdentifier);
					if (keggPrefix == null && (keggIdentifier != null && keggIdentifier.trim().equals("")))
					{
						keggPrefix = KEGGSpeciesCache.extractKEGGSpeciesCode(keggIdentifier);
					}
					if (keggPrefix == null || (keggData.get(KEGGKeys.KEGG_SPECIES) != null && !keggPrefix.equals(keggData.get(KEGGKeys.KEGG_SPECIES))))
					{
						// Use the KEGG SPECIES if it was not possible to extract the code from the identifier/gene identifier OR if there's a mismatch between
						// the extracted code and the species.
						keggPrefix = keggData.get(KEGGKeys.KEGG_SPECIES);
					}
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
							String targetDB = null;
							// "vg:" and "ad:" aren't in the species list because they are not actually species. So that's why it's OK to check for them here, after
							// the identifier has already been pruned.
							if (keggIdentifier.startsWith("vg:"))
							{
								targetDB = "KEGG Gene (Viruses)";
								keggIdentifier = keggIdentifier.replaceFirst("vg:", "");
							}
							else if (keggIdentifier.startsWith("ag:"))
							{
								targetDB = "KEGG Gene (Addendum)";
								keggIdentifier = keggIdentifier.replaceFirst("ag:", "");
							}
							else
							{
								Long targetDBID = KEGGReferenceDatabaseGenerator.getKeggReferenceDatabase(keggPrefix);
								if (targetDBID != null)
								{
									targetDB = targetDBID.toString();
								}
								// If targetDB is STILL NULL, it means we weren't able to determine which KEGG ReferenceDatabase to use for this keggIdentifier. 
								// So, we can't add the cross-reference since we don't know which species-specific ReferenceDatabase to use. 
								if (targetDB == null)
								{
									logger.warn("No KEGG DB Name could be obtained for this identifier: {}. The next step is to try to create a *new* ReferenceDatabase.", keggIdentifier);
									
									if (keggPrefix != null)
									{
										targetDB = createNewKEGGReferenceDatabase(objectCache, keggIdentifier, keggPrefix);
									}
								}
//								targetDB = KEGGReferenceDatabaseGenerator.generateDBNameFromKeggSpeciesCode(objectCache, keggPrefix);
//								if (targetDB == null)
//								{
//									targetDB = KEGGReferenceDatabaseGenerator.generateDBNameFromReactomeSpecies(objectCache, String.valueOf(speciesID));	
//								}
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

	private synchronized  String createNewKEGGReferenceDatabase(ReferenceObjectCache objectCache, String keggIdentifier, String keggPrefix)
	{
		String targetDB = null;
		// we have a valid KEGG prefix, so let's try to use that to create a new RefereneDatabase.
		String keggSpeciesName = KEGGSpeciesCache.getSpeciesName(keggPrefix);
		if (keggSpeciesName != null)
		{
			targetDB = KEGGReferenceDatabaseGenerator.createReferenceDatabaseFromKEGGData(keggPrefix, keggSpeciesName, objectCache);
			objectCache.rebuildRefDBNamesAndMappings();
		}
		if (targetDB == null)
		{
			logger.error("Could not create a new KEGG ReferenceDatabase for the KEGG code {} for KEGG species \"{}\". Identifier {} will not be added, since there is no ReferenceDatabase for it.", keggPrefix, keggSpeciesName, keggIdentifier);
		}
		return targetDB;
	}
}
