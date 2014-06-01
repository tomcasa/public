package com.monitor;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings.Secure;
import android.util.Log;
import android.view.Menu;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

public class Monitor extends Activity {

	public static class Evt {

		public Location loc;
		public String label;
		public Date date;

	}

	static SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd,HH:mm:ss");
	private SharedPreferences pref;
	private Switch switch1;
	private Switch switch2;
	private static LocationListener locationListener;
	private static LocationManager locationManager;
	private CheckBox checkBox1;
	private CheckBox checkBox2;
	private static Date lastSave;
	private static int countEvents;
	private TextView textView1;
	private TextView editText1;
	private static Date lastData;
	private static LinkedList<Evt> listE = new LinkedList<Monitor.Evt>();
	private EditText editText2;
	private static Date startDate; 

	public String getPhoneName() {
		BluetoothAdapter myDevice = BluetoothAdapter.getDefaultAdapter();

		if (myDevice == null) {
			String android_id = Secure.getString(getContentResolver(), Secure.ANDROID_ID);
			return android_id;
		} else {
			String deviceName = myDevice.getName();
			return deviceName;
		}
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_monitor);
		
		locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

		pref = getPreferences(MODE_PRIVATE);
 
		switch2 = ((Switch) findViewById(R.id.switch2));
		switch2.setChecked(pref.getBoolean(R.id.switch2 + "", false));

		switch1 = ((Switch) findViewById(R.id.switch1));
		switch1.setChecked(pref.getBoolean(R.id.switch1 + "", false));

		checkBox1 = ((CheckBox) findViewById(R.id.checkBox1));
		checkBox1.setChecked(pref.getBoolean(R.id.checkBox1 + "", false));
		
		checkBox2 = ((CheckBox) findViewById(R.id.CheckBox01));
		checkBox2.setChecked(pref.getBoolean(R.id.CheckBox01 + "", false));

		textView1 = ((TextView) findViewById(R.id.textView1));
		
		editText1 = ((EditText) findViewById(R.id.editText1));
		editText1.setText(""+pref.getInt(R.id.editText1+ "", 0));
		
		editText2 = ((EditText) findViewById(R.id.editText2));
		editText2.setText(""+pref.getString(R.id.editText2+ "", getPhoneName()));
		
