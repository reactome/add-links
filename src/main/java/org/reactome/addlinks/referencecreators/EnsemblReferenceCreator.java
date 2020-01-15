package org.reactome.addlinks.referencecreators;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;

import java.util.List;
import java.util.Map;

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

        System.out.println(sourceReferences.size());
        for (String mappingsKey : mappings.keySet()) {
            if (mappingsKey.contains("uniprotToENSP")) {

                //TODO: Transcript, Gene
                for (String uniprotId : mappings.get(mappingsKey).keySet()) {
                    for (String enspId : mappings.get(mappingsKey).get(uniprotId)) {
//                        System.out.println(enspId);
                    }
                }
            }
        }
    }
}
