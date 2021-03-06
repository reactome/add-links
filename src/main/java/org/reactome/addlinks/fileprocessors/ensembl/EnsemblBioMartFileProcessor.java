package org.reactome.addlinks.fileprocessors.ensembl;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.reactome.addlinks.EnsemblBioMartUtil;
import org.reactome.addlinks.fileprocessors.FileProcessor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class EnsemblBioMartFileProcessor extends FileProcessor<Map<String, List<String>>> {

    public EnsemblBioMartFileProcessor()
    {
        super();
    }

    public EnsemblBioMartFileProcessor(String processorName)
    {
        super(processorName);
    }

    private static final int GENE_IDENTIFIER_INDEX = 0;
    private static final int TRANSCRIPT_IDENTIFIER_INDEX = 1;
    private static final int PROTEIN_IDENTIFIER_INDEX = 2;
    private static final int IDENTIFIER_INDEX = 3;
    /**
     * This class builds a large, rather unwieldy, two-layer mapping structure of identifiers corresponding to a specific species.
     * The first level has 4 types of keys for each species (see structure below). These keys are 'uniprotToProteins', 'proteinToGenes',
     * 'proteinToTranscripts', and 'transcriptToOtherIdentifiers'. These describe the type of mapping that this
     * key is to. For example, the key 'hsapiens_uniprotToProteins' would access a Map of Human UniProt protein identifiers
     * to a List of Ensembl protein identifiers. Transcript denotes Ensembl transcript identifiers, Gene is Ensembl gene identifiers
     * and OtherIdentifiers denotes either microarray or GO term identifiers.
     * @return Map<String, Map<String, List<String>>>> mappings, the two-layer mapping structure of species-(identifier-[identifiers]).
     *
     * Below is a sample of the structure of the final mappings object. Each species has up to 4 different mappings associated with it.
     *
     *              +hsapiens_uniprotToProteins
     *              |              |
     *              |              +---Q123456
     *              |              |       |
     *              |              |       + ENSP00001, ENSP00002,...
     *              |              |
     *              |              +---Q789012
     *              |                      |
     *              |                      + ENSP00003, ENSP00004,...
     *              |
     *              +ggallus_proteinsToTranscripts
     *              |              |
     *              |              +---ENSGALP00017
     *              |              |       |
     *              |              |       + ENSGALT00005, ENSGALT00006,...
     *              |              |
     *              |              +---ENSGALP00018
     *              |                      |
     *              |                      + ENSGALT00007, ENSGALT00008,...
     *              +hsapiens_transcriptsToOtherIdentifiers
     *              |              |
     *              |              +---ENST000019
     *              |              |       |
     *              |              |       + ILMN_123456, ILMN_7891011,...
     *              |              |
     *              |              +---ENST000020
     *              |                      |
     *              |                      + ILMN_121314, ILMN_141617,...
     *              |
     *              +mmusculus_proteinsToGenes
     *              |              |
     *              |              +---ENSMUSP000020
     *              |              |       |
     *              |              |       + ENSMUSG00009, ENSMUSG00010,...
     *              |              |
     *              |              +---ENSMUSP000021
     *              |                      |
     *              |                      + ENSMUSG00011, ENSMUSG00012,...
     */
    @Override
    public Map<String, Map<String, List<String>>> getIdMappingsFromFile() {

        Map<String, Map<String, List<String>>> mappings = new HashMap<>();
        // Read each file in BioMart directory and iterate through each line.
        try (Stream<Path> paths = Files.walk(this.pathToFile)) {
            paths.filter(Files::isRegularFile)
                 .forEach(bioMartFile -> {
                    // Get species name from filename
                    String biomartFileSpecies = bioMartFile.getFileName().toString().split("_")[0];
                    logger.info("Processing {}", bioMartFile.getFileName());

                    // Processing of UniProt mapping files.
                    // UniProt identifier mapping files are used to fully populate
                    // the 'uniprotToProteins' and 'proteinToGenes' mappings.
                    if (bioMartFile.toString().endsWith(EnsemblBioMartUtil.UNIPROT_SUFFIX)) {
                        // 'uniprotToProteins' mapping requires the 3rd (protein) and 4th (uniprot) values in the line.
                        Map<String, List<String>> uniprotToProteins = getIdentifierMapping(bioMartFile, IDENTIFIER_INDEX, PROTEIN_IDENTIFIER_INDEX);
                        if (!uniprotToProteins.isEmpty()) {
                            // Add each mapping generated to the 'super' mapping
                            mappings.put(biomartFileSpecies + EnsemblBioMartUtil.UNIPROT_TO_PROTEINS_SUFFIX, uniprotToProteins);
                        }
                        // 'proteinToGenes' mappings require the 1st (gene) and 3rd (protein) values in the line.
                        Map<String, List<String>> proteinToGenes = getIdentifierMapping(bioMartFile, PROTEIN_IDENTIFIER_INDEX, GENE_IDENTIFIER_INDEX);
                        if (!proteinToGenes.isEmpty()) {
                            // Add each mapping generated to the 'super' mapping
                            mappings.put(biomartFileSpecies + EnsemblBioMartUtil.PROTEIN_TO_GENES_SUFFIX, proteinToGenes);
                        }
                    // Processing of OtherIdentifier (Microarray/GO/NCBI term) mapping files.
                    // OtherIdentifier mapping files are used to fully populate the
                    // 'proteinToTranscripts' and 'transcriptToOtherIdentifiers' mappings.
                    } else if (bioMartFile.toString().endsWith(EnsemblBioMartUtil.OTHER_IDENTIFIERS_SUFFIX)) {
                        // 'proteinToTranscripts' mapping requires the 2nd (transcript) and 3rd (protein) values in the line.
                        Map<String, List<String>> proteinToTranscripts = getIdentifierMapping(bioMartFile, PROTEIN_IDENTIFIER_INDEX, TRANSCRIPT_IDENTIFIER_INDEX);
                        if (!proteinToTranscripts.isEmpty()) {
                            // Add each mapping generated to the 'super' mapping
                            mappings.put(biomartFileSpecies + EnsemblBioMartUtil.PROTEIN_TO_TRANSCRIPTS_SUFFIX, proteinToTranscripts);
                        }
                        // 'transcriptToOtherIdentifiers' mapping requires the 2nd (transcript) and 4th (microarray/GO term) values in the line.
                        Map<String, List<String>> transcriptToOtherIdentifiers = getIdentifierMapping(bioMartFile, TRANSCRIPT_IDENTIFIER_INDEX, IDENTIFIER_INDEX);
                        if (!transcriptToOtherIdentifiers.isEmpty()) {
                            // Add each mapping generated to the 'super' mapping
                            mappings.put(biomartFileSpecies + EnsemblBioMartUtil.TRANSCRIPT_TO_OTHER_IDENTIFIERS_SUFFIX, transcriptToOtherIdentifiers);
                        }
                    }
                });
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("Error reading file ({}): {}", this.pathToFile, e);
        }
        return mappings;
    }

    /**
     * This method builds the mappings from the BioMart data. It iterates through all biomart file lines and then
     * adds one of the four values (Gene, Transcript, Protein, UniProt/Microarray/GO terms) as a key to a List of
     * one of the other four values. For example, if it was building 'TranscriptToOtherIdentifiers', the key is the
     * transcript identifier of the line while the value is a List of Microarray/GO term identifiers.
     * @param bioMartFile Path - A Path to a biomart file.
     * @param keyIndex int - The column indice that will be used as a key.
     * @param valueIndex int - The column indice that will be used as a value.
     * @return Map<String, List<String>> - The completed mapping from the BioMart file being processed.
     */
    private Map<String, List<String>> getIdentifierMapping(Path bioMartFile, int keyIndex, int valueIndex)
    {
        Map<String, List<String>> identifierMapping = new HashMap<>();
        try
        {
            LineIterator iterator = FileUtils.lineIterator(bioMartFile.toFile(), StandardCharsets.ISO_8859_1.toString());
            
            while (iterator.hasNext())
            {
                String line = iterator.nextLine();
                List<String> tabSplit = Arrays.asList(line.split("\t"));
                if (properArraySizeAndNecessaryColumnsContainData(tabSplit, keyIndex, valueIndex))
                {
                    String mapKey = tabSplit.get(keyIndex);
                    String mapValue = tabSplit.get(valueIndex);
                    identifierMapping.computeIfAbsent(mapKey, k -> new ArrayList<>()).add(mapValue);
                }
            }
        }
        catch (IOException e)
        {
            logger.error("I/O Error with file: " + bioMartFile.toString(), e);
            e.printStackTrace();
        }
        
        return identifierMapping;
    }

    // Checks that the data columns exist and that data exists at the necessary column indices.
    private boolean properArraySizeAndNecessaryColumnsContainData(List<String> tabSplit, int necessaryColumnIndex1, int necessaryColumnIndex2) {
        int maxRequiredArrayIndex = Collections.max(Arrays.asList(necessaryColumnIndex1, necessaryColumnIndex2));
        return tabSplit.size() > maxRequiredArrayIndex && !tabSplit.get(necessaryColumnIndex1).isEmpty() && !tabSplit.get(necessaryColumnIndex2).isEmpty();
    }
}
