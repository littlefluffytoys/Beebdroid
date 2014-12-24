/*
 * Copyright © 2011, Little Fluffy Toys Ltd
 * All rights reserved.
 */
package com.littlefluffytoys.beebdroid;

import java.util.Locale;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class UserPrefs extends PreferenceActivity {

	private static final String TAG="UserPrefs";
	
	public static final String PREFKEY_ABOUT_CHECKBOX = "AboutCheckbox";
	public static final String PREFKEY_GRANDFATHERED_IN = "GrandfatheredIn";
	
	public static final int ABOUTSCREEN_FIRST_EVER = 0;
	public static final int ABOUTSCREEN_SHOW = 1;
	public static final int ABOUTSCREEN_HIDE = 2;
	
	public static int aboutScreenCheckbox = ABOUTSCREEN_FIRST_EVER;

	public static int getAboutScreenCheckbox(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context).getInt(PREFKEY_ABOUT_CHECKBOX, ABOUTSCREEN_FIRST_EVER);
	}
	public static void setAboutScreenCheckbox(Context context, int value) {
		aboutScreenCheckbox = value;
		putInt(context, UserPrefs.PREFKEY_ABOUT_CHECKBOX, aboutScreenCheckbox);
	}

	public static void hideSplashScreen(Context context) {
		setAboutScreenCheckbox(context, ABOUTSCREEN_HIDE);
	}
	
	public static boolean shouldShowSplashScreen(Context context) {
		return (getAboutScreenCheckbox(context) != ABOUTSCREEN_HIDE);
	}
	
	public static void setGrandfatheredIn(Context context, boolean value) {
		putBoolean(context, UserPrefs.PREFKEY_GRANDFATHERED_IN, value);
	}
    static int safeGetPrefsInt(SharedPreferences prefs, String key, int defaultVal) {
    	// This function is stupidly named. The preference must be stored as a string, not an integer.
    	// Be absolutely 100% certain sure that your preference is stored as a string not an integer,
    	// else you have an epic runtime fail
    	return Integer.parseInt(prefs.getString(key, String.valueOf(defaultVal)));
    }
	static float safeGetPrefsFloat(SharedPreferences prefs, String key, float defaultVal) {
		int i = safeGetPrefsInt(prefs, key, (int)(100 * defaultVal));
		return ((float)i)/100f;
	}

	static void refresh(Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);		
		aboutScreenCheckbox = prefs.getInt(PREFKEY_ABOUT_CHECKBOX, ABOUTSCREEN_FIRST_EVER);
		
	}

	static int getInt(Context context, String key, int defaultVal) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);		
	    int intValue = prefs.getInt(key, defaultVal);
	    prefs = null;
	    return intValue; 
	}
	static void putInt(Context context, String key, int val) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);		
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt(key, val);
		editor.commit();
		editor = null;
		prefs = null;
	}
	static void putLong(Context context, String key, long val) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);		
		SharedPreferences.Editor editor = prefs.edit();
		editor.putLong(key, val);
		editor.commit();
		editor = null;
		prefs = null;
	}
	static void putBoolean(Context context, String key, boolean val) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);		
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(key, val);
		editor.commit();
		editor = null;
		prefs = null;
	}
	static void putString(Context context, String key, String val) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);		
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(key, val);
		editor.commit();
		editor = null;
		prefs = null;
	}
}
