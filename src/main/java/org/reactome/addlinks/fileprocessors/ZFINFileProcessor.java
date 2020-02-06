package org.reactome.addlinks.fileprocessors;

import org.reactome.addlinks.EnsemblBioMartUtil;

import java.nio.file.Path;
import java.util.*;

public class ZFINFileProcessor extends FileProcessor{

    public ZFINFileProcessor(String processorName)
    {
        super(processorName);
    }

    public ZFINFileProcessor()
    {
        super();
    }

    private static final int ZFIN_IDENTIFIER_INDEX = 0;
    private static final int UNIPROT_IDENTIFIER_INDEX = 2;

    /**
     * Build map of UniProt identifiers to Zebrafish Information Network (ZFIN) identifiers that is used to create ZFIN cross-references in database.
     * @return - Map<String, List<String>>
     */
    @Override
    public Map<String, List<String>> getIdMappingsFromFile()
    {
        Map<String, List<String>> mappings = new HashMap<>();
        Path inputFilePathUnzipped = this.pathToFile;

        for (String line : EnsemblBioMartUtil.getLinesFromFile(inputFilePathUnzipped, false)) {
            List<String> tabSplit = Arrays.asList(line.split("\t"));
            if (EnsemblBioMartUtil.necessaryColumnPresent(tabSplit, UNIPROT_IDENTIFIER_INDEX)) {
                String zfinId = tabSplit.get(ZFIN_IDENTIFIER_INDEX);
                String uniprotId = tabSplit.get(UNIPROT_IDENTIFIER_INDEX);
                mappings.computeIfAbsent(uniprotId, k -> new ArrayList<>()).add(zfinId);
            }
        }
        return mappings;
    }
}
