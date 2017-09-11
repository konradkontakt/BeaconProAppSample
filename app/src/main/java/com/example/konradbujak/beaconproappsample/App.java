package com.example.konradbujak.beaconproappsample;

import android.app.Application;
import android.util.Log;

import com.kontakt.sdk.android.common.KontaktSDK;

/**
 * Created by Admin on 11.09.2017.
 */

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        KontaktSDK.initialize(this);
        if (KontaktSDK.isInitialized()){
            Log.d("SDK", "Kontakt SDK initialized");
        }
        else{
            Log.e("SDK", "Failed to initialize Kontakt SDK");
        }
    }
}
