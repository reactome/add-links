package org.reactome.addlinks.referencecreators;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

import java.util.*;

public class EnsemblReferenceCreator extends SimpleReferenceCreator<Map<String, List<String>>>{

    public EnsemblReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB)
    {
        super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
    }

    public EnsemblReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB, String refCreatorName)
    {
        super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, refCreatorName);
    }

    //TODO: Add unit tests

    /**
     * Find all Ensembl identifiers connected to an RGP instance in the Reactome DB and create a DatabaseIdentifier cross-reference instance for them.
     * @param personID - Newly created Identifiers will be associated with an InstanceEdit. That InstanceEdit will be associated with the Person entity whose ID == personID
     * @param mappings - Large mapping structure generated in FileProcessor step.
     * @param sourceReferences - A list of GKInstance objects that were in the database. References will be created if they are in the keys of mapping and also in sourceReferences.
     * This is to handle cases where a mapping comes from a third-party file that may contain source identifiers that are in *their* system but aren't actually in Reactome.
     * @throws Exception
     */
    @Override
    public void createIdentifiers(long personID, Map<String, Map<String, List<String>>> mappings, List<GKInstance> sourceReferences) throws Exception {

        // Iterate through each ReferenceGeneProduct (RGP) instance in the DB.
        logger.info("Creating Ensembl cross-references");
        for (GKInstance sourceInst : sourceReferences) {
            String sourceIdentifier = (String) sourceInst.getAttributeValue(ReactomeJavaConstants.identifier);
            // Determine which species the instance pertains too. Retrieve corresponding mapping structures.
            GKInstance speciesInst = (GKInstance) sourceInst.getAttributeValue(ReactomeJavaConstants.species);
            String biomartSpeciesName = getBiomartSpeciesName(speciesInst.getDisplayName());
            // Iterate through each Ensembl Protein identifier. Retrieve any corresponding Transcript and Gene
            // identifiers and add each to a set. These will be used to create cross references to Ensembl from Reactome.
            Set<String> ensemblIds = collectEnsemblIdentifiers(mappings, sourceIdentifier, biomartSpeciesName);
            // Iterate through each Ensembl identifier and create a ? instance.
            for (String ensemblId : ensemblIds) {
                if (!this.checkXRefExists(sourceInst, ensemblId))
                {
                    if (!this.testMode)
                    {
                        this.refCreator.createIdentifier(ensemblId, String.valueOf(sourceInst.getDBID()), targetRefDB, personID, this.getClass().getName(), speciesInst.getDBID());
                    }
                }
            }
        }
    }

    /**
     * Retrieve all Ensembl Gene, Transcript and Protein identifiers associated with the source instance identifier.
     * @param mappings Large mapping structure generated in FileProcessor step.
     * @param sourceIdentifier Identifier taken from RGP instance.
     * @param biomartSpeciesName Species name in biomart format (eg: hsapiens).
     * @return Filtered Set of Ensembl identifiers (Gene, Transcript and Protein) associated with RGP identifier.
     */
    private Set<String> collectEnsemblIdentifiers(Map<String, Map<String, List<String>>> mappings, String sourceIdentifier, String biomartSpeciesName) {
        String proteinToTranscriptsKey = biomartSpeciesName + "_proteinToTranscripts";
        String proteinToGenesKey = biomartSpeciesName + "_proteinToGenes";
        String uniprotToENSPKey = biomartSpeciesName + "_uniprotToProteins";
        // Check that the 'uniprotToProteins' mapping exists and that the source instance's
        // 'identifier' attribute maps to ensembl protein identifiers.
        Set<String> ensemblIds = new HashSet<>();
        if (identifierHasMapping(mappings, uniprotToENSPKey, sourceIdentifier)) {
            // Retrieve all ensembl protein identifiers that map to the source instance identifier and store in 'ensIds'.
            List<String> enspIds = mappings.get(uniprotToENSPKey).get(sourceIdentifier);
            for (String enspId : enspIds) {
                // Only store Ensembl protein identifiers.
                if (enspId.startsWith("ENS")) {
                    ensemblIds.add(enspId);
                }
                // Retrieve any Ensembl transcript identifiers that map to the Ensembl protein identifier and store in 'ensIds'.
                if (identifierHasMapping(mappings, proteinToTranscriptsKey, enspId)) {
                    ensemblIds.addAll(collectEnsemblIdentifiers(mappings.get(proteinToTranscriptsKey).get(enspId)));
                }
                // Retrieve any Ensembl gene identifiers that map to the Ensembl protein identifier and store in 'ensIds'.
                if (identifierHasMapping(mappings, proteinToGenesKey, enspId)) {
                    ensemblIds.addAll(collectEnsemblIdentifiers(mappings.get(proteinToGenesKey).get(enspId)));
                }

            }
        }
        return ensemblIds;
    }

    // Filter non-Ensembl identifiers from supplied List.
    private Collection<String> collectEnsemblIdentifiers(List<String> ensIds) {
        Set<String> filteredEnsemblIds = new HashSet<>();
        for (String ensId : ensIds) {
            // Only store 'Ensembl' identifiers.
            if (ensId.startsWith("ENS")) {
                filteredEnsemblIds.add(ensId);
            }
        }
        return filteredEnsemblIds;
    }


    // Check that the supplied identifier has a mapping in the 'mappings' structure.
    private boolean identifierHasMapping(Map<String, Map<String, List<String>>> mappings, String outerKey, String identifier) {
        return mappings.containsKey(outerKey) && mappings.get(outerKey).containsKey(identifier);
    }

    // Update species name attribute to Ensembl Biomart format (eg: Homo sapiens --> hsapiens).
    private String getBiomartSpeciesName(String speciesName) {
        return speciesName.substring(0,1).toLowerCase() + speciesName.split(" ")[1];
    }
}
