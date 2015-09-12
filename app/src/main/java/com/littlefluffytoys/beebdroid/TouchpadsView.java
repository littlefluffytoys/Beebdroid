package com.littlefluffytoys.beebdroid;

import java.util.ArrayList;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable.Orientation;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;


public class TouchpadsView extends View {
	
	
	
	public void defaultOnKeyPressed(Key key, boolean pressed) {
	}
	
	public static interface KeyListener {
		public void onKeyPressed(boolean pressed);
	}
	
	public class Key {
    	String label, labelTop;
    	int flags;
    	float layout_width;
    	float layout_weight;
    	int scancode;
    	boolean pressed;
    	RectF bounds;
    	KeyListener listener;
		public void press(boolean pressed) {
			if (listener == null) {
				defaultOnKeyPressed(this, pressed);
			}
			else {
				listener.onKeyPressed(pressed);
			}
			this.pressed = pressed;
		}
    }
	
	
	public ArrayList<Key> allkeys = new ArrayList<Key>();
	public boolean shiftPressed;
	
	
	public Beebdroid beebdroid;
	private Drawable padDrawable, padDrawableFn, padDrawableHilite;
	private Paint paint, paintBig, paintTiny;

	public TouchpadsView(Context context, AttributeSet attrs) {
		super(context, attrs);
		padDrawable = getResources().getDrawable(R.drawable.key);
		padDrawableFn = getResources().getDrawable(R.drawable.key_fn);
		padDrawableHilite = getResources().getDrawable(R.drawable.pad_pressed);
		int alpha = (context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) ? 127:255;
		padDrawable.setAlpha(alpha);
		padDrawableFn.setAlpha(alpha);
		padDrawableHilite.setAlpha(alpha);
		paintTiny = new Paint();
		paintTiny.setColor(Color.WHITE);
		paintTiny.setTextSize(12);
		paintTiny.setTextAlign(Align.CENTER);
		paintTiny.setAntiAlias(true);
		paint = new Paint();
		paint.setColor(Color.WHITE);
		paint.setTextSize(22);
		paint.setTextAlign(Align.CENTER);
		paint.setAntiAlias(true);
		paintBig = new Paint();
		paintBig.setColor(Color.WHITE);
		paintBig.setTypeface(Typeface.DEFAULT_BOLD);
		paintBig.setTextSize(30);
		paintBig.setTextAlign(Align.CENTER);
	}
		
	
	private Key[] pressedPads = {null,null,null,null};

	
	private void dumpEvent(MotionEvent event) {
	   String names[] = { "DOWN" , "UP" , "MOVE" , "CANCEL" , "OUTSIDE" ,
	      "POINTER_DOWN" , "POINTER_UP" , "7?" , "8?" , "9?" };
	   StringBuilder sb = new StringBuilder();
	   int action = event.getAction();
	   int actionCode = action & MotionEvent.ACTION_MASK;
	   sb.append("event ACTION_" ).append(names[actionCode]);
	   if (actionCode == MotionEvent.ACTION_POINTER_DOWN
	         || actionCode == MotionEvent.ACTION_POINTER_UP) {
	      sb.append("(pid " ).append(
	      action >> MotionEvent.ACTION_POINTER_ID_SHIFT);
	      sb.append(")" );
	   }
	   sb.append("[" );
	   for (int i = 0; i < event.getPointerCount(); i++) {
	      sb.append("#" ).append(i);
	      sb.append("(pid " ).append(event.getPointerId(i));
	      sb.append(")=" ).append((int) event.getX(i));
	      sb.append("," ).append((int) event.getY(i));
	      if (i + 1 < event.getPointerCount())
	         sb.append(";" );
	   }
	   sb.append("]" );
	   Log.d("Touch", sb.toString());
	}
	
	private Key hitTest(int x, int y) {
		for (Key key : allkeys) {
			if (key.bounds.contains(x, y)) {
				return key;
			}
		}
		return null;
	}

