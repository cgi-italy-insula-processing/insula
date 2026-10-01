package com.cgi.eoss.platform.core.queues.service;

public interface BrowserClient {

	public void handleMessage(Message m);
	
	public boolean stopBrowsing();
}
