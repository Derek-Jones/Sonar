package com.dicon.sonar;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class SonarPreference extends PreferenceActivity {
	
	@Override
	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		getPreferenceManager().setSharedPreferencesName(measure.SHARED_PREFS_NAME);
		addPreferencesFromResource(R.xml.settings);
	}
}
