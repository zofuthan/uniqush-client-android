package org.uniqush.android;

import org.uniqush.client.CredentialProvider;

public interface UserInfoProvider extends CredentialProvider {
	/**
	 * The app may have several user accounts. This method call only returns
	 * the current user's information. If a connection to the app server is required to
	 * perform certain operation, then this method will be called to establish such
	 * a connection on behave of the user. (If there's already such a connection, the
	 * connection will be reused. Otherwise a new connection will be established.)
	 * 
	 * @return The current user's information
	 */
	public UserInfo getUserInfo();
	
	/**
	 * The app may have several user accounts. This method returns the accounts which wants
	 * to receive push notifications when they are offline.
	 * 
	 * @return
	public UserInfo[] getSubscribers();
	 */
	
	/**
	 * Returns the host name (either domain name or IP address) of the server.
	 * This method will be called only when a new connection needs to be established
	 * between the app and the app server.
	 * 
	 * @return
	 */
	public String getHost();

	/**
	 * Returns the port of the server, on which the uniqush-conn is running.
	 * This method will be called only when a new connection needs to be established
	 * between the app and the app server.
	 * 
	 * @return
	 */
	public int getPort();

	public String[] getSenderIds();
	public MessageHandler getMessageHandler(String host, int port, String service, String username);
}
