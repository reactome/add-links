package org.reactome.addlinks.dataretrieval.pharos;

/**
 * An exception to represent problems caused by data issues from Pharos
 * @author sshorser
 *
 */
public class PharosDataException extends Exception
{

	public PharosDataException(String message)
	{
		super(message);
	}

}
