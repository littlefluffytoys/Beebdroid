package com.littlefluffytoys.beebdroid;

import java.util.ArrayList;

import com.littlefluffytoys.beebdroid.TouchpadsView.Key;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

public class Keyboard extends TouchpadsView {

	// The height of a row of keys
	private static final int ROWHEIGHT_SMALL = 36;

	// Shift modes
	public static final int SHIFTMODE_NORMAL = 0;
	public static final int SHIFTMODE_ONCE = 1;
	public static final int SHIFTMODE_LOCKED = 2;
	public int shiftMode;
	public boolean shiftActuallyHeld;
	
	// Shift LED drawables
	Drawable ledOn, ledOff;
	
	// The keyboard keys, arranged in rows
	public ArrayList<KeyRow> rows = new ArrayList<KeyRow>();

	public Keyboard(Context context, AttributeSet attrs) {
		super(context, attrs);

		ledOff = context.getResources().getDrawable(R.drawable.led_off);
		ledOn = context.getResources().getDrawable(R.drawable.led_on);
		
        addRow();
        add("TAB", null, 1f, BeebKeys.BBCKEY_TAB);
        add("f0", null, 1f, BeebKeys.BBCKEY_F0, 1);
        add("f1", null, 1f, BeebKeys.BBCKEY_F1, 1);
        add("f2", null, 1f, BeebKeys.BBCKEY_F2, 1);
        add("f3", null, 1f, BeebKeys.BBCKEY_F3, 1);
        add("f4", null, 1f, BeebKeys.BBCKEY_F4, 1);
        add("f5", null, 1f, BeebKeys.BBCKEY_F5, 1);
        add("f6", null, 1f, BeebKeys.BBCKEY_F6, 1);
        add("f7", null, 1f, BeebKeys.BBCKEY_F7, 1);
        add("f8", null, 1f, BeebKeys.BBCKEY_F8, 1);
        add("f9", null, 1f, BeebKeys.BBCKEY_F9, 1);
        add("BREAK", null, 1f, BeebKeys.BBCKEY_BREAK);
        addRow();
        add("ESC", null, 1f, BeebKeys.BBCKEY_ESCAPE);
        add("1", "!",  1f, BeebKeys.BBCKEY_1);
        add("2", "\"",  1f, BeebKeys.BBCKEY_2);
        add("3", "#",  1f, BeebKeys.BBCKEY_3);
        add("4", "$",  1f, BeebKeys.BBCKEY_4);
        add("5", "%",  1f, BeebKeys.BBCKEY_5);
        add("6", "&",  1f, BeebKeys.BBCKEY_6);
        add("7", "'",  1f, BeebKeys.BBCKEY_7);
        add("8", "(",  1f, BeebKeys.BBCKEY_8);
        add("9", ")",  1f, BeebKeys.BBCKEY_9);
        add("0", null,  1f, BeebKeys.BBCKEY_0);
        add("-", "=",  1f, BeebKeys.BBCKEY_MINUS);
        add("^", "~",  1f, BeebKeys.BBCKEY_CARET);
        add("\\", "|",  1f, BeebKeys.BBCKEY_BACKSLASH);
        addRow();
        add("Q", null, 1f, BeebKeys.BBCKEY_Q);
        add("W", null,  1f, BeebKeys.BBCKEY_W);
        add("E", null,  1f, BeebKeys.BBCKEY_E);
        add("R", null,  1f, BeebKeys.BBCKEY_R);
        add("T", null,  1f, BeebKeys.BBCKEY_T);
        add("Y", null,  1f, BeebKeys.BBCKEY_Y);
        add("U", null,  1f, BeebKeys.BBCKEY_U);
        add("I", null,  1f, BeebKeys.BBCKEY_I);
        add("O", null,  1f, BeebKeys.BBCKEY_O);
        add("P", null,  1f, BeebKeys.BBCKEY_P);
        add("@", null,  1f, BeebKeys.BBCKEY_AT);
        add("[", "{",  1f, BeebKeys.BBCKEY_BRACKET_LEFT_SQ);
        add("_", "\u00a3",  1f, BeebKeys.BBCKEY_UNDERSCORE);
        addRow();
        add("A", null, 1f, BeebKeys.BBCKEY_A);
        add("S", null,  1f, BeebKeys.BBCKEY_S);
        add("D", null,  1f, BeebKeys.BBCKEY_D);
        add("F", null,  1f, BeebKeys.BBCKEY_F);
        add("G", null,  1f, BeebKeys.BBCKEY_G);
        add("H", null,  1f, BeebKeys.BBCKEY_H);
        add("J", null,  1f, BeebKeys.BBCKEY_J);
        add("K", null,  1f, BeebKeys.BBCKEY_K);
        add("L", null,  1f, BeebKeys.BBCKEY_L);
        add(";", "+",   1f, BeebKeys.BBCKEY_SEMICOLON);
        add(":", "*",   1f, BeebKeys.BBCKEY_COLON);
        add("]", "}",  1f, BeebKeys.BBCKEY_BRACKET_RIGHT_SQ);
        add("\u2190", null,  1f, BeebKeys.BBCKEY_ARROW_LEFT);
        add("\u2192", null,  1f, BeebKeys.BBCKEY_ARROW_RIGHT);
        addRow();
        add("Z", null, 1f, BeebKeys.BBCKEY_Z);
        add("X", null,  1f, BeebKeys.BBCKEY_X);
        add("C", null,  1f, BeebKeys.BBCKEY_C);
        add("V", null,  1f, BeebKeys.BBCKEY_V);
        add("B", null,  1f, BeebKeys.BBCKEY_B);
        add("N", null,  1f, BeebKeys.BBCKEY_N);
        add("M", null,  1f, BeebKeys.BBCKEY_M);
        add(",", "<",  1f, BeebKeys.BBCKEY_COMMA);
        add(".", ">",  1f, BeebKeys.BBCKEY_PERIOD);
        add("/", "?",  1f, BeebKeys.BBCKEY_SLASH);
        add("\u2191", null,  1f, BeebKeys.BBCKEY_ARROW_UP);
        add("\u2193", null,  1f, BeebKeys.BBCKEY_ARROW_DOWN);
        addRow();
        Key shiftkey = add("SHIFT", null, 2f, BeebKeys.BBCKEY_SHIFT);
        shiftkey.listener = new KeyListener() {
			@Override
			public void onKeyPressed(boolean pressed) {
				shiftActuallyHeld = pressed;
				if (pressed) {
					shiftMode = SHIFTMODE_ONCE;
					shiftPressed = true;
				}
				else {
					if (shiftMode == SHIFTMODE_NORMAL) {
						shiftPressed = false;
					}
				}
				invalidate();
			}
        };
        add("CTRL", null,  1f, BeebKeys.BBCKEY_CTRL);
        add(" ", null, 4f, BeebKeys.BBCKEY_SPACE);
        add("DEL", null, 1f, BeebKeys.BBCKEY_DELETE);
        add("COPY", null, 1f, BeebKeys.BBCKEY_COPY);
        add("RETURN", null, 2f, BeebKeys.BBCKEY_ENTER);
	}

