package common;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import android.content.Context;
import android.os.AsyncTask;

/*
 * Packageable - a storage mechanism that's wayyyy faster than SQLite and Serializable
 */

public abstract class Packageable {
	public abstract void readFromPackage(PackageInputStream in)  throws IOException ;
	public abstract void writeToPackage(PackageOutputStream out)  throws IOException ; 

	private File file;
	private int currentVersion;
	
	public void load(Context context, File file, int currentVersion) {
		this.file = file;
		this.currentVersion = currentVersion;
		PackageInputStream in = null;
		try {
			in = new PackageInputStream(new FileInputStream(file));
			int version = in.readInt();
			if (version == currentVersion) {
				readFromPackage(in);
			}
		}
		catch (FileNotFoundException ex) { // no problem				
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		if (in != null) {
			in.close();
		}
	}
	
	public void save() {
		new AsyncTask<Integer, Void, Integer>() {

			@Override
			protected Integer doInBackground(Integer... params) {
				PackageOutputStream out = null;
				try {
					out = new PackageOutputStream(new FileOutputStream(file));
					out.writeInt(currentVersion);
					writeToPackage(out);
				}
				catch (FileNotFoundException ex) { // cant happen					
				}
				catch (IOException e) {
					e.printStackTrace();
				}
				if (out != null) {
					out.close();
				}
				return 1;
			}
			
		}.execute();		
	}

	
	public static final class PackageInputStream {

	    private DataInputStream input;

	    public PackageInputStream(InputStream in) {
	    	input = new DataInputStream(new BufferedInputStream(in));
	    }

	    public void close() {
	    	if (input != null) {
	    		try {
	    			input.close();
	    		}
	    		catch (IOException ex) {
	    		}
	    		input = null;
	    	}    	
	    }

	    // Primitives
	    public final boolean readBoolean() throws IOException {
	    	return input.readBoolean();
	    }
	    public final int readInt() throws IOException {
	    	return input.readInt();
	    }
	    public final long readLong() throws IOException {
	    	return input.readLong();
	    }
	    public final long[] readLongArray() throws IOException {
	    	int c = input.readInt();
	    	if (c == -1) {
	    		return null;
	    	}
	    	long[] a = new long[c];
	    	for (int i=0 ; i<c ; i++) {
	    		a[i] = input.readLong();
	    	}
	    	return a;
	    }
	    public final float readFloat()  throws IOException {
	    	return input.readFloat();
	    }
	    public final double readDouble()  throws IOException {
	    	return input.readDouble();
	    }
	    public final String readString()  throws IOException {
	    	return input.readUTF();
	    }
	    public final <T extends Packageable> T readPackageable(Class<T> clazz) throws IOException {
	    	int i = input.readByte();
	        if (i > 0) {
				try {
					T item = (T) clazz.newInstance();
		            item.readFromPackage(this);
		            return item;
				} catch (InstantiationException e) {
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				}
	        }
	        return null;
	    }    
	    public final <T extends Packageable> List<T> readPackageableList(Class<T> clazz) throws IOException {
	    	int N = readInt();
	        if (N == -1) {
	            return null;
	        }
	        ArrayList<T> list = new ArrayList<T>();
	        while (N>0) {
	        	T item = readPackageable(clazz);
	        	list.add(item);
	            N--;
	        }
	        return list;
	    }
	 
	}
	
	
	
	
	public static final class PackageOutputStream {

	    private DataOutputStream output;

	    public PackageOutputStream(OutputStream out) {
	    	output = new DataOutputStream(new BufferedOutputStream(out));
	    }

	    public void close()  {
	    	if (output != null) {
	    		try {
	    			output.close();
	    		}
	    		catch (IOException ex) {    			
	    		}
	    		output = null;
	    	}
	    }

	    // Primitives
	    public final void writeBoolean(boolean val) throws IOException {
	    	output.writeBoolean(val);
	    }
	    public final void writeInt(int val) throws IOException {
	    	output.writeInt(val);
	    }
	    public final void writeLong(long val) throws IOException {
	    	output.writeLong(val);
	    }
	    public final void writeLongArray(long[] val) throws IOException {
	    	if (val == null) {
	    		writeInt(-1);
	    		return;
	    	}
	    	writeInt(val.length);
	    	for (int i=0 ; i<val.length ; i++) {
	    		output.writeLong(val[i]);
	    	}
	    }
	    
	    public final void writeFloat(float val) throws IOException {
	    	output.writeFloat(val);
	    }
	    public final void writeDouble(double val) throws IOException {
	    	output.writeDouble(val);
	    }
	    public final void writeString(String val) throws IOException {
	    	if (val == null) {
	    		output.writeUTF("");
	    		return;
	    	}
	    	output.writeUTF(val);
	    }

	    public void writePackageable(Packageable val) throws IOException {
	        if (val == null) {
	            output.writeByte(0);
	            return;
	        }
	        output.writeByte(1);
	        val.writeToPackage(this);
	    }
	    public final <T extends Packageable> void writePackageableList(List<T> val) throws IOException {
	        if (val == null) {
	            writeInt(-1);
	            return;
	        }
	        int N = val.size();
	        int i=0;
	        writeInt(N);
	        while (i < N) {
	        	Packageable item = val.get(i);
	        	writePackageable(item);
	            i++;
	        }
	    }

	}
}