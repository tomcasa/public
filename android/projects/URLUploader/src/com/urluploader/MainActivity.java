package com.urluploader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;

import org.apache.http.client.utils.URIUtils;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.DownloadManager.Request;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

public class MainActivity extends Activity {

	private static final String mK = "mK";
	private static final String mA = "mA";
	private SharedPreferences pref;
	private int mode;
	private int action;

	@Override
	public void onBackPressed() {
		super.onBackPressed();
		Log.i("user", "onBackPressed");
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.i("user", "onCreate");

		pref = getPreferences(MODE_PRIVATE);

		mode = pref.getInt(R.string.Download_Method + "", R.id.rDownDir);
		action = pref.getInt("" + R.string.Default_Action, R.id.aOpen);

		setContentView(R.layout.activity_main);
		((RadioButton) findViewById(mode)).setChecked(true);
		((RadioButton) findViewById(action)).setChecked(true);

		// config mode

		// Get the intent that started this activity
		Intent intent = getIntent();
		String url = null;
		try {
			((TextView) findViewById(R.id.textView2)).setText("");
			((TextView) findViewById(R.id.textView3)).setText("");
			url = intent.getClipData().getItemAt(0).getText().toString();
		} catch (Exception e) {

		}

		Log.i("user", "" + url);
		if (url != null && intent.getType().equals("text/plain")) {
			((TextView) findViewById(R.id.textView2)).setText(url);
			((TextView) findViewById(R.id.textView3)).setText("");
			((ImageView) findViewById(R.id.imageView1)).setImageBitmap(null);

			// new DownloadImageTask(((ImageView)
			// findViewById(R.id.imageView1))).execute(url);
			if (mode == R.id.rDownDir) {
				new DownloadFileTask().execute(url);
				((TextView) findViewById(R.id.textView3)).setText("done");
			} else if (mode == R.id.rDownMng || mode == R.id.rDownMngOpen) {
				final DownloadManager mgr = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
				Request req = new DownloadManager.Request(Uri.parse(url));
				// req.setDescription(url.substring(url.lastIndexOf("/")));
				req.setDescription("Downloading...");
				req.setTitle("Downloading...");
				req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

				if (mode == R.id.rDownMng) {
					final long id = mgr.enqueue(req);
				} else {
					BroadcastReceiver onComplete = new BroadcastReceiver() {
						public void onReceive(Context ctxt, Intent intent) {
							Long dwnId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
							Log.i("user", "onReceive " + dwnId);
						}
					};
					registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

					final File f = new File(Environment.getExternalStorageDirectory() + "/Download/"
							+ System.currentTimeMillis() + ".jpg");
					req.setDestinationUri(Uri.fromFile(f));
					final long id = mgr.enqueue(req);
					Log.i("user", "queued " + id);

					onComplete = new BroadcastReceiver() {
						public void onReceive(Context ctxt, Intent intent) {
							Long dwnId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
							if (id == dwnId) {
								// try {
								// mgr.openDownloadedFile(id);
								// } catch (FileNotFoundException e) {
								// e.printStackTrace();
								// }

								Cursor cur = mgr.query(new DownloadManager.Query().setFilterById(id));
								if (cur.moveToFirst()) {
									String uriString = cur.getString(cur
											.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
									if (uriString != null) {
										File mFile = new File(Uri.parse(uriString).getPath());
										Log.i("user", "onReceive " + uriString);
										open(mFile);

									}
								}
							}
						}
					};
					registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
				}
			}
		} else {

		}
	}

	private void share(Bitmap bmp) {

		Intent intent = new Intent(Intent.ACTION_SEND);
		Intent chooser = Intent.createChooser(intent, "Share via");
		startActivity(chooser);

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		Log.i("user", "onCreateOptionsMenu");
		// Inflate the menu; this adds items to the action bar if it is present.
		// getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	public void close(View view) {
		super.finish();
	}
 

	@Override
	protected void onPause() {
		Log.i("user", "onPause");

		Editor editor = getPreferences(MODE_PRIVATE).edit();

		if (((RadioButton) findViewById(R.id.rDownDir)).isChecked())
			editor.putInt("" + R.string.Download_Method, R.id.rDownDir);
		else if (((RadioButton) findViewById(R.id.rDownMng)).isChecked())
			editor.putInt("" + R.string.Download_Method, R.id.rDownMng);
		else
			editor.putInt("" + R.string.Download_Method, R.id.rDownMngOpen);

		if (((RadioButton) findViewById(R.id.aEdit)).isChecked())
			editor.putInt("" + R.string.Default_Action, R.id.aEdit);
		else if (((RadioButton) findViewById(R.id.aSend)).isChecked())
			editor.putInt("" + R.string.Default_Action, R.id.aSend);
		else if (((RadioButton) findViewById(R.id.aOpen)).isChecked())
			editor.putInt("" + R.string.Default_Action, R.id.aOpen);

		editor.commit();
		super.onPause();
	}

	private class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
		ImageView bmImage;

		public DownloadImageTask(ImageView bmImage) {
			this.bmImage = bmImage;
		}

		protected Bitmap doInBackground(String... urls) {
			String urldisplay = urls[0];
			Bitmap mIcon11 = null;
			try {
				InputStream in = new java.net.URL(urldisplay).openStream();
				File file = new File(getCacheDir(), urldisplay.substring(urldisplay.lastIndexOf("/")));

			} catch (Exception e) {
				Log.e("Error", e.getMessage());
				e.printStackTrace();
			}
			return mIcon11;
		}

		protected void onPostExecute(Bitmap result) {
			bmImage.setImageBitmap(result);
		}
	}

