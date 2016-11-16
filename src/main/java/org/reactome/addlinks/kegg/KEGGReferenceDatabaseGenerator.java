package org.reactome.addlinks.kegg;

public class KEGGReferenceDatabaseGenerator
{

	/**
	 * This mapping was derivd from the Config_Species.pm module in the Release code, relative path: Release/modules/GKB/Config_Species.pm
	 * @author sshorser
	 *
	 */
	enum speciesMapping
	{
		//If you want more, you can use this regexp:
		// ^[A-Z]\ *[a-z]\{3,4\} *[a-zA-Z0-9 .\[\]\/\:-_]*
		// to parse this file:
		// http://www.genome.jp/kegg-bin/download_htext?htext=br08601.keg&format=htext&filedir=
		ath("Arabidopsis thaliana"),
		osa("Oryza sativa"),
		cel("Caenorhabditis elegans"),
		ddi("Dictyostelium discoideum"),
		dme("Drosophila melanogaster"),
		eco("Escherichia coli"),
		hsa("Homo sapiens"),
		mja("Methanococcus jannaschii"),
		mmu("Mus musculus"),
		pfa("Plasmodium falciparum"),
		sce("Saccharomyces cerevisiae"),
		spo("Schizosaccharomyces pombe"),
		sso("Sulfolobus solfataricus"),
		tni("Tetraodon nigroviridis"),
		gga("Gallus gallus"),
		rno("Rattus norvegicus"),
		cne("Cryptococcus neoformans"),
		ncr("Neurospora crassa"),
		syn("Synechococcus sp."),
		mtu("Mycobacterium tuberculosis"),
		ehi("Entamoeba histolytica"),
		cme("Cyanidioschyzon merolae"),
		tps("Thalassiosira pseudonana"),
		;
		private String fullName;
		speciesMapping(String s)
		{
			this.fullName = s;
		}
		
		public String getFullName()
		{
			return this.fullName;
		}
	}
	
}
