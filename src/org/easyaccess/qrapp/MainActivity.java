package org.easyaccess.qrapp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.Timestamp;
import java.util.Hashtable;

import org.apache.http.protocol.HTTP;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.net.MailTo;
import android.net.Uri;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;

public class MainActivity extends Activity {
	// constants for writing information
	private static final String LANGUAGE = "Language";
	private static final String TOUCH_PRESENT = "TouchPresent";
	private static final String FONT_SIZE = "FontSize";
	private static final String TTS_VOICE = "TTS_Voice";
	private static final String TTS_SPEED = "TTS_Speed";
	private static final String SCAN_MODE = "Scan_Mode";
	private static final String SCAN_MODE_SPEED = "Scan_Mode_Speed";
	private static final String REVERSE_SCREEN = "Reverse_Screen";

	// fields for nfc
	NfcAdapter gNFCAdapter = null;
	// array of intent filter to be scan by app for reading nfc tag
	IntentFilter[] gIntentFilters = null;
	// intent to execute when the intent filter is detected by the systems
	PendingIntent gPendingIntent = null;
	String nfcMessage = null;

	// views
	ImageView gQRImageView = null;
	TextView gQRImageValue = null, gQRImageHint = null;
	Button gScannerButton = null, gQRCodeButton = null, gWriteNFC = null;
	Spinner gLangSpinner = null, gTouchPresentSpinner = null,
			gFontSizeSpinner = null, gTTSVoiceSpinner = null,
			gTTSSpeedSpinner = null, gReverseScreenSpinner = null,
			gScanMode = null, gScanModeSpeed = null, gQRSizeSpiner = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		gQRSizeSpiner = (Spinner) findViewById(R.id.spinner_qr_size);
		gTouchPresentSpinner = (Spinner) findViewById(R.id.spinner_touch_present);
		gLangSpinner = (Spinner) findViewById(R.id.spinner_langs);
		gFontSizeSpinner = (Spinner) findViewById(R.id.spinner_font_size);
		gScanMode = (Spinner) findViewById(R.id.spinner_scan_mode);
		gScanModeSpeed = (Spinner) findViewById(R.id.spinner_scan_mode_speed);
		gTTSVoiceSpinner = (Spinner) findViewById(R.id.spinner_voice);
		gTTSSpeedSpinner = (Spinner) findViewById(R.id.spinner_voice_speed);
		gReverseScreenSpinner = (Spinner) findViewById(R.id.spinner_reverse_scrn);

		gQRImageView = (ImageView) findViewById(R.id.iv_qr_code);
		gQRImageValue = (TextView) findViewById(R.id.tv_qr_value);
		gQRImageHint = (TextView) findViewById(R.id.tv_qr_hint);
		gQRCodeButton = (Button) findViewById(R.id.btn_qr_gen);
		gScannerButton = (Button) findViewById(R.id.btn_scanner);
		gWriteNFC = (Button) findViewById(R.id.btn_write_nfc);
		gWriteNFC.setVisibility(View.GONE);

