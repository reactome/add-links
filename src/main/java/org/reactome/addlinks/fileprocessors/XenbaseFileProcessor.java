package org.reactome.addlinks.fileprocessors;

import org.reactome.addlinks.EnsemblBioMartUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class XenbaseFileProcessor extends FileProcessor{

    public XenbaseFileProcessor(String processorName)
    {
        super(processorName);
    }

    public XenbaseFileProcessor()
    {
        super();
    }

    private static final int uniprotIdentifierIndex = 0;
    private static final int xenbaseIdentifierIndex = 3;

    /**
     * Build map of UniProt identifiers to Xenbase identifiers that is used to create Xenbase cross-references in database.
     * @return - Map<String, List<String>
     */
    @Override
    public Map<String, List<String>> getIdMappingsFromFile()
    {
        Map<String, List<String>> mappings = new HashMap<>();

        List<String> lines = new ArrayList<>();
        try {
            lines = EnsemblBioMartUtil.getLinesFromFile(this.pathToFile.toAbsolutePath(), false);
        } catch (IOException e) {
            logger.error("Error reading file ({}): {}", this.pathToFile.toAbsolutePath(), e.getMessage());
            e.printStackTrace();
        }

        for (String line : lines) {
            List<String> tabSplit = Arrays.asList(line.split("\t"));
            String xenbaseId = tabSplit.get(xenbaseIdentifierIndex);
            String uniprotId = tabSplit.get(uniprotIdentifierIndex);
            mappings.computeIfAbsent(uniprotId, k -> new ArrayList<>()).add(xenbaseId);
        }

        return mappings;
    }
}