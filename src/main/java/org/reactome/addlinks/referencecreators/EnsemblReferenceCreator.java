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

    @Override
    public void createIdentifiers(long personID, Map<String, Map<String, List<String>>> mappings, List<GKInstance> sourceReferences) throws Exception {

        for (GKInstance sourceInst : sourceReferences) {
            String sourceIdentifier = (String) sourceInst.getAttributeValue(ReactomeJavaConstants.identifier);
            GKInstance species = (GKInstance) sourceInst.getAttributeValue(ReactomeJavaConstants.species);

            String biomartSpeciesName = getBiomartSpeciesName(species.getDisplayName());
            String proteinToTranscriptsKey = biomartSpeciesName + "_proteinToTranscript";
            String proteinToGenesKey = biomartSpeciesName + "_proteinToGenes";
            String uniprotToENSPKey = biomartSpeciesName + "_uniprotToENSP";
            Set<String> ensIds = new HashSet<>();
            if (mappings.get(uniprotToENSPKey) != null && mappings.get(uniprotToENSPKey).get(sourceIdentifier) != null) {
                List<String> enspIds = mappings.get(uniprotToENSPKey).get(sourceIdentifier);
                for (String enspId : enspIds) {
                    if (enspId.startsWith("ENS")) {
                        ensIds.add(enspId);
                    }
                    if (mappings.get(proteinToTranscriptsKey) != null && mappings.get(proteinToTranscriptsKey).get(enspId) != null) {
                        List<String> enstIds = mappings.get(proteinToTranscriptsKey).get(enspId);
                        for (String enstId : enstIds) {
                            if (enstId.startsWith("ENS")) {
                                ensIds.add(enstId);
                            }
                        }
                    }
                    if (mappings.get(proteinToGenesKey) != null && mappings.get(proteinToGenesKey).get(enspId) != null) {
                        List<String> ensgIds = mappings.get(proteinToGenesKey).get(enspId);
                        for (String ensgId : ensgIds) {
                            if (ensgId.startsWith("ENS")) {
                                ensIds.add(ensgId);
                            }
                        }
                    }

                }
            }
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

//        for (String mappingsKey : mappings.keySet()) {
//            if (mappingsKey.contains("uniprotToENSP")) {
//
//                //TODO: Transcript, Gene
//                for (String uniprotId : mappings.get(mappingsKey).keySet()) {
//                    for (String enspId : mappings.get(mappingsKey).get(uniprotId)) {
////                        System.out.println(enspId);
//                    }
//                }
//            }
//        }

        System.exit(0);
    }

    private String getBiomartSpeciesName(String speciesName) {

        return speciesName.substring(0,1).toLowerCase() + speciesName.split(" ")[1];
    }
}
