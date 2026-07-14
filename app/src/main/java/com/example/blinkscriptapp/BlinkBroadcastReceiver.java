package com.example.blinkscriptapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// Receives blink commands sent directly from laptop via ADB:
// adb shell am broadcast -a com.example.blinkscriptapp.BLINK_ACTION --ei option 1
public class BlinkBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int option = intent.getIntExtra("option", 0);
        if (option < 1 || option > 6) return;

        android.util.Log.d("BlinkReceiver", "ADB broadcast received, option=" + option);

        Intent launch = new Intent(context, MainActivity.class);
        launch.setAction("com.example.blinkscriptapp.BLINK_SELECT");
        launch.putExtra("option", option);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(launch);
    }
}
