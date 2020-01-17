package org.reactome.addlinks.referencecreators;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EnsemblReferenceCreator extends SimpleReferenceCreator<Map<String, List<String>>>{

    public EnsemblReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB)
    {
        super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
    }

    public EnsemblReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB, String refCreatorName)
    {
        super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, refCreatorName);
    }

    //TODO: Add logging
    //TODO: Add commenting
    //TODO: Add unit tests
    //TODO: Rewrite variable names
    //TODO: Global variables for file names
    //TODO: Functional refactor
    //TODO: Function-level commenting
    //TODO: Verify which instance types are being handled.

    @Override
    public void createIdentifiers(long personID, Map<String, Map<String, List<String>>> mappings, List<GKInstance> sourceReferences) throws Exception {

        // Iterate through each ? instance in the DB.
        for (GKInstance sourceInst : sourceReferences) {
            String sourceIdentifier = (String) sourceInst.getAttributeValue(ReactomeJavaConstants.identifier);
            GKInstance species = (GKInstance) sourceInst.getAttributeValue(ReactomeJavaConstants.species);
            // Determine which species instance pertains too. Retrieve corresponding mapping structures.
            String biomartSpeciesName = getBiomartSpeciesName(species.getDisplayName());
            String proteinToTranscriptsKey = biomartSpeciesName + "_proteinToTranscript";
            String proteinToGenesKey = biomartSpeciesName + "_proteinToGenes";
            String uniprotToENSPKey = biomartSpeciesName + "_uniprotToProteins";
            // Iterate through each Ensembl Protein identifier. Retrieve any corresponding Transcript and Gene
            // identifiers and add each to a set. These will be used to create cross references to Ensembl from Reactome.
            Set<String> ensIds = new HashSet<>();
            // Check that the 'uniprotToProteins' mapping exists and that the source instance's
            // 'identifier' attribute maps to ensembl protein identifiers.
            if (mappings.get(uniprotToENSPKey) != null && mappings.get(uniprotToENSPKey).get(sourceIdentifier) != null) {
                // Retrieve all ensembl protein identifiers that map to the source instance identifier and store in 'ensIds'.
                List<String> enspIds = mappings.get(uniprotToENSPKey).get(sourceIdentifier);
                for (String enspId : enspIds) {
                    // Only store Ensembl protein identifiers.
                    if (enspId.startsWith("ENS")) {
                        ensIds.add(enspId);
                    }
                    // Retrieve any Ensembl transcript identifiers that map to the Ensembl protein identifier and store in 'ensIds'.
                    if (mappings.get(proteinToTranscriptsKey) != null && mappings.get(proteinToTranscriptsKey).get(enspId) != null) {
                        List<String> enstIds = mappings.get(proteinToTranscriptsKey).get(enspId);
                        for (String enstId : enstIds) {
                            // Only store Ensembl transcript identifiers.
                            if (enstId.startsWith("ENS")) {
                                ensIds.add(enstId);
                            }
                        }
                    }
                    // Retrieve any Ensembl gene identifiers that map to the Ensembl protein identifier and store in 'ensIds'.
                    if (mappings.get(proteinToGenesKey) != null && mappings.get(proteinToGenesKey).get(enspId) != null) {
                        List<String> ensgIds = mappings.get(proteinToGenesKey).get(enspId);
                        for (String ensgId : ensgIds) {
                            // Only store Ensembl gene identifiers.
                            if (ensgId.startsWith("ENS")) {
                                ensIds.add(ensgId);
                            }
                        }
                    }

                }
            }
            // Iterate through each Ensembl identifier and create a ? instance.
            for (String ensId : ensIds) {
                if (!this.checkXRefExists(sourceInst, ensId))
                {
                    if (!this.testMode)
                    {
                        this.refCreator.createIdentifier(ensId, String.valueOf(sourceInst.getDBID()), targetRefDB, personID, this.getClass().getName(), species.getDBID());
                    }
                }
            }
        }
    }

    // Update species name attribute to Ensembl Biomart format (eg: Homo sapiens --> hsapiens).
    private String getBiomartSpeciesName(String speciesName) {

        return speciesName.substring(0,1).toLowerCase() + speciesName.split(" ")[1];
    }
}
