package com.littlefluffytoys.beebdroid;

import java.util.HashMap;
import java.util.Map;


import android.view.KeyEvent;

public class Controllers {

	
	static ControllerInfo controller_ZX_4way = new ControllerInfo();
	static ControllerInfo controller_ZX_5way = new ControllerInfo();
	static ControllerInfo controller_RocketRaid_Game = new ControllerInfo();
	static ControllerInfo controller_Qman_Game = new ControllerInfo();
	static ControllerInfo controller_Arcadians_Game = new ControllerInfo();
	static ControllerInfo controller_Arcadians_Menu = new ControllerInfo();
	static ControllerInfo controller_CastleQuest_Game = new ControllerInfo();
	static ControllerInfo controller_ChuckieEgg_Game = new ControllerInfo();
	static ControllerInfo controller_ChuckieEgg_Game_Alt = new ControllerInfo();
	static ControllerInfo controller_DareDevilDennis_Game = new ControllerInfo();
	static ControllerInfo controller_Thrust_Game = new ControllerInfo();
	static ControllerInfo controller_Planetoid_Game = new ControllerInfo();
	static ControllerInfo controller_Imogen_Game = new ControllerInfo();
	static ControllerInfo controller_Elite_Game = new ControllerInfo();
	static ControllerInfo controller_Zalaga_Game = new ControllerInfo();

	static ControllerInfo DEFAULT_CONTROLLER = controller_ZX_5way;

