package org.reactome.addlinks.fileprocessors;

import org.reactome.addlinks.EnsemblBioMartUtil;

import java.util.*;

public class RGDFileProcessor extends FileProcessor{

    public RGDFileProcessor(String processorName)
    {
        super(processorName);
    }

    public RGDFileProcessor()
    {
        super();
    }

    private static final int RGD_IDENTIFIER_INDEX = 0;
    private static final int UNIPROT_IDENTIFIERS_INDEX = 21;

    /**
     * Build map of UniProt identifiers to Rat Genome Database (RGD) identifiers that is used to create RGD cross-references in database.
     * @return - Map<String, List<String>
     */
    @Override
    public Map<String, List<String>> getIdMappingsFromFile()
    {
        Map<String, List<String>> mappings = new HashMap<>();

        for (String line : EnsemblBioMartUtil.getLinesFromFile(this.pathToFile, true)) {
            if (isFileBodyLine(line)) {
                List<String> tabSplit = Arrays.asList(line.split("\t"));
                String rgdId = tabSplit.get(RGD_IDENTIFIER_INDEX);
                List<String> uniprotIds = Arrays.asList(tabSplit.get(UNIPROT_IDENTIFIERS_INDEX).split(";"));
                for (String uniprotId : uniprotIds) {
                    mappings.computeIfAbsent(uniprotId, k -> new ArrayList<>()).add(rgdId);
                }
            }
        }

        return mappings;
    }

    // Checks that first value in the line is a digit. This distinguishes between file header and the body.
    private boolean isFileBodyLine(String line) {
        return Character.isDigit(line.charAt(0));
    }
}