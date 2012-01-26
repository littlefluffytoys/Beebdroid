package common;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import junit.framework.Assert;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

public class Utils {
	
	public static void setVisibility(Object object, int id, int visibility) {
		View view = getView(object, id);
		if (view != null) {
			view.setVisibility(visibility);
		}
	}
	public static void setVisible(Object object, int id, boolean visible) {
		setVisibility(object, id, visible?View.VISIBLE:View.GONE);
	}
	
	public static String getText(Object object, int id) {
		TextView textView = (TextView)getView(object, id);
		if (textView != null) {
			return textView.getText().toString();
		}
		return "";
	}

	public static void setText(Object object, int id, String text) {
		TextView textView = (TextView)getView(object, id);
		if (textView != null) {
			textView.setText(text);
		}
	}

	public static void setImage(Object object, int id, int resourceId) {
		ImageView imageView = (ImageView)getView(object, id);
		if (imageView != null) {
			imageView.setImageResource(resourceId);
		}
	}

	public static void setTextBold(Object object, int id, boolean bold) {
		TextView textView = (TextView)getView(object, id);
		if (textView != null) {
			textView.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
		}
	}
	
	public static View getView(Object object, int id) {
		View view = null;
		if (object instanceof Dialog) {
			view = ((Dialog)object).findViewById(id); 
		}
		else if (object instanceof Activity) {
			view = ((Activity)object).findViewById(id); 
		}
		else if (object instanceof ViewGroup) {
			view = ((ViewGroup)object).findViewById(id); 
		}
		return view;
	}


	public static String getHttpResponseText(HttpResponse response) throws IOException {
		HttpEntity entity = response.getEntity();
		if (entity == null) {
			return "";
		}
		byte[] sBuffer = new byte[4096];
		InputStream inputStream = entity.getContent();
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(4096);
	    int readBytes = 0;
	    int totalBytesRead = 0;
	    while ((readBytes = inputStream.read(sBuffer)) != -1) {
	    	outputStream.write(sBuffer, 0, readBytes);
	    	totalBytesRead += readBytes;
	    }
	    entity.consumeContent();
	    return new String(outputStream.toByteArray());
	}

	public static String readTextStreamAvailable(InputStream inputStream) throws IOException {
		byte[] buffer = new byte[4096];
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(4096);
		
		// Do the first byte via a blocking read
		outputStream.write(inputStream.read());
		
		// Slurp the rest
		int available = inputStream.available();
	    while (available > 0) {
	    	int cbToRead = Math.min(buffer.length, available);
	    	int cbRead = inputStream.read(buffer, 0, cbToRead);
	    	if (cbRead <= 0) {
	    		throw new IOException("Unexpected end of stream");
	    	}
	    	outputStream.write(buffer, 0, cbRead);
	    	available -= cbRead;
	    }
	    return new String(outputStream.toByteArray());
	}
	
	//
	// readTextStream
	//
	public static String readTextStream(InputStream inputStream) throws IOException {
		byte[] buffer = new byte[4096];
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(4096);
	    int readBytes = 0;
	    int totalBytesRead = 0;
	    while ((readBytes = inputStream.read(buffer)) != -1) {
	    	outputStream.write(buffer, 0, readBytes);
	    	totalBytesRead += readBytes;
	    }
	    return new String(outputStream.toByteArray());
	}


	//
	// getCommaDelimitedIntArray
	//
	public static int[] getCommaDelimitedIntArray(String s) {
		if (s.indexOf(',')==-1) {
			return new int[] {};
		}
		String[] as = s.split(",");
		int[] ai = new int[as.length];
		for (int i=0 ; i<as.length ; i++) {
			ai[i] = Integer.parseInt(as[i].trim());
		}
		return ai;
	}
	
	//
	// getFriendlyDuration
	//
	public static String getFriendlyDuration(int durationMs) {
		int seconds = durationMs / 1000;
		int minutes = seconds / 60;
		int hours = minutes / 60;
		String retval = "";
		if (hours>0) {
			retval = hours + "h ";
			minutes -= hours * 60;
			seconds -= hours * 60*60;
		}
		if (minutes>0) {
			retval += minutes + "m ";
			seconds -= minutes * 60;
		}
		retval += seconds + "s";
		return retval;
	}
	
	public static class HashMapArrayList<K, V> extends HashMap<K,V> {
		private static final long serialVersionUID = 1L;
		private ArrayList<V> array = new ArrayList<V>();
		
		public ArrayList<V> getList() {
			return array;
		}
		
		public V[] toArray(V[] a) {
			return array.toArray(a);
		}
		@Override 
		public V put(K k, V v) {
			array.add(v);
			v = super.put(k, v);
			Assert.assertTrue(size()==array.size()); // Broken? Change this function to not allow duplicate entries in this.array
			return v;
		}
		
		public V getByIndex(int index) {
			if (index>=array.size()) {
				return null;
			}
			return array.get(index);
		}

		@Override
		public V remove(Object k) {
			V v = get(k);
			if (v != null) {
				array.remove(v);
			}
			super.remove(k);
			return v;
		}

		@Override
		public void clear() {
			super.clear();
			array.clear();
		}
	}

	
    public static void closeQuietly(InputStream input) {
        try {
            if (input != null) {
                input.close();
            }
        } catch (IOException ioe) {
            // ignore
        }
    }
    public static void closeQuietly(OutputStream output) {
        try {
            if (output != null) {
            	output.close();
            }
        } catch (IOException ioe) {
            // ignore
        }
    }
		

	public static String safeGetJsonString(JSONObject obj, String fieldName) throws JSONException {
		if (obj.has(fieldName)) {
			return obj.getString(fieldName);
		}
		return "";
	}
	
	public static String age(long timestamp) {
		long age = System.currentTimeMillis() - timestamp;
		
		if (age<5000){
			return "just now";
		}

		long seconds = age / 1000;
		if (seconds<60){
			return Long.toString(seconds) + " seconds ago";
		}

		long minutes = seconds / 60;
		if (minutes<60){
			return Long.toString(minutes) + ((minutes==1) ? " minute ago" : " minutes ago");
		}

		long hours = minutes / 60;
		if (hours<24){
			return Long.toString(hours) + ((hours==1) ? " hour ago" : " hours ago");
		}

		long days = hours / 24;
		if (days<7) {
			return (days==1) ? "Yesterday" : (Long.toString(days) + " days ago");
		}
		

		long weeks = days / 7;
		if (weeks<4){
			return Long.toString(weeks) + ((weeks==1) ? " week ago" : " weeks ago");
		}
		
		SimpleDateFormat formatter = new SimpleDateFormat("dd-MMM-yy");
		return formatter.format(timestamp);
	}

	
	public static void unzip(File zipfile, File targetfile, boolean deleteZipAfterwards) throws IOException {
		ZipInputStream input = new ZipInputStream(new FileInputStream(zipfile));
		ZipEntry entry = input.getNextEntry(); 
		OutputStream output = new BufferedOutputStream(new FileOutputStream(targetfile));
        int count;
        byte buffer[] = new byte[4096];
        while ((count = input.read(buffer, 0, 4096)) != -1) {
        	output.write(buffer, 0, count);
        }
        output.flush();
        output.close();
        input.close();
        if (deleteZipAfterwards) {
        	zipfile.delete();
        }
		
	}
}
