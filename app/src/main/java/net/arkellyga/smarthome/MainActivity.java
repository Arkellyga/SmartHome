package net.arkellyga.smarthome;

import android.Manifest;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;

import net.arkellyga.smarthome.receivers.WifiReceiver;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(ThemeUtils.getTheme(this));
        setContentView(R.layout.main_lay);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE
                    }, 0);
        } else {
            //NotificationHelper.startNotification(this);
            getFragmentManager().beginTransaction()
                    .replace(R.id.content_main, new Preferences())
                    .commit();
            //HomeApi.startMQTT(this);
            MqttService.actionStart(this);
        }
    }
}
