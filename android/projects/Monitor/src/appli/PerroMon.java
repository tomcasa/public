package appli;

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
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsStatus;
import android.location.GpsStatus.Listener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.provider.Settings.Secure;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.FloatMath;
import android.util.Log;
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
import android.widget.Toast;
import app.PerroMon.R;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient.ConnectionCallbacks;
import com.google.android.gms.common.GooglePlayServicesClient.OnConnectionFailedListener;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.plus.PlusClient;
import com.google.android.gms.plus.PlusClient.OnPeopleLoadedListener;
import com.google.android.gms.plus.model.people.PersonBuffer;

@SuppressLint({ "SimpleDateFormat", "DefaultLocale" })
public class PerroMon extends Activity {

	public static class Evt {

		public Location loc;
		public String label;
		public Date date;

	}
	public static class Conf {

		public boolean save;
		public boolean record;
		public boolean reqLoc;
		public boolean gpsAuto;
		public boolean details;
		public int secSave;
		public int secReqInt;
		public String devName;
 

	}

	static SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd,HH:mm:ss");
	private SharedPreferences pref;
	private Switch switchRecordOn;
	private Switch switchSaveDrive;
	private LocationListener locationListener; 
	private LocationManager locationManager;
	private CheckBox cbGPSAuto;
	private CheckBox cbDetails;
	private long lastSave = new Date().getTime();
	private int countEvents;
	private TextView textView1;
	private TextView tSaveInterSec;
	private long lastData;
	private LinkedList<Evt> listE = new LinkedList<PerroMon.Evt>();
	private EditText tDevName;
	private EditText tMaxInterval;
	private Button saveNow;
	private ScrollView scroll;
	private Button scrollLog;
	private long netRequested;
	private Switch switchRequestLoc;
	private Listener gpsStatList;
	private SensorManager sensorMan;
	private Sensor accelerometer;
	private float mAccel;
	private SensorEventListener sensorEventListener;
	private float mAccelLast;
	private float mAccelCurrent;
	private LocationListener locReqGPSListener;
	private LocationListener locReqNETListener;
	private TelephonyManager telephonyManager;
	private PhoneStateListener phoneListener;
	protected List<CellInfo> mCellInfo;
	protected int lastCellId;
	private PlusClient mPlusClient;
	private Button bSignin;
	protected Object mConnectionResult;
	private long gpsAsked;
	private long gpsRequested = 0;
	private Date startDate = new Date();
	private Conf conf= new Conf();
	private ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(1); 
	private int gpsStatus = GpsStatus.GPS_EVENT_STOPPED;
	
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
	protected void onResume() {
		super.onResume();
		
		appendLog("resume");

		if (BootReceiver.token) {
			BootReceiver.token = false;
			recreate();
		}
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		try{
		
		if (BootReceiver.token){			
//			requestWindowFeature(Window.FEATURE_NO_TITLE);							
			setTheme(R.style.Invisible);
		}else{
			setTheme(R.style.AppBaseTheme);
		}
			
		setContentView(R.layout.activity_monitor);
		 

		initGPus();
	    
	    
		locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
		gpsStatList = new Listener() {

			@Override
			public void onGpsStatusChanged(int event) {				
				if (gpsStatus != event){
					gpsStatus = event;
					log("gps status "+event);
				}				
			}
		};
		
		locationManager.addGpsStatusListener(gpsStatList);


		loadConf();
		
		switchSaveDrive = ((Switch) findViewById(R.id.switchSaveDrive));
		switchSaveDrive.setChecked(conf.save);

		switchRecordOn = ((Switch) findViewById(R.id.switchRecordOn));
		switchRecordOn.setChecked(conf.record);
		
		switchRequestLoc= ((Switch) findViewById(R.id.requestLoc));
		switchRequestLoc.setChecked(conf.reqLoc);

		cbGPSAuto = ((CheckBox) findViewById(R.id.cbGPSAuto));
		cbGPSAuto.setChecked(conf.gpsAuto);
		
		scroll = ((ScrollView) findViewById(R.id.scrollView1));
		
		cbDetails = ((CheckBox) findViewById(R.id.cbDetails));
		cbDetails.setChecked(conf.details);

		textView1 = ((TextView) findViewById(R.id.textView1));
		
		tSaveInterSec = ((EditText) findViewById(R.id.tSaveInterSec));
		tSaveInterSec.setText(""+conf.secSave);
		
		tMaxInterval = ((EditText) findViewById(R.id.tMaxInterval));
		tMaxInterval.setText(""+conf.secReqInt);
		
		tDevName = ((EditText) findViewById(R.id.tDevName));
		tDevName.setText(""+conf.devName);

		switchRecordOn.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {				
				recordLoc();
				check();
				detectMov();
			}
		});

