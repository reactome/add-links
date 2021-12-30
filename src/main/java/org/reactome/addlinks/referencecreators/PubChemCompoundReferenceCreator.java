package org.reactome.addlinks.referencecreators;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

import java.util.List;
import java.util.Map;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 *         Created 11/7/2021
 */
public class PubChemCompoundReferenceCreator extends SimpleReferenceCreator<List<String>> {

    public PubChemCompoundReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB) {
        super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
    }

    public PubChemCompoundReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB, String refCreatorName) {
        super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, refCreatorName);
    }

    @Override
    public void createIdentifiers(long personID, Map<String, List<String>> mapping, List<GKInstance> sourceReferences) throws Exception {
        int sourceIdentifiersWithNewIdentifier = 0;
        int sourceIdentifiersWithExistingIdentifier = 0;
        int sourceIdentifiersWithNoMapping = 0;

        for (GKInstance sourceReference : sourceReferences) {
            String sourceIdentifier = (String) sourceReference.getAttributeValue(ReactomeJavaConstants.identifier);

            if (mapping.containsKey(sourceIdentifier)) {
                for (String pubChemCompoundIdentifier : mapping.get(sourceIdentifier)) {
                    if (!this.checkXRefExists(sourceReference, pubChemCompoundIdentifier)) {
                        sourceIdentifiersWithNewIdentifier++;
                        if (!this.testMode) {
                            this.refCreator.createIdentifier(pubChemCompoundIdentifier, sourceReference.getDBID().toString(), this.targetRefDB, personID, this.getClass().getName());
                        }
                    } else {
                        sourceIdentifiersWithExistingIdentifier++;
                    }
                }
            } else {
                sourceIdentifiersWithNoMapping++;
            }
        }

        logger.info("{} reference creation summary: \n"
                + "\t# {} IDs with a new {} identifier (a new {} reference was created): {};\n"
                + "\t# {} identifiers which already had the same {} reference (nothing new was created): {};\n"
                + "\t# {} identifiers not in the {} mapping file (no new {} reference was created for them): {}\n"
                + "\t# {} keys in input mapping: {}\n"
                + "\t# existing {} input source identifiers: {}",
            this.targetRefDB,
            this.sourceRefDB, this.targetRefDB, this.targetRefDB, sourceIdentifiersWithNewIdentifier,
            this.sourceRefDB, this.targetRefDB, sourceIdentifiersWithExistingIdentifier,
            this.sourceRefDB, this.targetRefDB, this.targetRefDB, sourceIdentifiersWithNoMapping,
            this.sourceRefDB, mapping.keySet().size(),
            this.sourceRefDB, sourceReferences.size());
    }
}
