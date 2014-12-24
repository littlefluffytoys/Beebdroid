package com.littlefluffytoys.beebdroid;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import android.content.Context;
import android.util.Log;

public class Model {

	public static final int FLAG_I8271 = 1;
	public static final int FLAG_WD1770 = 2;
	public static final int FLAG_x65c02 = 4;
	public static final int FLAG_BPLUS = 8;
	public static final int FLAG_MASTER = 16;
	public static final int FLAG_SWRAM = 32;
	public static final int FLAG_MODELA = 64;
	public static final int FLAG_OS01 = 128;
	public static final int FLAG_COMPACT = 256;
	
	public static class ModelInfo {
		String name;
        int flags;
        String os;
        String roms;
        public ModelInfo(String name, String os, String roms, int flags) {
        	this.name = name;
        	this.os = os;
        	this.roms = roms;
        	this.flags = flags;
        }
        
	}
	
	ModelInfo info;
	//ByteBuffer os;
    //ByteBuffer rom;
    //ByteBuffer ram;
	ByteBuffer mem;
    ByteBuffer roms;
    
    public Model() {
    	mem = ByteBuffer.allocateDirect(65536);
        roms = ByteBuffer.allocateDirect(16*16384);
    }
    
    
    public void loadAsset(Context context, ByteBuffer buff, int offset, String assetPath) {
    	InputStream strm;
		try {
			strm = context.getAssets().open(assetPath);
			int size = strm.available();
			//Log.d("Beebdroid", "loadAsset " + assetPath + " is " + size + " bytes");
			byte[] localbuff = new byte[size];
        	strm.read(localbuff, 0, size);
        	strm.close();
        	buff.position(offset);
        	buff.put(localbuff);
        	buff.position(0);
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    public void loadRoms(Context context, ModelInfo info) {
        this.info = info;
        
    	// TODO: loadcmos(models[curmodel]);
       	
    	// OS ROM first.
        if (info.os != null) {
        	loadAsset(context, mem, 0xc000, "roms/"+info.os);
        }

        // Load BASIC and other ROMs.
        String[] romPaths = info.roms.split(";");
        for (int i=0,c=15 ; i<romPaths.length ; i++,c--) {
        	loadAsset(context, roms, c*16384, "roms/"+romPaths[i]);        	
        }

        //if (models[curmodel].swram) fillswram();
    }
    

	public static ModelInfo[] SupportedModels = {
		new ModelInfo("BBC A",             "os",   "a/BASIC.ROM", FLAG_I8271 | FLAG_MODELA),
		new ModelInfo("BBC B w/8271 FDC",  "os",   "b/DFS-0.9.rom;b/BASIC.ROM", FLAG_I8271),
		new ModelInfo("BBC B w/8271+SWRAM","os",   "b/DFS-0.9.rom;b/BASIC.ROM", FLAG_I8271 | FLAG_SWRAM),
		new ModelInfo("BBC B w/1770 FDC",  "os",   "b/DFS-0.9.rom;b/BASIC.ROM", FLAG_WD1770 | FLAG_SWRAM),
		new ModelInfo("BBC B+ 64K",        "bpos", "bp/dfs.rom;bp/BASIC.ROM;bp/zADFS.ROM", FLAG_WD1770 | FLAG_BPLUS),
	};
	
	
}
