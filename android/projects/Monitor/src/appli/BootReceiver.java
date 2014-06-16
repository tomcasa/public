package appli;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {

	public static boolean token = false; 
	@Override
	public void onReceive(Context context, Intent intent) {

		if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {

			token = true;
			
			Intent myStarterIntent = new Intent(context, PerroMon.class);
			myStarterIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);			
			PerroMon.appendLog(Intent.ACTION_BOOT_COMPLETED.toString());	
			context.startActivity(myStarterIntent);					
			
		}
	}

}
