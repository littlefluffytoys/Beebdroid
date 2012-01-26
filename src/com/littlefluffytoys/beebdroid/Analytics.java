package com.littlefluffytoys.beebdroid;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;
import android.content.Context;

public class Analytics {
	
    private static GoogleAnalyticsTracker tracker;
	
	private static final String TAG="Analytics";
	
	public static GoogleAnalyticsTracker getInstance(Context context) {
		if (tracker == null) {
			// start new analytics session if required
	        tracker = GoogleAnalyticsTracker.getInstance();
	        tracker.startNewSession("UA-26505968-1", context);
		}
		return tracker;
	}
	
	public static void trackEvent(Context context, String category, String action, String label, int value) {
		getInstance(context).trackEvent(category, action, label, value);
	}
	
	public static void trackPageView(Context context, String page) {
		getInstance(context).trackPageView(page);
	}

	public static void dispatch() {
		if (tracker != null) {
			tracker.dispatch();
		}
	}

	public static void dispatchAndStopSession() {
		if (tracker != null) {
			tracker.dispatch();
			tracker.stopSession();
			tracker = null;
		}
	}
}