	static {
		controller_ZX_4way.addKey("Left",  "Z",   0, -1, 1f, 1f, BeebKeys.BBCKEY_Z, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BUTTON_X);
		controller_ZX_4way.addKey("Right", "X",   1, -1, 1f, 1f, BeebKeys.BBCKEY_X, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_BACK);
		controller_ZX_4way.addKey("Up",    ":",  -1,  0, 1f, 1f, BeebKeys.BBCKEY_COLON, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_BUTTON_Y);
		controller_ZX_4way.addKey("Down",  "/",  -1,  1, 1f, 1f, BeebKeys.BBCKEY_SLASH, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER);
		controller_ZX_5way.addKey("Left",  "Z",   0,  -1, 1f, 1f, BeebKeys.BBCKEY_Z, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BUTTON_X);
		controller_ZX_5way.addKey("Right", "X",   1,  -1, 1f, 1f, BeebKeys.BBCKEY_X, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_BACK);
		controller_ZX_5way.addKey("Up",    ":",  -2,  0, 1f, 1f, BeebKeys.BBCKEY_COLON, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_BUTTON_Y);
		controller_ZX_5way.addKey("Down",  "/",  -2,  1, 1f, 1f, BeebKeys.BBCKEY_SLASH, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER);
		controller_ZX_5way.addKey("Fire", "Return",  -1,  0, 1f, 2f, BeebKeys.BBCKEY_ENTER, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1);
		controller_RocketRaid_Game.addKey("Back",  "SPACE",   0,  -1, 1f, 1f, BeebKeys.BBCKEY_SPACE, KeyEvent.KEYCODE_DPAD_LEFT);
		controller_RocketRaid_Game.addKey("Forwards", "SHIFT",   1,  -1, 1f, 1f, BeebKeys.BBCKEY_SHIFT, KeyEvent.KEYCODE_DPAD_RIGHT);
		controller_RocketRaid_Game.addKey("Up",    "A",  -2,  0, 1f, 1f, BeebKeys.BBCKEY_A, KeyEvent.KEYCODE_DPAD_UP);
		controller_RocketRaid_Game.addKey("Down",  "Z",  -2,  1, 1f, 1f, BeebKeys.BBCKEY_Z, KeyEvent.KEYCODE_DPAD_DOWN);
		controller_RocketRaid_Game.addKey("Fire", "Return",  -1,  0, 1f, 1f, BeebKeys.BBCKEY_ENTER, KeyEvent.KEYCODE_DPAD_CENTER);
		controller_RocketRaid_Game.addKey("Bomb", "Tab",  -1,  1, 1f, 1f, BeebKeys.BBCKEY_TAB, KeyEvent.KEYCODE_BACK);
		controller_Qman_Game.addKey("Up-Left",  "A",   0, 0, 1f, 1f, BeebKeys.BBCKEY_A, KeyEvent.KEYCODE_BUTTON_X);
		controller_Qman_Game.addKey("Down-Left", "Z",   0, -1, 1f, 1f, BeebKeys.BBCKEY_Z, KeyEvent.KEYCODE_DPAD_DOWN);
		controller_Qman_Game.addKey("Up-Right",    ":",  -1,  0, 1f, 1f, BeebKeys.BBCKEY_COLON, KeyEvent.KEYCODE_DPAD_RIGHT);
		controller_Qman_Game.addKey("Down-Right",  "/",  -1,  1, 1f, 1f, BeebKeys.BBCKEY_SLASH, KeyEvent.KEYCODE_DPAD_CENTER);
		
		// Arcadians has 2 controllers and uses PC triggers to switch between them
//		controller_Arcadians_Menu.addKey("1","1",    0f,  0, 1.5f, 1.5f,  BeebKeys.BBCKEY_1);
//		controller_Arcadians_Menu.addKey("2","2",    1.5f,  0, 1.5f, 1.5f,  BeebKeys.BBCKEY_2);
//		controller_Arcadians_Menu.addTrigger((short) 0x3203, controller_Arcadians_Game);
		controller_Arcadians_Menu.addKey("Left","Caps",    0f,  0, 1.25f, 1.25f,  BeebKeys.BBCKEY_CAPS, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BUTTON_X);
		controller_Arcadians_Menu.addKey("Right","Ctrl",   1.25f,  0, 1.25f, 1.25f,  BeebKeys.BBCKEY_CTRL, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_BACK);
		controller_Arcadians_Menu.addKey("Fire","Return",    -1f,  0,   1f,   1.25f,  BeebKeys.BBCKEY_ENTER, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1);
//		controller_Arcadians_Game.addTrigger((short) 0x4d95, controller_Arcadians_Menu);

		controller_CastleQuest_Game.addKey("Left", "Z",   0,  -1, 1f, 1f, BeebKeys.BBCKEY_Z, KeyEvent.KEYCODE_DPAD_LEFT);
		controller_CastleQuest_Game.addKey("Right", "X",   1,  -1, 1f, 1f, BeebKeys.BBCKEY_X, KeyEvent.KEYCODE_DPAD_RIGHT);
		controller_CastleQuest_Game.addKey("Up", ":",  -1,  0, 1f, 1f, BeebKeys.BBCKEY_COLON, KeyEvent.KEYCODE_DPAD_UP);
		controller_CastleQuest_Game.addKey("Down", "/",  -1,  1, 1f, 1f, BeebKeys.BBCKEY_SLASH, KeyEvent.KEYCODE_DPAD_DOWN);
		controller_CastleQuest_Game.addKey("Pick Up", "P",   0,  1, 1f, 1f, BeebKeys.BBCKEY_P, KeyEvent.KEYCODE_BUTTON_Y);
		controller_CastleQuest_Game.addKey("Drop", "D",   1,  1, 1f, 1f, BeebKeys.BBCKEY_D, KeyEvent.KEYCODE_DPAD_CENTER);
		controller_CastleQuest_Game.addKey("Jump", "Return",   2,  1,  1f, 1f, BeebKeys.BBCKEY_ENTER, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1);
		controller_ChuckieEgg_Game.addKey("Left",  ",",      0,   -1,  1f, 1f, BeebKeys.BBCKEY_COMMA, KeyEvent.KEYCODE_DPAD_LEFT);
		controller_ChuckieEgg_Game.addKey("Right", ".",      1,   -1,  1f, 1f, BeebKeys.BBCKEY_PERIOD, KeyEvent.KEYCODE_DPAD_RIGHT);
		controller_ChuckieEgg_Game.addKey("Up",    "A",     -2,    0,  1f, 1f, BeebKeys.BBCKEY_A, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_BUTTON_Y);
		controller_ChuckieEgg_Game.addKey("Down",  "Z",     -2,    1,  1f, 1f, BeebKeys.BBCKEY_Z,  KeyEvent.KEYCODE_DPAD_CENTER);
		controller_ChuckieEgg_Game.addKey("Jump",  " ",     -1,    0,  1f, 2f, BeebKeys.BBCKEY_SPACE, KeyEvent.KEYCODE_BACK);
		controller_ChuckieEgg_Game.addKey("S",     "S", -1.75f,   1f, .5f, .5f, BeebKeys.BBCKEY_S);
		controller_ChuckieEgg_Game.addKey("1",     "1", -1.75f, 1.5f, .5f, .5f, BeebKeys.BBCKEY_1);
		controller_ChuckieEgg_Game.useDPad = true;
		controller_ChuckieEgg_Game_Alt.addKey("Left",  ",",      0,   -1,  1f, 1f, BeebKeys.BBCKEY_COMMA, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BUTTON_X);
		controller_ChuckieEgg_Game_Alt.addKey("Right", ".",      1,   -1,  1f, 1f, BeebKeys.BBCKEY_PERIOD, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_BACK);
		controller_ChuckieEgg_Game_Alt.addKey("Up",    "A",     -2,    0,  1f, 1f, BeebKeys.BBCKEY_A, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_BUTTON_Y);
		controller_ChuckieEgg_Game_Alt.addKey("Down",  "Z",     -2,    1,  1f, 1f, BeebKeys.BBCKEY_Z,  KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER);
		controller_ChuckieEgg_Game_Alt.addKey("Jump",  " ",     -1,    0,  1f, 2f, BeebKeys.BBCKEY_SPACE, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1);
		controller_ChuckieEgg_Game_Alt.addKey("S",     "S", -1.75f,   1f, .5f, .5f, BeebKeys.BBCKEY_S);
		controller_ChuckieEgg_Game_Alt.addKey("1",     "1", -1.75f, 1.5f, .5f, .5f, BeebKeys.BBCKEY_1);
		controller_ChuckieEgg_Game_Alt.useDPad = true;
		controller_DareDevilDennis_Game.addKey("Accel", "Shift",   0,  0, 1f, 2f, BeebKeys.BBCKEY_SHIFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_BACK);
		controller_DareDevilDennis_Game.addKey("Stop", "Return",   1,  0, 1f, 2f, BeebKeys.BBCKEY_ENTER, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BUTTON_X);
		controller_DareDevilDennis_Game.addKey("Jump", "",  -2,  0, 2f, 2f, BeebKeys.BBCKEY_SPACE, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1);
		controller_Imogen_Game.addKey("Left",  "Z",   0,  -1, 1f, 1f, BeebKeys.BBCKEY_Z, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BUTTON_X);
		controller_Imogen_Game.addKey("Right", "X",   1,  -1, 1f, 1f, BeebKeys.BBCKEY_X, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_BACK);
		controller_Imogen_Game.addKey("Up",    ":",  -2,  0, 1f, 1f, BeebKeys.BBCKEY_COLON, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_BUTTON_Y);
		controller_Imogen_Game.addKey("Down",  "/",  -2,  1, 1f, 1f, BeebKeys.BBCKEY_SLASH, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER);
		controller_Imogen_Game.addKey("Fire", "Return",  -1,  0, 1f, 2f, BeebKeys.BBCKEY_ENTER, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1);
		controller_Imogen_Game.addKey("<-",  "<-",   0,  0, .5f, .5f, BeebKeys.BBCKEY_ARROW_LEFT, KeyEvent.KEYCODE_BUTTON_Y);
		controller_Imogen_Game.addKey("->", "->",   0.5f,  -0, .5f, .5f, BeebKeys.BBCKEY_ARROW_RIGHT, KeyEvent.KEYCODE_BACK);
		controller_Imogen_Game.addKey("[]",    "[]",  1,  0, .5f, .5f, BeebKeys.BBCKEY_SPACE, KeyEvent.KEYCODE_DPAD_CENTER);
		controller_Thrust_Game.addKey("Left","Caps",    0f,  -1, 1f, 1f,  BeebKeys.BBCKEY_CAPS, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BUTTON_X);
		controller_Thrust_Game.addKey("Right","Ctrl",   1f,  -1, 1f, 1f,  BeebKeys.BBCKEY_CTRL, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_BACK);
		controller_Thrust_Game.addKey("Thrust","Shift", -1f,  0, 1f, 2f,  BeebKeys.BBCKEY_SHIFT, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER);
		controller_Thrust_Game.addKey("Fire","Return", -2f,  0, 1f, 1f,  BeebKeys.BBCKEY_ENTER, KeyEvent.KEYCODE_BUTTON_R1);
		controller_Thrust_Game.addKey("Shield","", -2f,  1, 1f, 1f,  BeebKeys.BBCKEY_SPACE, KeyEvent.KEYCODE_BUTTON_L1);
		controller_Planetoid_Game.addKey("Up",    "A",  -2,  0, 1f, 1f, BeebKeys.BBCKEY_A, KeyEvent.KEYCODE_DPAD_UP);
		controller_Planetoid_Game.addKey("Down",  "Z",  -2,  1, 1f, 1f, BeebKeys.BBCKEY_Z, KeyEvent.KEYCODE_DPAD_DOWN);
		controller_Planetoid_Game.addKey("Reverse",  "SPACE",   0,  -1, 1f, 1f, BeebKeys.BBCKEY_SPACE, KeyEvent.KEYCODE_DPAD_LEFT);
		controller_Planetoid_Game.addKey("Forwards", "SHIFT",   1,  -1, 1f, 1f, BeebKeys.BBCKEY_SHIFT, KeyEvent.KEYCODE_DPAD_RIGHT);
		controller_Planetoid_Game.addKey("Fire", "Return",  -1,  0, 1f, 1f, BeebKeys.BBCKEY_ENTER, KeyEvent.KEYCODE_DPAD_CENTER);
		controller_Planetoid_Game.addKey("Bomb", "Tab",  -1,  1, 1f, 1f, BeebKeys.BBCKEY_TAB, KeyEvent.KEYCODE_BACK);


		controller_Elite_Game.addKey("Left",  "<",   0,  0.8f, .75f, .75f, BeebKeys.BBCKEY_LESS_THAN, KeyEvent.KEYCODE_DPAD_LEFT);
		controller_Elite_Game.addKey("Right", ">",   1.5f,  0.8f, .75f, .75f, BeebKeys.BBCKEY_MORE_THAN, KeyEvent.KEYCODE_DPAD_RIGHT);
		controller_Elite_Game.addKey("Up",    "S",  .75f,  .5f, .75f, .75f, BeebKeys.BBCKEY_S, KeyEvent.KEYCODE_DPAD_UP);
		controller_Elite_Game.addKey("Down",  "X",  .75f,  1.25f, .75f, .75f, BeebKeys.BBCKEY_X, KeyEvent.KEYCODE_DPAD_DOWN);
		controller_Elite_Game.addKey("Fire",  "A",  -.75f,  0, .75f, 2f, BeebKeys.BBCKEY_A, KeyEvent.KEYCODE_DPAD_CENTER);
		controller_Elite_Game.addKey("Acc.",  "SPC",-1.35f,  0, .5f, 1f, BeebKeys.BBCKEY_SPACE, KeyEvent.KEYCODE_DPAD_CENTER);
		controller_Elite_Game.addKey("Dec.",  "?",  -1.35f,  1, .5f, 1f, BeebKeys.BBCKEY_QUESTIONMARK, KeyEvent.KEYCODE_DPAD_CENTER);
		controller_Elite_Game.addKey("F0",    "Launch",    0f,  0, .5f, .5f, BeebKeys.BBCKEY_F0);
		controller_Elite_Game.addKey("F1",    "Rear",     .5f,  0, .5f, .5f, BeebKeys.BBCKEY_F1);
		controller_Elite_Game.addKey("F2",    "Left",      1f,  0, .5f, .5f, BeebKeys.BBCKEY_F2);
		controller_Elite_Game.addKey("F3",    "Right",   1.5f,  0, .5f, .5f, BeebKeys.BBCKEY_F3);
		controller_Zalaga_Game.addKey("Left","Caps",    0f,  0, 1.25f, 1.25f,  BeebKeys.BBCKEY_CAPS, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BUTTON_X);
		controller_Zalaga_Game.addKey("Right","Ctrl",   1.25f,  0, 1.25f, 1.25f,  BeebKeys.BBCKEY_CTRL, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_BACK);
		controller_Zalaga_Game.addKey("Fire","Return",    -1f,  0,   1f,   1.25f,  BeebKeys.BBCKEY_ENTER, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1);
	}
	
