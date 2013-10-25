package com.dicon.sonar;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

public class HeadsetReceiver extends BroadcastReceiver {
	    Context c;
	    AlertDialog alertDialog = null;
	
	    public HeadsetReceiver(Context nc) {
	    	c = nc;
	    }
	    
	    @Override
	    public void onReceive(Context context, Intent intent) {

	    	if (intent.getExtras().getInt("state") == 0) {
	    		if (alertDialog != null && alertDialog.isShowing())
	    		alertDialog.cancel();
                /* HEADSET IS OR HAS BEEN DISCONNECTED */ 
        }else{ 
                /* HEADSET IS OR HAS BEEN CONNECTED */
        	alertDialog = new AlertDialog.Builder(c).create();
        	alertDialog.setTitle("Headphones warning.");
        	alertDialog.setMessage("It seems like there are headphones connected to your phone. Please remove them to make this app work correctly. Also this app may create loud and unpleasant noises, that you do not want to hear through headphones.");
        	alertDialog.setButton("Continue", new DialogInterface.OnClickListener() {
        		public void onClick(DialogInterface dialog, int id) {dialog.cancel();};
        	});
        	alertDialog.show();
        } 
	    	
	    }
}
