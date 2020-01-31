package org.reactome.addlinks.fileprocessors;

import org.reactome.addlinks.EnsemblBioMartUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class MGIFileProcessor extends FileProcessor{

    public MGIFileProcessor(String processorName)
    {
        super(processorName);
    }

    public MGIFileProcessor()
    {
        super();
    }

    private static final int mgiIdentifierIndex = 0;
    private static final int uniprotIdentifiersIndex = 6;

    /**
     * Build map of UniProt identifiers to Mouse Genome Informatics (MGI) identifiers that is used to create MGI cross-references in database.
     * @return - Map<String, List<String>
     */
    @Override
    public Map<String, List<String>> getIdMappingsFromFile()
    {
        Map<String, List<String>> mappings = new HashMap<>();
        Path inputFilePath = Paths.get(this.pathToFile.toAbsolutePath().toString().replace(".gz", ""));
        List<String> lines = new ArrayList<>();
        try {
            lines = EnsemblBioMartUtil.getLinesFromFile(inputFilePath, false);
        } catch (IOException e) {
            logger.error("Error reading file ({}): {}", inputFilePath.toString(), e.getMessage());
            e.printStackTrace();
        }

        for (String line : lines) {
            List<String> tabSplit = Arrays.asList(line.split("\t"));
            if (EnsemblBioMartUtil.necessaryColumnPresent(tabSplit, uniprotIdentifiersIndex)) {

                String mgiId = tabSplit.get(mgiIdentifierIndex).split(":")[1];
                String[] uniprotIds = tabSplit.get(uniprotIdentifiersIndex).split(" ");
                for (String uniprotId : uniprotIds) {
                  mappings.computeIfAbsent(uniprotId, k -> new ArrayList<>()).add(mgiId);
                }
            }
        }
        return mappings;
    }
}