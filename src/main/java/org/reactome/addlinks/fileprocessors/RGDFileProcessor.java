package org.reactome.addlinks.fileprocessors;

import org.reactome.addlinks.EnsemblBioMartUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    private static final int rgdIdentifierIndex = 0;
    private static final int uniprotIdentifiersIndex = 21;

    /**
     * Build map of UniProt identifiers to Rat Genome Database (RGD) identifiers that is used to create RGD cross-references in database.
     * @return - Map<String, List<String>
     */
    @Override
    public Map<String, List<String>> getIdMappingsFromFile()
    {
        Map<String, List<String>> mappings = new HashMap<>();
        Path inputFilePath = Paths.get(this.pathToFile.toAbsolutePath().toString());

        List<String> lines = new ArrayList<>();
        try {
            lines = EnsemblBioMartUtil.getLinesFromFile(inputFilePath, true);
        } catch (IOException e) {
            logger.error("Error reading file ({}): {}", inputFilePath.toString(), e.getMessage());
            e.printStackTrace();
        }

        for (String line : lines) {
            if (isFileBodyLine(line)) {
                List<String> tabSplit = Arrays.asList(line.split("\t"));
                String rgdId = tabSplit.get(rgdIdentifierIndex);
                List<String> uniprotIds = Arrays.asList(tabSplit.get(uniprotIdentifiersIndex).split(";"));
                if (!uniprotIds.isEmpty()) {
                    for (String uniprotId : uniprotIds) {
                        mappings.computeIfAbsent(uniprotId, k -> new ArrayList<>()).add(rgdId);
                    }
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