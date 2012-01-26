package com.littlefluffytoys.beebdroid;

public class BeebKeys {
	/*
	 * BBC Model B Keyboard : taken from 	http://www.flickr.com/photos/39013214@N03/5660684665/sizes/o/in/photostream/

	         0    1    2    3    4    5    6   7    8    9
	         ----------------------------------------------
	0x70     ESC  F1   F2   F3   F5   F6   F8   F9   \|  right
	0x10     Q    3    4    5    F4   8    F7   -=   ^~  left
	0x20     F0   W    E    T    7    I    9    0    £_  down
	0x30     1    2    D    R    6    U    O    P    [(  up
	0x40     CAP  A    X    F    Y    J    K    @    :*  RET
	0x50     SLC  S    C    G    H    N    L    ;+   ])  DEL
	0x60     TAB  Z    SPC  V    B    M    ,<   .>   /?  CPY
	 */

	public static final int BBCKEY_BREAK = 0xaa;
	//  0x00    
	public static final int BBCKEY_SHIFT = 0x100;
	public static final int BBCKEY_CTRL = 0x01;
	// 	0x10     Q    3    4    5    F4   8    F7   -=   ^~
	public static final int BBCKEY_Q = 0x10;
	public static final int BBCKEY_3 = 0x11;
	public static final int BBCKEY_4 = 0x12;
	public static final int BBCKEY_5 = 0x13;
	public static final int BBCKEY_F4 = 0x14;
	public static final int BBCKEY_8 = 0x15;
	public static final int BBCKEY_F7 = 0x16;
	public static final int BBCKEY_MINUS = 0x17;
	public static final int BBCKEY_EQUALS = 0x117;
	public static final int BBCKEY_TILDE = 0x118;
	public static final int BBCKEY_CARET = 0x18;
	public static final int BBCKEY_ARROW_LEFT = 0x19;
	// 0x20     F0   W    E    T    7    I    9    0  £_
	public static final int BBCKEY_F0 = 0x20;
	public static final int BBCKEY_W = 0x21;
	public static final int BBCKEY_E = 0x22;
	public static final int BBCKEY_T = 0x23;
	public static final int BBCKEY_7 = 0x24;
	public static final int BBCKEY_I = 0x25;
	public static final int BBCKEY_9 = 0x26;
	public static final int BBCKEY_0 = 0x27;
	public static final int BBCKEY_UNDERSCORE = 0x28;
	public static final int BBCKEY_POUND = 0x128;
	public static final int BBCKEY_ARROW_DOWN = 0x29;
	// 0x30     1    2    O    R    6   U    O    P    [(
	public static final int BBCKEY_1 = 0x30;
	public static final int BBCKEY_2 = 0x31;
	public static final int BBCKEY_D = 0x32;
	public static final int BBCKEY_R = 0x33;
	public static final int BBCKEY_6 = 0x34;
	public static final int BBCKEY_U = 0x35;
	public static final int BBCKEY_O = 0x36;
	public static final int BBCKEY_P = 0x37;
	public static final int BBCKEY_BRACKET_LEFT = 0x138;
	public static final int BBCKEY_BRACKET_LEFT_SQ = 0x38;
	public static final int BBCKEY_ARROW_UP = 0x39;
	// 0x40     CAP  A    X    F    Y    J    K    @    :*
	public static final int BBCKEY_CAPS = 0x40;
	public static final int BBCKEY_A = 0x41;
	public static final int BBCKEY_X = 0x42;
	public static final int BBCKEY_F = 0x43;
	public static final int BBCKEY_Y = 0x44;
	public static final int BBCKEY_J = 0x45;
	public static final int BBCKEY_K = 0x46;
	public static final int BBCKEY_AT = 0x47;
	public static final int BBCKEY_COLON = 0x48;
	public static final int BBCKEY_STAR = 0x148;
	public static final int BBCKEY_ENTER = 0x49;
	// 	0x50     SLC  S    C    G    H    N    L    ;+   ])
	public static final int BBCKEY_SHIFTLOCK = 0x50;
	public static final int BBCKEY_S = 0x51;
	public static final int BBCKEY_C = 0x52;
	public static final int BBCKEY_G = 0x53;
	public static final int BBCKEY_H = 0x54;
	public static final int BBCKEY_N = 0x55;
	public static final int BBCKEY_L = 0x56;
	public static final int BBCKEY_SEMICOLON = 0x57;
	public static final int BBCKEY_PLUS = 0x157;
	public static final int BBCKEY_BRACKET_RIGHT = 0x158;
	public static final int BBCKEY_BRACKET_RIGHT_SQ = 0x58;
	public static final int BBCKEY_DELETE = 0x59;
	// 0x60     TAB  Z    SPC  V    B    M    ,<   .>   /?
	public static final int BBCKEY_TAB = 0x60;
	public static final int BBCKEY_Z = 0x61;
	public static final int BBCKEY_SPACE = 0x62;
	public static final int BBCKEY_V = 0x63;
	public static final int BBCKEY_B = 0x64;
	public static final int BBCKEY_M = 0x65;
	public static final int BBCKEY_COMMA = 0x66;
	public static final int BBCKEY_LESS_THAN = 0x166;
	public static final int BBCKEY_PERIOD = 0x67;
	public static final int BBCKEY_MORE_THAN = 0x167;
	public static final int BBCKEY_SLASH = 0x68;
	public static final int BBCKEY_QUESTIONMARK = 0x168;
	public static final int BBCKEY_COPY = 0x69;
	// 0x70     ESC  F1   F2   F3   F5   F6   F8   F9   \| 
	public static final int BBCKEY_ESCAPE = 0x70;
	public static final int BBCKEY_F1 = 0x71;
	public static final int BBCKEY_F2 = 0x72;
	public static final int BBCKEY_F3 = 0x73;
	public static final int BBCKEY_F5 = 0x74;
	public static final int BBCKEY_F6 = 0x75;
	public static final int BBCKEY_F8 = 0x76;
	public static final int BBCKEY_F9 = 0x77;
	public static final int BBCKEY_BACKSLASH = 0x78;
	public static final int BBCKEY_ARROW_RIGHT = 0x79;
	
}
