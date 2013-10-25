package com.dicon.sonar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;

public class measure extends Activity implements OnClickListener, SharedPreferences.OnSharedPreferenceChangeListener {
	public final static String SHARED_PREFS_NAME="sonarsettings";
    sonicPing sp;
    GraphView gv;
    CheckBox cb;
    AudioManager am;
    String unit = "m";
    float SoS = 340.f;
    boolean contMode = false;
    boolean pinging = false;
    Button button;
    HeadsetReceiver hr;
    
    private final Handler mHandler = new Handler();
    private final Runnable contPing = new Runnable() {
		public void run() {
			//Log.d("sonar", "contPing.run()");
			float[] res = sp.ping();
			if (res == null) {
				if (sp.error == -5) {
					dieWithError("Recording failed. (contPing), recRes = "+sp.error_detail);
				} else if (sp.error == -6) {
					dieWithError("startRecording failed. (contPing). Maybe the mic is already in use? If not:");
				} else
					dieWithError("Unknown error in ping(). (contPing), error = " + sp.error_detail + ", detail = " + sp.error_detail);
				return;
			}
    		gv.setGraph(res, sp.getResultSize(), sp.getMinRange(), sp.getMaxRange(), sp.getLastDistance());
			mHandler.removeCallbacks(contPing);
			if (pinging) {
				mHandler.postDelayed(contPing, 100);
			}
		}
	};
/*	private final Runnable autoKill = new Runnable() {
		public void run() {
			//Log.d("sonar", "autoKill.run()");
			dieWithError("Automatic self-termination. Hope this works :)");
		}
	};*/
    
    private SharedPreferences mPrefs;
	
    @Override
    public void onConfigurationChanged(Configuration config) {
    	super.onConfigurationChanged(config);
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	//Log.d("sonar", "onCreate()");
        super.onCreate(savedInstanceState);
        
        hr = new HeadsetReceiver(measure.this); /* replace this by your receiver class */ 
        IntentFilter inf = new IntentFilter(); 
        inf.addAction("android.intent.action.HEADSET_PLUG"); 
        registerReceiver(hr, inf); 
        
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        
//Create sp from settings
//        sp = new sonicPing();
        am = ((AudioManager)this.getSystemService(AUDIO_SERVICE));
        
        
        setContentView(R.layout.main);
        
        button = (Button)findViewById(R.id.PingButton);
        button.setOnClickListener(this);
        
        gv = (GraphView)findViewById(R.id.SinglePingSurface);
        cb = (CheckBox)findViewById(R.id.CheckMaxVol);
        
        mPrefs = measure.this.getSharedPreferences(SHARED_PREFS_NAME, 0);
		mPrefs.registerOnSharedPreferenceChangeListener(this);
		onSharedPreferenceChanged(mPrefs, null);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	//Log.d("sonar", "onCreateOptionsMenu()");
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	//Log.d("sonar", "onCreateOptionsItemSelected()");
        switch (item.getItemId()) {
        case R.id.explanations:
            startActivity(new Intent(measure.this, explanations.class));
            return true;
        case R.id.settings:
            startActivity(new Intent(measure.this, SonarPreference.class));
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
    
    public void onClick(View v) {
    	//Log.d("sonar", "onClick)");
//    	mHandler.postDelayed(autoKill, 5000);
    	int oldVolume = -100;
    	if ((!contMode || !pinging) && cb.isChecked()) {
    		oldVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
    		am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
    	}
    	if (!contMode) {
    		float[] res = sp.ping();
    		if (res == null) {
				if (sp.error == -5) {
					dieWithError("Recording failed. (singlePing), recRes = "+sp.error_detail);
				} else if (sp.error == -6) {
					dieWithError("startRecording failed. (singlePing). Maybe the mic is already in use? If not:");
				} else
					dieWithError("Unknown error in ping(). (singlePing), error = " + sp.error_detail + ", detail = " + sp.error_detail);
				return;
			}
    		gv.setGraph(res, sp.getResultSize(), sp.getMinRange(), sp.getMaxRange(), sp.getLastDistance());
    	}
    	if ((!contMode || pinging) && oldVolume > -100)
    		am.setStreamVolume(AudioManager.STREAM_MUSIC, oldVolume, 0);
    	if (contMode) {
    		if (pinging) { //Stop it
    			pinging = false;
    			mHandler.removeCallbacks(contPing);
    			button.setText("Ping");
    		} else { //Start i
    			pinging = true;
    			mHandler.postDelayed(contPing, 100);
    			button.setText("Stop");
    		}
    	}
    }
    
    private void dieWithError(String error) {
    	//Log.d("sonar", "dieWithError()");
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(error + "\nPlease report this error and your device to DiConX@gmail.com");
		builder.setCancelable(false);
		builder.setNeutralButton("OK", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		                measure.this.finish();
		           }
		       });
		AlertDialog x = builder.create();
		x.show();
    }
    
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
    	//Log.d("sonar", "onSharedPreferenceChanged()");
    	if (prefs.getBoolean("show_previous", true))
    		gv.showPrev = 4;
    	else
    		gv.showPrev = 0;
    	if (prefs.getBoolean("continuous", false)) {
    		contMode = true;
    		sp = new sonicPing(1, 100, 3000, 2000, 250, 2);
    	} else {
    		contMode = false;
    		sp = new sonicPing();
    	}
    	
    	if (sp == null) {
    		dieWithError("The class sonicPing could not be created.");
    		return;
    	}
    	
    	if (sp.error < 0) {
    		switch (sp.error) {
    		case -1:	dieWithError("No suitable frequency found.");
    					return;
    		case -2:	dieWithError("Could not create Audio Track.");
						return;
    		case -3:	dieWithError("Could not fill Audio Track.");
						return;
    		case -4:	dieWithError("Could not create AudioRecord.");
						return;
    		}
    	}
    	
    	unit = prefs.getString("units", "m");
    	try {
    		SoS = Float.valueOf(prefs.getString("SoS", "340"));
    	} catch (Exception e) {
    		SoS = 340.f;
    	}
    	gv.setUnit(unit);
    	sp.setDistFactor(unit, SoS);
    	
    	pinging = false;
    	button.setText("Ping");
    	mHandler.removeCallbacks(contPing);
    	
    	sp.setCamMic(prefs.getBoolean("camMic", true));
	}
    
    public void onDestroy() {
    	//Log.d("sonar", "onDestroy()");
    	super.onDestroy();
        unregisterReceiver(hr); 
    	pinging = false;
    	button.setText("Ping");
    	mHandler.removeCallbacks(contPing);
    	mPrefs.unregisterOnSharedPreferenceChangeListener(this);
    }
    
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
    	//Log.d("sonar", "onWindowsFocusChanged()");
    	if (!hasFocus) {
        	pinging = false;
        	button.setText("Ping");
        	mHandler.removeCallbacks(contPing);
    	}
    }
}