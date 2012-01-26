package common;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.net.http.AndroidHttpClient;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;


public class ImageCache {
    private static final String TAG = "ImageCache";

    private static final int MAX_FILECACHE_AGE = 2 * 24 * 60 * 60 * 1000; // 2 days
    private static final int MAX_FILECACHE_SIZE = 2 * 1024*1024;
    
    private static ImageCache instance;
  
    private File cachedir;
    private List<File> filecachelist = new ArrayList<File>();   
    private HashMap<String, File> filecachemap = new HashMap<String, File>();
    private long filecachesize;
    
    //
    // The public API
    //
    public static void getImage(View parent, int id, String url) {
    	ImageView view = (ImageView)parent.findViewById(id);
    	getImage(view, url);
    }
    public static void getImage(ImageView imageView, String url) {
    	getImage(imageView, url, false);
    }
    public static void getImage(ImageView imageView, String url, boolean useExifRotation) {
    	if (instance == null) {
    		instance = new ImageCache(imageView.getContext());
    	}
    	instance.getImageInternal(imageView, url, useExifRotation);
    }

 
    private void getImageInternal(ImageView imageView, String url, boolean useExifRotation) {
    	    	
    	// Reset the image
    	imageView.setImageDrawable(null);
    	if (url == null) {
    		return;
    	}
    	
		// Cancel any queued job for the given imageview (if its a different image)
    	synchronized (alljobs) {
			for (Job job : alljobs) {
				if (imageView.equals(job.imageView)) {
					if (0 == job.url.compareTo(url)) {
						return; // already queued, nothing to do
					}
					job.cancelled = true;
					alljobs.remove(job); // this job is no longer needed.
					qjobs.remove(job); // this job is no longer needed.
					break;
				}
			}
	  	}
    	
    	// In-memory cache lookup. 
        Bitmap bitmap = null;
        synchronized (memcache_map) {
	        SoftReference<Bitmap> bitmapRef = memcache_map.get(url);
	        if (bitmapRef != null) {
	            bitmap = bitmapRef.get();
	            if (bitmap == null) {
	                memcache_map.remove(url);
	                memcache_keys.remove(url);
	            }
	        }
        }
        
        // Early exit if we had a cache hit
        if (bitmap != null) {
        	imageView.setImageBitmap(bitmap);
        	return;
        }
        
        // Otherwise, create a new job and add it to the queue.
        Job job = new Job(imageView, url, useExifRotation);
        synchronized (alljobs) {
			alljobs.add(job);
			qjobs.add(job);
        }
        
        // Release the semaphore
        semaphore.release();
        
    }

    
    private ImageCache(Context context) {
    	cachedir = new File(context.getCacheDir(), "img");
    	cachedir.mkdirs();
    	File[] files = cachedir.listFiles();
    	if (files != null) {
    		for (File file : files) {
    			if (file.getName().startsWith("tmp") || System.currentTimeMillis() - file.lastModified() > MAX_FILECACHE_AGE) {
    				file.delete();
    			}
    			else {
	    			filecachelist.add(file);
	    			filecachemap.put(file.getName(), file);
	    			filecachesize += file.length();
    			}
    		}
    		Collections.sort(filecachelist, new Comparator<File>() {
    			@Override
    			public int compare(File file1, File file2) {
    				return (int) (file1.lastModified() - file2.lastModified());
    			}    	    	
    	    });
    	}
    	
    	// Create threads
    	for (int i=0 ; i<MAX_THREADS ; i++) {
    		BitmapDownloaderThread thread = new BitmapDownloaderThread();
    		threads.add(thread);
    		thread.setPriority(Thread.NORM_PRIORITY - 1);
    		thread.start();
    	}
    }
	private String urlToCacheFilename(String url) {
		if (url.startsWith("http://")) {
			url = url.substring(7);
		}
		else if (url.startsWith("https://")) {
			url = url.substring(8);
		}
		if (url.startsWith("www")) {
			url = url.substring(3);
		}
		return URLEncoder.encode(url);
	}

