package org.reactome.addlinks.referencecreators;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;

public class TargetPathogenReferenceCreator extends SimpleReferenceCreator<String>
{
	private static final String REACTOME_IDENTIFIER_PATTERN_STRING = "R-[A-Z]{3}-.*";
	private static final Pattern REACTOME_IDENTIFIER_PATTERN = Pattern.compile(REACTOME_IDENTIFIER_PATTERN_STRING);

	// These sets are static so that the first instance of this class can populate them, and all other instances can
	// just rely on the data that has already been set.
	private static Map<String, Set<String>> reactomeToTargetPathogen = new HashMap<>();
	private static Map<String, Set<String>> uniProtToTargetPathogen = new HashMap<>();

	private long personID;

	public TargetPathogenReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
	}

	public TargetPathogenReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB, String refCreatorName)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, refCreatorName);
	}

	/**
	 * Can create references to Target Pathogen on reaction objects that are identifier by a Reactome identifier. We will also probably want to create a reference
	 * from proteins identified by UniProt identifiers. Might be tricky to combine this functionality into one ReferenceCreator, but we'll see...
	 */
	@Override
	public void createIdentifiers(long personID, Map<String, String> mapping, List<GKInstance> sourceReferences) throws Exception
	{
		this.personID = personID;
		if (reactomeToTargetPathogen.size() == 0 && uniProtToTargetPathogen.size() == 0)
		{
			this.logger.info("Identifier sets are empty, populating them now...");

			// the maps are static, shared between instances. Synchronized block should ensure that they cannot be accessed asynchronously.
			synchronized (this)
			{
				for (String targetPathogenIdentifier : mapping.keySet())
				{
					String[] mappedIdentifierParts = mapping.get(targetPathogenIdentifier).split("\\|");
					for (String identifier : mappedIdentifierParts)
					{
						// If the string matches the Reactome pattern, it is a Reactome identifier.
						if (REACTOME_IDENTIFIER_PATTERN.matcher(identifier).matches())
						{
							if (!TargetPathogenReferenceCreator.reactomeToTargetPathogen.containsKey(identifier))
							{
								TargetPathogenReferenceCreator.reactomeToTargetPathogen.put(identifier, new HashSet<>(Arrays.asList(targetPathogenIdentifier)));
							}
							else
							{
								Set<String> tmpSet = TargetPathogenReferenceCreator.reactomeToTargetPathogen.get(identifier);
								tmpSet.add(targetPathogenIdentifier);
								TargetPathogenReferenceCreator.reactomeToTargetPathogen.put(identifier, tmpSet);
							}
						}
						else
						{
							if (!TargetPathogenReferenceCreator.uniProtToTargetPathogen.containsKey(identifier))
							{
								TargetPathogenReferenceCreator.uniProtToTargetPathogen.put(identifier, new HashSet<>(Arrays.asList(targetPathogenIdentifier)));
							}
							else
							{
								Set<String> tmpSet = TargetPathogenReferenceCreator.uniProtToTargetPathogen.get(identifier);
								tmpSet.add(targetPathogenIdentifier);
								TargetPathogenReferenceCreator.uniProtToTargetPathogen.put(identifier, tmpSet);
							}
						}
					}
				}
			}
		}
		else
		{
			this.logger.info("Identifier sets appear to be already populated. Continuing with the process...");
		}
		this.logger.info("{} mappings in input (keyed by TargetPathogen identifier)", mapping.keySet().size());
		this.logger.info("{} identifiers in reactome-to-TargetPathogen map", reactomeToTargetPathogen.size());
		this.logger.info("{} identifiers in uniProt-to-TargetPathogen map", uniProtToTargetPathogen.size());
		// Now we need to go through the sourceReferences and create references based on reactomeIdentifiers and uniProtIdentifiers.
		// It might be necessary to have two instances of TargetPathogenReferenceCreator: one for creating cross-refs for Reactions and the other for creating cross-refs for Proteins.
		// But... that would not be terribly efficient.

		// So... which instance are we? We can determine from how the SourceRefDB and classReferring are set.
		if (this.sourceRefDB.equals("UniProt") && this.classReferringToRefName.equals(ReactomeJavaConstants.ReferenceGeneProduct))
		{
			this.createReferencesForSourceIdentifiers(sourceReferences, TargetPathogenReferenceCreator.uniProtToTargetPathogen, ReactomeJavaConstants.ReferenceGeneProduct);
		}
		else if (this.sourceRefDB.equals("Reactome") && this.classReferringToRefName.equals(ReactomeJavaConstants.Reaction))
		{
			this.createReferencesForSourceIdentifiers(sourceReferences, TargetPathogenReferenceCreator.reactomeToTargetPathogen, ReactomeJavaConstants.Reaction);
		}
		else
		{
			this.logger.error("sourceRefDB was: {} and classReferring was: {} - THIS IS NOT A VALID COMBINATION! Valid options are: UniProt/ReferenceGeneProduct and Reactome/Reaction.", this.sourceRefDB, this.classReferringToRefName);
		}
	}

	private static String getIdentifier(GKInstance instance) throws InvalidAttributeException, Exception
	{
		String identifier = null;
		if (instance.getSchemClass().getName().equals(ReactomeJavaConstants.Reaction))
		{
			GKInstance reactomeStableIdentifier = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
			identifier = (String) reactomeStableIdentifier.getAttributeValue(ReactomeJavaConstants.identifier);
		}
		else // Only really expecting ReactomeJavaConstants.ReferenceGeneProduct here, but I don't think it's necessary to strictly enforce that.
		{
			identifier = (String) instance.getAttributeValue(ReactomeJavaConstants.identifier);
		}

		return identifier;
	}

	/**
	 * Create references for Reactome Identifiers.
	 * @param sourceReferences A list of source-reference objects that are already in the database. New TargetPathogen identifiers identify these objects.
	 * @param mapToTargetPathogen A mapping of identifiers that map TO TargetPathogen, FROM some other source (Reactome or UniProt).
	 * @param typeToFilterFor The Type to filter for (should be a valid Reactome "class" from ReactomeJavaConstants).
	 * @throws Exception
	 */
	private void createReferencesForSourceIdentifiers(List<GKInstance> sourceReferences, Map<String, Set<String>> mapToTargetPathogen, String typeToFilterFor) throws Exception
	{
		int numRefsCreated = 0;
		int numRefsPreexisting = 0;
		int numTargetIdentifiers = 0;
		int[] counts = { numTargetIdentifiers, numRefsCreated, numRefsPreexisting };
		for (GKInstance sourceRef : sourceReferences.stream()
									.filter(inst -> inst.getSchemClass().getName().equals(typeToFilterFor)).collect(Collectors.toList()))
		{
			String sourceRefIdentifier = getIdentifier(sourceRef);
			if (mapToTargetPathogen.containsKey(sourceRefIdentifier))
			{
				for (String targetPathogenID : mapToTargetPathogen.get(sourceRefIdentifier))
				{
					counts = createReferencesForIdentifier(sourceRef, targetPathogenID, counts);
				}
			}
		}
		logPostReferenceCreationMessage(sourceReferences.size(), counts);
	}

	/**
	 * Logs a summary message.
	 * @param sourceReferencesSize - how many source references there were.
	 * @param counts - an array of counts of things. Must be ordered as: { numTargetIdentifiers, numRefsCreated, numRefsPreexisting }
	 */
	private void logPostReferenceCreationMessage(int sourceReferencesSize, int[] counts)
	{
		int numRefsCreated;
		int numRefsPreexisting;
		int numTargetIdentifiers;
		numTargetIdentifiers = counts[0];
		numRefsCreated = counts[1];
		numRefsPreexisting = counts[2];
		this.logger.info("Inspected {} objects, {} identifiers for TargetPathogen.\n"
				+ " {} new references were created.\n"
				+ "{} references already existed (and were NOT re-created).",
				sourceReferencesSize, numTargetIdentifiers, numRefsCreated, numRefsPreexisting);
	}

	/**
	 * Creates the reference and updates the counts.
	 * @param sourceRef - The thing that will be referenced by the cross-reference.
	 * @param targetPathogenID - The ID for Target Pathogen.
	 * @param counts - Counts, in an array. Must be ordered as: { numTargetIdentifiers, numRefsCreated, numRefsPreexisting }
	 * @return An array of updated counts.
	 * @throws Exception
	 */
	private int[] createReferencesForIdentifier(GKInstance sourceRef, String targetPathogenID, int[] counts) throws Exception
	{
		int numTargetIdentifiers = counts[0];
		int numRefsCreated = counts[1];
		int numRefsPreexisting = counts[2];

		numTargetIdentifiers++;
		// Check that the cross-reference doesn't exist.
		if (!this.checkXRefExists(sourceRef, targetPathogenID))
		{
			if (!this.testMode)
			{
				this.refCreator.createIdentifier(targetPathogenID, sourceRef.getDBID().toString(), this.targetRefDB, this.personID, this.getClass().getName());
			}
			numRefsCreated++;
		}
		else
		{
			numRefsPreexisting++;
		}
		return new int[]{ numTargetIdentifiers, numRefsCreated, numRefsPreexisting };
	}
}
