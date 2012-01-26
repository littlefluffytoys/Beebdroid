package common;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.UnknownHostException;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;


public class Network {

	
	//
	// DownloadTaskBase
	//
	abstract public static class DownloadTaskBase extends AsyncTask<String, Long, String> {
		protected static final String TAG="DownloadTask";
		protected String errorMessage;
		protected int httpCode;
		
		// Functions to be implemented by derived classes
		abstract protected HttpUriRequest getHttpRequest();
		abstract protected void onError(String errorText);
		
		protected boolean processStatusLine(HttpResponse response) {
			httpCode = response.getStatusLine().getStatusCode();
			Log.d(TAG, "got HTTP " + httpCode);
			if (httpCode != 200 && httpCode !=206) {
				if (httpCode == 403) {
					errorMessage = "Access Denied"; // urgh! can we be less forbidding?
				}
				else {
					errorMessage = "HTTP Error " + httpCode;
				}
				return false;
			}
			return true;
		}
		
	}
	
	//
	// DownloadTextTask - a background task for downloading relatively small
	//                    amounts of text.
	//
	abstract public static class DownloadTextTask extends DownloadTaskBase {

		protected String responseETag = "";
		
		// Functions to be implemented by derived classes
		abstract protected void onDownloadComplete(String str);

		@Override
		protected String doInBackground(String... params) {				
			HttpClient client = new DefaultHttpClient();
			HttpUriRequest request = getHttpRequest();
			Log.d(TAG, request.getRequestLine().toString());
			try {
				HttpResponse response = client.execute(request);
				if (!processStatusLine(response)) {
					return null;
				}
				responseETag = "";
				if (response.containsHeader("ETag")) {
					responseETag = response.getFirstHeader("ETag").getValue();
				}
				String rv = Utils.getHttpResponseText(response);
				Log.d("DownloadTextTask", "Response text is:\n" + rv);
				return rv;
			}
			catch (UnknownHostException e) {
				errorMessage = "No network";
			}
			catch (IOException e) {
				errorMessage = "Error during download: " + e.getMessage();
			}				
			return null;
		}

		@Override protected void onPostExecute(String str) {
			if (str == null) {
				if (TextUtils.isEmpty(errorMessage)) {
					errorMessage = "Unspecified error"; 
				}
				Log.e(TAG, errorMessage);
				onError(errorMessage);
				return;
			}
			onDownloadComplete(str);
		}
	}
	


	//
	// DownloadJsonTask - helper for downloading JSON
	//
	abstract public static class DownloadJsonTask extends DownloadTextTask {
		
		// Functions to be implemented by derived classes
		abstract protected void onDownloadJsonComplete(Object object) throws JSONException;
		
		// Parse the text response into JSON
		@Override
		protected final void onDownloadComplete(String str) {
			JSONTokener tokener = new JSONTokener(str);
			try {
				Object object = tokener.nextValue();
				if (object instanceof JSONObject) {
					onDownloadJsonComplete(object);
				}
				else if (object instanceof JSONArray) {
					onDownloadJsonComplete(object);
				}
				else {
					onError("JSON response is an unexpected data type");
				}
			} catch (JSONException e) {
				onError("JSONException while parsing response: " + e.getLocalizedMessage());
			}
		}
	}



	//
	// DownloadBinaryTask - a background task for downloading any size any kind of data
	//
	abstract public static class DownloadBinaryTask extends DownloadTaskBase {
		protected String localPath;
		protected long cbTotalSize = -1;
		protected long cbDownloaded = 0;
		protected boolean append;
		public String contentType;
		
		// Functions to be implemented by derived classes
		abstract protected void onDownloadComplete();

		// Constructor.
		public DownloadBinaryTask(String localPath, boolean append) {
			this.localPath = localPath;
			this.append = append;
		}
		
		@Override
		protected String doInBackground(String... params) {
			HttpClient client = new DefaultHttpClient();
			File outputFile=new File(localPath);
			HttpUriRequest request = getHttpRequest();
			long cbDownloaded = 0;
			if (outputFile.exists()) {
				if (!append) {
					outputFile.delete();
				}
				else {
					cbDownloaded = outputFile.length();
					Log.d(TAG, "Adding header: Range: " + cbDownloaded + "-");
					request.addHeader("Range", "bytes=" + Long.toString(cbDownloaded) + "-");
				}
			}
			try {
				HttpResponse response = client.execute(request);
				if (!processStatusLine(response)) {
					return null;
				}
				HttpEntity entity = response.getEntity();
				contentType = "";
				Header hdrContentType = response.getFirstHeader("Content-Type");
				if (hdrContentType != null) {
					contentType = hdrContentType.getValue();
				}
				if (entity != null) {
					byte[] sBuffer = new byte[16384];
					InputStream inputStream = entity.getContent();
					long cbThisDownloadSize  = entity.getContentLength();
					cbTotalSize = cbDownloaded + cbThisDownloadSize;
					Log.d(TAG, "cbTotalSize is " + cbTotalSize);
			    	RandomAccessFile output = new RandomAccessFile(localPath, "rw");
			    	if (cbDownloaded > 0) {
			    		output.seek(cbDownloaded);
			    	}
				    int readBytes = 0;
				    while ((readBytes = inputStream.read(sBuffer)) != -1) {
				    	output.write(sBuffer, 0, readBytes);
				    	cbDownloaded += readBytes;
				    	publishProgress(cbDownloaded, cbTotalSize);
				    	if (isCancelled()) {
				    		break;
				    	}
				    }
				    output.close();
				    entity.consumeContent();
				    return "OK"; // success
				}
			}
			catch (IOException e2) {
				errorMessage = "Exception in download: " + e2.getLocalizedMessage();
			}				
			return null;
		}
		
		@Override protected void onPostExecute(String str) {
			if (isCancelled()) {
				return;
			}
			if (str == null) {
				if (TextUtils.isEmpty(errorMessage)) {
					errorMessage = "Unspecified error"; 
				}
				Log.e(TAG, errorMessage);
				onError(errorMessage);
				return;
			}
			onDownloadComplete();
		}
	}

}