		cbGPSAuto.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {				
				detectMov();
				
			}
		});
		

		switchRequestLoc.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {				
				check();
			}
		});

		saveNow = ((Button) findViewById(R.id.saveNow));
		saveNow.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				trySave();
				
			}
		});

		scrollLog = ((Button) findViewById(R.id.scrollLog));
		scrollLog.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				scroll.fullScroll(View.FOCUS_DOWN);				
			}
		});
 
		if (cbDetails.isChecked()){
			log("init (p" +listE.size()+ ") (t"+ countEvents+")");
			log("root " +isTaskRoot());
			log("init start events size " +listE.size()+ " count "+ countEvents);
			if (countEvents > 0){
				log("start date " + format.format(startDate));
				if (lastSave!=0);			
					log("last saved " + format.format(lastSave));
			}
		}

		

		if (locReqNETListener == null) {
			locReqNETListener = new LocationListener() {
				public void onLocationChanged(Location location) {
 
					netRequested = 0;
					
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
		}
		if (locReqGPSListener == null) {
			locReqGPSListener = new LocationListener() {
				public void onLocationChanged(Location location) {
  				
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
			}

			telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
			phoneListener = new PhoneStateListener() {

				@Override
				public void onCellInfoChanged(java.util.List<android.telephony.CellInfo> cellInfo) {
					mCellInfo = cellInfo;
					if (mCellInfo != null) {
						for (Iterator<CellInfo> iterator = cellInfo.iterator(); iterator.hasNext();) {

							CellInfoGsm cellInfoGsm = (CellInfoGsm) iterator.next();

							if (cbDetails.isChecked()) {
								log("cell ID " + cellInfoGsm.getCellIdentity());
							}
						}
					}
					super.onCellInfoChanged(cellInfo);
				};

				@Override
				public void onCellLocationChanged(CellLocation location) {

					GsmCellLocation gsmCellLocation = (GsmCellLocation) location;
					
					lastCellId = gsmCellLocation.getCid();
					if (cbDetails.isChecked()) {
						log("cellp " + gsmCellLocation.getCid() + "," + gsmCellLocation.getLac() + ","
								+ gsmCellLocation.getPsc());
					}
						
					super.onCellLocationChanged(location);
				}

			};
			telephonyManager.listen(phoneListener, PhoneStateListener.LISTEN_CELL_INFO);
			telephonyManager.listen(phoneListener, PhoneStateListener.LISTEN_CELL_LOCATION);

			recordLoc();
			check();	
		detectMov();
		
		if (BootReceiver.token){		 
			Intent startMain = new Intent(Intent.ACTION_MAIN);
			startMain.addCategory(Intent.CATEGORY_HOME);
			startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(startMain);
						
		}
		
		}catch (Exception e){
			log(e.getLocalizedMessage(),e);
		}
		

	} 

	private void initGPus() {


		bSignin = ((Button) findViewById(R.id.Signin));
		bSignin.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				try{
					mPlusClient.connect();
				}catch(Exception e){
					log(e.toString(),e);
				}
			}
		});		
		
		
		
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO) {
			log("build " + Build.VERSION.SDK_INT +"<"+ Build.VERSION_CODES.FROYO);
		    return;
		}
		
		ConnectionCallbacks cb = new ConnectionCallbacks() {
			
			@Override
			public void onDisconnected() {
		        Toast.makeText(PerroMon.this, "Disconnected", Toast.LENGTH_LONG).show();
				
			}
			
			@Override
			public void onConnected(Bundle arg0) {

		        String accountName = mPlusClient.getAccountName();
		        Toast.makeText(PerroMon.this, accountName + " is connected.", Toast.LENGTH_LONG).show();

		        mPlusClient.loadPeople(new OnPeopleLoadedListener() {
					
					@Override
					public void onPeopleLoaded(ConnectionResult status, PersonBuffer personBuffer, String nextPageToken) {

				        Toast.makeText(PerroMon.this, personBuffer.get(0).getDisplayName()+ " is connected.", Toast.LENGTH_LONG).show();
						
					}
				}, "me");
				
			}
		};
 
	    
		OnConnectionFailedListener cb2 = new OnConnectionFailedListener() {
			
			@Override
			public void onConnectionFailed(ConnectionResult result) {
				   if (result.hasResolution()) {

				        Toast.makeText(PerroMon.this, "hasResolution", Toast.LENGTH_LONG).show();
			            try {
			            	int REQUEST_CODE_RESOLVE_ERR = 9000;
			            	result.startResolutionForResult(PerroMon.this, REQUEST_CODE_RESOLVE_ERR);
			            } catch (SendIntentException e) {

					        Toast.makeText(PerroMon.this, "reconnect", Toast.LENGTH_LONG).show();
			                mPlusClient.connect();
			            }
			        }
			        mConnectionResult = result;				
			}
		};
		
	    mPlusClient = new PlusClient.Builder(this, cb, cb2)
