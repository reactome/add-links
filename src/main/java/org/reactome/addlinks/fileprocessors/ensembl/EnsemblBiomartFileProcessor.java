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

    //TODO: Add logging
    //TODO: Add commenting
    //TODO: Add unit tests
    //TODO: Rewrite variable names
    //TODO: Global variables for file names
    //TODO: Function-level commenting

    @Override
    public Map<String, Map<String, List<String>>> getIdMappingsFromFile() throws IOException {

        Map<String, Map<String, List<String>>> mappings = new HashMap<>();
        // Iterate through each biomart file in the addlinks directory
        File biomartDir = new File(this.pathToFile.toString());
        File[] microarrayFiles = biomartDir.listFiles();
        for (File biomartFile : microarrayFiles) {
            // Get species name from filename
            String microarrayFileSpecies = biomartFile.getName().split("_")[0];
            // Mappings that will be generated from each file for the species.
            Map<String, List<String>> proteinToTranscripts = new HashMap<>();
            Map<String, List<String>> proteinToGenes = new HashMap<>();
            Map<String, List<String>> transcriptToMicroarrays = new HashMap<>();
            Map<String, List<String>> uniprotToProteins = new HashMap<>();
            // Read file and iterate through each line.
            BufferedReader br = new BufferedReader(new FileReader(biomartFile));
            String microarrayLine;
            while ((microarrayLine = br.readLine()) !=null) {
                // Split each tab-separated line. Each line has four values. The first three are always
                // Ensembl Gene (ENSG), Transcript (ENST) and Protein (ENSP), in that order.
                // The 4th can be a UniProt or microarray identifier, depending on the file being read.
                // All 4 values may not be in each line.
                List<String> tabSplit = Arrays.asList(microarrayLine.split("\t"));
                // Processing of UniProt mapping files.
                // UniProt identifier mapping files are used to fully populate
                // the 'uniprotToProteins' and 'proteinToGenes' mappings.
                if (biomartFile.getName().contains("uniprot")) {
                    // 'uniprotToProteins' mapping requires the 3rd (protein) and 4th (uniprot)values in the line.
                    if (tabSplit.size() > 3 && necessaryColumnsContainData(tabSplit, 2,3)) {
                        uniprotToProteins = mapUniprotToProteins(uniprotToProteins, tabSplit.get(2), tabSplit.get(3));
                        // Add each mapping generated to the 'super' mapping
                        mappings.put(microarrayFileSpecies + "_uniprotToProteins", uniprotToProteins);
                    }
                    // 'proteinToGenes' mappings require the 1st (gene) and 3rd (protein) values in the line.
                    if (necessaryColumnsContainData(tabSplit, 0, 2)) {
                        proteinToGenes = mapProteinToGenes(proteinToGenes, tabSplit.get(0), tabSplit.get(2));
                        // Add each mapping generated to the 'super' mapping
                        mappings.put(microarrayFileSpecies + "_proteinToGenes", proteinToGenes);
                    }

                // Processing of Microarray mapping files.
                // Microarray identifier mapping files are used to fully populate the
                // 'proteinToTranscripts' and 'transcriptToMicroarray' mappings.
                // Add each mapping generated to the 'super' mapping data structure that is returned.
                } else if (biomartFile.getName().contains("microarray")) {
                    // 'proteinToTranscripts' mapping requires the 2nd (transcript) and 3rd (protein) values in the line.
                    if (tabSplit.size() > 2 && necessaryColumnsContainData(tabSplit, 1, 2)) {
                        proteinToTranscripts = mapProteinToTranscripts(proteinToTranscripts, tabSplit.get(1), tabSplit.get(2));
                        // Add each mapping generated to the 'super' mapping
                        mappings.put(microarrayFileSpecies + "_proteinToTranscripts", proteinToTranscripts);
                    }
                    // 'transcriptToMicroarrays' mapping requires the 2nd (transcript) and 4th (microarray) values in the line.
                    if (tabSplit.size() > 3 && necessaryColumnsContainData(tabSplit, 1, 3)) {
                        transcriptToMicroarrays = mapTranscriptToMicroarrays(transcriptToMicroarrays, tabSplit.get(1), tabSplit.get(3));
                        // Add each mapping generated to the 'super' mapping
                        mappings.put(microarrayFileSpecies + "_transcriptToMicroarrays", transcriptToMicroarrays);
                    }
                }
            }
        }
        return mappings;
    }

    private Map<String, List<String>> mapTranscriptToMicroarrays(Map<String, List<String>> transcriptToMicroarrays, String transcript, String microarray) {
        if (transcriptToMicroarrays.get(transcript) != null) {
            transcriptToMicroarrays.get(transcript).add(microarray);
        } else {
            ArrayList<String> singleProbeArray = new ArrayList<>(Arrays.asList(microarray));
            transcriptToMicroarrays.put(transcript, singleProbeArray);
        }
        return transcriptToMicroarrays;
    }

    private Map<String, List<String>> mapProteinToTranscripts(Map<String, List<String>> proteinToTranscripts, String transcript, String protein) {
        if (proteinToTranscripts.get(protein) != null) {
            proteinToTranscripts.get(protein).add(transcript);
        } else {
            ArrayList<String> singleTranscriptArray = new ArrayList<>(Arrays.asList(transcript));
            proteinToTranscripts.put(protein, singleTranscriptArray);
        }
        return proteinToTranscripts;
    }

    private Map<String, List<String>> mapProteinToGenes(Map<String, List<String>> proteinToGenes, String gene, String protein) {
        if (proteinToGenes.get(protein) != null) {
            proteinToGenes.get(protein).add(gene);
        } else {
            ArrayList<String> singleGeneArray = new ArrayList<>(Arrays.asList(gene));
            proteinToGenes.put(protein, singleGeneArray);
        }
        return proteinToGenes;
    }

    private Map<String, List<String>> mapUniprotToProteins(Map<String, List<String>> uniprotToProteins, String protein, String uniprot) {
        if (uniprotToProteins.get(uniprot) != null) {
            uniprotToProteins.get(uniprot).add(protein);
        } else {
            ArrayList<String> singleProteinArray = new ArrayList<>(Arrays.asList(protein));
            uniprotToProteins.put(uniprot, singleProteinArray);
        }
        return uniprotToProteins;
    }

    private boolean necessaryColumnsContainData(List<String> tabSplit, int columnIndex1, int columnIndex2) {
         return !tabSplit.get(columnIndex1).isEmpty() && !tabSplit.get(columnIndex2).isEmpty();
    }
}
