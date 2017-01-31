package org.reactome.addlinks.fileprocessors.ensembl;

/**
 * A very simple class to contain a full ENSEMBL mapping, from Protein to Transcript to Gene to cross-reference ID.
 * @author sshorser
 *
 */
public class EnsemblMapping
{
	// ENSEMBL Protein ID
	private String ensp;
	// ENSEMBL Transcript ID
	private String enst;
	// ENSEMBL Gene ID
	private String ensg;
	// Cross-reference to another database.
	private String crossReference;
	// Name of cross-reference database.
	private String crossReferenceDBName;
	
	public EnsemblMapping(String p, String t, String g, String xref, String xrefDBName)
	{
		this.ensp = p;
		this.enst = t;
		this.ensg = g;
		this.crossReference = xref;
		this.crossReferenceDBName = xrefDBName;
	}
	
	public String getEnsp()
	{
		return this.ensp;
	}
	public void setEnsp(String ensp)
	{
		this.ensp = ensp;
	}
	public String getEnst()
	{
		return this.enst;
	}
	public void setEnst(String enst)
	{
		this.enst = enst;
	}
	public String getEnsg()
	{
		return this.ensg;
	}
	public void setEnsg(String ensg)
	{
		this.ensg = ensg;
	}
	public String getCrossReference()
	{
		return this.crossReference;
	}
	public void setCrossReference(String crossReference)
	{
		this.crossReference = crossReference;
	}
	public String getCrossReferenceDBName()
	{
		return this.crossReferenceDBName;
	}
	public void setCrossReferenceDBName(String crossReferenceDBName)
	{
		this.crossReferenceDBName = crossReferenceDBName;
	}
}