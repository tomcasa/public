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
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.location.GpsStatus;
import android.location.GpsStatus.Listener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.provider.Settings.Secure;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ScrollView;
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
	private Switch switchRecordOn;
	private Switch switchSaveDrive;
	private static LocationListener locationListener;
	private static LocationManager locationManager;
	private CheckBox cbGPSAuto;
	private CheckBox cbDetails;
	private static Date lastSave;
	private static int countEvents;
	private TextView textView1;
	private TextView tSaveInterSec;
	private static Date lastData;
	private static LinkedList<Evt> listE = new LinkedList<Monitor.Evt>();
	private EditText tDevName;
	private EditText tMaxInterval;
	private Button saveNow;
	private ScrollView scroll;
	private Button clearLog;
	private static long gpsRequested = 0;
	private static Date startDate;
	private static ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(1); 
	private static int gpsStatus = -1;
	
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
		locationManager.addGpsStatusListener(new Listener() {

			@Override
			public void onGpsStatusChanged(int event) {
				gpsStatus = event;
			}
		});

		pref = getPreferences(MODE_PRIVATE);
 
		switchSaveDrive = ((Switch) findViewById(R.id.switchSaveDrive));
		switchSaveDrive.setChecked(pref.getBoolean(R.id.switchSaveDrive + "", false));

		switchRecordOn = ((Switch) findViewById(R.id.switchRecordOn));
		switchRecordOn.setChecked(pref.getBoolean(R.id.switchRecordOn + "", false));

		cbGPSAuto = ((CheckBox) findViewById(R.id.cbGPSAuto));
		cbGPSAuto.setChecked(pref.getBoolean(R.id.cbGPSAuto + "", false));
		
		scroll = ((ScrollView) findViewById(R.id.scrollView1));
		
		cbDetails = ((CheckBox) findViewById(R.id.cbDetails));
		cbDetails.setChecked(pref.getBoolean(R.id.cbDetails + "", false));

		textView1 = ((TextView) findViewById(R.id.textView1));
		
		tSaveInterSec = ((EditText) findViewById(R.id.tSaveInterSec));
		tSaveInterSec.setText(""+pref.getInt(R.id.tSaveInterSec+ "", 0));
		
		tMaxInterval = ((EditText) findViewById(R.id.tMaxInterval));
		tMaxInterval.setText(""+pref.getInt(R.id.tMaxInterval+ "", 0));
		
		tDevName = ((EditText) findViewById(R.id.tDevName));
		tDevName.setText(""+pref.getString(R.id.tDevName+ "", getPhoneName()));
		
		switchRecordOn.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {				
				recordLoc();
			}
		});
		
		tMaxInterval.addTextChangedListener(new TextWatcher() {
			
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {				
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}
			
			@Override
			public void afterTextChanged(Editable s) {
				if (s.length() > 0){
					checkMaxInterval();
				}
			}
		});

		saveNow = ((Button) findViewById(R.id.saveNow));
		saveNow.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				trySave();
				
			}
		});

		clearLog = ((Button) findViewById(R.id.clearLog));
		clearLog.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				textView1.setText("");				
			}
		});
 
		 recordLoc();		  
		 
		 checkMaxInterval();
			 
		 
	} 

	private void checkMaxInterval() {
		if (!switchRecordOn.isChecked())
			return;

		long max = Long.parseLong("" + tMaxInterval.getText());
		log("checkMaxInterval for " + max + " pending " + listE.size());

		long now = new Date().getTime();
		if ((now - lastData.getTime()) > (max * 1000)) {
			if (GpsStatus.GPS_EVENT_SATELLITE_STATUS == gpsStatus && gpsRequested < now) {
				log("request GPS location");
				gpsRequested = now + 1000*60*5; //5 min wait  
				locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, locationListener, Looper.getMainLooper());
			}else{
				log("request NET location");
				locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, locationListener, Looper.getMainLooper());
			}
		}

		if (max > 5) {
//			log("schedule checkMaxInterval");
			pool.schedule(new Runnable() {

				@Override
				public void run() {
					saveNow.post(new Runnable() {						
						@Override
						public void run() {
							checkMaxInterval();							
						}
					});
				}
			}, (max * 1000 + 500), TimeUnit.MILLISECONDS);
		}
		
	}

	@Override
	protected void onPause() {
		log("onPause\n");
		

		Editor editor = getPreferences(MODE_PRIVATE).edit();
		
		editor.putBoolean("" + R.id.cbDetails, cbDetails.isChecked());
		editor.putBoolean("" + R.id.cbGPSAuto, cbGPSAuto.isChecked());
		editor.putBoolean("" + R.id.switchRecordOn, switchRecordOn.isChecked());
		editor.putBoolean("" + R.id.switchSaveDrive, switchSaveDrive.isChecked());
		editor.putInt("" + R.id.tSaveInterSec, Integer.parseInt(""+tSaveInterSec.getText()));
		editor.putInt("" + R.id.tMaxInterval, Integer.parseInt(""+tMaxInterval.getText()));
		editor.putString("" + R.id.tDevName, ""+tDevName.getText());

		editor.commit();
		super.onPause();
	}

	private void makeUseOfNewLocation(final Location location) throws IOException {

		if (LocationManager.GPS_PROVIDER.equals(location.getProvider())) {
			gpsRequested = 0;
		}

		if (switchSaveDrive.isChecked()) {

			Evt evt = new Evt();
			evt.loc = location;
			listE.add(evt);
			lastData = new Date();
			log(format.format(new Date()) + " " + location.getProvider() + " " + listE.size() + "\n");

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

		} else if (diff > Long.parseLong("" + tSaveInterSec.getText()) * 1000) {

			log("doing because " + (lastData.getTime() - lastSave.getTime()) + ">"
					+ (Integer.parseInt("" + tSaveInterSec.getText()) * 1000) + "\n");

			trySave();

		} else {

			log("waiting " + (lastData.getTime() - lastSave.getTime()) / 1000 + ">"
					+ (Integer.parseInt("" + tSaveInterSec.getText())) + "\n");

		}
		
	}

	private void trySave() {
		
		if (!isOnline() || listE.size() == 0){
			return;
		}
		
		int end = listE.size() < 30 ? listE.size() : 30; 
		final LinkedList<Evt> listE2 = new LinkedList<Monitor.Evt>();
		listE2.addAll(listE.subList(0, end));
		listE.removeAll(listE2);

		log("save to drive "+listE2.size()+" items\n");
		pool.schedule(new Runnable(){
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
		},0,TimeUnit.SECONDS);		
		
		if(listE.size()>0){
			trySave();
		}

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

		
//			Intent browserIntent = new Intent(Intent.ACTION_VIEW).setData(Uri.parse(url.toExternalForm()));
//			startActivity(browserIntent);

			 
		download(url);

		log("done");
	}

	private String getDeviceName() {
		
		return URLEncoder.encode(tDevName.getText().toString());		
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


		log(""+status);
		
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
			

			log("redirected");
	 
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
		
		
		if (switchRecordOn.isChecked()){
			
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

	private void log(final String string) {
		
		textView1.post(new Runnable() {
			
			@Override
			public void run() {
				if (string.endsWith("\n")){			
					textView1.append(string);	
					appendLog(format.format(new Date()) + " " + string);
				}else{					
					textView1.append(string+ "\n");						
					appendLog(format.format(new Date()) + " " + string+ "\n");
				}
				Log.i("user", string);		
				scroll.fullScroll(View.FOCUS_DOWN);									
			}
		});

	}

	private void log(String string, Exception e) {

		final StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		sw.append(e.getMessage() + "\n");
		e.printStackTrace(pw);
		log(sw.toString());
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
