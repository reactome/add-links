package org.reactome.addlinks.referencecreators;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.sound.midi.SoundbankResource;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaAttribute;
import org.gk.schema.InvalidAttributeException;
import org.reactome.addlinks.db.ReferenceObjectCache;

/**
 * GeneCards is very much like the 1:1 reference creator, but it must filter on only Human instances
 * @author sshorser
 *
 */
public class GeneCardsReferenceCreators extends OneToOneReferenceCreator
{
	private ReferenceObjectCache refObjectCache = new ReferenceObjectCache(this.adapter,true);
	
	public GeneCardsReferenceCreators(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB, String refCreatorName)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, refCreatorName);
	}

	public GeneCardsReferenceCreators(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
	}

	@Override
	public void createIdentifiers(long personID, Map<String, Object> mapping, List<GKInstance> sourceReferences) throws Exception
	{
		logger.warn("This function does not make use of the \"mapping\" parameter. The override \"createIdentifiers(long personID, List<GKInstance> sourceReferences)\". will be called for you. Just so you know...");
		this.createIdentifiers(personID, sourceReferences);
	}

	private Long getSpeciesForInstance(GKInstance inst) throws InvalidAttributeException, Exception
	{
		Long speciesID = -1L;
		@SuppressWarnings("unchecked")
		Collection<GKSchemaAttribute> attributes = (Collection<GKSchemaAttribute>) inst.getSchemClass().getAttributes();
		if ( attributes.stream().filter(attr -> attr.getName().equals(ReactomeJavaConstants.species)).findFirst().isPresent())
		{
			GKInstance speciesInst = (GKInstance) inst.getAttributeValue(ReactomeJavaConstants.species);
			
			if (speciesInst != null)
			{
				speciesID = new Long(speciesInst.getDBID());
			}
		}
		return speciesID;
	}

	
	@Override
	/**
	 * Overrides the default createIdentifiers function so that it can filter by species.
	 */
	public void createIdentifiers(long personID, List<GKInstance> sourceReferences) throws Exception
	{
		logger.info("{} possible source references.", sourceReferences.size());
		List<GKInstance> filteredInstances = sourceReferences
												.parallelStream()
												.filter(inst ->
												{
													try
													{
														List<String> speciesNames = this.refObjectCache.getSpeciesNamesByID().get(this.getSpeciesForInstance(inst).toString());
														if (speciesNames != null && speciesNames.size() > 0 && speciesNames.contains("Homo sapiens"))
														{
															return true;
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
													return false;
												}).collect(Collectors.toList()) ;
		logger.info("{} source references, after filtering for species \"Homo sapiens\".", filteredInstances.size());
		super.createIdentifiers(personID, filteredInstances);
	}
	
	
}
