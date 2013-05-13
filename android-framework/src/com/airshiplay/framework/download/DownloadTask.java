package com.airshiplay.framework.download;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import android.os.Message;

import com.airshiplay.framework.bean.BaseBean;
import com.airshiplay.framework.log.Logger;
import com.airshiplay.framework.log.LoggerFactory;
import com.airshiplay.framework.util.PreferenceUtil;
import com.airshiplay.framework.util.TelephoneUtil;

/**
 * @author lig
 * @version 1.0
 * @since 1.0 2013-2-6
 */
public class DownloadTask {
	private static Logger log = LoggerFactory.getLogger(DownloadTask.class);

	public static final int STATE_WAIT = 0;
	public static final int STATE_CONNETING = 1;
	public static final int STATE_DOWNLOADING = 2;
	public static final int STATE_PAUSED = 3;
	public static final int STATE_FINISHED = 4;
	public static final int STATE_NET_ERROR = 5;
	public static final int STATE_FILE_ERROR = 6;
	BaseBean bean;
	int percent;
	long loadSize;
	long size;
	int state;
	long lastSendTime;
	String versionCode;
	String versionName;

	private Map<Object, DownloadTaskListener> listenerMap;

	/** 文件全路径 */
	String path;

	private boolean downloadFlag;

	private Thread mThread;

	private boolean lastState_IsWifi;

	public DownloadTask() {
		this.listenerMap = new ConcurrentHashMap<Object, DownloadTaskListener>();
	}

	public void addDownloadListener(DownloadTaskListener downloadTaskListener,
			Object keyListener) {
		this.listenerMap.remove(keyListener);
		this.listenerMap.put(keyListener, downloadTaskListener);
	}

	int caculatePercent() {
		if ((this.size != 0L) && (this.loadSize != 0L)) {
			return Long.valueOf(this.loadSize * 100L / this.size).intValue();
		}
		return 0;
	}

	void deleteFile() {
		File file = new File(this.path);
		if (file.exists())
			file.delete();
	}

	boolean download() {
		return false;
	}

	long getCurrentSize() {
		File file = new File(this.path);
		if (file.exists())
			return file.length();
		return 0;
	}

	void removeFile() {
		String str = this.path;
		File localFile = new File(str);
		if (localFile.exists())
			localFile.delete();
	}

	public void resume() {
		setState(STATE_WAIT);
	}

	void setState(int status) {
		if ((this.state == STATE_PAUSED)
				&& ((status == STATE_FILE_ERROR) || (status == STATE_NET_ERROR)))
			return;
		this.state = status;
		if (status == STATE_FINISHED) {
			rename();
			changeDirectoryPrivilege(this.path);
			DownloadMgr.onFinish(this);
		}
		fireStateChangeEvent();
		if (!isDownloading())
			DownloadMgr.scheduleTask(this);
	}

	private boolean isDownloading() {
		return this.downloadFlag;
	}

	private void changeDirectoryPrivilege(String path) {
		try {
			Runtime.getRuntime().exec("chmod 755 " + path);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	void rename() {
		String file = this.path.substring(0, this.path.indexOf(".tmp"));
		File soure = new File(this.path);
		File destFile = new File(file);
		soure.renameTo(destFile);
		this.path = file;
	}

	void fireProgressChangeEvent() {
		long currentTime = System.currentTimeMillis();
		if ((currentTime - lastSendTime > 500L) || (this.percent == 100)) {
			lastSendTime = currentTime;
			sendMsg(DownloadTaskListener.PROGRESS_CHANGE);
		}
	}

	void fireStateChangeEvent() {
		sendMsg(DownloadTaskListener.STATE_CHANGE);
	}

	void sendMsg(int what) {
		Iterator<Map.Entry<Object, DownloadTaskListener>> lIterator = this.listenerMap
				.entrySet().iterator();
		while (lIterator.hasNext()) {
			Map.Entry<Object, DownloadTaskListener> entry = lIterator.next();
			DownloadTaskListener downloadTaskListener = entry.getValue();
			if (downloadTaskListener != null) {
				Message message = Message.obtain();
				message.what = what;
				downloadTaskListener.sendMessage(message);
			} else {
				log.debug("sendMsg is listener is null");
			}
		}
	}

	void setNetworkConnectState() {
		boolean isWifiEnable = TelephoneUtil.isWifiEnable(DownloadMgr.mCtx);
		boolean isWithoutWifiNotify = PreferenceUtil.getBoolean(DownloadMgr.mCtx,
				"NOTIFY_LARGE_WITHOUT_WIFI",
				PreferenceUtil.DEFAULT_NOTIFY_LARGE_FILE_WITHOUT_WIFI);
		if ((this.lastState_IsWifi) && (!isWifiEnable) && (isWithoutWifiNotify)) {
			Message message = Message.obtain();
			message.obj = this;
			DownloadMgr.no_wifi.sendMessage(message);
		}
		this.lastState_IsWifi = isWifiEnable;
	}

	void start() {
		this.downloadFlag = true;
		setState(STATE_CONNETING);
		this.mThread = new Thread() {
			@Override
			public void run() {
			}
		};
		mThread.start();
	}

	public void stop() {
		this.downloadFlag = false;
		setState(STATE_PAUSED);
	}
}