		gNFCAdapter = NfcAdapter.getDefaultAdapter(this);
		gPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this,
				getClass()).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
				| Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

		// Intent to start an activity when a tag is discovered.
		IntentFilter ndefDiscovered = new IntentFilter(
				NfcAdapter.ACTION_NDEF_DISCOVERED);
		ndefDiscovered.addCategory(Intent.CATEGORY_DEFAULT);
		try {
			ndefDiscovered.addDataType("*/*");
		} catch (MalformedMimeTypeException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		// Intent to start an activity when a tag is discovered.
		IntentFilter techDiscovered = new IntentFilter(
				NfcAdapter.ACTION_TECH_DISCOVERED);
		// Intent filter to filter tag_discovered start an activity when a tag
		// is discovered.
		IntentFilter tagDiscovered = new IntentFilter(
				NfcAdapter.ACTION_TAG_DISCOVERED);

		// intent filters to be used by the application
		gIntentFilters = new IntentFilter[] { ndefDiscovered, techDiscovered,
				tagDiscovered };

		gQRImageView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mailQrCode();
			}
		});

		gScannerButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				new Thread(new Runnable() {
					@Override
					public void run() {
						Intent intent = new Intent(
								"com.google.zxing.client.android.SCAN");
						intent.setPackage("org.easyaccess.qrapp");
						intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
						startActivityForResult(intent, 0);
					}
				}).start();
			}
		});

		gWriteNFC.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
				intent.putExtra("android.intent.extras.CAMERA_FACING", 1);
				startActivityForResult(intent, 0);
			}
		});

		gQRCodeButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				FileOutputStream fileWritingStream = null;
				Hashtable<EncodeHintType, String> hints = new Hashtable<EncodeHintType, String>(
						2);
				hints.put(EncodeHintType.CHARACTER_SET, HTTP.ISO_8859_1);

				String qr_data = generateQRDataJSON();

				if (gQRImageValue.getVisibility() == View.VISIBLE) {
					gQRImageValue.setVisibility(View.GONE);
				}

				QRCodeEncoder qrCodeWriter = new QRCodeEncoder(qr_data, null,
						Contents.Type.TEXT, BarcodeFormat.QR_CODE.toString(),
						Integer.valueOf(getResources().getStringArray(
								R.array.qr_size_array)[gQRSizeSpiner
								.getSelectedItemPosition()]));
				try {
					Bitmap bitmap = qrCodeWriter.encodeAsBitmap();
					gQRImageHint.setVisibility(View.VISIBLE);
					gQRImageView.setVisibility(View.VISIBLE);
					gQRImageView.setImageBitmap(bitmap);
					File file = new File(
							Environment
									.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
									+ File.separator
									+ getString(R.string.qrcode_filename_prefix)
									+ new Timestamp(System.currentTimeMillis())
									+ ".png");

					fileWritingStream = new FileOutputStream(file);
					bitmap.compress(CompressFormat.PNG, 100, fileWritingStream);

					Toast.makeText(getApplicationContext(),
							"QrCode Image Saved to pictures", Toast.LENGTH_LONG)
							.show();
				} catch (WriterException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} finally {
					try {
						if (fileWritingStream != null) {
							fileWritingStream.flush();
							fileWritingStream.close();
						}
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				// BitMatrix bitMatrix = qrCodeWriter.encode(qr_data,
				// BarcodeFormat.QR_CODE, IMAGE_WEIDTH , IMAGE_HEIGHT, hints);
			}
		});
	}

	protected void mailQrCode() {
		File qrCodeImage = getLastMadifiedQRFile();
		if (qrCodeImage != null) {
			try {
				String subject = "QrCode Image";
				final Intent emailIntent = new Intent(
						android.content.Intent.ACTION_SEND);
				emailIntent.setType("plain/text");
				emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT,
						subject);
				Log.d("MainActivity",
						"Uri.fromFile = " + Uri.fromFile(qrCodeImage));
				emailIntent.putExtra(Intent.EXTRA_STREAM,
						Uri.fromFile(qrCodeImage));
				this.startActivity(Intent.createChooser(emailIntent,
						"Sending email..."));
			} catch (Throwable t) {
				Toast.makeText(this,
						"Request failed try again: " + t.toString(),
						Toast.LENGTH_LONG).show();
			}
		}
	}

	protected File getLastMadifiedQRFile() {
		File file = Environment
				.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
		File[] qrCodeFileArray = file.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String filename) {
				boolean result = false;
				if (filename
						.startsWith(getString(R.string.qrcode_filename_prefix))) {
					result = true;
				}
				return result;
			}
		});

		File lastModifiedFile = null;
		if (qrCodeFileArray != null) {
			Log.d("MainActivity", "qrCodeFileArray.length = "
					+ qrCodeFileArray.length);
			lastModifiedFile = qrCodeFileArray[0];
			for (int i = 1; i < qrCodeFileArray.length; i++) {
				if (lastModifiedFile.lastModified() < qrCodeFileArray[i]
						.lastModified()) {
					lastModifiedFile = qrCodeFileArray[i];
				}
			}
		}

		return lastModifiedFile;
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (gNFCAdapter != null) {
			if (!gNFCAdapter.isEnabled()) {
				new AlertDialog.Builder(this)
						.setMessage(getString(R.string.launch_nfc_setting))
						.setPositiveButton(getString(android.R.string.ok),
								new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog,
											int which) {
										Intent nfcIntent = new Intent(
												Settings.ACTION_WIRELESS_SETTINGS);
										startActivity(nfcIntent);
									}
								}).create().show();
			} else {
				// new String[] { NfcA.class.getName()},
				// new String[] { MifareUltralight.class.getName()},
				String[][] techListsArray = new String[][] { new String[] { Ndef.class
						.getName() } };
				gNFCAdapter.enableForegroundDispatch(MainActivity.this,
						gPendingIntent, gIntentFilters, techListsArray);
			}
		} else {
			Toast.makeText(this, "NFC Controller not found", Toast.LENGTH_SHORT)
					.show();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (gNFCAdapter != null) {
			gNFCAdapter.disableForegroundDispatch(this);
		}
	}

	@Override
	protected void onNewIntent(Intent intent) {
		Log.d("tushar", "acquired new intent");
		Log.d("tushar", "action of the discovered tag = " + intent.getAction());

		if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
			// means some ndef data is present on the tag
			Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
			Ndef ndefTag = Ndef.get(detectedTag);

			Toast.makeText(this, "ndef data discovered", Toast.LENGTH_SHORT)
					.show();

			for (int i = 0; i < detectedTag.getTechList().length; i++) {
				Log.d("tushar", "detected tag supported tech list = "
						+ detectedTag.getTechList()[i]);
			}

			if (ndefTag != null) {
				int size = ndefTag.getMaxSize(); // tag size
				boolean writable = ndefTag.isWritable(); // is tag writable?
				String type = ndefTag.getType();
				Log.d("tushar", "tag size = " + size + ", is tag writeable = "
						+ writable + ", tag type = " + type);
			}

			Parcelable[] messages = intent
					.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
			if (messages != null) {
				Log.d("tushar",
						"message length = " + messages.length
								+ ", discribe content value = "
								+ messages[0].describeContents());

				String payload = "";
				for (int i = 0; i < messages.length; i++) {
					NdefMessage msg = (NdefMessage) messages[i];
					int describe_content_aftr_masking = msg.describeContents()
							& NdefMessage.CONTENTS_FILE_DESCRIPTOR;
					Log.d("tushar", "describe content after masking = "
							+ describe_content_aftr_masking);

					for (int j = 0; j < msg.getRecords().length; j++) {
						payload = payload
								+ new String(msg.getRecords()[j].getPayload());
						Log.d("tushar",
								"record length = "
										+ msg.getRecords().length
										+ ", tnf value of the record = "
										+ msg.getRecords()[j].getTnf()
										+ ", id value of the record = "
										+ msg.getRecords()[j].getId()
										+ ", type value of the record = "
										+ new String(msg.getRecords()[j]
												.getType()));
						gQRImageValue.setVisibility(View.VISIBLE);
						gQRImageValue.setText(payload);
					}
				}

				String jsonData = generateQRDataJSON();
				boolean result = createNdefMessage(intent, jsonData);

				if (result) {
					showInfoDialog("data written in the tag");
				} else {
					showInfoDialog("error in writing the tag, may be the tag is tempered");
				}

				// final Intent alertIntent = intent;
				// new
				// AlertDialog.Builder(this).setMessage("Some data already exists. If u want to override that data press ok while nfc tag in place").setNeutralButton(android.R.string.ok,
				// new DialogInterface.OnClickListener() {
				// @Override
				// public void onClick(DialogInterface dialog, int which) {
				// String jsonData = generateQRDataJSON();
				// boolean result = createNdefMessage(alertIntent, jsonData);
				//
				// if(result){
				// Toast.makeText(MainActivity.this, "data written in the tag",
				// Toast.LENGTH_SHORT).show();
				// }else{
				// Toast.makeText(MainActivity.this, "error in writing",
				// Toast.LENGTH_SHORT).show();
				// }
				// }
				// }).create().show();
			}
		} else if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())) {
			// means no ndef data is found on the tag
			Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
			Log.d("tushar", "tag discovered, detected tag contents = "
					+ detectedTag.describeContents());

			for (int i = 0; i < detectedTag.getTechList().length; i++) {
				Log.d("tushar", "detected tag supported tech list = "
						+ detectedTag.getTechList()[i]);
			}

			Toast.makeText(this, "tech discovered", Toast.LENGTH_SHORT).show();

			String jsonData = generateQRDataJSON();
			boolean result = createNdefMessage(intent, jsonData);

			if (result) {
				showInfoDialog("data written in the tag");
			} else {
				showInfoDialog("error in writing the tag, may be the tag is tempered");
			}
		} else if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
			showInfoDialog("Nfc tag discovered but tag is not supported");
		}
	}

	protected String generateQRDataJSON() {
		// TODO Auto-generated method stub
		JSONObject jObject = null;
		String tempString = null;

		// try {
		// jObject = new JSONObject();
		tempString = getResources().getStringArray(R.array.lang_array_value)[gLangSpinner
				.getSelectedItemPosition()];
		// jObject.put(LANGUAGE, tempString);
		tempString = tempString
				+ ";"
				+ getResources().getStringArray(
						R.array.touch_present_array_value)[gTouchPresentSpinner
						.getSelectedItemPosition()];
		// jObject.put(TOUCH_PRESENT, tempString);
		tempString = tempString
				+ ";"
				+ getResources().getStringArray(R.array.font_size_array_value)[gFontSizeSpinner
						.getSelectedItemPosition()];
		// jObject.put(FONT_SIZE, tempString);
		tempString = tempString
				+ ";"
				+ getResources().getStringArray(R.array.tts_voice_array_value)[gTTSVoiceSpinner
						.getSelectedItemPosition()];
		// jObject.put(TTS_VOICE, tempString);
		tempString = tempString
				+ ";"
				+ getResources().getStringArray(R.array.tts_speed_array_value)[gTTSSpeedSpinner
						.getSelectedItemPosition()];
		// jObject.put(TTS_SPEED, tempString);
		tempString = tempString
				+ ";"
				+ getResources().getStringArray(
						R.array.reverse_scrn_array_value)[gReverseScreenSpinner
						.getSelectedItemPosition()];
		// jObject.put(REVERSE_SCREEN, tempString);
		tempString = tempString
				+ ";"
				+ getResources().getStringArray(R.array.scan_mode_array_value)[gScanMode
						.getSelectedItemPosition()];
		// jObject.put(SCAN_MODE, tempString);
		tempString = tempString
				+ ";"
				+ getResources().getStringArray(
						R.array.scan_mode_speed_array_value)[gScanModeSpeed
						.getSelectedItemPosition()];
		// jObject.put(SCAN_MODE_SPEED, tempString);
		// } catch (JSONException e) {
		// e.printStackTrace();
		// }
		return tempString;
		// return jObject.toString();
	}

	private boolean createNdefMessage(Intent intent, String string) {
		boolean result = false;

		NdefRecord ndefRecord = createNDEFRecord(string);

		if (ndefRecord != null) {
			// wrap record into the message
			NdefMessage ndefMessage = new NdefMessage(ndefRecord);

			// write msg to nfc tag
			Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
			Ndef ndefTag = Ndef.get(tag);
			Log.d("tushar", "string length = " + string.getBytes().length
					+ ", nfc storage capacity = " + ndefTag.getMaxSize());
			Log.d("tushar", "comparison test = "
					+ (string.getBytes().length < ndefTag.getMaxSize()));

			if (string.getBytes().length < ndefTag.getMaxSize()) {
				try {
					ndefTag.connect();
					ndefTag.writeNdefMessage(ndefMessage);
					ndefTag.close();
					result = true;
				} catch (IOException e1) {
					e1.printStackTrace();
				} catch (FormatException e1) {
					e1.printStackTrace();
				}
			} else {
				showInfoDialog("Not enough space to write the settings");
				// new
				// AlertDialog.Builder(this).setMessage("Not enough space to write the settings")
				// .setNeutralButton(android.R.string.ok, null).create().show();
			}
		}
		return result;
	}

	private NdefRecord createNDEFRecord(String string) {
		NdefRecord ndefRecord = null;
		byte[] byteArray;
		try {
			byteArray = string.getBytes(HTTP.ASCII);
			ndefRecord = new NdefRecord(NdefRecord.TNF_WELL_KNOWN,
					NdefRecord.RTD_TEXT, new byte[0], byteArray);

		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return ndefRecord;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// TODO Auto-generated method stub
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == 0) {
			if (gQRImageView.getVisibility() == View.VISIBLE) {
				gQRImageView.setVisibility(View.GONE);
				gQRImageHint.setVisibility(View.GONE);
			}
			if (resultCode == Activity.RESULT_OK) {
				String capturedQrValue = data.getStringExtra("SCAN_RESULT");
				gQRImageValue.setVisibility(View.VISIBLE);
				gQRImageValue.setText(capturedQrValue);
			} else if (resultCode == RESULT_CANCELED) {
				gQRImageValue.setVisibility(View.VISIBLE);
				gQRImageValue.setText("Scan Canceled");
			}
		}
	}

	public void showInfoDialog(String msg) {
		new AlertDialog.Builder(this).setMessage(msg)
				.setNeutralButton(android.R.string.ok, null).create().show();
	}
}