		switch1.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {				
				recordLoc();
			}
		});
 
		 recordLoc();		  
			 
	}

	@Override
	protected void onPause() {
		log("onPause\n");
		

		Editor editor = getPreferences(MODE_PRIVATE).edit();
		
		editor.putBoolean("" + R.id.CheckBox01, checkBox2.isChecked());
		editor.putBoolean("" + R.id.checkBox1, checkBox1.isChecked());
		editor.putBoolean("" + R.id.switch1, switch1.isChecked());
		editor.putBoolean("" + R.id.switch2, switch2.isChecked());
		editor.putInt("" + R.id.editText1, Integer.parseInt(""+editText1.getText()));
		editor.putString("" + R.id.editText2, ""+editText2.getText());

		editor.commit();
		super.onPause();
	}
	  
	private void makeUseOfNewLocation(final Location location) throws IOException {

		log(format.format(new Date()) + " " + location.getProvider()+ "\n");

		if ( switch2 .isChecked()){
			
			Evt evt = new Evt();
			evt.loc = location;			
			listE.add(evt);			
			lastData = new Date();
			
			checkSave();

		} else {
			log("done\n");
		}
	}

	private void checkSave() {

		
		int maxI = 20;
		
//		log("debug " + lastData + " " + lastSave + "\n");

		while (lastSave == null) {
			log("error lastSave "+ lastSave);
			lastSave = new Date(0) ;
		}
		
		if (!isOnline()) {
			log("no internet\n");
			return;
		}

		long diff = lastData.getTime() - lastSave.getTime();

		if ( listE.size() > maxI){
			
			log("doing because " + maxI + " items \n");
			trySave();

		} else if (diff > Long.parseLong("" + editText1.getText()) * 1000) {

			log("doing because " + (lastData.getTime() - lastSave.getTime()) + ">"
					+ (Integer.parseInt("" + editText1.getText()) * 1000) + "\n");

			trySave();

		} else {

			log("waiting " + (lastData.getTime() - lastSave.getTime()) + ">"
					+ (Integer.parseInt("" + editText1.getText()) * 1000) + "\n");

		}
		
	}

	private void trySave() {
		
		if (!isOnline()){
			return;
		}
		
		final LinkedList<Evt> listE2 = new LinkedList<Monitor.Evt>();
		listE2.addAll(listE);
		listE.clear();	

		log("save to drive "+listE2.size()+" items\n");
		Thread thread = new Thread(new Runnable(){
		    @Override
		    public void run() {
		        try {
					saveLoc(listE2);
					countEvents += listE2.size();
					lastSave = lastData;
		        } catch (final Exception e) {
		            textView1.post(new Runnable() {
						
						@Override
						public void run() {
							log("error "+e.getMessage()+"\n", e);
							listE.addAll(0, listE2);							
						}
					});
		        }
		    }
		});
		thread.start();

	}

	private void saveLoc(LinkedList<Evt> listE2) throws IOException {

//		KeyStore keyStore = new KeyStore()
//		String algorithm = TrustManagerFactory.getDefaultAlgorithm();
//		TrustManagerFactory tmf = TrustManagerFactory.getInstance(algorithm);
//		tmf.init(keyStore);
//
//		SSLContext context = SSLContext.getInstance("TLS");
//		context.init(null, tmf.getTrustManagers(), null);

		StringBuilder sb = new StringBuilder();

		sb.append("[");
		for (Iterator iterator = listE2.iterator(); iterator.hasNext();) {
			Evt evt = (Evt) iterator.next();
			if (evt.loc==null){

				sb.append(
				"{\"t\":\""
				+format.format(evt.date)
				+"\",\"e\":\""+evt.label
				+"\",\"x\":\""+""
				+"\",\"y\":\""+""
				+"\",\"a\":\""+""
				+"\",\"s\":\""+""
				+"\",\"ac\":\""+""
				+"\"}");
				
			}else{
				sb.append(
				"{\"t\":\""
				+format.format(new Date(evt.loc.getTime()))
				+"\",\"e\":\""+evt.loc.getProvider()
				+"\",\"x\":\""+evt.loc.getLongitude()
				+"\",\"y\":\""+evt.loc.getLatitude()
				+"\",\"a\":\""+evt.loc.getAltitude()
				+"\",\"s\":\""+evt.loc.getSpeed()
				+"\",\"ac\":\""+evt.loc.getAccuracy()
				+"\"}");
			}
			
			if (iterator.hasNext()){
				sb.append(",");
			}
		}
		sb.append("]");

		log(sb.toString()+ "\n");

//		String ut = "https://script.google.com/macros/s/AKfycbzx8IyveRdV-eaaukr8GkXZVYYI6qdT8kKv-RicUUo/dev";
		String ut = "https://script.google.com/macros/s/AKfycbwhx0tQ2KemfVBM1Zz7_Xo38RalVmxCAWhhdHQiLP1_mXMc9eM/exec";
		
		URL url = new URL(ut+"?div="+getDeviceName()+"&js="
		+URLEncoder.encode(sb.toString(), "UTF-8"));
		
		log(ut);

		if (checkBox1.isChecked()) {
			Intent browserIntent = new Intent(Intent.ACTION_VIEW).setData(Uri.parse(url.toExternalForm()));
			startActivity(browserIntent);

		} else {

			
			try {
				download(url);
			} catch (Exception e) {

				final StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				sw.append(e.getMessage()+"\n");
				e.printStackTrace(pw);
				
				textView1.post(new Runnable() {
					
					@Override
					public void run() {
						textView1.append(sw.toString() + "\n");
					}
				});

			}

		}

		textView1.post(new Runnable() {
			
			@Override
			public void run() {
				textView1.append("done\n");
			}
		});
		
	}

	private String getDeviceName() {
		
		return URLEncoder.encode(editText2.getText().toString());		
	}

	public boolean isOnline() {
	    ConnectivityManager cm =
	        (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo netInfo = cm.getActiveNetworkInfo();
	    if (netInfo != null && netInfo.isConnected()) {
	        return true;
	    }
	    return false;
	}
	
	private void download(URL url) throws IOException {
		
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
	
		boolean redirect = false;
		
		// normally, 3xx is redirect
		final int status = conn.getResponseCode();
		if (status != HttpURLConnection.HTTP_OK) {
			if (status == HttpURLConnection.HTTP_MOVED_TEMP
				|| status == HttpURLConnection.HTTP_MOVED_PERM
					|| status == HttpURLConnection.HTTP_SEE_OTHER)
			redirect = true;
		}
	 
		System.out.println("Response Code ... " + status);


		textView1.post(new Runnable() {
			
			@Override
			public void run() {
				textView1.append(status + "\n");
			}
		});

		
		if (redirect) {
	 
			// get redirect url from "location" header field
			String newUrl = conn.getHeaderField("Location");
	 
			// get the cookie if need, for login
			String cookies = conn.getHeaderField("Set-Cookie");
			conn.disconnect();

			conn = (HttpURLConnection) new URL(newUrl).openConnection();
			conn.setRequestProperty("Cookie", cookies);
			conn.addRequestProperty("Accept-Language", "en-US,en;q=0.8");
			conn.addRequestProperty("User-Agent", "Mozilla");
			conn.addRequestProperty("Referer", "google.com");
	 
			System.out.println("Redirect to URL : " + newUrl);
			

			textView1.post(new Runnable() {
				
				@Override
				public void run() {
					textView1.append("redirected\n");
				}
			});


	 
		}
	 
		InputStream in = conn.getInputStream();

		byte[] bytes = new byte[1000];
		StringBuilder x = new StringBuilder();
		int numRead = 0;
		while ((numRead = in.read(bytes)) >= 0) {
			x.append(new String(bytes, 0, numRead));
		}
		conn.disconnect();

		
	}

	private void recordLoc()  {

		log("init start events size " +listE.size()+ " count "+ countEvents);

		if (lastSave == null) {
			lastSave = new Date();
		}
		if (startDate == null) {
			startDate = new Date();
		}
		
		log("last saved " +format.format(lastSave));
		log("start record " +format.format(startDate));
		
		
		if (switch1.isChecked()){
			
			if (locationListener != null) {
				log("continue recording ..." + "\n");
				
			} else {

				
				
				log("recording ..." + "\n");
				// Define a listener that responds to location updates
				locationListener = new LocationListener() {
					public void onLocationChanged(Location location) {
						// Called when a new location is found by the network
						// location
						// provider.
						try {
							makeUseOfNewLocation(location);
						} catch (IOException e) {
							log(e.toString(),e);
						}
					}

					public void onStatusChanged(String provider, int status, Bundle extras) {
					}

					public void onProviderEnabled(String provider) {
					}

					public void onProviderDisabled(String provider) {
					}
				};

				locationManager
						.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 5 * 1000, 20, locationListener);
				Evt evt = new Evt();
				evt.label = "start";
				evt.date = new Date();
				startDate = evt.date;
				listE.add(evt);
				lastData = new Date();
				checkSave();
			}

		} else {

			if (locationListener!=null){
				log("stopped" + "\n");
				locationManager.removeUpdates(locationListener);
				locationListener = null;
				Evt evt = new Evt();
				evt.label = "stop";
				evt.date = new Date();
				lastData = new Date();
				listE.add(evt);
				trySave();

			}else{
				log("ready" + "\n");
			}
		}

	}

	private void log(String string) {
		if (!string.endsWith("\n")){
			string += "\n";
		}
		Log.i("user", string);
		textView1.append(string);
		appendLog(format.format(new Date()) + " " + string);
		
	}
	private void log(String string, Exception e) {
		Log.i("user", string);
		((TextView) findViewById(R.id.textView1)).append(string);
		appendLog(string);
		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.monitor, menu);
		return true;
	}



	@Override
	public void onDestroy() {
		Evt evt = new Evt();
		evt.label = "destroyed";
		evt.date = new Date();
		lastData = new Date();
		listE.add(evt);
		log("destroy");
		trySave();
	}


	public void appendLog(String text) {
		File root = Environment.getExternalStorageDirectory();
		File logFile = new File(root, "PeroMon.txt");
		if (!logFile.exists()) {
			try {
				logFile.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		try {
			// BufferedWriter for performance, true to set append to file flag
			BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
			buf.append(text);
//			buf.newLine();
			buf.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
 
	
}
