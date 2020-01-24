package org.reactome.addlinks.fileprocessors.ensembl;

import org.reactome.addlinks.fileprocessors.FileProcessor;

import java.io.*;
import java.util.*;

public class EnsemblBiomartFileProcessor extends FileProcessor<Map<String, List<String>>> {

    public EnsemblBiomartFileProcessor()
    {
        super(null);
    }

    public EnsemblBiomartFileProcessor(String processorName)
    {
        super(processorName);
    }

    // TODO: Add unit tests
    /**
     * This class builds a large, rather unwieldy two-layer mapping structure of identifiers corresponding to a specific species.
     * The first level has 4 types of keys for each species. These keys are 'uniprotToProteins', 'proteinToGenes',
     * 'proteinToTranscripts', and 'transcriptToMicroarrays'. These describe the type of mapping that this
     * key is to. For example, the key 'hsapiens_uniprotToProteins' would access a Map of Human UniProt protein identifiers
     * to a List of Ensembl protein identifiers. Transcript denotes Ensembl transcript identifiers, Gene is Ensembl gene identifiers
     * and Microarray denotes microarray probe identifiers.
     * @return Map<String, Map<String, List<String>>>> mappings, the two-layer mapping structure of species-(identifier-[identifiers]).
     * @throws IOException Can be thrown if file does not exist.
     */
    @Override
    public Map<String, Map<String, List<String>>> getIdMappingsFromFile() throws IOException {

        Map<String, Map<String, List<String>>> mappings = new HashMap<>();
        // Iterate through each biomart file in the addlinks directory
        File biomartDir = new File(this.pathToFile.toString());
        for (File biomartFile : biomartDir.listFiles()) {
            // Get species name from filename
            String biomartFileSpecies = biomartFile.getName().split("_")[0];
            // Mappings that will be generated from each file for the species.
            Map<String, List<String>> proteinToTranscripts = new HashMap<>();
            Map<String, List<String>> proteinToGenes = new HashMap<>();
            Map<String, List<String>> transcriptToMicroarrays = new HashMap<>();
            Map<String, List<String>> uniprotToProteins = new HashMap<>();
            // Read file and iterate through each line.
            BufferedReader br = new BufferedReader(new FileReader(biomartFile));
            String biomartLine;
            while ((biomartLine = br.readLine()) != null) {
                // Split each tab-separated line. Each line has four values. The first three are always
                // Ensembl Gene (ENSG), Transcript (ENST) and Protein (ENSP), in that order.
                // The 4th can be a UniProt or microarray identifier, depending on the file being read.
                // All 4 values may not be in each line.
                List<String> tabSplit = Arrays.asList(biomartLine.split("\t"));
                // Processing of UniProt mapping files.
                // UniProt identifier mapping files are used to fully populate
                // the 'uniprotToProteins' and 'proteinToGenes' mappings.
                if (biomartFile.getName().endsWith("uniprot")) {
                    // 'uniprotToProteins' mapping requires the 3rd (protein) and 4th (uniprot)values in the line.
                    if (tabSplit.size() > 3 && necessaryColumnsContainData(tabSplit, 2,3)) {
                        uniprotToProteins = mapIdentifiers(uniprotToProteins, tabSplit.get(3), tabSplit.get(2));
                        // Add each mapping generated to the 'super' mapping
                        mappings.put(biomartFileSpecies + "_uniprotToProteins", uniprotToProteins);
                    }
                    // 'proteinToGenes' mappings require the 1st (gene) and 3rd (protein) values in the line.
                    if (necessaryColumnsContainData(tabSplit, 0, 2)) {
                        proteinToGenes = mapIdentifiers(proteinToGenes, tabSplit.get(2), tabSplit.get(0));
                        // Add each mapping generated to the 'super' mapping
                        mappings.put(biomartFileSpecies + "_proteinToGenes", proteinToGenes);
                    }

                // Processing of Microarray mapping files.
                // Microarray identifier mapping files are used to fully populate the
                // 'proteinToTranscripts' and 'transcriptToMicroarray' mappings.
                // Add each mapping generated to the 'super' mapping data structure that is returned.
                } else if (biomartFile.getName().endsWith("microarray")) {
                    // 'proteinToTranscripts' mapping requires the 2nd (transcript) and 3rd (protein) values in the line.
                    if (tabSplit.size() > 2 && necessaryColumnsContainData(tabSplit, 1, 2)) {
                        proteinToTranscripts = mapIdentifiers(proteinToTranscripts, tabSplit.get(2), tabSplit.get(1));
                        // Add each mapping generated to the 'super' mapping
                        mappings.put(biomartFileSpecies + "_proteinToTranscripts", proteinToTranscripts);
                    }
                    // 'transcriptToMicroarrays' mapping requires the 2nd (transcript) and 4th (microarray) values in the line.
                    if (tabSplit.size() > 3 && necessaryColumnsContainData(tabSplit, 1, 3)) {
                        transcriptToMicroarrays = mapIdentifiers(transcriptToMicroarrays, tabSplit.get(1), tabSplit.get(3));
                        // Add each mapping generated to the 'super' mapping
                        mappings.put(biomartFileSpecies + "_transcriptToMicroarrays", transcriptToMicroarrays);
                    }
                }
            }
        }
        return mappings;
    }

    /**
     * Maps one type of identifier to a List of another type of identifier
     * @param identifierToIdentifierMapping Map<String, List<String>> mapping structure that holds all identifier mappings
     * @param identifier1 String -- Identifier that will be the key to the List of the other type of identifiers
     * @param identifier2 String -- Identifier that will be added to the List keyed to the other identifier
     * @return identifierToIdentifierMapping that has been updated with new values.
     */
    private Map<String, List<String>> mapIdentifiers(Map<String, List<String>> identifierToIdentifierMapping, String identifier1, String identifier2) {
        if (identifierToIdentifierMapping.containsKey(identifier1)) {
            identifierToIdentifierMapping.get(identifier1).add(identifier2);
        } else {
            ArrayList<String> singleIdentifierArray = new ArrayList<>(Arrays.asList(identifier2));
            identifierToIdentifierMapping.put(identifier1, singleIdentifierArray);
        }
        return identifierToIdentifierMapping;
    }

    // Checks that data exists at the supplied column indices.
    private boolean necessaryColumnsContainData(List<String> tabSplit, int necessaryColumnIndex1, int necessaryColumnIndex2) {
         return !tabSplit.get(necessaryColumnIndex1).isEmpty() && !tabSplit.get(necessaryColumnIndex2).isEmpty();
    }
}