	private class DownloadFileTask extends AsyncTask<String, Void, File> {

		public DownloadFileTask() {

		}

		protected File doInBackground(String... urls) {
			String urldisplay = urls[0];
			File file = null;
			try {
				InputStream in = new java.net.URL(urldisplay).openStream();
				// file = new File(getCacheDir(),
				// urldisplay.substring(urldisplay.lastIndexOf("/")));
				file = new File(Environment.getExternalStorageDirectory() + "/Download/",
						urldisplay.substring(urldisplay.lastIndexOf("/")));
				file.getParentFile().mkdirs();

				try {
					OutputStream output = new FileOutputStream(file);
					try {
						try {
							final byte[] buffer = new byte[1024];
							int read;
							while ((read = in.read(buffer)) != -1)
								output.write(buffer, 0, read);

							output.flush();
						} finally {
							output.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				} finally {
					in.close();
				}

			} catch (Exception e) {
				Log.e("Error", e.getMessage());
				e.printStackTrace();
			}
			return file;
		}

		protected void onPostExecute(File file) {

			open(file);

		}

	}

	private void open(File file) {
		((TextView) findViewById(R.id.textView3)).setText(file.toString());
		Uri uri = Uri.fromFile(file);
		String type = URLConnection.guessContentTypeFromName(file.getName());

		Intent sharingIntent = new Intent();

		// sharingIntent.setAction(Intent.ACTION_EDIT);
		String lab;
		if (action == R.id.aSend){
			lab = getString(R.string.aSend);
			sharingIntent.setType(type);
			sharingIntent.putExtra(Intent.EXTRA_STREAM, uri);
			sharingIntent.setAction(Intent.ACTION_SEND);
		} else if (action == R.id.aEdit) {
			lab = getString(R.string.aEdit);
			sharingIntent.setType(type);
			sharingIntent.setData(uri);
			sharingIntent.setAction(Intent.ACTION_EDIT);
		} else {
			sharingIntent.setAction(Intent.ACTION_VIEW);
			sharingIntent.setDataAndType(uri, type);
			lab = getString(R.string.aEdit);
		}

		startActivity(Intent.createChooser(sharingIntent, lab + " " + file + " " + type + " using"));

	}

	public static String getMimeType(String url) {
		String extension = url.substring(url.lastIndexOf("."));
		String mimeTypeMap = MimeTypeMap.getFileExtensionFromUrl(extension);
		String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(mimeTypeMap);
		return mimeType;
	}
}
