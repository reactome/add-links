package org.reactome.addlinks.dataretrieval;

abstract class AuthenticatableFileRetriever extends FileRetriever
{
	public AuthenticatableFileRetriever(String retrieverName)
	{
		super(retrieverName);
	}
	
	public AuthenticatableFileRetriever()
	{
		super();
	}
	
	protected String userName;
	protected String password;
	
	public void setUserName(String userName)
	{
		this.userName = userName;
	}
	public void setPassword(String password)
	{
		this.password = password;
	}
	
}