//    	.setActions(Scopes.PROFILE)
//    	.setActions(Scopes.PLUS_ME)
	    	.setActions("http://schemas.google.com/AddActivity", "http://schemas.google.com/BuyActivity")
	        .build();

	    

		
	}




	@SuppressLint("FloatMath")
	private void detectMov() {

		if (cbGPSAuto.isChecked() && switchRecordOn.isChecked()){
			
		
		sensorMan = (SensorManager) getSystemService(SENSOR_SERVICE);
		accelerometer = sensorMan.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		mAccel = 0.00f;
		mAccelCurrent = SensorManager.GRAVITY_EARTH;
		mAccelLast = SensorManager.GRAVITY_EARTH;
		
		sensorEventListener = new SensorEventListener() {
			
			private float[] mGravity;
			

			@Override
			public void onSensorChanged(SensorEvent event) {
				if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
			        mGravity = event.values.clone();
			        // Shake detection
			        float x = mGravity[0];
			        float y = mGravity[1];
			        float z = mGravity[2];
			        mAccelLast = mAccelCurrent;
			        mAccelCurrent = FloatMath.sqrt(x*x + y*y + z*z);
			        float delta = mAccelCurrent - mAccelLast;
			        mAccel = mAccel * 0.9f + delta;
			            // Make this higher or lower according to how much
			            // motion you want to detect

					movDetected();
			    }
			}
			
			@Override
			public void onAccuracyChanged(Sensor sensor, int accuracy) { 
				
			}
		};
		sensorMan.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);

		}else{
			
			if(sensorEventListener != null){
				sensorMan.unregisterListener(sensorEventListener);
				gpsAsked = 0;				
				movDetected();
				sensorEventListener = null;
			}
		}
	}



	final static SimpleDateFormat hm = new SimpleDateFormat("hh:mm");
	String hmCurr;
	private int hmMovSum;

	protected void movDetected() {

		int limMov = 450;
		Date nowd = new Date();
		if (hm.format(nowd).equals(hmCurr)) {
			if (cbGPSAuto.isChecked()) {
				if (mAccel > 1){
					hmMovSum += mAccel;
					if(cbDetails.isChecked()){
						log("mov sum " + hmMovSum + " + " + (int) mAccel + " >? " + limMov);
					}
				}
			}
		} else {
			hmMovSum = 0;
			hmCurr = hm.format(nowd);
		}

		
		if (hmMovSum >= limMov ) {
			gpsAsked = new Date().getTime() + 1000 * 60 * 3;
		}
		
		
		if (gpsAsked < nowd.getTime() && gpsRequested >0) {
				log("cancel GPS requests");
				locationManager.removeUpdates(locReqGPSListener);
				gpsRequested = 0;
		} 
		
		if (gpsAsked > nowd.getTime() && gpsRequested == 0) {
			log("request GPS location (mov detected "+hmMovSum+")");
			gpsRequested = nowd.getTime() ;
			locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 20,
					locReqGPSListener, Looper.getMainLooper());
		}
	} 
	

	
	private void loadConf() {
		
		pref = getPreferences(MODE_PRIVATE);
		conf.save = pref.getBoolean(R.id.switchSaveDrive + "", false);
		conf.record = pref.getBoolean(R.id.switchRecordOn + "", false);
		conf.reqLoc = pref.getBoolean(R.id.requestLoc + "", false);
		conf.gpsAuto = pref.getBoolean(R.id.cbGPSAuto + "", false);
		conf.details = pref.getBoolean(R.id.cbDetails + "", false);
		conf.secSave = pref.getInt(R.id.tSaveInterSec+ "", 0);
		conf.secReqInt = pref.getInt(R.id.tMaxInterval+ "", 0);
		conf.devName= pref.getString(R.id.tDevName+ "", getPhoneName());
		
	}

	private void check() {
		

		int netTimeout = 4;
		long now = new Date().getTime();
		
		if (!switchRecordOn.isChecked() || !switchRequestLoc.isChecked()){

//			gpsRequested = now + 1000 * 60 * gpsTimeout;
//			netRequested = now + 1000 * 60 * gpsTimeout; 
			gpsRequested = 0;
			netRequested = 0;
			gpsAsked = 0;

			locationManager.removeUpdates(locReqNETListener);
			locationManager.removeUpdates(locReqGPSListener);
			
			return;
		}

		long max = Long.parseLong("" + tMaxInterval.getText());
		if (max < 5)
			max = 5;

		if (cbDetails.isChecked()){
			log("check each " + max + "s (p" + listE.size()+")");
		}
		
		if ((now - lastData) > ((max) * 1000)) {
			if (netRequested != 0 && netRequested < now) {
				log("cancel NET requests");
				locationManager.removeUpdates(locReqNETListener);
				netRequested = 0;
			} else if (netRequested < now){
				log("request NET location");
				netRequested = now + 1000 * 60 * netTimeout;  
				locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, locReqNETListener,
						Looper.getMainLooper());
			} else{
				log("waiting NET location");
			}
		}
		
		if (switchRequestLoc.isChecked()) {
//			log("schedule checkMaxInterval");
			pool.schedule(new Runnable() {

				@Override
				public void run() {
					runOnUiThread(new Runnable() {						
						@Override
						public void run() {
							check();							
						}
					});
				}
			}, (max * 1000 + 500), TimeUnit.MILLISECONDS);
		}
		
	}


	@Override
	protected void onPause() {
		try{
			if (cbDetails.isChecked()){
				log("paused");
			}
	
			Editor editor = getPreferences(MODE_PRIVATE).edit();
			
			editor.putBoolean("" + R.id.cbDetails, cbDetails.isChecked());
			editor.putBoolean("" + R.id.cbGPSAuto, cbGPSAuto.isChecked());
			editor.putBoolean("" + R.id.switchRecordOn, switchRecordOn.isChecked());
			editor.putBoolean("" + R.id.requestLoc, switchRequestLoc.isChecked());		
			editor.putBoolean("" + R.id.switchSaveDrive, switchSaveDrive.isChecked());
			try{ editor.putInt("" + R.id.tSaveInterSec, Integer.parseInt(""+tSaveInterSec.getText())); }catch(Exception e){}		
			try{ editor.putInt("" + R.id.tMaxInterval, Integer.parseInt(""+tMaxInterval.getText())); }catch(Exception e){}
			editor.putString("" + R.id.tDevName, ""+tDevName.getText());
	
			editor.commit();
		} catch (Exception e) {
			log(e.getLocalizedMessage(),e);
		}
		super.onPause();
	}

	@SuppressLint("DefaultLocale")
	private void makeUseOfNewLocation(final Location location) throws IOException {

		if (switchSaveDrive.isChecked()) {

			if (lastData == location.getTime()){
				return;
			}
			
//			List<NeighboringCellInfo> neighboringCellInfos = telephonyManager.getNeighboringCellInfo();
//			log("cell info " + neighboringCellInfos.size());
//			for(NeighboringCellInfo neighboringCellInfo : neighboringCellInfos)
//			{
//			    neighboringCellInfo.getCid();
//			    neighboringCellInfo.getLac();
//			    neighboringCellInfo.getPsc();
//			    neighboringCellInfo.getNetworkType();
//			    neighboringCellInfo.getRssi();
//
//			    log("cell info " + neighboringCellInfo.toString());
//			}
			
			Evt evt = new Evt();
			evt.loc = location;
			listE.add(evt);
			lastData = location.getTime();
			log("get " + location.getProvider().toUpperCase() + " loc " + format.format(new Date()).substring(11) + " (p" + listE.size() + ")");

			checkSave();
		} else {
			log("save disabled");
		}
	}

	private void checkSave() {

		int maxI = 20;
		 
		
		if (!isOnline()) {
			if (cbDetails.isChecked()){
				log("waiting internet");
			}
			return;
		}

		long diff = lastData - lastSave;

		long st = Long.parseLong("" + tSaveInterSec.getText());
		if (st<60)
			st =60;
		
		if ( listE.size() > maxI){
			
			log("saving because " + maxI + " items \n");
			trySave();

		} else if (diff > st * 1000) {

			log("saving because " + (int)((lastData - lastSave)/1000) + ">"
					+ (st) + "\n");

			trySave();

		} else {

			if (cbDetails.isChecked()) {
				log("waiting " + (lastData - lastSave) / 1000 + ">" + st + "\n");
			}				
		}		
	}

	private void trySave() {
		
		if (!isOnline() || listE.size() == 0){
			return;
		}
		
		int end = listE.size() < 30 ? listE.size() : 30; 
		final LinkedList<Evt> listE2 = new LinkedList<PerroMon.Evt>();
		listE2.addAll(listE.subList(0, end));
		listE.removeAll(listE2);

		log("saving to drive " + listE2.size() + " items\n");
		pool.schedule(new Runnable() {
			@Override
			public void run() {
				try {
					saveLoc(listE2);
					countEvents += listE2.size();
					lastSave = lastData;
				} catch (final Exception e) {
					// log("error "+e.getMessage()+"\n", e);
					String m = e.getMessage();
					log("not saved " + (m.length() > 60 ? "" : m));
					listE.addAll(0, listE2);
				}

				if (listE.size() > 0) {
					checkSave();
				}

			}
		}, 0, TimeUnit.SECONDS);

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
		for (Iterator<Evt> iterator = listE2.iterator(); iterator.hasNext();) {
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
				+"\",\"a\":\""+String.format("%.2f", evt.loc.getAltitude())
				+"\",\"s\":\""+String.format("%.1f", evt.loc.getSpeed())
				+"\",\"ac\":\""+String.format("%.1f", evt.loc.getAccuracy())
				+"\"}");
			}
			
			if (iterator.hasNext()){
				sb.append(",");
			}
		}
		sb.append("]");

