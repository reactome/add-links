package org.reactome.addlinks.referencecreators;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

public class IntActReferenceCreator extends SimpleReferenceCreator<String>
{
	private static final Logger logger = LogManager.getLogger();
	public IntActReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring,
			String referringAttribute, String sourceDB, String targetDB)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
	}
	
	public void createIdentifiers(long personID, Map<String,String> mappings, List<GKInstance> sourceReferences) throws IOException
	{
		for (String intActID : mappings.keySet())
		{
			String reactomeStableIdentifier = mappings.get(intActID);
			try
			{
				// we need to be able to get a real instance for the ID that is mapped to by intActID
				GKInstance instance = (GKInstance) this.adapter.fetchInstanceByAttribute(ReactomeJavaConstants.StableIdentifier, ReactomeJavaConstants.identifier, "=", reactomeStableIdentifier);
				// If the Reactome value in the mapping file is a legitimate StableIdentifier, then proceed.
				if (instance!=null)
				{
					// References will be created in Complex_2_interactionIdentifier, referencing DatabasIdentifier.
					// From an object point-of-view, we will be adding DatabaseIdentifiers as interactionIdentifiers to Complexes.
					this.refCreator.createIdentifier(intActID, String.valueOf(instance.getDBID()), this.getTargetRefDB(), personID, this.getClass().getName(), (Long)instance.getAttributeValue(ReactomeJavaConstants.species));
					
					// should we include a "generate interactions" flag like the old AddLinks, or just do it? For now, just do it!
					
					// Next: find interactions with ReferenceGeneProducts, those should be in sourceReferences, should be all UniProt IDs
					// I think this is what needs to happen:
					// Go through ALL UniProt references in the database.
					// If a UniProt ID has a StableIdentifier that is in "mappings" (as a value) that means it is in an interaction.
					// In this case, find PhysicalEntites the refer to the ReferenceEntitythat the UniProt ID refers to.
					
					
				}
				// The old code would try to do a lookup to see if mappings.get(intActID) was a db_id instead of a stable identifier.
				// The data file doesn't seem to contain ANY DB_IDs so that's probably just some old legacy stuff. 
				else
				{
					logger.warn("The Identifier {} was in the IntAct data file, but performing a lookup in Reactome returned nothing!", reactomeStableIdentifier);
				}
				
			}
			catch (Exception e)
			{
				e.printStackTrace();
				throw new Error(e);
			}
		}

	}
}