    private Bitmap downloadBitmap(String url) {

        // AndroidHttpClient is not allowed to be used from the main thread
        final HttpClient client = AndroidHttpClient.newInstance("Android");

        try {
        	int statusCode = HttpStatus.SC_OK;
        	HttpResponse response; 
        	while (true) {
                HttpGet getRequest = new HttpGet(url);
                response = client.execute(getRequest);
                statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 302) {
	            	url = response.getFirstHeader("Location").getValue();
	            	continue;
                }
               	break;
            }
            if (statusCode != HttpStatus.SC_OK) {
                Log.w(TAG, "Error " + statusCode + " while retrieving bitmap from " + url);
                return null;
            }

            final HttpEntity entity = response.getEntity();
            if (entity != null) {
                InputStream inputStream = null;
                try {
                	
                	// Read the first 4K
                	InputStream contentStream = entity.getContent();
                	byte[] hdr = new byte[4096];
                	int hdrlen = contentStream.read(hdr);
                	
                	// Decode the header (to get the width & height) using the byte array
                	ByteArrayInputStream bin = new ByteArrayInputStream(hdr);
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.RGB_565;
                    options.inSampleSize = 1;
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeStream(bin, null, options);
                	//Log.d("ImageCache", "hdrlen=" + hdrlen + " w=" + options.outWidth + " h=" + options.outHeight);
                    bin.close();
                    
                    // Calculate subsampling rate
                    int cbBitmap = options.outHeight * options.outWidth * 2;
                    while (cbBitmap >= 256*1024) {
                    	cbBitmap /= 2;
                    	options.inSampleSize++;
                    }
                    options.inJustDecodeBounds = false;

                    // Decode the bitmap
                    inputStream = new FlushedInputStream(url, contentStream, hdr, hdrlen);
                    Bitmap bmp = BitmapFactory.decodeStream(inputStream, null, options);
                    if (bmp == null) {
                    	Log.e(TAG, "Eek! Failed to decode " + url);
                    }
                    return bmp;
                } finally {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    entity.consumeContent();
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "I/O error while retrieving bitmap from " + url, e);
        } catch (IllegalStateException e) {
            Log.w(TAG, "Incorrect URL: " + url);
        } catch (Exception e) {
            Log.w(TAG, "Error while retrieving bitmap from " + url, e);
        } finally {
            if ((client instanceof AndroidHttpClient)) {
                ((AndroidHttpClient) client).close();
            }
        }
        return null;
    }

    /*
     * An InputStream that skips the exact number of bytes provided, unless it reaches EOF.
     */
    private class FlushedInputStream extends FilterInputStream {
        public FlushedInputStream(String url, InputStream inputStream, byte[] hdr, int hdrlen) {
            super(inputStream);
            this.url = url;
            this.hdr = hdr;
            this.hdr_prefetch = hdrlen;
        }
    
        
        private String url;
        private OutputStream tmp_out;
		private File tmpfile;
        private boolean errord;
        private byte[] hdr;
        private int hdr_prefetch;
        private int pos;
        
        private void writetmpfile(byte[] b, int offset, int len) {
        	if (errord) {
        		return;
        	}
        	if (tmp_out==null) {
        		int pass = 0;
        		tmpfile = null;
        		do {
        			String tmpfilename = "tmp" + Long.toString(System.currentTimeMillis()) + "_" + pass;
            		tmpfile = new File(cachedir, tmpfilename);
        			pass++;
        		} while (tmpfile.exists());
        		try {
					tmp_out = new FileOutputStream(tmpfile);
				} catch (FileNotFoundException e) {
					e.printStackTrace();
					errord = true;
				}
        	}
        	try {
				tmp_out.write(b, offset, len);
			} catch (Exception e) {
				e.printStackTrace();
				errord = true;
			}
        }
        @Override
        public int read(byte[] b) throws IOException {
        	return read(b, 0, b.length);
        	/*int r = in.read(b);
        	if (r>0) {
        		writetmpfile(b, 0, r);
        	}
        	return r;*/
        }
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
        	//Log.d("ImageCache", ">read: len=" + len + " off=" + off + " pos=" + pos);
        	int hdrlen = 0;
        	if (pos < hdr_prefetch) {
        		hdrlen = Math.min(len, hdr_prefetch-pos);
        		for (int i=0 ; i<hdrlen ; i++) {
        			b[i+off] = hdr[i+pos];
        		}
        		writetmpfile(hdr, pos, hdrlen);
        		pos += hdrlen;
        		off += hdrlen;
        		len -= hdrlen;
        	}
        	if (len <= 0) {
            	//Log.d("ImageCache", "<read 1: returning " + hdrlen + " pos=" + pos);
        		return hdrlen;
        	}
        	int r = in.read(b, off, len);
        	if (r <= 0) {
            	//Log.d("ImageCache", "<read 2: returning " + hdrlen + " pos=" + pos);
        		return (hdrlen>0) ? hdrlen : r;
        	}
       		writetmpfile(b, off, r);
        	//Log.d("ImageCache", "<read 3: returning " + (r+hdrlen) + " pos=" + pos);
        	return r+hdrlen;        	
        }
        @Override
        public int read() throws IOException {
        	throw new IOException("Can't be bothered to support read(). You should never see this.");
        }
        