	//
	// This section maps disk keys to built-in ControllerInfos
	//
	static Map<String, ControllerInfo> controllersForKnownDisks = new HashMap<String, ControllerInfo>();
	static {
		controllersForKnownDisks.put("arcadians", 			controller_Arcadians_Menu);
		controllersForKnownDisks.put("castle_quest", 		controller_CastleQuest_Game);
		//controllersForKnownDisks.put("chuckie_egg", 		controller_ChuckieEgg_Game);
		controllersForKnownDisks.put("chuckie_egg", 		controller_ChuckieEgg_Game_Alt);
		controllersForKnownDisks.put("dare_devil_dennis", 	controller_DareDevilDennis_Game);
		controllersForKnownDisks.put("firetrack",			controller_ZX_4way);		
		controllersForKnownDisks.put("gimpo", 				controller_ZX_4way);
		controllersForKnownDisks.put("hyper_viper", 		controller_ZX_4way);
		controllersForKnownDisks.put("elite", 				controller_Elite_Game);
		controllersForKnownDisks.put("imogen", 				controller_Imogen_Game);
		controllersForKnownDisks.put("qman", 				controller_Qman_Game);
		controllersForKnownDisks.put("planetoid", 			controller_Planetoid_Game);
		controllersForKnownDisks.put("repton1", 			controller_ZX_4way);
		controllersForKnownDisks.put("repton2", 			controller_ZX_4way);
		controllersForKnownDisks.put("repton3", 			controller_ZX_4way);
		controllersForKnownDisks.put("repton_infinity", 	controller_ZX_4way);
		controllersForKnownDisks.put("repton_thru_time", 	controller_ZX_4way);
		controllersForKnownDisks.put("ripton", 				controller_ZX_4way);
		controllersForKnownDisks.put("rocket_raid", 		controller_RocketRaid_Game);
		controllersForKnownDisks.put("snapper", 			controller_ZX_4way);
		controllersForKnownDisks.put("thrust", 				controller_Thrust_Game);
		controllersForKnownDisks.put("zalaga", 				controller_Zalaga_Game);
	}
}
