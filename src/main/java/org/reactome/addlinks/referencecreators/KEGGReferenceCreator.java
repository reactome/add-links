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
				boolean xrefAlreadyExists = false;
				
				List<Map<KEGGKeys, String>> keggMaps = mappings.get(sourceReferenceIdentifier);
				
				for (Map<KEGGKeys, String> keggData : keggMaps)
				{
					// Use KEGG Gene ID (from ENTRY line). If not available, use KEGG Identifier (from NAME line).
					String keggIdentifier = keggData.get(KEGGKeys.KEGG_IDENTIFIER);
					String keggGeneIdentifier =  keggData.get(KEGGKeys.KEGG_GENE_ID);
					// If keggIdentifier AND keggGeneIdentifier have no value, throw an exception! Can't insert null/empty identifiers.
					if ( (keggIdentifier == null || keggIdentifier.trim().equals(""))
						&& (keggGeneIdentifier == null || keggGeneIdentifier.trim().equals("")) )
					{
						throw new Exception("KEGG Identifier cannot be NULL or empty!");
					}
					
					// NOTE: If the identifier to use begins with a species prefix, it should be removed since we will
					// include a species prefix in the accessUrl of each KEGG species-specific ReferenceDatabase.
					// If we *don't* remove species codes from identifiers, we'll end up with URLs with doubled species codes
					// like: "http://www.genome.jp/dbget-bin/www_bget?hsa:hsa:2309" and that is not valid
					//
					// But... there's also a possibility that we will need to create a new KEGG Reference Database on-the-fly. This could happen
					// if the species names in KEGG don't quite match the names in Reactome. So if the names don't match, why create a Reference Database?
					// Because we got this KEGG identifier as a result of a successful mapping operation, so it MUST be a valid KEGG identifier that we can map to, 
					// and the problem is in the species names. Since we can't modify the Species data in Reactome to match the KEGG species, we'll just create a new Reference Database.
					
					// Use the KEGG Species for the prefix.
					String keggPrefix = keggData.get(KEGGKeys.KEGG_SPECIES);
					if (keggPrefix == null && (keggIdentifier != null && keggIdentifier.trim().equals("")))
					{
						// if somehow there was no species (probably should never happen, but KEGG data has surprised me before... *shrug*),
						// extract species code from the kegg Identifier (from the NAME line)
						keggPrefix = KEGGSpeciesCache.extractKEGGSpeciesCode(keggIdentifier);
					}
					// If prefix is STILL null try to extract from KEGG Gene ID (ENTRY line)
					if (keggPrefix == null)
					{
						keggPrefix = KEGGSpeciesCache.extractKEGGSpeciesCode(keggGeneIdentifier);
					}

//					// If the original KEGG_IDENTIFIER key didn't have a value, use the KEGG GENE ID.
					if (keggIdentifier == null || keggIdentifier.trim().equals("") )
					{
						keggIdentifier = keggGeneIdentifier;
					}

					logger.trace("Working on source object: {}", sourceReference.toString());
					String targetDB = null;
					KEGGReferenceCreatorHelper referenceCreatorHelper = new KEGGReferenceCreatorHelper(objectCache, this.logger);
					String[] parts = referenceCreatorHelper.determineKeggReferenceDatabase(keggGeneIdentifier, keggPrefix);
					targetDB = parts[0];
					// now, the identifier should have been cleaned up by determineKeggReferenceDatabase
					keggGeneIdentifier = parts[1];
					xrefAlreadyExists = this.checkXRefExists(sourceReference, keggGeneIdentifier, targetDB);
					if (!xrefAlreadyExists)
					{
						String keggDefinition = keggData.get(KEGGKeys.KEGG_DEFINITION);
						sourceIdentifiersWithNewIdentifier++;
						// Also need to add the keggDefinition and keggGeneID as "name" attributes.
						List<String> names = new ArrayList<String>(3);
						names.add(keggDefinition);
						names.add(keggGeneIdentifier);
						keggIdentifier = KEGGSpeciesCache.pruneKEGGSpeciesCode(keggIdentifier);
						if (! keggIdentifier.equals(keggGeneIdentifier))
						{
							names.add(keggIdentifier);
						}
						Map<String,List<String>> extraAttributes = new HashMap<String,List<String>>(1);
						extraAttributes.put(ReactomeJavaConstants.name, names);
						logger.trace("For {}, creating new KEGG xref: {}",sourceReference.getDisplayName(), keggIdentifier);
						if (!this.testMode)
						{
							if (!this.testMode && targetDB != null)
							{
								this.refCreator.createIdentifier(keggGeneIdentifier, String.valueOf(sourceReference.getDBID()),targetDB, personID, this.getClass().getName(), speciesID, extraAttributes);
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

}
