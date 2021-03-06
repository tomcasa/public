package appli;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
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
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.telephony.CellInfo;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.FloatMath;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import app.PerroMon.R;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient.ConnectionCallbacks;
import com.google.android.gms.common.GooglePlayServicesClient.OnConnectionFailedListener;
import com.google.android.gms.location.ActivityRecognitionClient;
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
		public boolean asServ;
 

	}

	private final String keyDevNam = "deviceName";
	private final String saveIntervalSec = "saveIntervalSec";
	private final String reqIntervalSec = "reqIntervalSec";
	
	
	private SharedPreferences pref;
	private Switch switchRecordOn; 
	private LocationListener locationListener; 
	private LocationManager locationManager;
	private CheckBox cbGPSAuto;
	private CheckBox cbDetails;
	private long lastSave = new Date().getTime();
	private int countEvents;
	private TextView textView1; 
	private long lastData;
	private LinkedList<Evt> listE = new LinkedList<PerroMon.Evt>(); 
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
	protected Object mConnectionResult;
	private long gpsAsked;
	private long gpsRequested = 0;
	private Date startDate = new Date();
	private Conf conf= new Conf();
	private ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(1); 
	private int gpsStatus = GpsStatus.GPS_EVENT_STOPPED;
	protected String accountName;
	private Button btShare;
	private SharedPreferences sharedPrefs;
	private String android_id;
	private ActivityRecognitionClient mActivityRecognitionClient;
	private Switch switchServiceOn;
	private LinkedList<String> dataFiles;
	public static PerroMon instance;

	
	
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
		 

		instance = this;

		
		
		initGPus();

		actReco();
	    
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
		 

		switchRecordOn = ((Switch) findViewById(R.id.switchRecordOn));
		switchRecordOn.setChecked(conf.record);
		
		switchServiceOn = ((Switch) findViewById(R.id.switchService));
		switchServiceOn.setChecked(conf.asServ);

		
		switchRequestLoc= ((Switch) findViewById(R.id.requestLoc));
		switchRequestLoc.setChecked(conf.reqLoc);

		cbGPSAuto = ((CheckBox) findViewById(R.id.cbGPSAuto));
		cbGPSAuto.setChecked(conf.gpsAuto);
		
		scroll = ((ScrollView) findViewById(R.id.scrollView1));
		
		cbDetails = ((CheckBox) findViewById(R.id.cbDetails));
		cbDetails.setChecked(conf.details);

		textView1 = ((TextView) findViewById(R.id.textView1));
		

		
		btShare = ((Button) findViewById(R.id.btShare));
		btShare.setOnClickListener(new OnClickListener() {
			

			@Override
			public void onClick(View v) {
				Intent sendIntent = new Intent();
				sendIntent.setAction(Intent.ACTION_SEND);				
				//https://googledrive.com/host/0B1jHAHa6ZW9tekRpQVdCLUdJdFU/GPSmap.html#date=2014-06-21&device=Note%20Tom&acc=50&u=tomcasa@gmail.com
				sendIntent.putExtra(Intent.EXTRA_TEXT, "View your recorded GPS logs on this URL: \n"
						+ "https://googledrive.com/host/0B1jHAHa6ZW9tekRpQVdCLUdJdFU/GPSmap.html#device="+URLEncoder.encode(sharedPrefs.getString(keyDevNam, "null"))+"&u="+accountName);
				sendIntent.setType("text/plain");
				startActivity(Intent.createChooser(sendIntent, "Link to see your records..."));
			}
		});


		switchRecordOn.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				
				if (isChecked){
					connectForStart();
				}else{
					startRecord();
				}
			}
		});
		
		if (conf.asServ){
			log("start service at startup");
			Intent intent = new Intent(PerroMon.this, PlayerService.class);
			startService(intent);
		}

		switchServiceOn.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

				Intent intent = new Intent(PerroMon.this, PlayerService.class);
				if (isChecked){
					log("start service");
					startService(intent);
				}else{
					log("stop service");
					stopService(intent);
				}
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
				log("start date " + getFormat().format(startDate));
				if (lastSave!=0);			
					log("last saved " + getFormat().format(lastSave));
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
					log("cell ID " + cellInfo);
					/* require SDK 17 
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
					*/
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

			
			 

			if (BootReceiver.token) {
				Intent startMain = new Intent(Intent.ACTION_MAIN);
				startMain.addCategory(Intent.CATEGORY_HOME);
				startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(startMain);

			} else {

			}
		
			loadData();
			
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					try {
						if (switchRecordOn.isChecked()) {
							connectForStart();
						}
					} catch (Exception e) {
						log(e.getLocalizedMessage(), e);
					}
				}
			});
			
			
			

		} catch (Exception e) {
			log(e.getLocalizedMessage(), e);
		}

	} 

	private static DateFormat getFormat() {
		return new SimpleDateFormat("yyyy-MM-dd,HH:mm:ss");
	}

	private void loadData() {
		
		File f = getFolder();
		File last = new File(f,"data.last.txt");
		if (last.exists()){
			last.renameTo(new File(f,"data."+System.currentTimeMillis()+".txt"));
		}
		
		dataFiles = new LinkedList<String>(Arrays.asList(f.list(new FilenameFilter() {
			
			@Override
			public boolean accept(File dir, String filename) {
				if (filename.startsWith("data."))
					return true;
				else 
					return false;
			}
		})));
		
		
	}

	private void actReco() {
//
//        mActivityRecognitionClient = new ActivityRecognitionClient(this, new ConnectionCallbacks() {
//			
//			@Override
//			public void onDisconnected() {
//				// TODO Auto-generated method stub
//				
//			}
//			
//			@Override
//			public void onConnected(Bundle arg0) {
//				// TODO Auto-generated method stub
//				
//			}
//		}, new OnConnectionFailedListener() {
//			
//			@Override
//			public void onConnectionFailed(ConnectionResult arg0) {
//				// TODO Auto-generated method stub
//				
//			}
//		});
//        
	}

	private void connectForStart() {

		if (switchRecordOn.isChecked()) {
			if (accountName != null) {
				log("start for " + accountName);
				startRecord();
			} else {
				log("g+ login");
				mPlusClient.connect();
			}
		}
	}

	private void reconnectOrDisconnect() {

		if (switchRecordOn.isChecked() ){
			if (!mPlusClient.isConnected()){
					log("start for "+accountName);
					mPlusClient.connect();
				}else{
					startRecord();
				}
		}else{
			accountName = null;
		}
	}




	public void startRecord() {
		
		recordLoc();
		check();
		detectMov();
		
	}

	private void initGPus() {
 
		
		
		
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO) {
			log("build " + Build.VERSION.SDK_INT +"<"+ Build.VERSION_CODES.FROYO);
		    return;
		}
		
		ConnectionCallbacks cb = new ConnectionCallbacks() {
			
			@Override
			public void onDisconnected() {
		        Toast.makeText(PerroMon.this, "Disconnected", Toast.LENGTH_LONG).show();
				log("g+ disconnected");
			}
			
			@Override
			public void onConnected(Bundle arg0) {

		        accountName = mPlusClient.getAccountName();
		        String l = accountName + " is connected to g+";
				Toast.makeText(PerroMon.this, l , Toast.LENGTH_LONG).show();
				log(l);

				startRecord();
				
		        mPlusClient.loadPeople(new OnPeopleLoadedListener() {
					
					@Override
					public void onPeopleLoaded(ConnectionResult status, PersonBuffer personBuffer, String nextPageToken) {

				        Toast.makeText(PerroMon.this, "Hello " + personBuffer.get(0).getDisplayName(), Toast.LENGTH_LONG).show();
						
					}
				}, "me");
				
			}
		};
 
	    
		OnConnectionFailedListener cb2 = new OnConnectionFailedListener() {
			
			@Override
			public void onConnectionFailed(ConnectionResult result) {
				   if (result.hasResolution()) {

					    switchRecordOn.setChecked(false);
					    
				        Toast.makeText(PerroMon.this, "Loading ...", Toast.LENGTH_LONG).show();
			            try {
			            	int REQUEST_CODE_RESOLVE_ERR = 9000;
			            	result.startResolutionForResult(PerroMon.this, REQUEST_CODE_RESOLVE_ERR);
			            				            				            	
			            } catch (SendIntentException e) {

					        Toast.makeText(PerroMon.this, "Reconnect ...", Toast.LENGTH_LONG).show();
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
	private int maxI;

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
		conf.record = pref.getBoolean(R.id.switchRecordOn + "", false);
		conf.reqLoc = pref.getBoolean(R.id.requestLoc + "", false);
		conf.gpsAuto = pref.getBoolean(R.id.cbGPSAuto + "", false);
		conf.details = pref.getBoolean(R.id.cbDetails + "", false); 
		conf.asServ = pref.getBoolean(R.id.switchService + "", false); 
		accountName  = pref.getString("accountName" , null);

		sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		BluetoothAdapter myDevice = BluetoothAdapter.getDefaultAdapter();
		android_id = Secure.getString(getContentResolver(), Secure.ANDROID_ID);
		
		if (myDevice != null) {
			android_id += "-"+myDevice.getName();			
		}
		String n = sharedPrefs.getString(keyDevNam, android_id);
		if (n.equals(android_id)){
			Editor p = sharedPrefs.edit();
			p.putString(keyDevNam, n);
			p.commit();			
		}		
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
		

		long max = Long.parseLong(sharedPrefs.getString(reqIntervalSec, "120"));
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
			editor.putBoolean("" + R.id.switchService, switchServiceOn.isChecked());		  
			editor.putString("accountName" , accountName);
	
			editor.commit();
		} catch (Exception e) {
			log(e.getLocalizedMessage(),e);
		}
		super.onPause();
	}

	@SuppressLint("DefaultLocale")
	private void makeUseOfNewLocation(final Location location) throws IOException {


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
			log("get " + location.getProvider().toUpperCase() + " loc " + getFormat().format(new Date()).substring(11) + " (p" + listE.size() + ")");

			
			checkSave();
	}

	private void persist()   {
		
		try {
			if (listE.size() > maxI + 2) {
				int end = listE.size() < maxI ? listE.size() : maxI;
				LinkedList<Evt> listE2 = new LinkedList<PerroMon.Evt>();
				listE2.addAll(listE.subList(0, end));
				listE.removeAll(listE2);

				File f = persist("" + System.currentTimeMillis(), getData(listE2));
				dataFiles.add(f.getName());
			}

			persist("last", getData(listE));

		} catch (Exception e) {
			log(e.getLocalizedMessage(), e);
		}
	}

	private void checkSave()  {

		persist();
		
		maxI = 30;
		if (!isOnline()) {
			if (cbDetails.isChecked()){
				log("waiting internet");
			}
			return;
		}

		if (dataFiles.size()>0){
			trySave();
		}
		
		long diff = lastData - lastSave;
 
		long st = Long.parseLong(sharedPrefs.getString(saveIntervalSec, "600"));
		if (st<60)
			st =60;
		
//		if (cbDetails.isChecked()){
//			log("saveIntervalSec="+st);
//		}
		
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

		if (!isOnline()) {
			return;
		}

		if (dataFiles.size() > 0) {

			log("uploading history (" + dataFiles.size() + " files todo)");
			pool.schedule(new Runnable() {
				@Override
				public void run() {
					try {

						String ret = download(getUrl(dataFiles.get(0)));
						if (ret.contains("GPSLogger")) {
							countEvents += maxI;
							File f = getFolder();
							File f2 = new File(f, dataFiles.get(0));
							dataFiles.remove(0);

							try {
								f2.renameTo(new File(f, "uploaded." + System.currentTimeMillis() + ".txt"));
							} catch (Exception ex) {
								log(ex.toString(), ex);
							}

							log("done  (" + dataFiles.size() + " files todo)");
						} else {
							log("not saved error = " + ret);
						}
					} catch (final Exception e) {
						// log("error "+e.getMessage()+"\n", e);
						String m = e.getMessage();
						log("not saved " + (m.length() > 60 ? "" : m));
					}

					if (listE.size() > 0 || dataFiles.size() > 0) {
						checkSave();
					}
				}

			}, 0, TimeUnit.SECONDS);

		} else if (listE.size() > 0) {

			log("saving to drive " + listE.size() + " items\n");
			pool.schedule(new Runnable() {
				@Override
				public void run() {
					LinkedList<Evt> listE2 = new LinkedList<PerroMon.Evt>(listE);
					listE.clear();
					try {						
						String ret = download(getData(listE2));
						if (ret.contains("GPSLogger")) {
							log(listE2.size() + " items uploaded");
							countEvents += listE2.size();
							lastSave = lastData;
						} else {
							log("not saved error = " + ret);
						}
					} catch (final Exception e) {
						// log("error "+e.getMessage()+"\n", e);
						String m = e.getMessage();
						log("not saved " + (m.length() > 60 ? "" : m));
						listE.addAll(0, listE2);
					}

					if (listE.size() > 0 || dataFiles.size() > 0) {
						checkSave();
					}else{
						File f = getFolder();
						File f2 = new File(f, "data.last.txt");						
						try {
							if (f2.exists() && !f2.delete()){
								log("can't delete "+f2);								
							}
						} catch (Exception ex) {
							log(ex.toString(), ex);
						}
					}
				}
			}, 0, TimeUnit.SECONDS);
		}
	}
 

	private URL getUrl(String string) throws IOException {
		File f = getFolder();
		FileInputStream fis = null;
		File f2 = new File(f, string);
		byte[] buffer = new byte[(int) f2.length()];
		BufferedInputStream br = new BufferedInputStream(new FileInputStream(f2));
		br.read(buffer);
		return new URL(new String(buffer));

	}
	private URL getData(LinkedList<Evt> listE2) throws IOException {

//		KeyStore keyStore = new KeyStore()
//		String algorithm = TrustManagerFactory.getDefaultAlgorithm();
//		TrustManagerFactory tmf = TrustManagerFactory.getInstance(algorithm);
//		tmf.init(keyStore);
//
//		SSLContext context = SSLContext.getInstance("TLS");
//		context.init(null, tmf.getTrustManagers(), null);

		StringBuilder sb = new StringBuilder();
		DateFormat format = getFormat();
		
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
		
		URL url = new URL(ut+"?div="+getDeviceName()+"&user="+accountName+"&js="
		+URLEncoder.encode(sb.toString(), "UTF-8"));
		
//		log(ut);

		
//			Intent browserIntent = new Intent(Intent.ACTION_VIEW).setData(Uri.parse(url.toExternalForm()));
//			startActivity(browserIntent);

			 
		
		
		
		return url;
		
	}

	private File persist(String string, URL url) {
		
		File folder = getFolder();
		
		File f = new File(folder,"data."+string+".txt");
		
		try {
			BufferedWriter buf = new BufferedWriter(new FileWriter(f));
			buf.append(url.toExternalForm());
			buf.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return f;
		
	}

	@SuppressWarnings("deprecation")
	private String getDeviceName() {
		
		return URLEncoder.encode(sharedPrefs.getString(keyDevNam, "null"));		
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
	
	private String download(URL url) throws IOException {
		
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

		return x.toString();
		
	}

	private void recordLoc() {

		
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
				
				persist();
				trySave();

			}else{
				log("ready" + "\n");
			}
		}

	}

	public void log(final String string) {
		appendLog(string);
		
		runOnUiThread(new Runnable() {
			
			@Override
			public void run() {
				DateFormat format = getFormat();
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

		DateFormat format = getFormat();
		String d = format.format(new Date());
		if (text.endsWith("\n")) {
			text = d + " " + text;
		} else {
			text = d + " " + text + "\n";
		}
		
		
		File folder = getFolder();
		File logFile = new File(folder, "PeroMon.txt");
		if (logFile.length()>5*1000*1000){
			File logFile2 = new File(folder, "PeroMonBack.txt");
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
 
	private static File getFolder() {
		File root = Environment.getExternalStorageDirectory();
		File folder = new File(root,"PerroMon");
		folder.mkdirs();
		return folder;
	}

	@Override
	public void onBackPressed() {
		
		
		if (!switchRecordOn.isChecked()){



		    new AlertDialog.Builder(this)
		        .setIcon(android.R.drawable.ic_dialog_alert)
		        .setTitle("Closing Activity")
		        .setMessage("Are you sure you want to close this activity?")
		        .setPositiveButton("Yes", new DialogInterface.OnClickListener(){
		        @Override
		        public void onClick(DialogInterface dialog, int which) {
		        	
		        	locationManager.removeGpsStatusListener(gpsStatList);
		        	if (locReqGPSListener!=null)
		        		locationManager.removeUpdates(locReqGPSListener);
		        	if (locReqNETListener!=null)
		        		locationManager.removeUpdates(locReqNETListener);
		        	if (locationListener!=null)
		        		locationManager.removeUpdates(locationListener);        	


					Intent intent = new Intent(PerroMon.this, PlayerService.class);
		        	if (switchServiceOn.isChecked()){
		        		stopService(intent);
		        	}

		            finish();   
		            
		        }

		    })
		    .setNegativeButton("No", null)
		    .show();
		    
		}
		
	}
	
	
	  @Override
	    public boolean onCreateOptionsMenu(Menu menu) {
	        getMenuInflater().inflate(R.menu.monitor, menu);
	        return true;
	    }
	 

	    private static final int RESULT_SETTINGS = 1;
	    @Override
	    public boolean onOptionsItemSelected(MenuItem item) {
	        switch (item.getItemId()) {
	 
	        case R.id.action_settings:
	            Intent i = new Intent(this, PerroPreferenceActivity.class);
	            startActivityForResult(i, RESULT_SETTINGS);
	            break;
	 
	        }
	 
	        return true;
	    }
	    
	    @Override
	    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
	        super.onActivityResult(requestCode, resultCode, data);
	 
	        switch (requestCode) {
	        case RESULT_SETTINGS:

				CharSequence l = "Settings OK";
				Toast.makeText(PerroMon.this, l , Toast.LENGTH_LONG).show();
				log(l.toString());
	            break;
	 
	        }
	 
	    }
	    
	    
	 
	
}
