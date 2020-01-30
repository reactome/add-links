package org.reactome.addlinks.referencecreators;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.reactome.addlinks.EnsemblBiomartUtil;

import java.util.*;

public class EnsemblBiomartMicroarrayPopulator extends SimpleReferenceCreator <Map<String, List<String>>>{

    public EnsemblBiomartMicroarrayPopulator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB)
    {
        super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
    }

    public EnsemblBiomartMicroarrayPopulator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB, String refCreatorName)
    {
        super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, refCreatorName);
    }


    /**
     * Adds microarray data to the 'otherIdentifier' attribute of ReferenceGeneProduct (RGP) instances. This class doesn't create
     * any cross-references for the microarray data, since URLs do not exist for them. Reactome's analysis tool still requires the
     * microarray data though, so this class populates it.
     * @param personID - Newly created Identifiers will be associated with an InstanceEdit. That InstanceEdit will be associated with the Person entity whose ID == personID
     * @param mappings Identifier mapping structure that contains all data used to populate microarray data to RGPs.
     * @param sourceReferences - A list of GKInstance objects that were in the database. References will be created if they are in the keys of mapping and also in sourceReferences.
     * This is to handle cases where a mapping comes from a third-party file that may contain source identifiers that are in *their* system but aren't actually in Reactome.
     * @throws Exception Can be thrown when retrieving data from or adding data to the DB.
     */
    @Override
    public void createIdentifiers(long personID, Map<String, Map<String, List<String>>> mappings, List<GKInstance> sourceReferences) throws Exception
    {

        // Iterate through each species that we add microarray data to.
        for (String speciesName : EnsemblBiomartUtil.getSpeciesNames()) {
            logger.info("Populating microarray data for " + speciesName);
            String speciesBiomartName = EnsemblBiomartUtil.getBiomartSpeciesName(speciesName);

            // Retrieve identifier mappings that are used from the 'super' mapping.
            // TODO: Helper class for getting species data
            Map<String, List<String>> proteinToTranscripts = EnsemblBiomartUtil.getProteinToTranscriptsMappings(speciesBiomartName, mappings);

            Map<String, List<String>> transcriptToProbes = EnsemblBiomartUtil.getTranscriptToMicroarraysMappings(speciesBiomartName, mappings);

            Map<String, List<String>> uniprotToProtein = EnsemblBiomartUtil.getUniprotToProteinsMappings(speciesBiomartName, mappings);

            // We need Uniprot identifiers to retrieve corresponding ReferenceGeneProduct instances (RGP) from the DB,
            // Ensembl Protein identifiers to retrieve Ensembl Transcript identifiers, and Transcript identifiers
            // to retrieve Microarray identifiers, which are inserted into the 'otherIdentifier' attribute of the RGP.
            if (allDataStructuresPopulated(proteinToTranscripts, transcriptToProbes, uniprotToProtein)) {
                // Retrieve Reactome Species instance and all RGPs associated with it.
                GKInstance speciesInst = (GKInstance) adapter.fetchInstanceByAttribute(ReactomeJavaConstants.Species, ReactomeJavaConstants.name, "=", speciesName).iterator().next();
                Collection<GKInstance> rgpInstances = adapter.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceGeneProduct, ReactomeJavaConstants.species, "=", speciesInst);
                // Get all RGP identifiers in database, mapping them to associated instances.
                Map<String, List<GKInstance>> rgpIdentifiersToRGPs = mapRGPIdentifiersToRGPInstances(rgpInstances);
                // Iterate through each identifier, and then through each RGP instance.
                for (String rgpIdentifier : rgpIdentifiersToRGPs.keySet()) {
                    for (GKInstance rgpInst : rgpIdentifiersToRGPs.get(rgpIdentifier)) {
                        addMicroarrayDataToReferenceGeneProduct(rgpIdentifier, rgpInst, speciesBiomartName, mappings);
                    }
                }
            }
        }
    }

    /**
     * Iterates through each protein associated with the RGP instance and finds transcripts associated with protein identifier.
     * Then takes transcript identifier and finds all microarray identifiers associated with it. These microarray identifiers
     * are then added to the 'otherIdentifier' attribute of the RGP instance. Once all microarray identifiers have been added,
     * the instance is updated with the new data in the DB.
     * @param rgpIdentifier String identifier associated with RGP instance.
     * @param rgpInst GKInstance
     * @param speciesBiomartName
     * @param mappings
     * @throws Exception Can be caused when retrieving data from or updating GKInstance
     */
    private void addMicroarrayDataToReferenceGeneProduct(String rgpIdentifier, GKInstance rgpInst, String speciesBiomartName, Map<String, Map<String, List<String>>> mappings) throws Exception {
        // Retrieve protein identifier associated with reference DB.
        Set<String> rgpProteins = findRGPProteins(rgpIdentifier, rgpInst, speciesBiomartName, mappings);
        // For each protein identifier retrieved, find microarray identifiers associated and
        // add them to the 'otherIdentifier' attribute of the RGP instance.
        for (String protein : rgpProteins) {
            if (EnsemblBiomartUtil.getProteinToTranscriptsMappings(speciesBiomartName, mappings).containsKey(protein)) {
                for (String transcript : EnsemblBiomartUtil.getProteinToTranscriptsMappings(speciesBiomartName, mappings).get(protein)) {
                    if (EnsemblBiomartUtil.getTranscriptToMicroarraysMappings(speciesBiomartName, mappings).containsKey(transcript)) {
                        for (String microarrayId : EnsemblBiomartUtil.getTranscriptToMicroarraysMappings(speciesBiomartName, mappings).get(transcript)) {
                            Collection<String> otherIdentifiers = rgpInst.getAttributeValuesList(ReactomeJavaConstants.otherIdentifier);
                            if (!otherIdentifiers.contains(microarrayId) && !this.testMode) {
                                rgpInst.addAttributeValue(ReactomeJavaConstants.otherIdentifier, microarrayId);
                            }
                        }
                    }
                }
            }
        }
        sortOtherIdentifiersAndUpdateInstance(rgpInst);
    }

    /**
     * Find proteins associated with ReferenceGeneProduct instance.
     * @param rgpIdentifier String identifier associated with RGP instance.
     * @param rgpInst GKInstance
     * @param speciesBiomartName
     * @param mappings
     * @return Set of proteins associated with RGP instance.
     * @throws Exception Can be caused when retrieving data from GKInstance.
     */
    public Set<String> findRGPProteins(String rgpIdentifier, GKInstance rgpInst, String speciesBiomartName, Map<String, Map<String, List<String>>> mappings) throws Exception {
        Set<String> rgpProteins = new HashSet<>();
        GKInstance refDbInst = (GKInstance) rgpInst.getAttributeValue(ReactomeJavaConstants.referenceDatabase);
        // If reference DB is Ensembl, add the identifier associated with RGP instance.
        if (refDbInst.getDisplayName().toLowerCase().contains("ensembl")) {
            if (EnsemblBiomartUtil.getProteinToTranscriptsMappings(speciesBiomartName, mappings).containsKey(rgpIdentifier)) {
                rgpProteins.add(rgpIdentifier);
            }
        // If reference DB is Uniprot, get Ensembl protein identifier associated with
        // instance's identifier attribute, which should be a Uniprot identifier.
        } else if (refDbInst.getDisplayName().toLowerCase().contains("uniprot")) {
            if (EnsemblBiomartUtil.getUniprotToProteinsMappings(speciesBiomartName, mappings) != null && EnsemblBiomartUtil.getUniprotToProteinsMappings(speciesBiomartName, mappings).containsKey(rgpIdentifier)) {
                rgpProteins.addAll(EnsemblBiomartUtil.getUniprotToProteinsMappings(speciesBiomartName, mappings).get(rgpIdentifier));
            }
        }
        return rgpProteins;
    }

    /**
     * Retrieve all identifiers from RGP instances, and build a map of identifiers to all associated instances.
     * @param rgpInstances Collection of ReferenceGeneProduct instances
     * @return Map of RGP identifiers to associated RGP instances
     * @throws Exception can be caused when retrieving data from GKInstance
     */
    public Map<String, List<GKInstance>> mapRGPIdentifiersToRGPInstances(Collection<GKInstance> rgpInstances) throws Exception {
        Map<String, List<GKInstance>> rgpIdentifiersToRGPs = new HashMap<>();
        for (GKInstance rgpInst : rgpInstances) {
            String identifier = rgpInst.getAttributeValue(ReactomeJavaConstants.identifier).toString();
            rgpIdentifiersToRGPs.computeIfAbsent(identifier, k -> new ArrayList<>()).add(rgpInst);
        }
        return rgpIdentifiersToRGPs;
    }

    // Sorts all microarray values added to otherIdentifier slot and updates instance with new data.
    private void sortOtherIdentifiersAndUpdateInstance(GKInstance rgpInst) throws Exception {
        List<String> otherIdentifiers = rgpInst.getAttributeValuesList(ReactomeJavaConstants.otherIdentifier);
        Collections.sort(otherIdentifiers);
        adapter.updateInstanceAttribute(rgpInst, ReactomeJavaConstants.otherIdentifier);
    }

    // Checks that none of the provided mapping structures are null.
    public boolean allDataStructuresPopulated(Map<String, List<String>> proteinToTranscripts, Map<String, List<String>> transcriptToProbes, Map<String, List<String>> uniprotToProtein) {
        return proteinToTranscripts != null && transcriptToProbes != null && uniprotToProtein != null;
    }
}
