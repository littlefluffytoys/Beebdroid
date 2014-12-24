package com.littlefluffytoys.beebdroid;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;

public class AboutActivity extends Activity {
	private static final String TAG="AboutActivity";
	CheckBox box;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d(TAG, "onCreate");
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_about);		
	}
	@Override
	public void onStart() {
		super.onStart();
		final int aboutBoxStatus = UserPrefs.getAboutScreenCheckbox(this);
		
		if (aboutBoxStatus == UserPrefs.ABOUTSCREEN_FIRST_EVER) {
			// don't show the "show next time" checkbox on the splashscreen the very first time ever that we show it;
			// instead, remember that next time we will show the checkbox
			UserPrefs.setAboutScreenCheckbox(getApplicationContext(), UserPrefs.ABOUTSCREEN_SHOW);
		}
		else {
			// show the "show next time" checkbox on the splashscreen
			box = (CheckBox)findViewById(R.id.showNextTime);
			box.setVisibility(View.VISIBLE);
			box.setChecked(aboutBoxStatus == UserPrefs.ABOUTSCREEN_SHOW);
		}
		
		Button btnOK = (Button)findViewById(R.id.button_ok);
		btnOK.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (box != null) {
					UserPrefs.setAboutScreenCheckbox(v.getContext(), box.isChecked() ? UserPrefs.ABOUTSCREEN_SHOW : UserPrefs.ABOUTSCREEN_HIDE);
				}
				AboutActivity.this.finish();				
			}			
		});
	}
}
