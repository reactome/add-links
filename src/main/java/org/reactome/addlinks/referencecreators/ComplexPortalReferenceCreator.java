package org.reactome.addlinks.referencecreators;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.MySQLAdaptor.AttributeQueryRequest;

public class ComplexPortalReferenceCreator extends SimpleReferenceCreator<List<String>>
{
	public ComplexPortalReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring,
			String referringAttribute, String sourceDB, String targetDB, String refCreatorName)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, refCreatorName);
	}
	
	@Override
	public void createIdentifiers(long personID, Map<String,List<String>> mappings, List<GKInstance> sourceReferences) throws IOException
	{
		AtomicInteger createdCounter = new AtomicInteger(0);
		AtomicInteger notCreatedCounter = new AtomicInteger(0);
		AtomicInteger xrefAlreadyExistsCounter = new AtomicInteger(0);
		AtomicInteger referencingReactome = new AtomicInteger(0);
		AtomicInteger referencingUniprot = new AtomicInteger(0);
		
		for (String complexPortalID : mappings.keySet())
		{
			try
			{
				// A ComplexPortal ID could have mappings to Uniprot, Reactome, or both.
				for (String uniprotOrReactomeIdentifier : mappings.get(complexPortalID))
				{
					// TODO: Refactor the code in the if-else blocks - it can probably be simplified to a single function.
					// If Reactome identifier...
					if (uniprotOrReactomeIdentifier.matches("R-[a-zA-Z]{3}.+"))
					{
						// Now, we need to find this instance identified by uniprotOrReactomeIdentifier
						@SuppressWarnings("unchecked")
						Collection<GKInstance> instances = (Collection<GKInstance>) this.adapter.fetchInstanceByAttribute(ReactomeJavaConstants.Complex, ReactomeJavaConstants.identifier, "=", uniprotOrReactomeIdentifier);
						// should only be one, but the function returns a collection...
						for (GKInstance instance : instances)
						{
							// check that the reference is not already there.
							if (!this.checkXRefExists(instance, complexPortalID))
							{
								Long speciesID = ((GKInstance)instance.getAttributeValue(ReactomeJavaConstants.species)).getDBID();
								if (!this.testMode)
								{
									// Updating the classReferringToRefName is necessary because if the identifier is a Reactome identifier, we're dealing with a Complex,
									// but if it's a Uniprot identifier, then we're dealing with some other PhysicalEntity, probably a ReferenceGeneProduct or EntityWithAccessionedSequence
									this.setClassReferringToRefName(instance.getSchemClass().getName());
									this.refCreator.createIdentifier(uniprotOrReactomeIdentifier, String.valueOf(instance.getDBID()), targetRefDB, personID, this.getClass().getName(), speciesID);
								}
								createdCounter.incrementAndGet();
								referencingReactome.incrementAndGet();
							}
							else
							{
								xrefAlreadyExistsCounter.incrementAndGet();
							}
						}
					}
					// else it's a Uniprot
					else
					{
						// Now, we need to find this instance identified by uniprotOrReactomeIdentifier
						@SuppressWarnings("unchecked")
						Collection<GKInstance> instances = (Collection<GKInstance>) this.adapter.fetchInstanceByAttribute(ReactomeJavaConstants.PhysicalEntity, ReactomeJavaConstants.identifier, "=", uniprotOrReactomeIdentifier);
						// should only be one, but the function returns a collection...
						for (GKInstance instance : instances)
						{
							// check that the reference is not already there.
							if (!this.checkXRefExists(instance, complexPortalID))
							{
								Long speciesID = ((GKInstance)instance.getAttributeValue(ReactomeJavaConstants.species)).getDBID();
								if (!this.testMode)
								{
									// Updating the classReferringToRefName is necessary because if the identifier is a Reactome identifier, we're dealing with a Complex,
									// but if it's a Uniprot identifier, then we're dealing with some other PhysicalEntity, probably a ReferenceGeneProduct or EntityWithAccessionedSequence
									this.setClassReferringToRefName(instance.getSchemClass().getName());
									this.refCreator.createIdentifier(uniprotOrReactomeIdentifier, String.valueOf(instance.getDBID()), targetRefDB, personID, this.getClass().getName(), speciesID);
								}
								createdCounter.incrementAndGet();
								referencingUniprot.incrementAndGet();
							}
							else
							{
								xrefAlreadyExistsCounter.incrementAndGet();
							}
						}
					}
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		logger.info("{} Reference creation summary:\n"
				+ "\t# Identifiers created: {}; {} for Reactome identifiers, {} for Uniprot identifiers\n"
				+ "\t# Identifiers which already existed: {} \n"
				+ "\t# Identifiers that were not created: {}",
				this.targetRefDB, 
				createdCounter.get(), referencingReactome.get(), referencingUniprot.get(), xrefAlreadyExistsCounter.get(), notCreatedCounter.get());

	}
}