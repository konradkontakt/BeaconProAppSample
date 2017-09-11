package com.example.konradbujak.beaconproappsample;

import android.Manifest;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.kontakt.sdk.android.ble.configuration.ActivityCheckConfiguration;
import com.kontakt.sdk.android.ble.configuration.ScanMode;
import com.kontakt.sdk.android.ble.configuration.ScanPeriod;
import com.kontakt.sdk.android.ble.connection.DeviceConnectionError;
import com.kontakt.sdk.android.ble.connection.KontaktDeviceConnection;
import com.kontakt.sdk.android.ble.connection.KontaktDeviceConnectionFactory;
import com.kontakt.sdk.android.ble.connection.OnServiceReadyListener;
import com.kontakt.sdk.android.ble.dfu.FirmwareUpdateListener;
import com.kontakt.sdk.android.ble.exception.KontaktDfuException;
import com.kontakt.sdk.android.ble.manager.ProximityManager;
import com.kontakt.sdk.android.ble.manager.ProximityManagerFactory;
import com.kontakt.sdk.android.ble.manager.listeners.ScanStatusListener;
import com.kontakt.sdk.android.ble.manager.listeners.SecureProfileListener;
import com.kontakt.sdk.android.ble.manager.listeners.simple.SimpleScanStatusListener;
import com.kontakt.sdk.android.ble.manager.listeners.simple.SimpleSecureProfileListener;
import com.kontakt.sdk.android.ble.rssi.RssiCalculators;
import com.kontakt.sdk.android.cloud.KontaktCloud;
import com.kontakt.sdk.android.cloud.KontaktCloudFactory;
import com.kontakt.sdk.android.cloud.response.CloudCallback;
import com.kontakt.sdk.android.cloud.response.CloudError;
import com.kontakt.sdk.android.cloud.response.CloudHeaders;
import com.kontakt.sdk.android.cloud.response.paginated.Firmwares;
import com.kontakt.sdk.android.common.model.Device;
import com.kontakt.sdk.android.common.model.Firmware;
import com.kontakt.sdk.android.common.model.FirmwareType;
import com.kontakt.sdk.android.common.profile.ISecureProfile;
import com.kontakt.sdk.android.common.profile.RemoteBluetoothDevice;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ProximityManager proximityManager;

    private ISecureProfile secureProfile;

    KontaktDeviceConnection connection;

    String TAG = "MyActivity";

    String uniqueId = "XYZY";

    private KontaktCloud cloud = KontaktCloudFactory.create();

    //Replace (Your Secret API key) with your API key acquired from the Kontakt.io Web Panel
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        oneTimeConfiguration();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        proximityManager.stopScanning();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        proximityManager.disconnect();
        proximityManager = null;
        super.onDestroy();
        close();
    }

    public void oneTimeConfiguration(){
        checkPermissionAndStart();
        configureProximityManager();
        setListeners();
    }

    private void checkPermissionAndStart() {
        int checkSelfPermissionResult = ContextCompat.checkSelfPermission(this, Arrays.toString(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.READ_EXTERNAL_STORAGE}));
        if (PackageManager.PERMISSION_GRANTED == checkSelfPermissionResult) {
            //already granted
            Log.d(TAG,"Permission already granted");
            startScanning();
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
                startScanning();
            }
        } else
        {
            Log.d(TAG,"Permission not granted");
            showToast("Kontakt.io SDK require this permission");
        }
    }

    private void configureProximityManager() {
        proximityManager = ProximityManagerFactory.create(this);
        proximityManager.configuration()
                .deviceUpdateCallbackInterval(1000)
                .rssiCalculator(RssiCalculators.newLimitedMeanRssiCalculator(5))
                .resolveShuffledInterval(1)
                .scanMode(ScanMode.BALANCED)
                .scanPeriod(ScanPeriod.RANGING)
                .activityCheckConfiguration(ActivityCheckConfiguration.DEFAULT);
    }

    private void setListeners()
    {
        proximityManager.setScanStatusListener(createScanStatusListener());
        proximityManager.setSecureProfileListener(createSecureListener());
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

    private void startScanning() {
        proximityManager.connect(new OnServiceReadyListener()
        {
            @Override
            public void onServiceReady() {
                proximityManager.startScanning();
            }
        });
    }

    private void stopScanning() {
        proximityManager.disconnect();
    }

    // Special listener for the Beacon Pros
    public SecureProfileListener createSecureListener(){
        return new SimpleSecureProfileListener(){
            // On discovery of the Beacon Pro
            @Override
            public void onProfileDiscovered(ISecureProfile profile) {
                // List beacons with not newest firmwares
                if (checkFirmwares("1.9",profile.getFirmwareRevision())) {
                    Log.d(TAG, "UniqueID : " + profile.getUniqueId()
                            + " Firmware Version : " + profile.getFirmwareRevision()
                            + " RSSI: " + profile.getRssi()
                            + " Name : " + profile.getName()
                            + " TX Power : " + profile.getTxPower()
                            + " Battery % : " + profile.getBatteryLevel());
                }

                // List the Beacon with Firmware 1.8
/*                if ("1.8".equals(profile.getFirmwareRevision())) {
                    Log.d(TAG, "UniqueID : " + profile.getUniqueId()
                            + " Firmware Version : " + profile.getFirmwareRevision()
                            + " RSSI: " + profile.getRssi()
                            + " Name : " + profile.getName()
                            + " TX Power : " + profile.getTxPower()
                            + " Battery % : " + profile.getBatteryLevel()
                    );
                }*/
                if(uniqueId.equals(profile.getUniqueId())){
                    secureProfile = profile;
                    connection = KontaktDeviceConnectionFactory.create(MainActivity.this, secureProfile, connectionListener);
                    connection.connect();
                    stopScanning();
                }
            }
            // When there will be updated for example the RSSI value will change, there wil create new log
            @Override
            public void onProfilesUpdated(List<ISecureProfile> profiles) {
//                for (ISecureProfile profile : profiles) {
//                    Log.d(TAG, "Unique Id: " + profile.getUniqueId()
//                            + " Updated RSSI: " + profile.getRssi()
////                            + " Accelerometer: " + KontaktDeviceCharacteristic.valueOf("ACCELEROMETER")
//                    );
//                }
            }
            @Override
            public void onProfileLost(ISecureProfile profile) {
                Log.d(TAG, "I'm sorry but I lost " +profile.getUniqueId());
                // TODO What if you lose beacon signal
            }
        };
    }

    private KontaktDeviceConnection.ConnectionListener connectionListener = new KontaktDeviceConnection.ConnectionListener() {
        @Override
        public void onConnectionOpened() {
            Log.d(TAG, "Connection Opened");
        }

        @Override
        public void onAuthenticationSuccess(RemoteBluetoothDevice.Characteristics characteristics) {
            Log.d(TAG, "Authentication Succeeded");
            fetchFirmware();
        }

        @Override
        public void onAuthenticationFailure(int failureCode) {
            switch (failureCode) {
                case DeviceConnectionError.FAILURE_WRONG_PASSWORD:
                    Log.d(TAG, "Wrong password");
                    break;
                case DeviceConnectionError.FAILURE_UNKNOWN_BEACON:
                    Log.d(TAG, "Device not recognized as Kontakt.io Beacon");
                    break;
            }
            close();

        }

        @Override
        public void onCharacteristicsUpdated(RemoteBluetoothDevice.Characteristics characteristics) {
            Log.d(TAG, "onCharacteristicsUpdated");
        }

        @Override
        public void onErrorOccured(int errorCode) {
            if (DeviceConnectionError.isGattError(errorCode)) {
                //low level bluetooth stack error. Most often 133
                int gattError = DeviceConnectionError.getGattError(errorCode);
                Log.d(TAG, "onErrorOccurred gattError: " + gattError);
            } else {
                //sdk error
                Log.d(TAG, "onErrorOccurred: " + errorCode);
            }
            close();
        }

        @Override
        public void onDisconnected() {
            Log.d(TAG, "onDisconnected");
            close();
        }
    };

//    public void apiEndpoints(){
//
//        // Schedule firmware update for Beacon Pro with uniqueId CsS9 with firmware 1.5 ( replace with yours Beacon's Pro uniqueId )
//        cloud.firmwares().scheduleUpdate().forDevices(uniqueId).withVersion("1.9").execute(new CloudCallback<String>() {
//            @Override
//            public void onSuccess(String response, CloudHeaders headers) {
//                Log.d(TAG,"Update scheduled");
//                fetchFirmware();
//            }
//            @Override
//            public void onError(CloudError error) {
//                Log.d(TAG,"Update schedule fail");
//            }
//        });
//    }

    private void fetchFirmware() {
        // Fetch newest firmware update for Beacon Pro with uniqueId CsS9 ( replace with yours Beacon's Pro uniqueId )
        cloud.firmwares().fetch().withType(FirmwareType.SCHEDULED).forDevices(uniqueId).execute(new CloudCallback<Firmwares>() {
            @Override
            public void onSuccess(Firmwares response, CloudHeaders headers) {
                Log.d(TAG, "Firmwares fetched");

                //If firmwares list is empty then there are no new firmware versions available for this device.
                if (!response.getContent().isEmpty()) {
                    updateFirmware(response.getContent().get(0));
                }
            }
            @Override
            public void onError(CloudError error) {
                Log.d(TAG, "Firmwares fail: " + error.getMessage());
            }
        });
    }

    private void updateFirmware(Firmware firmware) {
        //Initialize update
        final Device device = new Device.Builder().firmware(firmware.getName()).build();
        connection.updateFirmware(firmware, cloud, new FirmwareUpdateListener() {
            @Override
            public void onStarted() {
                Log.d(TAG, "Firmware update started");
            }

            @Override
            public void onFinished(long totalDurationMillis) {
                //Make sure connection is closed
                connection.close();
                Log.i(TAG, "Firmware update finished. Total time: " + totalDurationMillis);
                cloud.devices().update(uniqueId).with(device).execute(new CloudCallback<String>() {
                    @Override
                    public void onSuccess(String response, CloudHeaders headers) {
                        // Success

                    }

                    @Override
                    public void onError(CloudError error) {
                        // Failure
                    }
                });
            }

            @Override
            public void onProgress(int progressPercent, String progressStatus) {
                Log.d(TAG, "Firmware update in progress: " + progressPercent + "%");
            }

            @Override
            public void onError(KontaktDfuException exception) {
                Log.d(TAG, "Something went wrong! " + exception.getMessage());
                startScanning();
            }
        });
    }

    //Close the connection
    private void close() {
        if (connection != null) {
            Log.d(TAG, "Connection closed");
            connection.close();
            connection = null;
            startScanning();
        }
    }

    // Check if you have newest firmware
    // Firmware1 should be newest version and the Firmware2 should be version that beacon already has.
    public boolean checkFirmwares(String Firmware1, String Firmware2) {
        String[] split1 = Firmware1.split(".");
        String[] split2 = Firmware2.split(".");

        try {
            int major1 = Integer.valueOf(split1[0]);
            int major2 = Integer.valueOf(split2[0]);

            int minor1 = Integer.valueOf(split1[1]);
            int minor2 = Integer.valueOf(split1[1]);

            if (major1 == major2) {
                // TODO compare minors
                if (minor1 > minor2) {
                    // TODO Update is needed
                    return true;
                } else if (minor1 == minor2) {
                    // TODO No update needed
                    return false;
                } else {
                    Log.e(TAG, "Invalid firmware version");
                    return false;
                }
            } else if (major1 > major2) {
                // TODO update needed
                return true;
            } else {
                Log.e(TAG, "Invalid firmware version");
                return false;
            }
        } catch(ArrayIndexOutOfBoundsException e1){
            Log.e(TAG, "Exception thrown: " + e1);
        }
        return false;
    }
}
