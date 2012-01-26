package com.littlefluffytoys.beebdroid;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import common.Packageable;
import common.Utils;



public class DiskInfo extends Packageable {
	
	String key;
	String title;
	String publisher;
	String coverUrl;
	String diskUrl;
	String bootCmd;
	//ControllerInfo defaultController;
	//TriggerAction[] triggers;
	//public boolean renderAt25fps;
	
	public DiskInfo() {
	}
	public DiskInfo(JSONObject obj) throws JSONException {
		key = obj.getString("k");
		title = obj.getString("t");
		publisher = obj.getString("pub");
		diskUrl = Utils.safeGetJsonString(obj, "disk");
		coverUrl = Utils.safeGetJsonString(obj, "cover");
		bootCmd = Utils.safeGetJsonString(obj, "boot");
	}
	
	@Override
	public void readFromPackage(PackageInputStream in) throws IOException {
		key = in.readString();
		title = in.readString();
		publisher = in.readString();
		coverUrl = in.readString();
		diskUrl = in.readString();
		bootCmd = in.readString();
	}

	@Override
	public void writeToPackage(PackageOutputStream out) throws IOException {
		out.writeString(key);
		out.writeString(title);
		out.writeString(publisher);
		out.writeString(coverUrl);
		out.writeString(diskUrl);
		out.writeString(bootCmd);
	}	

	/*public DiskInfo(String key, String title, String publisher, String bootCmd, ControllerInfo defaultController, TriggerAction[] triggers) {
		this.key = key;
		this.cover = key + ".jpg";
		this.title = title;
		this.publisher = publisher;
		this.diskImage = key;
		this.bootCmd = bootCmd;
		this.defaultController = defaultController;
		this.triggers = triggers;
		renderAt25fps = true;
		if (key.equals("starquake") || key.equals("hyper_viper")) {
			renderAt25fps = false;			
		}
		diskMap.put(key, this);
	}*/
	
	/*public static class TriggerAction {
		short pc_trigger;
		public TriggerAction(short pc_trigger) {
			this.pc_trigger = pc_trigger;
		}
	}
	public static class TriggerActionSetController extends TriggerAction {
		public TriggerActionSetController(short pc_trigger, ControllerInfo controllerInfo) {
			super(pc_trigger);
			this.controllerInfo = controllerInfo;
		}
		ControllerInfo controllerInfo;
	}*/
	
	
	

		
}
