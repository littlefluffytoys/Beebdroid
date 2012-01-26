package com.littlefluffytoys.beebdroid;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;


public class SavedGameInfo {

	public static SavedGameInfo current;
	
	public String filename;
	public DiskInfo diskInfo;
	public Bitmap thumbnail;
	public long offsetToMachineData;
	public long timestamp;
	
	
	public static ArrayList<SavedGameInfo> savedGames;
	
	
	
	public static void init(Context c) {
		savedGames = new ArrayList<SavedGameInfo>();
		
		
		File[] files = c.getFilesDir().listFiles();
		if (files == null) {
			return;
		}
		for (int i=0 ; i<files.length ; i++) {
			File file = files[i];
			if (!file.getName().startsWith("saved_")) {
				continue;
			}
			SavedGameInfo info = new SavedGameInfo();
			try {
				info.filename = file.getName();
				FileInputStream fin = new FileInputStream(file);
				DataInputStream din = new DataInputStream(fin);
				
				// Version number
				int version = din.readInt();
				if (version != 1) {
					Log.e("Beebdroid", "Can't decode saved game file. Version incompatible.");
					din.close();
					continue;
				}
				
				// Disk info
				String diskName = din.readUTF();
				if (!TextUtils.isEmpty(diskName)) {
					info.diskInfo = InstalledDisks.getByKey(diskName);
					// TODO: if disk uninstalled, panic
				}
				info.offsetToMachineData =  fin.getChannel().position();
				
				int cbMachine = din.readInt();
				long skipped = din.skip(cbMachine);
				
				// Thumbnail
				//int cbThumb = din.readInt();
				info.thumbnail = BitmapFactory.decodeStream(din);
				
				info.timestamp = files[i].lastModified();
				din.close();
				savedGames.add(info);

			}
			catch (IOException ex) {
				
			}
			
			Collections.sort(savedGames, new Comparator<SavedGameInfo>() {
				@Override
				public int compare(SavedGameInfo object1, SavedGameInfo object2) {
					return (int)(object2.timestamp - object1.timestamp);
				}
				
			});
		}
	}
	
	public static void delete(Context c, int index) {
		SavedGameInfo info = savedGames.get(index);
		c.deleteFile(info.filename);
		savedGames.remove(index);
	}

	
	  public static Bitmap getRoundedCornerBitmap(Bitmap bitmap) {
		    Bitmap output = Bitmap.createBitmap(bitmap.getWidth(),
		        bitmap.getHeight(), Config.ARGB_8888);
		    Canvas canvas = new Canvas(output);
		 
		    final int color = 0xff424242;
		    final Paint paint = new Paint();
		    final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
		    final RectF rectF = new RectF(rect);
		    final float roundPx = Beebdroid.dp(16);
		 
		    paint.setAntiAlias(true);
		    canvas.drawARGB(0, 0, 0, 0);
		    paint.setColor(color);
		    canvas.drawRoundRect(rectF, roundPx, roundPx, paint);
		 
		    paint.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));
		    canvas.drawBitmap(bitmap, rect, rect, paint);
		 
		    return output;
		  }	
	//
	// save
	//
	public void save(Beebdroid beebdroid) {
		diskInfo = beebdroid.diskInfo;
		
		// Create thumbnail
		int thumbWidth = (int)Beebdroid.dp(160);
		int thumbHeight = (int)Beebdroid.dp(128);
		Bitmap bmp = Bitmap.createBitmap(800, 600, Bitmap.Config.RGB_565);
    	int dims = beebdroid.bbcGetThumbnail(bmp);
		thumbnail = Bitmap.createBitmap(thumbWidth, thumbHeight, Config.ARGB_8888);
		Canvas canvas = new Canvas(thumbnail);
		Paint paint = new Paint();
		paint.setAntiAlias(true);
		paint.setFilterBitmap(true);
		Rect rc = new Rect(0,0,thumbWidth,thumbHeight);
		canvas.drawBitmap(bmp, new Rect(0,0,672,272), rc, paint);
		// Round the corners
		bmp.recycle();
		thumbnail = getRoundedCornerBitmap(thumbnail);
		// Cleanup
		bmp = null;
		System.gc();
        
    	if (filename == null) {
    		filename = "saved_" + Long.toString(System.currentTimeMillis());
    	}
		try {
			//FileOutputStream fileOut = new FileOutputStream(new File(dir, filename)); //, Context.MODE_PRIVATE);
			FileOutputStream fileOut = beebdroid.openFileOutput(filename, Context.MODE_PRIVATE);
			DataOutputStream dout = new DataOutputStream(fileOut);
			
			// Start with version number
			dout.writeInt(1);
			
			// Disk name
			String diskName = (diskInfo==null) ? "" : diskInfo.key;
			dout.writeUTF(diskName);

	        // Machine data, preceded by its length
			offsetToMachineData = fileOut.getChannel().position();
			byte[] buffer = new byte[65*1024];
			int cb = beebdroid.bbcSerialize(buffer);
			dout.writeInt(cb);
			dout.write(buffer, 0, cb);

			// Thumbnail image
			ByteArrayOutputStream bo = new ByteArrayOutputStream(16*1024);
			thumbnail.compress(CompressFormat.PNG, 0, bo);
			cb = bo.size();
	        dout.write(bo.toByteArray(), 0, cb);

			dout.close();
			timestamp = System.currentTimeMillis();
			current = this;
			Toast.makeText(beebdroid, "Saved", Toast.LENGTH_SHORT).show();
		}
		catch (IOException ex) {
			String text = "Error saving state: " + ex;
			Toast.makeText(beebdroid, text, Toast.LENGTH_LONG).show();
		}
	}

}
