package net.arkellyga.smarthome.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.nfc.Tag;
import android.util.Log;

import net.arkellyga.smarthome.MqttService;

public class WifiReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = "WifiReceiver";
    private static final String ACTION = "android.net.conn.CONNECTIVITY_CHANGE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null) {
            if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                if (manager != null) {
                    NetworkInfo info = manager.getActiveNetworkInfo();
                    if (info != null && info.isConnected()) {
                        Log.d(LOG_TAG, "connected");
                        if (info.getType() == ConnectivityManager.TYPE_WIFI) {
                            Log.d(LOG_TAG, "connected wifi");
                            final WifiManager wifiManager = (WifiManager)
                                    context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                            if (wifiManager != null) {
                                final WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                                Log.d(LOG_TAG, "wifiinfo: " + wifiInfo.getSSID());
                                if (wifiInfo.getSSID().equals("\"Clinic\"")) {
                                    Log.d(LOG_TAG, "equals");
                                    MqttService.actionStart(context);
                                }
                            }
                        }
                    } else {
                        Log.d(LOG_TAG, "missed info");
                        MqttService.actionStop(context);
                    }
                } else {
                    Log.d(LOG_TAG, "manage null");
                }
            }
        }
    }
}
