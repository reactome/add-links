<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:output method="text" indent="no" encoding="utf-8" media-type="text/plain" />
	<xsl:strip-space elements="*" />
	<!-- match against nodes that have a valid Parent attribute -->
	<xsl:template match="//opt/data/*[@Parent] | //opt/data[@Parent]">
		<!-- Each line should be the "@id,@Parent"
		The value of @id is what was originally looked up in ENSEMBL,
		@Parent is ENSEMBL ID of the parent element. -->
		<xsl:value-of select="concat(@id,',',@Parent)"/>
		<xsl:text>&#10;</xsl:text>
	</xsl:template>
	 
</xsl:stylesheet>