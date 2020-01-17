package org.reactome.addlinks.referencecreators;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
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

    //TODO: Add logging
    //TODO: Add commenting
    //TODO: Add unit tests
    //TODO: Rewrite variable names
    //TODO: Global variables for file names
    //TODO: Function-level commenting

    private static Map<String, List<String>> proteinToTranscripts = new HashMap<>();
    private static Map<String, List<String>> transcriptToProbes = new HashMap<>();
    private static Map<String, List<String>> uniprotToProtein = new HashMap<>();

    @Override
    public void createIdentifiers(long personID, Map<String, Map<String, List<String>>> mappings, List<GKInstance> sourceReferences) throws Exception
    {

        // Iterate through each species that we add microarray data to.
        for (String speciesName : getSpeciesNames()) {
            String speciesBiomartName = speciesName.substring(0,1).toLowerCase() + speciesName.split(" ")[1];

            // Retrieve identifier mappings that are used from the 'super' mapping.
            String proteinToTranscriptsKey = speciesBiomartName + "_proteinToTranscripts";
            proteinToTranscripts = mappings.get(proteinToTranscriptsKey);

            String transcriptToProbesKey = speciesBiomartName + "_transcriptToMicroarrays";
            transcriptToProbes = mappings.get(transcriptToProbesKey);

            String uniprotToENSPKey = speciesBiomartName + "_uniprotToProteins";
            uniprotToProtein = mappings.get(uniprotToENSPKey);

            // We need Uniprot identifiers to retrieve corresponding ReferenceGeneProduct instances from the DB,
            // Ensembl Protein identifiers to retrieve Ensembl Transcript identifiers, and Transcript identifiers
            // to retrieve Microarray identifiers, which are inserted into the 'otherIdentifier' attribute of the RGP.
            // This means we require all 3 mapping structures to exist for the species.
            if (allDataStructuresPopulated(proteinToTranscripts, transcriptToProbes, uniprotToProtein)) {
                System.out.println("Populating microarray data for " + speciesName);
                // Retrieve Reactome Species instance and all RGPs associated with it.
                GKInstance speciesInst = (GKInstance) adapter.fetchInstanceByAttribute(ReactomeJavaConstants.Species, ReactomeJavaConstants.name, "=", speciesName).iterator().next();
                Collection<GKInstance> rgpInstances = adapter.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceGeneProduct, ReactomeJavaConstants.species, "", speciesInst);

                // Get all RGP identifiers in database, mapping them to associated instances.
                Map<String, ArrayList<GKInstance>> identifiersToRGPs = getRGPIdentifiers(rgpInstances);
                // Iterate through each identifier, and then through each RGP instance.
                for (String rgpIdentifier : identifiersToRGPs.keySet()) {
                    for (GKInstance rgpInst : identifiersToRGPs.get(rgpIdentifier)) {
                        addMicroarrayDataToReferenceGeneProduct(rgpIdentifier, rgpInst);
                    }
                }
            }
        }
    }

    private void addMicroarrayDataToReferenceGeneProduct(String rgpIdentifier, GKInstance rgpInst) throws Exception {
        // Retrieve protein identifier associated with reference DB.
        Set<String> proteins = findInstanceProteins(rgpIdentifier, rgpInst);
        // For each protein identifier retrieved, find microarray identifiers associated and
        // add them to the 'otherIdentifier' attribute of the RGP instance.
        for (String protein : proteins) {
            if (proteinToTranscripts.get(protein) != null) {
                for (String transcript : proteinToTranscripts.get(protein)) {
                    if (transcriptToProbes.get(transcript) != null) {
                        for (String probe : transcriptToProbes.get(transcript)) {
                            Collection<String> otherIdentifiers = rgpInst.getAttributeValuesList(ReactomeJavaConstants.otherIdentifier);
                            if (!otherIdentifiers.contains(probe)) {
                                rgpInst.addAttributeValue(ReactomeJavaConstants.otherIdentifier, probe);
                            }
                        }
                    }
                }
            }
        }
        sortOtherIdentifiersAndUpdateInstance(rgpInst);
    }

    private void sortOtherIdentifiersAndUpdateInstance(GKInstance rgpInst) throws Exception {
        List<String> otherIdentifiers = rgpInst.getAttributeValuesList(ReactomeJavaConstants.otherIdentifier);
        Collections.sort(otherIdentifiers);
        adapter.updateInstanceAttribute(rgpInst, ReactomeJavaConstants.otherIdentifier);
    }

    private Set<String> findInstanceProteins(String rgpIdentifier, GKInstance rgpInst) throws Exception {
        Set<String> proteins = new HashSet<>();
        GKInstance refDbInst = (GKInstance) rgpInst.getAttributeValue(ReactomeJavaConstants.referenceDatabase);
        // If reference DB is Ensembl, add the identifier associated with RGP instance.
        if (refDbInst.getDisplayName().toLowerCase().contains("ensembl")) {
            if (proteinToTranscripts.get(rgpIdentifier) != null) {
                proteins.add(rgpIdentifier);
            }
            // If reference DB is Uniprot, get Ensembl protein identifier associated with
            // instance's identifier attribute, which should be a Uniprot identiifier.
        } else if (refDbInst.getDisplayName().toLowerCase().contains("uniprot")) {
            if (uniprotToProtein != null && uniprotToProtein.get(rgpIdentifier) != null) {
                proteins.addAll(uniprotToProtein.get(rgpIdentifier));
            }
        }
        return proteins;
    }

    private boolean allDataStructuresPopulated(Map<String, List<String>> proteinToTranscripts, Map<String, List<String>> transcriptToProbes, Map<String, List<String>> uniprotToProtein) {
        return proteinToTranscripts != null && transcriptToProbes != null && uniprotToProtein != null;
    }

    // Retrieve all identifiers from RGP instances, and build a map of identifiers to all associated instances.
    private Map<String, ArrayList<GKInstance>> getRGPIdentifiers(Collection<GKInstance> rgpInstances) throws Exception {
        Map<String, ArrayList<GKInstance>> identifiersToRGPs = new HashMap<>();
        for (GKInstance rgpInst : rgpInstances) {
            String identifier = rgpInst.getAttributeValue(ReactomeJavaConstants.identifier).toString();
            if (identifiersToRGPs.get(identifier) != null) {
                identifiersToRGPs.get(identifier).add(rgpInst);
            } else {
                ArrayList<GKInstance> singleRGPArray = new ArrayList<>(Arrays.asList(rgpInst));
                identifiersToRGPs.put(identifier, singleRGPArray);
            }
        }
        return identifiersToRGPs;
    }

    // Get species name from json config file.
    private Set<String> getSpeciesNames() throws IOException, ParseException {
        Properties applicationProps = new Properties();
        String propertiesLocation = System.getProperty("config.location");
        try(FileInputStream fis = new FileInputStream(propertiesLocation))
        {
            applicationProps.load(fis);
        }
        String pathToSpeciesConfig = applicationProps.getProperty("pathToSpeciesConfig");
        JSONParser parser = new JSONParser();
        Object obj = parser.parse(new FileReader(pathToSpeciesConfig));
        JSONObject jsonObject = (JSONObject) obj;
        Set<String> fullSpeciesNames = new HashSet<>();
        for (Object speciesKey : jsonObject.keySet()) {
            JSONObject speciesJson = (JSONObject) jsonObject.get(speciesKey);
            JSONArray speciesNames = (JSONArray) speciesJson.get("name");
            String speciesName = (String) speciesNames.get(0);
            fullSpeciesNames.add(speciesName);
        }
        return fullSpeciesNames;
    }
}
