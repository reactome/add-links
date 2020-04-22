package org.reactome.addlinks.fileprocessors;

import org.reactome.addlinks.EnsemblBioMartUtil;

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

    private static final int MGI_IDENTIFIER_INDEX = 0;
    private static final int UNIPROT_IDENTIFIERS_INDEX = 6;

    /**
     * Build map of UniProt identifiers to Mouse Genome Informatics (MGI) identifiers that is used to create MGI cross-references in database.
     * @return - Map<String, List<String>
     */
    @Override
    public Map<String, List<String>> getIdMappingsFromFile()
    {
        Map<String, List<String>> mappings = new HashMap<>();

        for (String line : EnsemblBioMartUtil.getLinesFromFile(this.pathToFile, false)) {
            List<String> tabSplit = Arrays.asList(line.split("\t"));
            if (EnsemblBioMartUtil.necessaryColumnPresent(tabSplit, UNIPROT_IDENTIFIERS_INDEX)) {

                String mgiId = EnsemblBioMartUtil.getIdentifierWithoutPrefix(tabSplit.get(MGI_IDENTIFIER_INDEX));
                String[] uniprotIds = tabSplit.get(UNIPROT_IDENTIFIERS_INDEX).split(" ");
                for (String uniprotId : uniprotIds) {
                  mappings.computeIfAbsent(uniprotId, k -> new ArrayList<>()).add(mgiId);
                }
            }
        }

        return mappings;
    }
}