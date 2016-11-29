package com.example.konradbujak.beaconproappsample;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.nfc.Tag;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.kontakt.sdk.android.ble.configuration.ActivityCheckConfiguration;
import com.kontakt.sdk.android.ble.configuration.ScanPeriod;
import com.kontakt.sdk.android.ble.configuration.scan.ScanMode;
import com.kontakt.sdk.android.ble.connection.OnServiceReadyListener;
import com.kontakt.sdk.android.ble.device.BeaconRegion;
import com.kontakt.sdk.android.ble.device.EddystoneNamespace;
import com.kontakt.sdk.android.ble.manager.ProximityManager;
import com.kontakt.sdk.android.ble.manager.listeners.EddystoneListener;
import com.kontakt.sdk.android.ble.manager.listeners.IBeaconListener;
import com.kontakt.sdk.android.ble.manager.listeners.ScanStatusListener;
import com.kontakt.sdk.android.ble.manager.listeners.SecureProfileListener;
import com.kontakt.sdk.android.ble.manager.listeners.simple.SimpleEddystoneListener;
import com.kontakt.sdk.android.ble.manager.listeners.simple.SimpleIBeaconListener;
import com.kontakt.sdk.android.ble.manager.listeners.simple.SimpleScanStatusListener;
import com.kontakt.sdk.android.ble.manager.listeners.simple.SimpleSecureProfileListener;
import com.kontakt.sdk.android.ble.rssi.RssiCalculators;
import com.kontakt.sdk.android.ble.spec.EddystoneFrameType;
import com.kontakt.sdk.android.cloud.CloudConstants;
import com.kontakt.sdk.android.cloud.IKontaktCloud;
import com.kontakt.sdk.android.cloud.KontaktCloud;
import com.kontakt.sdk.android.cloud.api.ActionsApi;
import com.kontakt.sdk.android.cloud.response.CloudCallback;
import com.kontakt.sdk.android.cloud.response.CloudError;
import com.kontakt.sdk.android.cloud.response.CloudHeaders;
import com.kontakt.sdk.android.cloud.response.paginated.Actions;
import com.kontakt.sdk.android.cloud.response.paginated.Firmwares;
import com.kontakt.sdk.android.common.KontaktSDK;
import com.kontakt.sdk.android.common.Proximity;
import com.kontakt.sdk.android.common.model.Action;
import com.kontakt.sdk.android.common.model.Firmware;
import com.kontakt.sdk.android.common.profile.IBeaconDevice;
import com.kontakt.sdk.android.common.profile.IBeaconRegion;
import com.kontakt.sdk.android.common.profile.IEddystoneDevice;
import com.kontakt.sdk.android.common.profile.IEddystoneNamespace;
import com.kontakt.sdk.android.common.profile.ISecureProfile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private ProximityManager KontaktManager;
    String TAG = "MyActivity";
    //Replace (Your Secret API key) with your API key aquierd from the Kontakt.io Web Panel
    public static String API_KEY = "Your Secret API key";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        onetimeconfiguration();
    }
    @Override
    protected void onStart() {
        checkPermissionAndStart();
        super.onStop();
    }
    @Override
    protected void onStop() {
        KontaktManager.stopScanning();
        super.onStop();
    }
    @Override
    protected void onDestroy() {
        KontaktManager.disconnect();
        KontaktManager = null;
        super.onDestroy();
    }
    public void onetimeconfiguration(){
        sdkInitialise();
        configureProximityManager();
        setListeners();
        apiEndpoints();
    }
    private void checkPermissionAndStart() {
        int checkSelfPermissionResult = ContextCompat.checkSelfPermission(this, String.valueOf(new String[] {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.READ_EXTERNAL_STORAGE}));
        if (PackageManager.PERMISSION_GRANTED == checkSelfPermissionResult) {
            //already granted
            Log.d(TAG,"Permission already granted");
            startScan();
        }
        else {
            //request permission
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.READ_EXTERNAL_STORAGE}, 100);
            Log.d(TAG,"Permission request called");
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (100 == requestCode) {
                Log.d(TAG,"Permission granted");
                startScan();
            }
        } else
        {
            Log.d(TAG,"Permission not granted");
            showToast("Kontakt.io SDK require this permission");
        }
    }
    public void sdkInitialise()
    {
        KontaktSDK.initialize(API_KEY);
        if (KontaktSDK.isInitialized())
            Log.v(TAG, "SDK initialised");
    }
    private void configureProximityManager() {
        KontaktManager = new ProximityManager(this);
        KontaktManager.configuration()
                .deviceUpdateCallbackInterval(TimeUnit.SECONDS.toMillis(5))
                .rssiCalculator(RssiCalculators.newLimitedMeanRssiCalculator(5))
                .eddystoneFrameTypes(EnumSet.of(EddystoneFrameType.UID,EddystoneFrameType.TLM,EddystoneFrameType.EID))
                .resolveShuffledInterval(3)
                .scanMode(ScanMode.BALANCED)
                .scanPeriod(ScanPeriod.RANGING)
                .activityCheckConfiguration(ActivityCheckConfiguration.DEFAULT);
    }
    private void setListeners()
    {
        KontaktManager.setScanStatusListener(createScanStatusListener());
        KontaktManager.setKontaktSecureProfileListener(createSecureListener());
        Log.d(TAG,"Listeners Configured");
    }
    // Toasts on device
    private void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }
    private ScanStatusListener createScanStatusListener() {
        return new SimpleScanStatusListener() {
            @Override
            public void onScanStart()
            {
                Log.d(TAG,"Scanning started");
                showToast("Scanning started");
            }
            @Override
            public void onScanStop()
            {
                Log.d(TAG,"Scanning stopped");
                showToast("Scanning stopped");
            }
        };
    }
    private void startScan() {
        KontaktManager.connect(new OnServiceReadyListener()
        {
            @Override
            public void onServiceReady() {
                KontaktManager.startScanning();
            }
        });
    }
    // Special listener for the Beacon Pros
    private SecureProfileListener createSecureListener(){
        return new SimpleSecureProfileListener(){
            // On discovery of the Beacon Pro
            @Override
            public void onProfileDiscovered(ISecureProfile profile) {
                Log.d(TAG, "UniqueID : " + profile.getUniqueId()
                        + " Firmware Version : " + profile.getFirmwareRevision()
                        + " Name : " + profile.getName()
                        + " TX Power : " + profile.getTxPower()
                        + " Battery % : " + profile.getBatteryLevel());
            }
            // When there will be updated for example the RSSI value will change, there wil create new log
            @Override
            public void onProfilesUpdated(List<ISecureProfile> profiles) {
                for (ISecureProfile profile : profiles) {
                    Log.d(TAG, "Unique Id : " + profile.getUniqueId() + " Updated RSSI : " + profile.getRssi() + " TX Power : " + profile.getTxPower());
                }
            }
            @Override
            public void onProfileLost(ISecureProfile profile) {
                // TODO
            }
        };
    }
    public void apiEndpoints(){
        IKontaktCloud kontaktCloud = KontaktCloud.newInstance(API_KEY);
        // Fetch newest firmware update for Beacon Pro with uniqueId CsS9 ( replace with yours Beacon's Pro uniqueId )
        kontaktCloud.firmwares().fetch().forDevices("CsS9").execute(new CloudCallback<Firmwares>() {
            @Override
            public void onSuccess(Firmwares response, CloudHeaders headers) {
                Log.d(TAG, "Firmwares fetched");
            }
            @Override
            public void onError(CloudError error) {
                Log.d(TAG, "Firmwares fail");
            }
        });
        // Schedule firmware update for Beacon Pro with uniqueId CsS9 with firmware 1.5 ( replace with yours Beacon's Pro uniqueId )
        kontaktCloud.firmwares().scheduleUpdate().forDevices("CsS9").withVersion("1.5").execute(new CloudCallback<String>() {
            @Override
            public void onSuccess(String response, CloudHeaders headers) {
                Log.d(TAG,"Update scheduled");
            }
            @Override
            public void onError(CloudError error) {
                Log.d(TAG,"Update fail");
            }
        });
    }

}
