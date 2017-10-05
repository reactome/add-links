package org.reactome.addlinks.referencecreators;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;

/**
 * Creates PDB identifiers for UniProt IDs, based on DOCKBlaster's mapping.
 * @author sshorser
 *
 */
public class DOCKBlasterReferenceCreator  extends SimpleReferenceCreator<ArrayList<String>>
{
	public DOCKBlasterReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
	}
	
	
	public DOCKBlasterReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB, String refCreatorName)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, refCreatorName);
	}

	/**
	 * Creates identifiers, overrides parent-class implementation.
	 * @param personID - The ID of the person associated with the newly created identifiers.
	 * @param mapping - PDB IDs mapped to UniProt IDs, from DOCKBlaster. Could be 1:n mapping.
	 * @param sourceReferences - The list of UniProt IDs that are available in the database that we can map. We won't create PDB Identifiers
	 * that are mapped to UniProt Identifiers if those UniProt Identifiers are not in sourceReference.
	 */
	@Override
	public void createIdentifiers(long personID, Map<String, ArrayList<String>> mapping, List<GKInstance> sourceReferences) throws Exception
	{
		AtomicInteger uniprotsWithNoMapping = new AtomicInteger(0);
		AtomicInteger uniprotsWithNewIdentifier = new AtomicInteger(0);
		AtomicInteger uniprotsWithExistingIdentifier = new AtomicInteger(0);
		logger.traceEntry();
		
		Map<String, GKInstance> sourceMap = new HashMap<>(sourceReferences.size());
		
		//build a map of source objects
		for (GKInstance sourceInstance : sourceReferences)
		{
			//Pretty sure that identifier is always a single-valued attribute.
			sourceMap.put(sourceInstance.getAttributeValue(ReactomeJavaConstants.identifier).toString(), sourceInstance);
		}
		mapping.keySet().stream().sequential().forEach(pdbID -> {
			try
			{
				// For each UniProt ID that is in the source and also mapped from PDB (via DOCKBlaster)...
				List<String> uniProtIDs = mapping.get(pdbID).stream().parallel().filter(uniprotID -> sourceMap.containsKey(uniprotID)).collect(Collectors.toList());
				logger.trace("{}",uniProtIDs);
				for (String uniProtID : uniProtIDs)
				{
					// Check that this cross-reference doesn't already exist
					logger.trace("{} ID: {}; {} ID: {}", this.sourceRefDB, uniProtID, this.targetRefDB, pdbID);
					GKInstance sourceReference = sourceMap.get(uniProtID);
					// Look for cross-references.
					boolean xrefAlreadyExists = this.checkXRefExists(sourceReference, uniProtID);
					if (!xrefAlreadyExists)
					{
						logger.trace("\tNeed to create a new identifier!");
						uniprotsWithNewIdentifier.incrementAndGet();
						if (!this.testMode)
						{
							refCreator.createIdentifier(pdbID, String.valueOf(sourceReference.getDBID()), this.targetRefDB, personID, this.getClass().getName());
						}
					}
					else
					{
						uniprotsWithExistingIdentifier.incrementAndGet();
					}
				}
			}
			catch (InvalidAttributeException e)
			{
				e.printStackTrace();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		});

		logger.info("{} reference creation summary: \n"
				+ "\t# {} IDs with a new {} identifier (a new {} reference was created): {};\n"
				+ "\t# {} identifiers which already had the same {} reference (nothing new was created): {};\n"
				+ "\t# {} identifiers not in the {} mapping file (no new {} reference was created for them): {} ",
				this.targetRefDB,
				this.sourceRefDB, this.targetRefDB, this.targetRefDB, uniprotsWithNewIdentifier,
				this.sourceRefDB, this.targetRefDB, uniprotsWithExistingIdentifier,
				this.sourceRefDB, this.targetRefDB, this.targetRefDB, uniprotsWithNoMapping);
	}
}
