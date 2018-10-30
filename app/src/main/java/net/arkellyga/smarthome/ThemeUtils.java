package net.arkellyga.smarthome;

import android.content.Context;
import android.preference.PreferenceManager;

public class ThemeUtils {

    public static int getTheme(Context context) {
        String theme = PreferenceManager.
                getDefaultSharedPreferences(context).getString("theme", "SmartHome");
        switch (theme) {
            case "SmartHome":
                return R.style.SmartHome;
            case "SmartHomeLight":
                return R.style.SmartHomeLight;
        }
        return -1;
    }
}
