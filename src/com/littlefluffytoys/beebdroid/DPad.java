package com.littlefluffytoys.beebdroid;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;


public class DPad {

	public interface Listener {
		public void onLeft(boolean pressed);
		public void onUp(boolean pressed);
		public void onRight(boolean pressed);
		public void onDown(boolean pressed);
	}
	Listener listener;
	Drawable bkgnd;
	Drawable arrowUp, arrowDown, arrowLeft, arrowRight;
	Drawable joystick;
	boolean joystickMode;
	
	int current;
	Rect bounds = new Rect();
	int midx, midy;
	public static final int FLAG_LEFT = 1;
	public static final int FLAG_RIGHT = 2;
	public static final int FLAG_UP = 4;
	public static final int FLAG_DOWN = 8;
	
	public DPad(Context context) {
		bkgnd = context.getResources().getDrawable(R.drawable.dpad0);
		arrowUp = context.getResources().getDrawable(R.drawable.dpad_arrow_up);
		arrowDown = context.getResources().getDrawable(R.drawable.dpad_arrow_down);
		arrowLeft = context.getResources().getDrawable(R.drawable.dpad_arrow_left);
		arrowRight = context.getResources().getDrawable(R.drawable.dpad_arrow_right);
		joystickMode = false;
		velocityTracker = VelocityTracker.obtain();
		//joystick = context.getResources().getDrawable(R.drawable.joystick);
	}

	/*@Override
	protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);
	}*/
	public void onLayout(int left, int top, int right, int bottom) {
		bounds.set(left, top, right, bottom);
		bkgnd.setBounds(left, top, right, bottom);
		int arrowWidth = (int)Beebdroid.dp(20);
		int arrowHeight = (int)Beebdroid.dp(28);
		int margin = (int)Beebdroid.dp(10);
		midx = left + (right-left)/2;
		midy = top + (bottom-top)/2;
		arrowUp.setBounds(midx - arrowWidth, margin, midx+arrowWidth, margin+arrowHeight);
		arrowLeft.setBounds(left+margin, midy-arrowWidth, left+margin+arrowHeight, midy+arrowWidth);
		arrowRight.setBounds(right - (margin+arrowHeight), midy-arrowWidth, right-margin, midy+arrowWidth);
		arrowDown.setBounds(midx - arrowWidth, bottom-(margin+arrowHeight), midx+arrowWidth, bottom-margin);
	}
	
	
	public void draw(Canvas canvas) {
		bkgnd.draw(canvas);
		if ((current & FLAG_UP) != 0) arrowUp.draw(canvas);
		if ((current & FLAG_DOWN) != 0) arrowDown.draw(canvas);
		if ((current & FLAG_LEFT) != 0) arrowLeft.draw(canvas);
		if ((current & FLAG_RIGHT) != 0) arrowRight.draw(canvas);
	}
	
	VelocityTracker velocityTracker;
	
	public boolean onTouchEventJoystick(MotionEvent event) {
		int actionCode = event.getActionMasked(); // action & MotionEvent.ACTION_MASK;
		int newFlags = 0;
		switch (actionCode) {
		case MotionEvent.ACTION_DOWN:
			velocityTracker.clear();
			velocityTracker.addMovement(event);
			return true;
		case MotionEvent.ACTION_MOVE:
			velocityTracker.addMovement(event);
			velocityTracker.computeCurrentVelocity(50);
			int vx = (int)velocityTracker.getXVelocity()/20;
			int vy = (int)velocityTracker.getYVelocity()/20;
			//Log.d("Joystick", "Velocity: x=" + vx + " y=" + vy);
			if (vx==0 && vy==0) { // no change of direction, do nothing
				return true;
			}
			if (vx<0) newFlags |= FLAG_LEFT;
			if (vx>0) newFlags |= FLAG_RIGHT;
			if (vy<0) newFlags |= FLAG_UP;
			if (vy>0) newFlags |= FLAG_DOWN;
			break;
		case MotionEvent.ACTION_UP:
			velocityTracker.clear();
			break;
		}
		processNewMovement(newFlags);
		return true;
	}
	
	public boolean onTouchEvent(MotionEvent event) {
		float x = event.getX();
		float y = event.getY();
		if (!bounds.contains((int)x, (int)y)) {
			return false;
		}
		if (joystickMode) {
			return onTouchEventJoystick(event);
		}
		int actionCode = event.getActionMasked(); // action & MotionEvent.ACTION_MASK;
		int newFlags = 0;
		switch (actionCode) {
		case MotionEvent.ACTION_DOWN:
		case MotionEvent.ACTION_MOVE:
			double da = 180+Math.toDegrees(Math.atan2(y-midy, x-midx));
			da = (da-22.5)/45;
			if (da<0) da+=360/45;
			int a = (int)da;
			if (a==0 || a==1 || a==2) newFlags |=FLAG_UP;
			if (a==2 || a==3 || a==4) newFlags |=FLAG_RIGHT;
			if (a==4 || a==5 || a==6) newFlags |=FLAG_DOWN;
			if (a==6 || a==7 || a==0) newFlags |=FLAG_LEFT;
			//Log.d("DPad", "angle " + (int)a);
			break;
		case MotionEvent.ACTION_UP:
			break;
		default:
			return false;
		}
		processNewMovement(newFlags);
		return true;
	}

	protected void processNewMovement(int newFlags) {
		int changes = newFlags ^ current;
		if (changes != 0) {
			if (listener != null) {
				if ((changes & FLAG_LEFT)!=0) listener.onLeft((newFlags &FLAG_LEFT) !=0);
				if ((changes & FLAG_RIGHT)!=0) listener.onRight((newFlags &FLAG_RIGHT) !=0);
				if ((changes & FLAG_UP)!=0) listener.onUp((newFlags &FLAG_UP) !=0);
				if ((changes & FLAG_DOWN)!=0) listener.onDown((newFlags &FLAG_DOWN) !=0);
			}
			current = newFlags;
		}
		
	}

}
