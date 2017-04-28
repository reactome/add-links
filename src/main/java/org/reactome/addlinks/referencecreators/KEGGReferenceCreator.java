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
					//String keggGeneIdentifier = keggData.get(KEGGKeys.KEGG_SPECIES) + ":" + keggData.get(KEGGKeys.KEGG_GENE_ID);
					// No longer need to include the species code here because it will be a part of the URL. See in KEGGReferenceDatabaseGenerator.generateSpeciesSpecificReferenceDatabases
					// That's where we create the URLs with the species code built-in to the URL.
					String keggGeneIdentifier =  keggData.get(KEGGKeys.KEGG_GENE_ID);
					
					if (keggIdentifier == null || keggIdentifier.trim().equals("") )
					{
						keggIdentifier = keggGeneIdentifier;
					}
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
						if (!this.testMode)
						{
							String targetDB = this.targetRefDB;
							targetDB = KEGGReferenceDatabaseGenerator.generateKeggDBName(objectCache, String.valueOf(speciesID));
							if (targetDB == null)
							{
								targetDB = this.targetRefDB;
							}
							if (!this.testMode)
							{
								refCreator.createIdentifier(keggIdentifier, String.valueOf(sourceReference.getDBID()),targetDB, personID, this.getClass().getName(), speciesID, extraAttributes);
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
