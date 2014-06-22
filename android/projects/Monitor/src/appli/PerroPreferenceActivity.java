package appli;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import app.PerroMon.R;

public class PerroPreferenceActivity extends PreferenceActivity  {
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {	
		super.onCreate(savedInstanceState);
		
		addPreferencesFromResource(R.xml.settings);
	}

}
