package org.reactome.addlinks.referencecreators;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.swing.event.ListSelectionEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaAttribute;

public class IntActReferenceCreator extends SimpleReferenceCreator<String>
{
	private static final Logger logger = LogManager.getLogger();
	public IntActReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring,
			String referringAttribute, String sourceDB, String targetDB)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
	}
	
	public void createIdentifiers(long personID, Map<String,String> mappings, List<GKInstance> sourceReferences) throws Exception
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
					// In this case, find PhysicalEntites the refer to the ReferenceEntity that the UniProt ID refers to.
					
					// See: IntActDatabaseIdentifierToComplexOrReactionlikeEvent.pm:183 "my $interactions_hash = $interaction_generator->find_interactors_for_ReferenceSequences([$refpep], 3, undef, ++$idx);"
					// We are calling find_interactors_for_ReferenceSequences with mitab == undef so we only have to reproduce the functionality of InteractionGenerator.find_interactors_for_ReferenceSequence
					
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
		
		// The changes above will need to be committed to the database before running the interaction-generating code below.
		// The object cache will also probably need to be reloaded, and possibly also the sourceReferences variable.
		this.adapter.commit();
		
		for (GKInstance refGeneProduct : sourceReferences)
		{
			// load the referenceEntitites referring to *instance*. ReferenceGeneProducts could be referred to by an OpenSet, via the OpenSet's referenceEntity attribute.
			this.adapter.loadInstanceReverseAttributeValues((Collection<GKInstance>)Arrays.asList(refGeneProduct) , new String[]{ ReactomeJavaConstants.referenceEntity } );
			
			// now look at the PhysicalEntities that were loaded by loadInstanceReverseAttributeValues, put them into an array if they have *this* instance pointed to by their hasMember attribute
			// We want to get the EntitySet objects (or objects that are subtypes of EntitySet) that refer to this via their hasMember attribute.
			@SuppressWarnings("unchecked")
			Collection<GKInstance> referringPhysicalEntities = (Collection<GKInstance>)refGeneProduct.getReferers(ReactomeJavaConstants.hasMember);
			List<GKInstance> physicalEntitiesOfInterest = new ArrayList<GKInstance>(referringPhysicalEntities);
			for (GKInstance physicalEntity : referringPhysicalEntities)
			{
				// According to the old Perl code, we're interested in PhysicalEntity referers. TODO: add this as a filter to the lookup above.
				if (physicalEntity.getSchemClass().getName().equals(ReactomeJavaConstants.PhysicalEntity))
				{
					// Get the components of this referrer.
					this.adapter.loadInstanceReverseAttributeValues( (Collection<GKInstance>)Arrays.asList(physicalEntity) , new String[]{ ReactomeJavaConstants.hasComponent });
					physicalEntitiesOfInterest.addAll( (Collection<GKInstance>) physicalEntity.getReferers(ReactomeJavaConstants.hasComponent) );
				}
			}
		}
	}
	
	private int participatingProteinCount(GKInstance... complexes)
	{
		int count = 0;
		try
		{
			for (GKInstance complex : complexes)
			{
				// count all Complexes that reference this *complex* via hasComponent
				count += ((Collection<GKInstance>)complex.getReferers(ReactomeJavaConstants.hasComponent))
														.stream()
														.filter( instance -> instance.getSchemClass().getName().equals(ReactomeJavaConstants.Complex))
														.count();
				
				// count all EntitySets that reference this *complex* via hasMember
				count += ((Collection<GKInstance>)complex.getReferers(ReactomeJavaConstants.hasMember))
														.stream()
														.filter( instance -> instance.getSchemClass().getName().equals(ReactomeJavaConstants.EntitySet))
														.count();
				
				// count all Polymers that reference this *complex* via repeatedUnit
				count += ((Collection<GKInstance>)complex.getReferers(ReactomeJavaConstants.repeatedUnit))
														.stream()
														.filter( instance -> instance.getSchemClass().getName().equals(ReactomeJavaConstants.Polymer))
														.count();
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new Error(e);
		}
		return count;
	}
}