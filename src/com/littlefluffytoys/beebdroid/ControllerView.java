package com.littlefluffytoys.beebdroid;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;

public class ControllerView extends TouchpadsView implements DPad.Listener {

	public ControllerView(Context context, AttributeSet attrs) {
		super(context, attrs);		
     }
	
	ControllerInfo controllerInfo;
	DPad dpad;
	int dpadSize;
	int scancodeLeft;
	int scancodeRight;
	int scancodeUp;
	int scancodeDown;
	
	public void setController(ControllerInfo info) {
		controllerInfo = info;

		// Don't create visible keys on a Play
		if (beebdroid.isXperiaPlay) {
			return;
		}
		
		dpadSize = (int)Beebdroid.dp(160);
		dpad = null;
		if (info.useDPad) {
			dpad = new DPad(getContext());
			dpad.listener = this;
			requestLayout();
		}
		recreateKeys();
	}
	
	private void recreateKeys() {
		allkeys.clear();
		if (controllerInfo == null) {
			return;
		}

		int div = 4;
		if (Beebdroid.DP_SCREEN_WIDTH >= 500) { // big bastard screen
			div = 6;
		}
		float padwidth = Beebdroid.DP_SCREEN_WIDTH / div;
		float padheight = padwidth;
		
		int width = getWidth();
		int height = getHeight();
		for (ControllerInfo.KeyInfo keyinfo : controllerInfo.keyinfos) {
			Key key = new Key();
			key.scancode = keyinfo.scancode;
			key.label = keyinfo.label;
			
    		float l = (keyinfo.xc<0) ? (width + keyinfo.xc*padwidth) : (keyinfo.xc*padwidth);
    		float t = (keyinfo.yc<0) ? (height + keyinfo.yc*padheight) : (keyinfo.yc*padheight);
    		key.bounds = new RectF(l, t, (l+padwidth*keyinfo.width), (t+padwidth*keyinfo.height));
    		
    		if (dpad != null) {
	    		if (keyinfo.label.equals("Left")) {scancodeLeft = keyinfo.scancode; continue;}
	      		if (keyinfo.label.equals("Right")) {scancodeRight = keyinfo.scancode; continue;}
	      		if (keyinfo.label.equals("Up")) {scancodeUp = keyinfo.scancode; continue;}
	      		if (keyinfo.label.equals("Down")) {scancodeDown = keyinfo.scancode; continue;}
    		}
    		
    		allkeys.add(key);

		}
		invalidate();
	}
	
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		super.onLayout(changed, l, t, r, b); 
		if (dpad != null) {
			dpad.onLayout(l, b-dpadSize, l+dpadSize, b);
			//dpad.onLayout(r-dpadSize, b-dpadSize, r, b);
		}
		recreateKeys();
	}
	
	
	@Override
	public void draw(Canvas canvas) {
		if (beebdroid.isXperiaPlay) {
			return;
		}
		super.draw(canvas);
		if(dpad != null) {
			dpad.draw(canvas);
		}
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		
		// Dpad
		if (dpad != null) {
			if (dpad.onTouchEvent(event)) {
				//invalidate();
				return true;
			}
		}
		
		return super.onTouchEvent(event);
	}
	
	
	
	//
	// DPadView.Listener
	//
	@Override
	public void onLeft(boolean pressed) {
		beebdroid.bbcKeyEvent(scancodeLeft, 0, pressed?1:0);
		invalidate();
	}
	@Override
	public void onUp(boolean pressed) {
		beebdroid.bbcKeyEvent(scancodeUp, 0, pressed?1:0);
		invalidate();
	}
	@Override
	public void onRight(boolean pressed) {
		beebdroid.bbcKeyEvent(scancodeRight, 0, pressed?1:0);
		invalidate();
	}
	@Override
	public void onDown(boolean pressed) {
		beebdroid.bbcKeyEvent(scancodeDown, 0, pressed?1:0);
		invalidate();
	}	
}
