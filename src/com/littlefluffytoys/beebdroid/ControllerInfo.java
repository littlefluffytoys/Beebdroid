package com.littlefluffytoys.beebdroid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ControllerInfo {

	public String name;
	public ArrayList<KeyInfo> keyinfos = new ArrayList<KeyInfo>();
	public Map<Integer, KeyInfo> keyinfosMappedByAndroidKeycode = new HashMap<Integer, KeyInfo>();
	public boolean useDPad;
	public int androidKeycode;
	
	public class KeyInfo  {
		String label;
		int labelIconId;
		String keylabel;
		int scancode;
		float xc,yc, width, height;
    }
	
	public static class TriggerAction {
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
	}
	public List<TriggerAction> triggers =  new ArrayList<TriggerAction>();
	
	public void addTrigger(short pc, ControllerInfo switchToController) {
		TriggerActionSetController trigger = new TriggerActionSetController(pc, switchToController);
		triggers.add(trigger);
	}

	
	public void addKey(String label, String keylabel, float xc, float yc, float width, float height, int scancode) {
		addKey(label, keylabel, xc, yc, width, height, scancode, 0);
	}
	public void addKey(String label, String keylabel, float xc, float yc, float width, float height, int scancode, int androidKeycode) {
		addKey(label, keylabel, xc, yc, width, height, scancode, androidKeycode, 0);
	}
	public void addKey(String label, String keylabel, float xc, float yc, float width, float height, int scancode, int androidKeycode, int androidKeycode2) {	
		KeyInfo key = new KeyInfo();
		key.label = label;
		key.keylabel = keylabel;
		key.xc = xc;
		key.yc = yc;
		key.width = width;
		key.height = height;
		key.scancode = scancode;
		keyinfos.add(key);
		if (androidKeycode != 0) {
			keyinfosMappedByAndroidKeycode.put(androidKeycode, key);
		}
		if (androidKeycode2 != 0) {
			keyinfosMappedByAndroidKeycode.put(androidKeycode2, key);
		}
	}

	
}
