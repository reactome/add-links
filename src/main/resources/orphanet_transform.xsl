<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:output method="text" indent="no" encoding="utf-8" media-type="text/plain" />
	<xsl:strip-space elements="*"/>
	<xsl:template match="/JDBOR/GeneList/Gene">
		<!-- Only interested in Genes that have a Reactome reference. -->
		<xsl:if test="./ExternalReferenceList/ExternalReference[Source='Reactome']">
			<!-- Get the ID of the Gene node -->
			<xsl:value-of select="@id"/>
			<xsl:text>,</xsl:text>
			<!-- Get the ID of the ExternalReference node -->
			<xsl:value-of select="./ExternalReferenceList/ExternalReference[Source='Reactome']/@id"/>
			<xsl:text>,</xsl:text>
			<!-- Get the value of the Reference node, this will be treated as a UniProt ID
			We will be interested in the mapping of UniProt IDs to OrphaNet IDs. -->
			<xsl:value-of select="./ExternalReferenceList/ExternalReference[Source='Reactome']/Reference/text()"/>
			<!-- Linebreak -->
			<xsl:text>&#10;</xsl:text>
		</xsl:if>
	</xsl:template>
</xsl:stylesheet>
