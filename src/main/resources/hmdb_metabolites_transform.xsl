<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:hmdb="http://www.hmdb.ca" >
	<xsl:output encoding="utf-8" method="text"/>
	<xsl:strip-space elements="*" />
	<!-- Produce a TSV of HMDB accessions followed by a UniProt ID -->

	<!-- Match any metabolite -->
	<xsl:template match="/hmdb:metabolite/hmdb:accession" >
		<xsl:value-of select="./text()" />
 		<xsl:text>&#x9;</xsl:text>
		<xsl:value-of select="../hmdb:chebi_id/text()"/>
		<xsl:text>&#x9;</xsl:text>
		<xsl:for-each select="../hmdb:protein_associations/hmdb:protein/hmdb:uniprot_id">
			<xsl:value-of select="./text()"/>
			<xsl:text>&#x9;</xsl:text>
		</xsl:for-each>
		<xsl:text>&#10;</xsl:text>
	</xsl:template>
	
	
	<!-- Discard anything that does not match the template above. -->
	<xsl:template match="text()|@*"/>
	
</xsl:stylesheet>