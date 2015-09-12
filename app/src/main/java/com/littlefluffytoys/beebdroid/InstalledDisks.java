package com.littlefluffytoys.beebdroid;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import common.Packageable;

import android.content.Context;

public class InstalledDisks {
	public static final int CURRENT_VERSION=1;
	
	public static List<DiskInfo> disks = new ArrayList<DiskInfo>();
	private static Map<String, DiskInfo> map = new HashMap<String, DiskInfo>();
	
	public static void load(Context context) {
		packageable.load(context, new File(context.getCacheDir(), "disks.dat"), CURRENT_VERSION);		
		for (DiskInfo disk : disks) {
			map.put(disk.key, disk);
		}
	}
	public static int getCount() {
		return disks.size();
	}
	public static DiskInfo getByIndex(int index) {
		return disks.get(index);
	}
	public static DiskInfo getByKey(String key) {
		return map.get(key);
	}
	public static DiskInfo add(DiskInfo diskInfo) {
		disks.add(0, diskInfo);
		map.put(diskInfo.key, diskInfo);
		packageable.save();
		return diskInfo;
	}
	
	static Packageable packageable = new Packageable() {
		@Override
		public void readFromPackage(PackageInputStream in) throws IOException {
			disks = in.readPackageableList(DiskInfo.class);
		}
	
		@Override
		public void writeToPackage(PackageOutputStream out) throws IOException {
			out.writePackageableList(disks);
		}
	};
	
	
	
}