//		log(sb.toString()+ "\n");

//		String ut = "https://script.google.com/macros/s/AKfycbzx8IyveRdV-eaaukr8GkXZVYYI6qdT8kKv-RicUUo/dev";
		String ut = "https://script.google.com/macros/s/AKfycbwhx0tQ2KemfVBM1Zz7_Xo38RalVmxCAWhhdHQiLP1_mXMc9eM/exec";
		
		URL url = new URL(ut+"?div="+getDeviceName()+"&js="
		+URLEncoder.encode(sb.toString(), "UTF-8"));
		
//		log(ut);

		
//			Intent browserIntent = new Intent(Intent.ACTION_VIEW).setData(Uri.parse(url.toExternalForm()));
//			startActivity(browserIntent);

			 
		download(url);

		log(listE2.size()+" items saved to GDrive");
		
	}

	@SuppressWarnings("deprecation")
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
	 
//		System.out.println("Response Code ... " + status);

		if (status!=200){
			log("HTTP CODE "+status);
		}
		
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
	 
//			System.out.println("Redirect to URL : " + newUrl);
			

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

		
		if (switchRecordOn.isChecked()){
			
			if (locationListener != null) {
				log("continue recording ..." + "\n");
				
			} else {
				
				log("start recording ..." + "\n");
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

				locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 5 * 1000, 20, locationListener);
				Evt evt = new Evt();
				evt.label = "start";
				evt.date = new Date();
				startDate = evt.date;
				listE.add(evt);
				lastData = new Date().getTime();
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
				lastData = new Date().getTime();
				listE.add(evt);
				trySave();

			}else{
				log("ready" + "\n");
			}
		}

	}

	private void log(final String string) {
		appendLog(string);
		
		runOnUiThread(new Runnable() {
			
			@Override
			public void run() {
				String d = format.format(new Date());
				String s = textView1.getText().toString();
				if (s.length()>5000){
					s = s.substring(s.length()-3000);
					s = s.substring(s.indexOf("\n"));
					textView1.setText(s);
				}
				
				if (string.endsWith("\n")){			
					textView1.append(d.substring(11) + " "+ string);	
				}else{					
					textView1.append(d.substring(11) + " " + string+ "\n");						
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

	
//	@Override
//	public boolean onCreateOptionsMenu(Menu menu) {
//		// Inflate the menu; this adds items to the action bar if it is present.
//		getMenuInflater().inflate(R.menu.monitor, menu);
//		return true;
//	}



	@Override
	public void onDestroy() {
		appendLog("onDestroy");
		super.onDestroy();		
	}

	@Override
	public void onStop() {
		appendLog("onStop");
		super.onStop();		
	}

	
//		Evt evt = new Evt();
//		evt.label = "destroyed";
//		evt.date = new Date();
//		lastData = new Date();
//		listE.add(evt);
//		log("destroy");
//		trySave();
		

	public static void appendLog(String text) {

		String d = format.format(new Date());
		if (text.endsWith("\n")) {
			text = d + " " + text;
		} else {
			text = d + " " + text + "\n";
		}
		
		File root = Environment.getExternalStorageDirectory();		
		File logFile = new File(root, "PeroMon.txt");
		if (logFile.length()>5*1000*1000){
			File logFile2 = new File(root, "PeroMon.2.txt");
			if (logFile2.exists()){
				logFile2.delete();
			}
			logFile.renameTo(logFile2);
		}
			
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
 
	@Override
	public void onBackPressed() {
//		
//	    new AlertDialog.Builder(this)
//	        .setIcon(android.R.drawable.ic_dialog_alert)
//	        .setTitle("Closing Activity")
//	        .setMessage("Are you sure you want to close this activity?")
//	        .setPositiveButton("Yes", new DialogInterface.OnClickListener(){
//	        @Override
//	        public void onClick(DialogInterface dialog, int which) {
//	        	
//	        	locationManager.removeGpsStatusListener(gpsStatList);
//	        	if (locReqGPSListener!=null)
//	        		locationManager.removeUpdates(locReqGPSListener);
//	        	if (locReqNETListener!=null)
//	        		locationManager.removeUpdates(locReqNETListener);
//	        	if (locationListener!=null)
//	        		locationManager.removeUpdates(locationListener);        	
//	        	
//	            finish();   
//	            
//	        }
//
//	    })
//	    .setNegativeButton("No", null)
//	    .show();
	}
	
	
	
}
