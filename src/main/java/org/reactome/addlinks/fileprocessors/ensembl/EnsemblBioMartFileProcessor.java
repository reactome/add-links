package org.reactome.addlinks.fileprocessors.ensembl;

import org.reactome.addlinks.EnsemblBiomartUtil;
import org.reactome.addlinks.fileprocessors.FileProcessor;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class EnsemblBiomartFileProcessor extends FileProcessor<Map<String, List<String>>> {

    public EnsemblBiomartFileProcessor()
    {
        super(null);
    }

    public EnsemblBiomartFileProcessor(String processorName)
    {
        super(processorName);
    }

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

    private static final int geneIdentifierIndex = 0;
    private static final int transcriptIdentifierIndex = 1;
    private static final int proteinIdentifierIndex = 2;
    private static final int identifierIndex = 3;
    @Override
    public Map<String, Map<String, List<String>>> getIdMappingsFromFile() {

        // Below is a sample of the structure of the final mappings object. Each species has up to 4 different mappings associated with it.
        /*
             +hsapiens_uniprotToProteins
             |              |
             |              +---Q123456
             |              |       |
             |              |       + ENSP00001, ENSP00002,...
             |              |
             |              +---Q789012
             |                      |
             |                      + ENSP00003, ENSP00004,...
             |
             +ggallus_proteinsToTranscripts
             |              |
             |              +---ENSGALP00017
             |              |       |
             |              |       + ENSGALT00005, ENSGALT00006,...
             |              |
             |              +---ENSGALP00018
             |                      |
             |                      + ENSGALT00007, ENSGALT00008,...
             +hsapiens_transcriptsToMicroarrays
             |              |
             |              +---ENST000019
             |              |       |
             |              |       + ILMN_123456, ILMN_7891011,...
             |              |
             |              +---ENST000020
             |                      |
             |                      + ILMN_121314, ILMN_141617,...
             |
             +mmusculus_proteinsToGenes
             |              |
             |              +---ENSMUSP000020
             |              |       |
             |              |       + ENSMUSG00009, ENSMUSG00010,...
             |              |
             |              +---ENSMUSP000021
             |                      |
             |                      + ENSMUSG00011, ENSMUSG00012,...

         */

        Map<String, Map<String, List<String>>> mappings = new HashMap<>();
        // Read each file in Biomart directory and iterate through each line.
        try (Stream<Path> paths = Files.walk(this.pathToFile)) {
            paths
                .filter(Files::isRegularFile)
                .forEach(bioMartFile -> {
                    // Get species name from filename
                    String biomartFileSpecies = bioMartFile.getFileName().toString().split("_")[0];
                    // Mappings that will be generated from each file for the species.
                    Map<String, List<String>> proteinToTranscripts = new HashMap<>();
                    Map<String, List<String>> proteinToGenes = new HashMap<>();
                    Map<String, List<String>> transcriptToMicroarrays = new HashMap<>();
                    Map<String, List<String>> uniprotToProteins = new HashMap<>();
                    try {
                        for (String biomartLine : Files.readAllLines(bioMartFile)) {
                            // Split each tab-separated line. Each line has four values. The first three are always
                            // Ensembl Gene (ENSG), Transcript (ENST) and Protein (ENSP), in that order.
                            // The 4th can be a UniProt or microarray identifier, depending on the file being read.
                            // None of the values are guaranteed to in the line, except perhaps for the first (Gene).
                            List<String> tabSplit = Arrays.asList(biomartLine.split("\t"));
                            // Processing of UniProt mapping files.
                            // UniProt identifier mapping files are used to fully populate
                            // the 'uniprotToProteins' and 'proteinToGenes' mappings.
                            if (bioMartFile.toString().endsWith("uniprot")) {
                                // 'uniprotToProteins' mapping requires the 3rd (protein) and 4th (uniprot) values in the line.
                                if (properArraySizeAndNecessaryColumnsContainData(tabSplit, 3,proteinIdentifierIndex,identifierIndex)) {
                                    uniprotToProteins = mapIdentifiers(uniprotToProteins, tabSplit.get(identifierIndex), tabSplit.get(proteinIdentifierIndex));
                                    // Add each mapping generated to the 'super' mapping
                                    mappings.put(biomartFileSpecies + EnsemblBiomartUtil.uniprotToProteinsSuffix, uniprotToProteins);
                                }
                                // 'proteinToGenes' mappings require the 1st (gene) and 3rd (protein) values in the line.
                                if (properArraySizeAndNecessaryColumnsContainData(tabSplit, 0,geneIdentifierIndex, proteinIdentifierIndex)) {
                                    proteinToGenes = mapIdentifiers(proteinToGenes, tabSplit.get(proteinIdentifierIndex), tabSplit.get(geneIdentifierIndex));
                                    // Add each mapping generated to the 'super' mapping
                                    mappings.put(biomartFileSpecies + EnsemblBiomartUtil.proteinToGenesSuffix, proteinToGenes);
                                }

                            // Processing of Microarray mapping files.
                            // Microarray identifier mapping files are used to fully populate the
                            // 'proteinToTranscripts' and 'transcriptToMicroarray' mappings.
                            // Add each mapping generated to the 'super' mapping data structure that is returned.
                            } else if (bioMartFile.toString().endsWith("microarray")) {
                                // 'proteinToTranscripts' mapping requires the 2nd (transcript) and 3rd (protein) values in the line.
                                if (properArraySizeAndNecessaryColumnsContainData(tabSplit, 2,transcriptIdentifierIndex, proteinIdentifierIndex)) {
                                    proteinToTranscripts = mapIdentifiers(proteinToTranscripts, tabSplit.get(proteinIdentifierIndex), tabSplit.get(transcriptIdentifierIndex));
                                    // Add each mapping generated to the 'super' mapping
                                    mappings.put(biomartFileSpecies + EnsemblBiomartUtil.proteinToTranscriptsSuffix, proteinToTranscripts);
                                }
                                // 'transcriptToMicroarrays' mapping requires the 2nd (transcript) and 4th (microarray) values in the line.
                                if (properArraySizeAndNecessaryColumnsContainData(tabSplit, 3,transcriptIdentifierIndex, identifierIndex)) {
                                    transcriptToMicroarrays = mapIdentifiers(transcriptToMicroarrays, tabSplit.get(transcriptIdentifierIndex), tabSplit.get(identifierIndex));
                                    // Add each mapping generated to the 'super' mapping
                                    mappings.put(biomartFileSpecies + EnsemblBiomartUtil.transcriptToMicroarraysSuffix, transcriptToMicroarrays);
                                }
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
            });
        } catch (IOException e) {
            e.printStackTrace();
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
        identifierToIdentifierMapping.computeIfAbsent(identifier1, k -> new ArrayList<>()).add(identifier2);
        return identifierToIdentifierMapping;
    }
    //            rgpIdentifiersToRGPs.computeIfAbsent(identifier, k -> new ArrayList<>()).add(rgpInst);
    // Checks that data exists at the supplied column indices.
    private boolean properArraySizeAndNecessaryColumnsContainData(List<String> tabSplit, int necessaryArrayLength, int necessaryColumnIndex1, int necessaryColumnIndex2) {
         return tabSplit.size() > necessaryArrayLength && !tabSplit.get(necessaryColumnIndex1).isEmpty() && !tabSplit.get(necessaryColumnIndex2).isEmpty();
    }
}
