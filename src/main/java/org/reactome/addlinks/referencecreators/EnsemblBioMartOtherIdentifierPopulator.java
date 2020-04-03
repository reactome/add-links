package org.reactome.addlinks.referencecreators;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.reactome.addlinks.EnsemblBioMartUtil;

import java.util.*;

public class EnsemblBioMartOtherIdentifierPopulator extends SimpleReferenceCreator <Map<String, List<String>>>{

    public EnsemblBioMartOtherIdentifierPopulator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB)
    {
        super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
    }

    public EnsemblBioMartOtherIdentifierPopulator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB, String refCreatorName)
    {
        super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, refCreatorName);
    }

    /**
     * Adds microarray and GO data to the 'otherIdentifier' attribute of ReferenceGeneProduct (RGP) instances. This class doesn't create
     * any cross-references, but just adds plaintext identifiers.
     * @param personID - Newly created Identifiers will be associated with an InstanceEdit. That InstanceEdit will be associated with the Person entity whose ID == personID
     * @param mappings Identifier mapping structure that contains all data used to populate microarray and GO data to RGPs.
     * @param sourceReferences - A list of GKInstance objects that were in the database. References will be created if they are in the keys of mapping and also in sourceReferences.
     * This is to handle cases where a mapping comes from a third-party file that may contain source identifiers that are in *their* system but aren't actually in Reactome.
     * @throws Exception - Thrown by the MySQLAdaptor/GKInstance classes. IOException/ParseException can also be thrown when getting species names from Species.json file.
     */

    @Override
    public void createIdentifiers(long personID, Map<String, Map<String, List<String>>> mappings, List<GKInstance> sourceReferences) throws Exception {

        // Iterate through each species that we add OtherIdentifier data to.
        for (String speciesName : EnsemblBioMartUtil.getSpeciesNames()) {
            logger.info("Populating OtherIdentifier data for " + speciesName);
            String speciesBiomartName = EnsemblBioMartUtil.getBioMartSpeciesName(speciesName);

            // Retrieve identifier mappings that are used from the 'super' mapping.
            Map<String, List<String>> proteinToTranscripts = EnsemblBioMartUtil.getProteinToTranscriptsMappings(speciesBiomartName, mappings);

            Map<String, List<String>> transcriptToOtherIdentifiers = EnsemblBioMartUtil.getTranscriptToOtherIdentifiersMappings(speciesBiomartName, mappings);

            Map<String, List<String>> uniprotToProtein = EnsemblBioMartUtil.getUniprotToProteinsMappings(speciesBiomartName, mappings);

            // We need Uniprot identifiers to retrieve corresponding ReferenceGeneProduct instances (RGP) from the DB,
            // Ensembl Protein identifiers to retrieve Ensembl Transcript identifiers, and Transcript identifiers
            // to retrieve Microarray/GO identifiers, which are inserted into the 'otherIdentifier' attribute of the RGP.
            if (allDataStructuresPopulated(proteinToTranscripts, transcriptToOtherIdentifiers, uniprotToProtein)) {
                // Retrieve Reactome Species instance and all RGPs associated with it.
                GKInstance speciesInst = (GKInstance) adapter.fetchInstanceByAttribute(ReactomeJavaConstants.Species, ReactomeJavaConstants.name, "=", speciesName).iterator().next();
                Collection<GKInstance> rgpInstances = adapter.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceGeneProduct, ReactomeJavaConstants.species, "=", speciesInst);
                for (GKInstance rgpInst : rgpInstances) {
                    addOtherIdentifierDataToReferenceGeneProduct(rgpInst, speciesBiomartName, mappings);
                }
            }
        }
    }

    /**
     * Iterates through each protein associated with the RGP instance and finds transcripts associated with protein identifier.
     * Then it takes transcript identifier and finds all OtherIdentifiers associated with it. These identifiers
     * are then added to the 'otherIdentifier' attribute of the RGP instance. Once all identifiers have been added,
     * the instance is updated with the new data in the database.
     * @param rgpInst - GKInstance, ReferenceGeneProduct instance
     * @param speciesBiomartName, - String, Species name in BioMart format (eg: hsapiens).
     * @param mappings - Map<String, Map< String, List<String>>>, Super mapping structure generated by EnsemblBioMartFileProcessor.
     * @throws Exception - Thrown by MySQLAdaptor/GKInstance methods. Typically due to accessing data that doesn't exist or from InvalidAttributeExceptions.
     */
    private void addOtherIdentifierDataToReferenceGeneProduct(GKInstance rgpInst, String speciesBiomartName, Map<String, Map<String, List<String>>> mappings) throws Exception {
        // Retrieve protein identifier associated with reference DB.
        Set<String> rgpProteins = findRGPProteins(rgpInst, speciesBiomartName, mappings);
        // For each protein identifier retrieved, find OtherIdentifiers associated with it and
        // add them to the 'otherIdentifier' attribute of the RGP instance.
        Set<String> otherIdentifiers = new TreeSet<>(rgpInst.getAttributeValuesList(ReactomeJavaConstants.otherIdentifier));
        for (String protein : rgpProteins) {
            // This check is required since the identifier taken from the RGP may not have a key in the proteinToTranscript mapping.
            Map<String, List<String>> proteinToTranscripts = EnsemblBioMartUtil.getProteinToTranscriptsMappings(speciesBiomartName, mappings);
            for (String transcript : proteinToTranscripts.computeIfAbsent(protein, k -> new ArrayList<>())) {
                Map<String, List<String>> transcriptToOtherIdentifiers = EnsemblBioMartUtil.getTranscriptToOtherIdentifiersMappings(speciesBiomartName, mappings);
                for (String newOtherIdentifier : transcriptToOtherIdentifiers.computeIfAbsent(transcript, k -> new ArrayList<>())) {
                    otherIdentifiers.add(newOtherIdentifier);
                }
            }
        }
        if (!EnsemblBioMartOtherIdentifierPopulator.this.testMode) {
            rgpInst.setAttributeValue(ReactomeJavaConstants.otherIdentifier, new ArrayList<>(otherIdentifiers));
        }
        sortOtherIdentifiersAndUpdateInstance(rgpInst);
    }

    /**
     * Find proteins associated with ReferenceGeneProduct instance.
     * @param rgpInst - GKInstance, ReferenceGeneProduct instance
     * @param speciesBiomartName - String, Species name in BioMart format (eg: hsapiens).
     * @param mappings - Map<String, Map< String, List<String>>>, Super mapping structure generated by EnsemblBioMartFileProcessor.
     * @return - Set<String>, Set of proteins associated with RGP instance.
     * @throws Exception - Thrown when retrieving data from GKInstance that does not exist or invalid for the GKInstance class.
     */
    private Set<String> findRGPProteins(GKInstance rgpInst, String speciesBiomartName, Map<String, Map<String, List<String>>> mappings) throws Exception {
        Set<String> rgpProteins = new HashSet<>();
        String rgpIdentifier = rgpInst.getAttributeValue(ReactomeJavaConstants.identifier).toString();
        GKInstance refDbInst = (GKInstance) rgpInst.getAttributeValue(ReactomeJavaConstants.referenceDatabase);
        // If reference DB is Ensembl, add the identifier associated with RGP instance.
        String refDbDisplayName = refDbInst.getDisplayName().toLowerCase();
        if (refDbDisplayName.contains("ensembl") && EnsemblBioMartUtil.getProteinToTranscriptsMappings(speciesBiomartName, mappings).containsKey(rgpIdentifier)) {
            rgpProteins.add(rgpIdentifier);
        // If reference DB is Uniprot, get Ensembl protein identifier associated with
        // instance's identifier attribute, which should be a Uniprot identifier.
        } else if (refDbDisplayName.contains("uniprot") && EnsemblBioMartUtil.getUniprotToProteinsMappings(speciesBiomartName, mappings).containsKey(rgpIdentifier)) {
            rgpProteins.addAll(EnsemblBioMartUtil.getUniprotToProteinsMappings(speciesBiomartName, mappings).get(rgpIdentifier));
        }
        return rgpProteins;
    }

    /**
     * Retrieve all identifiers from RGP instances, and build a map of identifiers to all associated instances.
     * @param rgpInstances - Collection<GKInstance>, Collection of ReferenceGeneProduct instances
     * @return - Map<String, List<GKInstance>>, Map of RGP identifiers to associated RGP instances
     * @throws Exception - Thrown when retrieving data from GKInstance that does not exist or invalid for the GKInstance class.
     */
    public Map<String, List<GKInstance>> mapRGPIdentifiersToRGPInstances(Collection<GKInstance> rgpInstances) throws Exception {
        Map<String, List<GKInstance>> rgpIdentifiersToRGPs = new HashMap<>();
        for (GKInstance rgpInst : rgpInstances) {
            String identifier = rgpInst.getAttributeValue(ReactomeJavaConstants.identifier).toString();
            rgpIdentifiersToRGPs.computeIfAbsent(identifier, k -> new ArrayList<>()).add(rgpInst);
        }
        return rgpIdentifiersToRGPs;
    }

    // Sorts all microarray/GO term values added to otherIdentifier slot and updates instance with new data.
    private void sortOtherIdentifiersAndUpdateInstance(GKInstance rgpInst) throws Exception {
//        List<String> otherIdentifiers = rgpInst.getAttributeValuesList(ReactomeJavaConstants.otherIdentifier);
//        Collections.sort(otherIdentifiers);
        updateInstance(rgpInst);

    }

    // Updates the OtherIdentifier attribute of the ReferenceGeneProduct with Microarray/GO term data in the database.
    private void updateInstance(GKInstance rgpInst) throws Exception {
        adapter.updateInstanceAttribute(rgpInst, ReactomeJavaConstants.otherIdentifier);
    }

    // Checks that none of the provided mapping structures are null.
    public boolean allDataStructuresPopulated(Map<String, List<String>> proteinToTranscripts, Map<String, List<String>> transcriptToOtherIdentifiers, Map<String, List<String>> uniprotToProtein) {
        return proteinToTranscripts != null && transcriptToOtherIdentifiers != null && uniprotToProtein != null;
    }
}
