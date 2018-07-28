package com.imaginadesarrollo.universalhrm;

/**
 * Created by kike on 28/07/2018.
 */

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import org.runnerup.hr.HRData;
import org.runnerup.hr.HRDeviceRef;
import org.runnerup.hr.HRManager;
import org.runnerup.hr.HRProvider;
import org.runnerup.hr.HRProvider.HRClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;


public class HRSettingsActivity extends AppCompatActivity implements HRClient {

    private static final String TAG = "HRSettingsActivity";

    private final Handler handler = new Handler();
    private final StringBuffer logBuffer = new StringBuffer();

    private List<HRProvider> providers = null;
    private String btName;
    private String btAddress;
    private String btProviderName;
    private HRProvider hrProvider = null;

    private Button connectButton = null;
    private Button scanButton = null;
    private TextView tvBTName = null;
    private TextView tvHR = null;
    private TextView tvBatteryLevel = null;

    private DeviceAdapter deviceAdapter = null;
    private boolean mIsScanning = false;

    private final OnClickListener scanButtonClick = new OnClickListener() {
        public void onClick(View v) {
            clear();
            stopTimer();

            close();
            mIsScanning = true;
            log("select HR-provider");
            selectProvider();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Dexter.withActivity(this)
                .withPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(new PermissionListener() {
                    @Override public void onPermissionGranted(PermissionGrantedResponse response) {}
                    @Override public void onPermissionDenied(PermissionDeniedResponse response) {}
                    @Override public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {}
                }).check();

        providers = HRManager.getHRProviderList(this);
        deviceAdapter = new DeviceAdapter(this);

        if (providers.isEmpty()) {
            notSupported();
        }

        tvBTName = findViewById(R.id.hrName);
        tvHR = findViewById(R.id.hrValue);
        tvBatteryLevel = findViewById(R.id.batteryLevel);
        scanButton = findViewById(R.id.scanButton);
        scanButton.setOnClickListener(scanButtonClick);
        connectButton = findViewById(R.id.connectButton);
        connectButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                connect();
            }
        });

        load();
        open();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        close();
        stopTimer();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 0) {
            if (!hrProvider.isEnabled()) {
                log("Bluetooth not enabled!");
                scanButton.setEnabled(false);
                connectButton.setEnabled(false);
                return;
            }
            load();
            open();
            return;
        }
        if (requestCode == 123) {
            startScan();
        }
    }

    private int lineNo = 0;

    private void log(String msg) {
        logBuffer.insert(0, Integer.toString(++lineNo) + ": " + msg + "\n");
        if (logBuffer.length() > 5000) {
            logBuffer.setLength(5000);
        }
        Log.d(TAG, logBuffer.toString());
    }

    private void clearHRSettings() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.Clear_HR_settings));
        builder.setMessage(getString(R.string.Are_you_sure));
        builder.setPositiveButton(getString(R.string.OK), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                doClear();
            }
        });

        builder.setNegativeButton(getString(R.string.Cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // Do nothing but close the dialog
                        dialog.dismiss();
                    }

                });
        builder.show();
    }

    private void load() {
        Resources res = getResources();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        btName = prefs.getString(res.getString(R.string.pref_bt_name), null);
        btAddress = prefs.getString(res.getString(R.string.pref_bt_address), null);
        btProviderName = prefs.getString(res.getString(R.string.pref_bt_provider), null);
        Log.e(getClass().getName(), "btName: " + btName);
        Log.e(getClass().getName(), "btAddress: " + btAddress);
        Log.e(getClass().getName(), "btProviderName: " + btProviderName);

        if (btProviderName != null) {
            log("HRManager.get(" + btProviderName + ")");
            hrProvider = HRManager.getHRProvider(this, btProviderName);
        }
    }

    private void open() {
        if (hrProvider != null && !hrProvider.isEnabled()) {
            if (hrProvider.startEnableIntent(this, 0)) {
                return;
            }
            hrProvider = null;
        }
        if (hrProvider != null) {
            log(hrProvider.getProviderName() + ".open(this)");
            hrProvider.open(handler, this);
        } else {
            updateView();
        }
    }

    private void close() {
        if (hrProvider != null) {
            log(hrProvider.getProviderName() + ".close()");
            hrProvider.close();
            hrProvider = null;
        }
    }

    private void notSupported() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.Heart_rate_monitor_is_not_supported_for_your_device));
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        };
        builder.setNegativeButton(getString(R.string.ok_rats), listener);
        builder.show();
    }

    private void clear() {
        btAddress = null;
        btName = null;
        btProviderName = null;
    }

    private void updateView() {
        if (hrProvider == null) {
            scanButton.setEnabled(true);
            connectButton.setEnabled(false);
            connectButton.setText(getString(R.string.Connect));
            tvBTName.setText("");
            tvHR.setText("");
            return;
        }

        if (btName != null) {
            tvBTName.setText(btName);
        } else {
            tvBTName.setText("");
            tvHR.setText("");
        }

        if (hrProvider.isConnected()) {
            connectButton.setText(getString(R.string.Disconnect));
            connectButton.setEnabled(true);
        } else if (hrProvider.isConnecting()) {
            connectButton.setEnabled(false);
            connectButton.setText(getString(R.string.Connecting));
        } else {
            connectButton.setEnabled(btName != null);
            connectButton.setText(getString(R.string.Connect));
        }
    }

    private void selectProvider() {
        final CharSequence items[] = new CharSequence[providers.size()];
        final CharSequence itemNames[] = new CharSequence[providers.size()];
        for (int i = 0; i < items.length; i++) {
            items[i] = providers.get(i).getProviderName();
            itemNames[i] = providers.get(i).getName();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.Select_type_of_Bluetooth_device));
        builder.setPositiveButton(getString(R.string.OK),
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, int which) {
                        open();
                    }
                });
        builder.setNegativeButton(getString(R.string.Cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        mIsScanning = false;
                        load();
                        open();
                        dialog.dismiss();
                    }

                });
        builder.setSingleChoiceItems(itemNames, -1,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        hrProvider = HRManager.getHRProvider(HRSettingsActivity.this,
                                items[arg1].toString());
                        log("hrProvider = " + hrProvider.getProviderName());
                    }
                });
        builder.show();
    }

    private void startScan() {
        log(hrProvider.getProviderName() + ".startScan()");
        updateView();
        deviceAdapter.deviceList.clear();
        hrProvider.startScan();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.Scanning));
        builder.setPositiveButton(getString(R.string.Connect),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        log(hrProvider.getProviderName() + ".stopScan()");
                        hrProvider.stopScan();
                        connect();
                        updateView();
                        dialog.dismiss();
                    }
                });
        if (hrProvider.isBondingDevice()) {
            builder.setNeutralButton("Pairing", new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                    Intent i = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                    startActivityForResult(i, 123);
                }
            });
        }
        builder.setNegativeButton(getString(R.string.Cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        log(hrProvider.getProviderName() + ".stopScan()");
                        hrProvider.stopScan();
                        load();
                        open();
                        dialog.dismiss();
                        updateView();
                    }
                });

        builder.setSingleChoiceItems(deviceAdapter, -1,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        HRDeviceRef hrDevice = deviceAdapter.deviceList.get(arg1);
                        btAddress = hrDevice.getAddress();
                        btName = hrDevice.getName();
                    }
                });
        builder.show();
    }

    private void connect() {
        stopTimer();
        if (hrProvider == null || btName == null || btAddress == null) {
            updateView();
            return;
        }
        if (hrProvider.isConnecting() || hrProvider.isConnected()) {
            log(hrProvider.getProviderName() + ".disconnect()");
            hrProvider.disconnect();
            updateView();
            return;
        }

        tvBTName.setText(getName());
        tvHR.setText("?");
        String name = btName;
        if (name == null || name.length() == 0) {
            name = btAddress;
        }
        log(hrProvider.getProviderName() + ".connect(" + name + ")");
        hrProvider.connect(HRDeviceRef.create(btProviderName, btName, btAddress));
        updateView();
    }

    private void save() {
        Resources res = getResources();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Editor ed = prefs.edit();
        ed.putString(res.getString(R.string.pref_bt_name), btName);
        ed.putString(res.getString(R.string.pref_bt_address), btAddress);
        ed.putString(res.getString(R.string.pref_bt_provider), hrProvider.getProviderName());
        ed.commit();
    }

    private void doClear() {
        Resources res = getResources();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Editor ed = prefs.edit();
        ed.remove(res.getString(R.string.pref_bt_name));
        ed.remove(res.getString(R.string.pref_bt_address));
        ed.remove(res.getString(R.string.pref_bt_provider));
        ed.commit();
    }

    private CharSequence getName() {
        if (btName != null && btName.length() > 0)
            return btName;
        return btAddress;
    }

    private Timer hrReader = null;

    private void startTimer() {
        hrReader = new Timer();
        hrReader.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    public void run() {
                        readHR();
                    }
                });
            }
        }, 0, 500);
    }

    private void stopTimer() {
        if (hrReader == null)
            return;

        hrReader.cancel();
        hrReader.purge();
        hrReader = null;
    }

    private long timerStartTime = 0;

    private void readHR() {
        if (hrProvider != null) {
            HRData data = hrProvider.getHRData();
            if(data != null) {
                long age = data.timestamp;
                long hrValue = 0;
                if(data.hasHeartRate)
                    hrValue = data.hrValue;

                tvHR.setText(String.format(Locale.getDefault(), "%d", hrValue));
            }
        }
    }

    @Override
    public void onOpenResult(boolean ok) {
        log(hrProvider.getProviderName() + "::onOpenResult(" + ok + ")");
        if (mIsScanning) {
            mIsScanning = false;
            startScan();
            return;
        }

        updateView();
    }

    @Override
    public void onScanResult(HRDeviceRef device) {
        log(hrProvider.getProviderName() + "::onScanResult(" + device.getAddress() + ", "
                + device.getName() + ")");
        deviceAdapter.deviceList.add(device);
        deviceAdapter.notifyDataSetChanged();
    }

    @Override
    public void onConnectResult(boolean connectOK) {
        log(hrProvider.getProviderName() + "::onConnectResult(" + connectOK + ")");
        if (connectOK) {
            save();
            if (hrProvider.getBatteryLevel() > 0) {
                int level = hrProvider.getBatteryLevel();
                tvBatteryLevel.setVisibility(View.VISIBLE);
                tvBatteryLevel.setText(String.format(Locale.getDefault(), "%s: %d%%",
                        getResources().getText(R.string.Battery_level), hrProvider.getBatteryLevel()));
            }
            startTimer();
        }
        updateView();
    }

    @Override
    public void onDisconnectResult(boolean disconnectOK) {
        log(hrProvider.getProviderName() + "::onDisconnectResult(" + disconnectOK + ")");
    }

    @Override
    public void onCloseResult(boolean closeOK) {
        log(hrProvider.getProviderName() + "::onCloseResult(" + closeOK + ")");
    }

    @Override
    public void log(HRProvider src, String msg) {
        log(src.getProviderName() + ": " + msg);
    }

    @SuppressLint("InflateParams")
    class DeviceAdapter extends BaseAdapter {

        final ArrayList<HRDeviceRef> deviceList = new ArrayList<>();
        LayoutInflater inflater = null;
        // --Commented out by Inspection (2017-08-11 13:06):Resources resources = null;

        DeviceAdapter(Context ctx) {
            inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            //resources = ctx.getResources();
        }

        @Override
        public int getCount() {
            return deviceList.size();
        }

        @Override
        public Object getItem(int position) {
            return deviceList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row;
            if (convertView == null) {
                //Note: Parent is AlertDialog so parent in inflate must be null
                row = inflater.inflate(android.R.layout.simple_list_item_single_choice, null);
            } else {
                row = convertView;
            }
            TextView tv = row.findViewById(android.R.id.text1);
            //tv.setTextColor(resources.getColor(R.color.black));

            HRDeviceRef btDevice = deviceList.get(position);
            tv.setTag(btDevice);
            tv.setText(btDevice.getName());

            return tv;
        }
    }

}
