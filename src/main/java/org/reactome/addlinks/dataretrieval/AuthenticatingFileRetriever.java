package org.reactome.addlinks.dataretrieval;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.provider.sftp.SftpClientFactory;

import org.reactome.release.common.dataretrieval.AuthenticatableFileRetriever;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

public class AuthenticatingFileRetriever extends AuthenticatableFileRetriever
{
	public AuthenticatingFileRetriever(String retrieverName)
	{
		super(retrieverName);
	}

	public AuthenticatingFileRetriever()
	{
		super();
	}

	@Override
	protected void downloadData() throws Exception
	{
		logger.trace("Scheme is: "+this.uri.getScheme());
		Path path = Paths.get(new URI("file://"+this.destination));
		Files.createDirectories(path.getParent());
		if (this.uri.getScheme().equals("http") || this.uri.getScheme().equals("https"))
		{
			doHttpDownload(path);
		}
		else if (this.uri.getScheme().equals("ftp"))
		{
			doFtpDownload(this.userName, this.password);
		}
		else if (this.uri.getScheme().equals("sftp"))
		{
			doSftpDownload(this.userName, this.password);
		}
		else
		{
			throw new IllegalStateException("URI "+this.uri.toString()+" uses an unsupported scheme: "+this.uri.getScheme());
		}

	}

	protected void doSftpDownload(String user, String password) throws JSchException, SftpException, FileNotFoundException, IOException
	{
		String hostname = this.uri.getHost();
		int port = this.uri.getPort();

		FileSystemOptions fileSystemOptions = new FileSystemOptions();
		Session session = SftpClientFactory.createConnection(hostname, port, user.toCharArray(), password.toCharArray(), fileSystemOptions);

		Channel channel = session.openChannel("sftp");
		channel.connect();
		ChannelSftp sftpChannel = (ChannelSftp)channel;
		InputStream inStream = sftpChannel.get(this.getDataURL().getPath());

		writeInputStreamToFile(inStream);

		sftpChannel.disconnect();
		channel.disconnect();
		session.disconnect();
	}
}