        @Override
        public void close() throws IOException {
        	if (tmp_out != null) {
        		Utils.closeQuietly(tmp_out);
        		tmp_out = null;

        		// Rename the file to the permanent name
        		if (!errord) {
        			File newtmpfile = new File(cachedir, urlToCacheFilename(url));
	    			errord = !tmpfile.renameTo(newtmpfile);
	    			if (!errord) {
	    				tmpfile = newtmpfile;
	    			}
        		}
        		
        		// Add to cache
        		if (errord) {
    				tmpfile.delete();        			
        		}
        		else {
        			synchronized(ImageCache.this) {
        				Log.d(TAG, "adding " + url);
        				filecachelist.add(tmpfile);
        				filecachemap.put(url, tmpfile);
        				filecachesize += tmpfile.length();
        				// Trim cache
        				while (filecachesize > MAX_FILECACHE_SIZE) {
        					File trimfile = filecachelist.remove(0); // oldest
        					Log.d(TAG, "trimming " + trimfile.getName());
        					filecachemap.remove(trimfile.getName());
        					filecachesize -= trimfile.length();
        					trimfile.delete();
        				}
        			}
        		}
        	}
        	super.close();
        }
        @Override
        public long skip(long n) throws IOException {
        	Log.d(TAG, "skip " + n);
        	long skipped=0;
        	byte[] b = new byte[4096];
        	while (n>0) {
        		int thischunk = Math.min((int)n, 4096);
            	skipped += read(b, 0, thischunk);
            	n -= thischunk;
        	}
        	return skipped;

        }
    }

    //
    // Job
    //
    private class Job implements Runnable {
        String url;
        boolean useExifOrientation;
        ImageView imageView;
        //WeakReference<ImageView> imageViewRef;
        //List<WeakReference<ImageView>> imageViewRefList;
        Bitmap bitmap;
        boolean cancelled;
        public Job(ImageView imageView, String url, boolean useExifOrientation) {
        	this.imageView = imageView;
            //this.imageViewRef = new WeakReference<ImageView>(imageView);
            this.url = url;
            this.useExifOrientation = useExifOrientation;
        }
        /*public void addImageView(ImageView imageView) {
        	if (imageViewRefList == null) {
        		imageViewRefList = new ArrayList<WeakReference<ImageView>>();
        	}
        	imageViewRefList.add(new WeakReference<ImageView>(imageView));
        }*/
        
        // Runnable - runs on successful image load
       	@Override
    	public void run() {
            synchronized (alljobs) {
            	alljobs.remove(this);
            }
  			if (cancelled) {
      			return;
  			}
   	       	imageView.setImageBitmap(bitmap);
            /*if (imageViewRefList != null) {
            	for (WeakReference<ImageView> ref : imageViewRefList) {
            		imageView = ref.get();
            		if (imageView != null) {
                		imageView.setImageBitmap(bitmap);
            		}
            	}
            }*/   		
    	}
    }
    
    public static final int MAX_THREADS=8;
    Semaphore semaphore = new Semaphore(0, true);
    ArrayList<Job> alljobs = new ArrayList<Job>();
    ArrayList<Job> qjobs = new ArrayList<Job>();
    Handler handler = new Handler();
    boolean runThreads = true;
    ArrayList<Thread> threads = new ArrayList<Thread>();

    
    /**
     * Thread class
     */
    class BitmapDownloaderThread extends Thread implements Runnable {

    

        @Override
        public void run() {
        	
        	while (runThreads) {
        		
	        	// Wait for the semaphore
	        	try {
					semaphore.acquire();
				} catch (InterruptedException e) {
					break;
				}
	        	
	        	// Remove the job from the queue
	        	Job job = null;
	        	synchronized (alljobs) {
	        		if (qjobs.size() == 0) {
	        			Log.w(TAG, "Worker thread signalled but no work to do!");
	        			continue;
	        		}
	        		job = qjobs.remove(0);
	            }
	        	if (job.cancelled) {
	        		continue;
	        	}
	        	
	        	// Do the work (either loading the image from storage or from the internet)
	        	if (job.url.startsWith("file://")) {
	        		job.bitmap = loadBitmapFile(job);
	        	}
	        	else {
	        		job.bitmap = loadBitmapFromCache(job);
		        	if (job.bitmap == null) {
		        		job.bitmap = downloadBitmap(job.url);
		        	}
	        	}
	        	
	        	// If we got a bitmap, add to the cache and signal the UI thread
	        	if (job.bitmap != null) {
	                synchronized (memcache_map) {
	                	memcache_map.put(job.url, new SoftReference<Bitmap>(job.bitmap));
	                	memcache_keys.add(job.url);
	                	if (memcache_keys.size() >= 100) {
	                		String expiredUrl = memcache_keys.remove(0);
	                		memcache_map.remove(expiredUrl);
	                	}
	                }
	        	}
        		handler.post(job);
        	}
        }

        //
        // loadBitmapFile
        //
        protected Bitmap loadBitmapFile(Job job) {
			String imagePath = job.url.substring(7); // strip off file://
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inSampleSize = 2; // yeah yeah...
			Bitmap bitmap = BitmapFactory.decodeFile(imagePath, options);
	        if (bitmap == null) {
	        	Log.e(TAG, "Failed to decode " + job.url);
	        	return null;
	        }
			
			// Create a 16bpp mutable copy
			Bitmap mbitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.RGB_565);
			Canvas canvas = new Canvas(mbitmap);
			canvas.drawBitmap(bitmap, 0, 0, null);
			bitmap.recycle();
			return mbitmap; 
        }
        
        //
        //
        //
        protected Bitmap loadBitmapFromCache(Job job) {
        	Bitmap bitmap = null;
	        String filecachename = urlToCacheFilename(job.url);
	    	File cachefile = null;
	        synchronized (ImageCache.this) {
	        	cachefile = filecachemap.get(filecachename);
	        	if (cachefile != null) {
	        		filecachelist.remove(cachefile);
	        		filecachelist.add(cachefile);
	        	}
	        }
	    	if (cachefile != null) {
	       		//Log.d(TAG, "Cache hit! loading from disk: " + job.url);
	    		bitmap =  BitmapFactory.decodeFile(cachefile.getAbsolutePath());
	    		if (bitmap == null) {
	    			Log.e(TAG, "Failed to decode " + cachefile.getAbsolutePath() + " does it exist? " + cachefile.exists());
	    		}
	    	}
	    	return bitmap;
        }
	        
    
    }


    

    // In-memory cache
    private final HashMap<String, SoftReference<Bitmap>> memcache_map = new HashMap<String, SoftReference<Bitmap>>(100);
    private final ArrayList<String> memcache_keys = new ArrayList<String>(100);
    

    //
    // safeDecodeFile - decode an image file while respecting a memory limit & the orientation
    //
    public static Bitmap safeDecodeFile(String imagePath, int maxMem) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inSampleSize = 1;
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imagePath, options);
        
        // Calculate subsampling rate
        int cbBitmap = options.outHeight * options.outWidth * 2;
        while (cbBitmap >= maxMem) {
        	cbBitmap /= 2;
        	options.inSampleSize++;
        }
        options.inJustDecodeBounds = false;
        

        // Decode the file 
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath, options);
        return bitmap;
    }


}