	public Key add(String label, String labelTop, float weight, int scancode, int flags) {
		KeyRow row = rows.get(rows.size()-1);
		Key pad = new Key();
		pad.label = label;
		pad.labelTop = labelTop;
		pad.scancode = scancode;
		pad.layout_width = 0;
		pad.layout_weight = weight;
		pad.flags = flags;
		row.keys.add(pad);
		allkeys.add(pad);
		return pad;
	}
	public Key add(String label, String labelTop, float weight, int scancode) {
		return add(label, labelTop, weight, scancode, 0);
	}

	@Override
	public void defaultOnKeyPressed(Key key, boolean pressed) {
		if (shiftPressed && !shiftActuallyHeld && shiftMode == SHIFTMODE_ONCE) {
			shiftPressed = false;
			invalidate();
		}
		if (shiftActuallyHeld) {
			shiftMode = SHIFTMODE_NORMAL;
		}
	}
	
	public void addRow() {
		rows.add(new KeyRow());
	}
	
	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		super.onLayout(changed, l, t, r, b); 

		float rowheight = (b-t)/6f;//ROWHEIGHT_SMALL * (getContext().getResources().getDisplayMetrics().heightPixels / 480f);
		
		float y = 0;
		for (KeyRow row : rows) {
			row.layout(r-l, y, y+rowheight+Beebdroid.dp(2));
			y += rowheight ;//- Beebdroid.dp(2);
		}
	
		b -=t;
		t = 0;
		int o = (int)Beebdroid.dp(8);
		int d = (int)Beebdroid.dp(10);
		ledOn.setBounds(o, b-(d+o), d+o, b-o);
		ledOff.setBounds(o, b-(d+o), d+o, b-o);
	}
	
	@Override
	public void draw(Canvas canvas) {
		super.draw(canvas);
		Drawable led = shiftPressed ? ledOn : ledOff;
		led.draw(canvas);
	}
	
	

	//
	// KeyRow
	//
	public static class KeyRow {
		public ArrayList<Key> keys = new ArrayList<Key>();
		public void layout(int width, float ftop, float fbottom) {
			float sumweights = 0;
			float sumwidths = 0;
			for (Key key : keys) {
				sumweights += key.layout_weight;
				sumwidths += key.layout_width;
			}
			float x = 0;
			float excess_space = width - Beebdroid.dp(sumwidths);
			float excess_unit = (sumweights==0) ? 0 : (excess_space / sumweights);
			for (Key key : keys) {
				float keywidth = Beebdroid.dp(key.layout_width) + excess_unit * key.layout_weight;
				key.bounds = new RectF(x, ftop, x+keywidth, fbottom);
				x += keywidth;
			}
		}
	}	
}
