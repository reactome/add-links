package org.reactome.addlinks.referencecreators;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.reactome.addlinks.db.ReferenceObjectCache;

public class COSMICReferenceCreator extends SimpleReferenceCreator<String>
{

	ReferenceObjectCache refObjectCache = new ReferenceObjectCache(this.adapter, true);
	
	public COSMICReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
	}
	
	public COSMICReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB, String refCreatorName)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, refCreatorName);
	}

	/**
	 * COSMIC Reference creation isn't really based on a mapping. We will get a list of gene names from the COSMIC file.
	 * We then need to create a cross-reference to COSMIC for every ReferenceGeneProduct whose gene name is in the list of
	 * gene names.
	 * Also, We'll output a message if there is a "mapping" from COSMIC Gene Name to HGNC Gene ID where either COSMIC Gene Name != HGNC Gene ID
	 * or HGNC Gene ID == null. We'll also print a message for every COSMIC gene that we can't find in the Reactome database.
	 * @param mapping - A mapping of COSMIC Gene Names to HGNC IDs (according to COSMIC, these *almost* always are the same thing).
	 * @param sourceReferences - a list of ReferenceGeneProducts. If one of these has a Gene Name that is in mapping then a new cross-reference will be created.
	 */
	@Override
	public void createIdentifiers(long personID, Map<String, String> mapping, List<GKInstance> sourceReferences) throws Exception
	{
		int sourceIdentifiersWithNoMapping = 0;
		int sourceIdentifiersWithNewIdentifier = 0;
		int sourceIdentifiersWithExistingIdentifier = 0;
		
		// We will also keep track of which COSMIC gene names we don't actually have in Reactome.
		List<String> addedToReactome = new ArrayList<String>();
		
		for (GKInstance inst : sourceReferences)
		{
			Long species = ((GKInstance) inst.getAttributeValue(ReactomeJavaConstants.species)).getDBID();
			// First let's make sure this Gene is human.
			if ( this.refObjectCache.getSpeciesNamesToIds().get("Homo sapiens").contains(species.toString()) )
			{
				// Let's make sure the gene name is valid.
				String geneName = inst.getAttributeValue(ReactomeJavaConstants.geneName) != null ? inst.getAttributeValue(ReactomeJavaConstants.geneName).toString() : null;
				if (geneName != null)
				{
					// If the mapping has the name of *this* gene, we'll create a reference to COSMIC, since COSMIC is keyed by gene names.
					if ( mapping.containsKey(geneName) )
					{
						if (!this.checkXRefExists(inst, geneName))
						{
							// Let's check the HGNC ID - it should match the COSMIC gene name. If it doesn't (that *could* happen, according to the documentation) we'll log a warning message.
							if ( !mapping.get(geneName).equals(geneName) )
							{
								logger.warn("Found a COSMIC gene with COSMIC Gene ID: {} and mismatching HGNC ID: {} - we'll create the reference to COSMIC using the COSMIC Gene ID.", geneName, mapping.get(geneName));
							}
							this.refCreator.createIdentifier(geneName, String.valueOf(inst.getDBID()), this.targetRefDB, personID, this.getClass().getName());
							addedToReactome.add(geneName);
							sourceIdentifiersWithNewIdentifier++;
						}
						else
						{
							sourceIdentifiersWithExistingIdentifier++;
						}
					}
				}
				else
				{
					sourceIdentifiersWithNoMapping ++;
				}
			}
		}
		
		List<String> inCosmicNotInReactome = mapping.keySet().stream().filter( p -> !addedToReactome.contains(p)).collect(Collectors.toList());
		
		logger.info("Gene names in COSMIC that are not in Reactome: ");
		inCosmicNotInReactome.stream().sequential().forEach( geneName -> logger.info("{}",geneName));
		
		
		logger.info("{} reference creation summary: \n"
				+ "\t# {} IDs with a new {} identifier (a new {} reference was created): {};\n"
				+ "\t# {} identifiers which already had the same {} reference (nothing new was created): {};\n"
				+ "\t# {} identifiers not in the {} mapping file (no new {} reference was created for them): {}\n"
				+ "\t# {} keys in input mapping: {}\n"
				+ "\t# existing {} input source identifiers: {}",
				this.targetRefDB,
				this.sourceRefDB, this.targetRefDB, this.targetRefDB, sourceIdentifiersWithNewIdentifier,
				this.sourceRefDB, this.targetRefDB, sourceIdentifiersWithExistingIdentifier,
				this.sourceRefDB, this.targetRefDB, this.targetRefDB, sourceIdentifiersWithNoMapping,
				this.sourceRefDB, mapping.keySet().size(),
				this.sourceRefDB, sourceReferences.size());
	}
}
