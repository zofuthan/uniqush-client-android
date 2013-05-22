package org.uniqush.android;

import java.security.interfaces.RSAPublicKey;

import org.uniqush.client.Message;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class MessageCenter {
	
	private static String TAG = "UniqushMessageCenter";
	private ConnectionParameter defaultParam;
	private String defaultToken;
	
	public MessageCenter(String ...senderIds) {
		for (String s : senderIds) {
			ResourceManager.getResourceManager().setSenderIds(s);
		}
	}
	
	public void stop(Context context) {
		Log.i(TAG, "stoping the service...");
		Intent intent = new Intent(context, MessageCenterService.class);
		context.stopService(intent);
	}
	
	/**
	 * Asynchronously send the message to server. The result will be reported
	 * through the handler's onResult() method if id > 0.
	 * 
	 * NOTE: the method will return silently if connect() has never been called.
	 * 
	 * As long as connect() has been called once, users do not need to
	 * worry about re-connection and other related issues. MessageCenter
	 * will trigger another connection if the connection dropped or the
	 * background service killed by the OS.
	 * 
	 * @param context The context
	 * @param id If id > 0, then any result (error or success) will be reported
	 * through the handler's onResult() method. Otherwise (i.e. id <= 0), 
	 * only error will be reported through the handler's onError() method.
	 * @param msg
	 */
	public void sendMessageToServer(Context context, int id, Message msg) {
		if (this.defaultParam == null || this.defaultToken == null) {
			return;
		}
		Intent intent = new Intent(context, MessageCenterService.class);
		intent.putExtra("c", MessageCenterService.CMD_SEND_MSG_TO_SERVER);
		intent.putExtra("connection", this.defaultParam.toString());
		intent.putExtra("token", this.defaultToken);
		intent.putExtra("id", id);
		intent.putExtra("msg", msg);
		context.startService(intent);
	}
	
	public void sendMessageToUser(Context context,
			int id,
			String service,
			String username,
			Message msg,
			int ttl) {
		Intent intent = new Intent(context, MessageCenterService.class);
		intent.putExtra("c", MessageCenterService.CMD_SEND_MSG_TO_USER);
		intent.putExtra("connection", this.defaultParam.toString());
		intent.putExtra("token", this.defaultToken);
		intent.putExtra("id", id);
		intent.putExtra("service", service);
		intent.putExtra("username", username);
		intent.putExtra("ttl", ttl);
		intent.putExtra("msg", msg);
		context.startService(intent);
	}
	
	/**
	 * 
	 * @param context
	 * @param id If id > 0, then any result (error or success) will be reported
	 * through the handler's onResult() method. Otherwise (i.e. id <= 0), 
	 * only error will be reported through the handler's onError() method.
	 * @param address
	 * @param port
	 * @param publicKey The public key of the server.
	 * @param service The name of the service
	 * @param username
	 * @param token This could be used for password-based user authentication.
	 * The token will be sent to the server securely. i.e. encrypted with the
	 * session key. The value of the token has nothing to do with the value
	 * of the session key. In another word, it is not used to derive the
	 * session key.
	 * @param handler The message handler for the connection.
	 * NOTE: It is org.uniqush.android.MessageHandler, not org.uniqush.client.MessageHanlder.
	 */
	public void connectServer(Context context,
			int id,
			String address,
			int port, 
			RSAPublicKey publicKey, 
			String service, 
			String username, 
			String token,
			MessageHandler handler) {
		
		Log.i(TAG, "connect in message center");
		ConnectionParameter param = new ConnectionParameter(address,
				port,
				publicKey,
				service,
				username,
				handler);
		ResourceManager.getResourceManager().addConnectionParameter(param);
		Intent intent = new Intent(context, MessageCenterService.class);
		intent.putExtra("c", MessageCenterService.CMD_CONNECT);
		intent.putExtra("connection", param.toString());
		intent.putExtra("token", token);
		intent.putExtra("id", id);
		this.defaultParam = param;
		this.defaultToken = token;
		context.startService(intent);
	}
}
