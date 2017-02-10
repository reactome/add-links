package org.reactome.addlinks.referencecreators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaAttribute;
import org.reactome.addlinks.fileprocessors.KEGGFileProcessor.KEGGKeys;

public class KEGGReferenceCreator extends SimpleReferenceCreator<List<Map<KEGGKeys, String>>>
{
	private static final Logger logger = LogManager.getLogger();
	
	public KEGGReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
	}

	@Override
	public void createIdentifiers(long personID, Map<String, List<Map<KEGGKeys, String>>> mappings, List<GKInstance> sourceReferences) throws Exception
	{
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
			for (GKSchemaAttribute attrib : (Collection<GKSchemaAttribute>) sourceReference.getSchemaAttributes())
			{
				if (attrib.getName().equals(ReactomeJavaConstants.species) )
				{
					GKInstance speciesInst = (GKInstance) sourceReference.getAttributeValue(ReactomeJavaConstants.species);
					if (speciesInst != null)
					{
						speciesID = new Long(speciesInst.getDBID());
					}
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
					String keggGeneIdentifier = keggData.get(KEGGKeys.KEGG_SPECIES) + ":" + keggData.get(KEGGKeys.KEGG_GENE_ID);
					
					if (keggIdentifier == null || keggIdentifier.trim().equals("") )
					{
						keggIdentifier = keggGeneIdentifier;
					}
					for (GKInstance xref : xrefs)
					{
						logger.trace("\tcross-reference: {}",xref.getAttributeValue(ReactomeJavaConstants.identifier).toString());
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
					if (!xrefAlreadyExists)
					{
						String keggDefinition = keggData.get(KEGGKeys.KEGG_DEFINITION);
						sourceIdentifiersWithNewIdentifier++;
						if (!this.testMode)
						{
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
							refCreator.createIdentifier(keggIdentifier, String.valueOf(sourceReference.getDBID()), this.targetRefDB, personID, this.getClass().getName(), speciesID, extraAttributes);
						}
						// Not only do we need to create a KEGG reference, we also need to
						// create a BRENDA reference and an IntEnz reference if there are
						// EC numbers present
						// TODO: Remove this once the stand-alone IntEnz and BRENDA code works Ok.
						/*
						String ecNumbers = keggData.get(KEGGKeys.EC_NUMBERS);
						if (ecNumbers != null && !ecNumbers.trim().equals(""))
						{
							for (String ecNumber : ecNumbers.split(" "))
							{
								if (!ecNumber.contains("-"))
								{
									// According to the old Perl code, the BRENDA reference should only be created if there are no dashes in the EC number.
									if (!this.testMode)
									{
										refCreator.createIdentifier(ecNumber, String.valueOf(sourceReference.getDBID()), "BRENDA", personID, this.getClass().getName(), speciesID);
									}
								}
								else
								{
									// According to the old Perl code, dashes should be removed and trailing "." should be removed.
									if (!this.testMode)
									{
										ecNumber = ecNumber.replace("-", "").replaceAll("\\.*$", "");
									}
								}
								// IntEnz reference is always created.
								if (!this.testMode)
								{
									refCreator.createIdentifier(ecNumber, String.valueOf(sourceReference.getDBID()), "IntEnz", personID, this.getClass().getName(), speciesID);
								}
							}
						}
						*/
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
