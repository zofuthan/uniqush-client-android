package org.uniqush.android;

import java.util.HashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;

import org.uniqush.client.MessageCenter;

import com.google.android.gcm.GCMRegistrar;

public class MessageCenterService extends Service {

	private String TAG = "UniqushMessageCenterService";
	protected final static int CMD_STOP = 0;
	protected final static int CMD_CONNECT = 1;
	protected final static int CMD_SEND_MSG_TO_SERVER = 2;
	protected final static int CMD_SEND_MSG_TO_USER = 3;
	protected final static int CMD_REQUEST_MSG = 4;
	protected final static int CMD_CONFIG = 5;
	protected final static int CMD_SET_VISIBILITY = 6;
	protected final static int CMD_REQUEST_ALL_CACHED_MSG = 7;
	protected final static int CMD_ERROR_ACCOUNT_MISSING = 8;
	protected final static int CMD_SUBSCRIBE = 9;
	protected final static int CMD_UNSUBSCRIBE = 10;
	protected final static int CMD_MESSAGE_DIGEST = 11;
	protected final static int CMD_USER_INFO_READY = 12;
	protected final static int CMD_REGID_READY = 13;
	protected final static int CMD_MAX_CMD_ID = 14;

	private UserInfoProvider userInfoProvider;
	private Lock userInfoProviderLock;

	private MessageCenter center;
	private ConnectionInfo currentConn;
	private MessageHandler handler;
	private Thread readerThread;
	private ReadWriteLock centerLock;

	@Override
	public void onCreate() {
		userInfoProviderLock = new ReentrantLock();
		centerLock = new ReentrantReadWriteLock();
		userInfoProvider = null;
		center = null;
		readerThread = null;
		handler = null;
		Log.i(TAG, "service created");
	}

	@Override
	public void onDestroy() {
		Log.i(TAG, "service destroyed");
		centerLock.readLock().lock();
		if (handler != null) {
			handler.onServiceDestroyed();
		}
		centerLock.readLock().unlock();
		this.disconnect();
	}

	private void setUserInfoProvider() {
		userInfoProviderLock.lock();
		userInfoProvider = ResourceManager.getUserInfoProvider(this);
		userInfoProviderLock.unlock();
	}

	private void disconnect() {
		centerLock.writeLock().lock();
		if (this.center != null) {
			this.center.stop();
			try {
				this.readerThread.join(3000);
			} catch (InterruptedException e) {
				this.readerThread.stop();
				try {
					this.readerThread.join();
				} catch (InterruptedException e1) {
					// XXX what should I do here?
				}
			}

			this.center = null;
			this.currentConn = null;
			this.handler = null;
		}
		centerLock.writeLock().unlock();
	}

	private void connect(int callId) {
		Log.i(TAG, "connect with call id " + callId);
		userInfoProviderLock.lock();
		if (this.userInfoProvider == null) {
			this.userInfoProvider = ResourceManager.getUserInfoProvider(this);
			if (this.userInfoProvider == null) {
				Log.wtf(TAG,
						"UserInfoProvider is missing. call MessageCenter.init() first!");
				userInfoProviderLock.unlock();
				return;
			}
		}
		ConnectionInfo cinfo = this.userInfoProvider.getConnectionInfo();
		MessageHandler msgHandler = this.userInfoProvider.getMessageHandler(
				cinfo.getHostName(), cinfo.getPort(), cinfo.getServiceName(),
				cinfo.getUserName());
		UserInfoProvider uip = this.userInfoProvider;
		userInfoProviderLock.unlock();

		if (msgHandler == null || cinfo == null) {
			Log.w(TAG, "msg handler or connection info is null");
			return;
		}

		Log.i(TAG, "connect to " + cinfo.toString());
		centerLock.writeLock().lock();
		if (this.center != null) {
			if (this.currentConn.equals(cinfo)) {
				// We've already connected this server.
				if (this.currentConn.shouldSubscribe() != cinfo
						.shouldSubscribe()) {
					centerLock.writeLock().unlock();
					this.subscribe(callId);
				} else {
					centerLock.writeLock().unlock();
				}
				return;
			}
			this.disconnect();
		}

		this.center = new MessageCenter(uip);
		try {
			this.center.connect(cinfo.getHostName(), cinfo.getPort(),
					cinfo.getServiceName(), cinfo.getUserName(), msgHandler);
		} catch (Exception e) {
			Log.e(TAG, "Error on connection: " + e.toString());
			this.center = null;
			if (callId >= 0) {
				msgHandler.onResult(callId, e);
			} else {
				msgHandler.onError(e);
			}
			centerLock.writeLock().unlock();
			return;
		}
		readerThread = new Thread(this.center);
		readerThread.start();
		currentConn = cinfo;
		handler = msgHandler;
		centerLock.writeLock().unlock();

		Log.i(TAG, "connected");
		this.subscribe(callId);
	}