	@Override public boolean onTouchEvent(MotionEvent event) {
		//dumpEvent(event);
		
		int action = event.getAction();
		int actionCode = event.getActionMasked(); // action & MotionEvent.ACTION_MASK;
		int pid = -1;
		Key touchedPad, prevPad;
		
		
		switch (actionCode) {
		case MotionEvent.ACTION_DOWN:
			pid = 0; //event.getPointerId(0);
//			Log.d("Touch", "ACTION_DOWN " + pid);
			touchedPad = hitTest((int)event.getX(pid), (int)event.getY(pid));
			pressedPads[pid] = touchedPad;
			if (touchedPad != null) {
				beebdroid.bbcKeyEvent(touchedPad.scancode, shiftPressed?1:0, 1);
				touchedPad.press(true);
				invalidate();
				//Log.d("Touch", touchedPad.label + " in pid=" + pid);
				return true;
			}
			break;
		case MotionEvent.ACTION_POINTER_DOWN:
			pid = (action & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
//			Log.d("Touch", "ACTION_POINTER_DOWN " + pid);
			touchedPad = hitTest((int)event.getX(pid), (int)event.getY(pid));
			pressedPads[pid] = touchedPad;
			if (touchedPad != null) {
				beebdroid.bbcKeyEvent(touchedPad.scancode, shiftPressed?1:0, 1);
				touchedPad.press(true);
				invalidate();
	//			Log.d("Touch", touchedPad.label + " in pid=" + pid);
				return true;
			}
			break;
			
		case MotionEvent.ACTION_UP:
			pid = event.getPointerId(0);
		//	Log.d("Touch", "ACTION_UP " + pid);
			prevPad = pressedPads[pid];
			if (prevPad != null) {
				prevPad.press(false);
				beebdroid.bbcKeyEvent(prevPad.scancode, shiftPressed?1:0, 0);
				invalidate();
			//	Log.d("Touch", prevPad.label + " out pid=" + pid);
			}
			pressedPads[pid] = null;
			break;
		case MotionEvent.ACTION_POINTER_UP:
			pid = (action & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
			//Log.d("Touch", "ACTION_POINTER_UP " + pid);
			prevPad = pressedPads[pid];
			if (prevPad != null) {
				prevPad.press(false);
				beebdroid.bbcKeyEvent(prevPad.scancode, shiftPressed?1:0, 0);
				invalidate();
				//Log.d("Touch", prevPad.label + " out pid=" + pid);
			}
			pressedPads[pid] = null;
			break;
			
		case MotionEvent.ACTION_MOVE:
			
			for (int i = 0; i < event.getPointerCount(); i++) {
				pid = event.getPointerId(i);
				if (pid >=4) continue;
				prevPad = pressedPads[pid];
				touchedPad = hitTest((int)event.getX(i), (int)event.getY(i));
			
				// If nothing changed, nothing to do
				if (prevPad==null && touchedPad==null) {
					continue;
				}
				if (prevPad!=null) { // only care if tracking a gesture that started on a pad
					if (prevPad.equals(touchedPad)) {
						continue;
					}
				}
				
				// Moved into a new pad OR out of one we were in before
				if (prevPad!=null) { 
					beebdroid.bbcKeyEvent(prevPad.scancode, 0, 0);
					prevPad.press(false);
					//Log.d("Touch", prevPad.label + " dragout pid=" + pid);
					invalidate();
				}
				if (touchedPad != null) {
					beebdroid.bbcKeyEvent(touchedPad.scancode, 0, 1);
					touchedPad.press(true);
					invalidate();
					//Log.d("Touch", touchedPad.label + " dragin pid=" + pid);
				}
				pressedPads[pid] = touchedPad;
			}
			break;
				
		}
		return true;
	}
	
	
    @Override 
    protected void onDraw(Canvas canvas) {
    	for (Key key : allkeys) {
    		Drawable d = key.pressed?padDrawableHilite:padDrawable;
    		if (0 != (key.flags & 1)) {
    			d = padDrawableFn;
    		}
    		d.setBounds((int)key.bounds.left, (int)key.bounds.top, (int)key.bounds.right, (int)key.bounds.bottom);
    		d.draw(canvas);
    		float y = key.bounds.centerY();
    		if (key.labelTop != null) {
    			if (shiftPressed) {
	    			y-=Beebdroid.dp(4);
	    	 		canvas.drawText(key.labelTop, key.bounds.centerX(), y, paint);
	    			y+=Beebdroid.dp(12);
	       			canvas.drawText(key.label, key.bounds.centerX(), y, paintTiny);    				
    			}
    			else {
	    			y-=Beebdroid.dp(8);
	    	 		canvas.drawText(key.labelTop, key.bounds.centerX(), y, paintTiny);
	    			y+=Beebdroid.dp(13);
	       			canvas.drawText(key.label, key.bounds.centerX(), y, paint);
    			}
    		}
    		else {
    			canvas.drawText(key.label, key.bounds.centerX(), y, key.label.length() > 1 ? paintTiny : paint);
    		}
    	}
    }		
}