	private void subscribe(int callId) {
		centerLock.readLock().lock();
		if (center == null) {
			return;
		}
		String regid = this.getRegId();
		if (regid == null || regid.length() == 0) {
			// We dot not have the registration id. Wait for it.
			centerLock.readLock().unlock();
			return;
		}

		if (center == null || handler == null || currentConn == null) {
			centerLock.readLock().unlock();
			Log.w(TAG, "regid is ready but the message center is not.");
			return;
		}
		String service = this.currentConn.getServiceName();
		String username = this.currentConn.getUserName();
		HashMap<String, String> params = new HashMap<String, String>(3);
		params.put("pushservicetype", "gcm");
		params.put("service", service);
		params.put("subscriber", username);
		params.put("regid", regid);
		if (currentConn.shouldSubscribe()
				&& !ResourceManager.subscribed(this, service, username)) {
			// should subscribe.
			try {
				center.subscribe(params);
			} catch (Exception e) {
				if (callId >= 0) {
					handler.onResult(callId, e);
				} else {
					handler.onError(e);
				}
				centerLock.readLock().unlock();
				return;
			}
			ResourceManager.setSubscribed(this, service, username, true);

		} else if (!currentConn.shouldSubscribe()
				&& ResourceManager.subscribed(this, service, username)) {
			// should unsubscribe.
			try {
				center.unsubscribe(params);
			} catch (Exception e) {
				if (callId >= 0) {
					handler.onResult(callId, e);
				} else {
					handler.onError(e);
				}
				centerLock.readLock().unlock();
				return;
			}
			ResourceManager.setSubscribed(this, service, username, false);
		}
		if (callId >= 0) {
			handler.onResult(callId, null);
		}
		centerLock.readLock().unlock();
	}

	private String getRegId() {
		userInfoProviderLock.lock();
		String regId = GCMRegistrar.getRegistrationId(this);

		if (regId.length() == 0) {
			Log.i(TAG, "not registered");
			GCMRegistrar.register(this, userInfoProvider.getSenderIds());
			regId = null;
		}
		userInfoProviderLock.unlock();
		return regId;
	}

	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		if (intent == null) {
			this.stopSelf();
			return START_NOT_STICKY;
		}

		int cmd = intent.getIntExtra("c", -1);
		final int callId = intent.getIntExtra("id", -1);

		if (cmd <= 0 || cmd >= MessageCenterService.CMD_MAX_CMD_ID) {
			Log.i(TAG, "wrong command: " + cmd);
			this.stopSelf();
			return START_NOT_STICKY;
		}

		switch (cmd) {
		// BOILERPLATE ALERT!
		case CMD_USER_INFO_READY:
			this.setUserInfoProvider();
			this.getRegId();
			break;
		case CMD_REGID_READY:
			new AsyncTask<Void, Void, Void>() {
				@Override
				protected Void doInBackground(Void... params) {
					subscribe(-1);
					return null;
				}
			}.execute();
			break;
		case CMD_CONNECT:
			new AsyncTask<Void, Void, Void>() {
				@Override
				protected Void doInBackground(Void... params) {
					connect(callId);
					return null;
				}
			}.execute();
			break;
		case CMD_ERROR_ACCOUNT_MISSING:
			new AsyncTask<Void, Void, Void>() {
				@Override
				protected Void doInBackground(Void... params) {
					centerLock.readLock().lock();
					if (handler != null) {
						handler.onMissingAccount();
					}
					centerLock.readLock().unlock();
					return null;
				}
			}.execute();
			break;
		case CMD_STOP:
			Log.i(TAG, "STOP");
			this.disconnect();
			this.stopSelf();
			return START_NOT_STICKY;
		}

		return START_STICKY;
	}
}
