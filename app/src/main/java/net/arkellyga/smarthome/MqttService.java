package net.arkellyga.smarthome;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextView;

import net.arkellyga.smarthome.receivers.WifiReceiver;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttTopic;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;

import java.io.IOException;
import java.util.Locale;

public class MqttService extends Service implements MqttCallback {
    private static final String DEBUG_TAG = "MqttService";
    private static final String ACTION_RECEIVER_BUTTON = "net.arkellyga.smarthome.action.NEW_BUTTON_STATE";
    private static final String ACTION_START = DEBUG_TAG + ".Start";
    private static final String ACTION_STOP = DEBUG_TAG + ".Stop";
    private static final String ACTION_KEEPALIVE = DEBUG_TAG + ".KeepAlive";
    private static final String ACTION_RECONNECT = DEBUG_TAG + ".Reconnect";
    private static final String	MQTT_THREAD_NAME = "MqttService[" + DEBUG_TAG + "]"; // Handler Thread ID
    private static final int MQTT_KEEP_ALIVE = 300000;

    private boolean mStarted = false;
    private Handler mConnHandler;

    //MQTT Objects
    private MqttConnectOptions mOpts; // Connection Options
    private MqttTopic mKeepAliveTopic; // Instance Variable for Keepalive topic
    private MqttClient mClient; // Mqtt Client
    private MqttDefaultFilePersistence mDataStore; // Defaults to FileStore
    private String mUsername;
    private char[] mPassword;
    private String mClientId;
    private String mServer;
    private boolean mRetainedMessages;

    private AlarmManager mAlarmManager; // Alarm manager to perform repeating tasks
    private ConnectivityManager mConnectivityManager; // To check for connectivity changes
    private SharedPreferences mPrefs; // used to store service state and uniqueId

    //Topics and state of lights
    private boolean mIsHallBlue = false;
    private boolean mIsHallWork = false;
    private boolean mIsHallRgbR = false;
    private boolean mIsHallRgbG = false;
    private boolean mIsHallRgbB = false;
    private boolean mIsKitchenLight = false;
    private boolean mIsKitchenKettle = false;
    private String mHallTemperature;
    //Array for rgb colors
    private char[] mHallRGB = new char[] {'r', 'g', 'b'};
    private int mRGBi = 0;
    //Other fore creating notification
    private boolean mIsSingleColor;
    private int mColorHallBlue, mColorHallWork, mColorKitchenLight,
            mColorKitchenKettle, mColorBackground;
    private int mColorHallBlueOff, mColorHallWorkOff, mColorKitchenLightOff,
            mColorKitchenKettleOff;

    private BroadcastReceiver mBtnReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(DEBUG_TAG, "onBtnReceiver");
            if (intent.getAction() != null) {
                if (intent.getAction().equals(ACTION_RECEIVER_BUTTON)) {
                    Log.d(DEBUG_TAG, "onBtnReceiver got");
                    //invert current state for send
                    boolean state = mRGBi == 0 ? mIsHallRgbR : mRGBi == 1 ? mIsHallRgbG : mIsHallRgbB;
                    String topic = intent.getStringExtra("topic");
                    if (topic.equals("hall/led/blue")) {
                        publishData("hall/led/" + mHallRGB[mRGBi], !state ? "1" : "0");
                        if (mRGBi != 0) publishData("hall/led/" + mHallRGB[mRGBi - 1], "1");
                        if (mRGBi == 2) mRGBi = 0;
                        else mRGBi++;
                    }
                    else
                        publishData(intent.getStringExtra("topic"), state ? "1" : "0");
                    //setNotification
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(DEBUG_TAG, "onCreate");
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mUsername = mPrefs.getString("mqtt_username", "");
        mPassword = mPrefs.getString("mqtt_password", "").toCharArray();
        mClientId = mPrefs.getString("mqtt_client_id", "");
        mServer = "tcp://" +  mPrefs.getString("mqtt_server", "127.0.0.1:1883");
        mRetainedMessages = mPrefs.getBoolean("mqtt_retained", false);

        mHallTemperature = "";

        HandlerThread thread = new HandlerThread(MQTT_THREAD_NAME);
        thread.start();
        mConnHandler = new Handler(thread.getLooper());
        mDataStore = new MqttDefaultFilePersistence(getCacheDir().getAbsolutePath());
        mOpts = new MqttConnectOptions();
        mOpts.setCleanSession(true);
        if (!mUsername.isEmpty() && mPassword.length > 0) {
            mOpts.setUserName(mUsername);
            mOpts.setPassword(mPassword);
        }

        mAlarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        mConnectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        registerReceiver(new WifiReceiver(), new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        String action = intent.getAction();
        if (action == null) {
            System.out.println("Service was started with null action. Exit.");
        } else {
            switch (action) {
                case ACTION_START:
                    start();
                    break;
                case ACTION_STOP:
                    stop();
                    break;
                case ACTION_KEEPALIVE:
                    keepAlive();
                    break;
                case ACTION_RECONNECT:
                    if (isNetworkAvailable()) {
                        reconnectIfNecessary();
                    }
                    break;
            }
        }
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mStarted) {
            stop();
        } else {
            setStarted(false);
        }
    }

    private synchronized void start() {
        if (mStarted) {
            Log.d(DEBUG_TAG, "Attempt to start while already started");
            return;
        }
        if (hasScheduledKeepAlive()) {
            stopKeepAlive();
        }
        connect();
        setStarted(true);
        Log.d(DEBUG_TAG, "registerReceiver");
        registerReceiver(mBtnReceiver, new IntentFilter(ACTION_RECEIVER_BUTTON));
        registerReceiver(mConnectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        createNotification();
    }

    private synchronized void stop() {
        if (!mStarted) {
            setStarted(false);
            return;
        }
        if (mClient != null) {
            mConnHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        mClient.disconnect();
                    } catch(MqttException ex) {
                        ex.printStackTrace();
                    }
                    mClient = null;
                    mStarted = false;
                    // Save stopped state in the preferences
                    setStarted(false);
                    stopKeepAlive();
                    // Let the Activity know the service is STOPPED
                }
            });
        }
        Log.d(DEBUG_TAG, "unregisterReceiver");
        unregisterReceiver(mConnectivityReceiver);
        unregisterReceiver(mBtnReceiver);
        stopForeground(true);
    }

    private synchronized void connect() {
          try {
              mClient = new MqttClient(mServer, mClientId, mDataStore);
          } catch (MqttException ex) {
              ex.printStackTrace();
          }
          mConnHandler.post(new Runnable() {
              @Override
              public void run() {
                  try {
                      mClient.setCallback(MqttService.this);
                      mClient.connect(mOpts);
                      if ((mClient == null) || !mClient.isConnected()) {
                          Log.d(DEBUG_TAG, "subscribe error");
                      } else {
                          mClient.subscribe("#");
                          Log.d(DEBUG_TAG, "subscribing to #");
                      }
                      mStarted = true;
                      setStarted(true);
                      startKeepAlive();
                  } catch (MqttException ex) {
                      ex.printStackTrace();
                  }
              }
          });
    }

    private void startKeepAlive() {
        Intent i = new Intent();
        i.setClass(this, MqttService.class);
        i.setAction(ACTION_KEEPALIVE);
        PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
        mAlarmManager.setRepeating(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + MQTT_KEEP_ALIVE,
                MQTT_KEEP_ALIVE, pi);
    }

    private void stopKeepAlive() {
        Intent i = new Intent();
        i.setClass(this, MqttService.class);
        PendingIntent pi = PendingIntent.getService(this, 0, i , 0);
        mAlarmManager.cancel(pi);
    }

    // Publishes a KeepALive to the topic in the broker
    private synchronized void keepAlive() {
        if(isConnected()) {
            try {
                sendKeepAlive();
                return;
            } catch(MqttException ex) {
                Log.d(DEBUG_TAG, "exception ex=" + ex);
                return;
            }
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        String msg = new String(message.getPayload());
        switch (topic) {
            case "hall/led/r": mIsHallRgbR = msg.equals("1"); mIsHallWork = msg.equals("1"); break;
            case "hall/led/g": mIsHallRgbG = msg.equals("1"); mIsHallWork = msg.equals("1"); break;
            case "hall/led/b": mIsHallRgbB = msg.equals("1"); mIsHallWork = msg.equals("1"); break;
            case "hall/led/work": mIsHallWork = msg.equals("1"); break;
            case "hall/temp": mHallTemperature = "Hall temperature: " + msg + " C"; break;
            case "kitchen/light": mIsKitchenLight = msg.equals("1"); break;
            case "kitchen/kettle": mIsKitchenKettle = msg.equals("1"); break;
        }
        createNotification();
        Log.d(DEBUG_TAG, topic + ": " + msg);
    }

    // Checks the current connectivity and reconnects if it is required.
    private synchronized void reconnectIfNecessary() {
        if(mStarted && mClient == null) {
            connect();
        }
    }

    private boolean isNetworkAvailable() {
        NetworkInfo info = mConnectivityManager.getActiveNetworkInfo();
        return info != null && info.isAvailable();
    }

    private boolean isConnected() {
        if(mStarted && mClient != null && !mClient.isConnected()) {
            Log.i(DEBUG_TAG,"Mismatch between what we think is connected and what is connected");
        }
        return mClient != null && ((mStarted && mClient.isConnected()));
    }

    // Receiver that listens for connectivity change via ConnectivityManager
    private final BroadcastReceiver mConnectivityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get network info
            NetworkInfo info = intent.getParcelableExtra (ConnectivityManager.EXTRA_NETWORK_INFO);
            // Is there connectivity?
            boolean hasConnectivity = (info != null && info.isConnected());
            Log.d(DEBUG_TAG, "Connectivity changed: connected=" + hasConnectivity);
            if (hasConnectivity) {
                Log.d(DEBUG_TAG, "Attempting reconnect");
                reconnectIfNecessary();
           }
        }
    };

    // Sends a Keep Alive message to the specified topic
    private synchronized MqttDeliveryToken sendKeepAlive()
            throws MqttException {
        if(!isConnected()) throw new MqttException(MqttException.REASON_CODE_CONNECTION_LOST);
        mKeepAliveTopic = mClient.getTopic(String.format(Locale.US,"%s/keepAlive", mClientId));
        Log.d(DEBUG_TAG, "sendKeepAlive");
        MqttMessage message = new MqttMessage(new byte[] {0});
        message.setQos(0);
        return mKeepAliveTopic.publish(message);
    }

    private synchronized void publishData(String topic, String msg) {
        if (!isConnected()) {
            Log.d(DEBUG_TAG, "publishData error. Have no connection.");
            return;
        }
        MqttMessage message = new MqttMessage(msg.getBytes());
        message.setQos(0);
        message.setRetained(mRetainedMessages);
        try {
            mClient.publish(topic, message);
        } catch (MqttException ex) {
            ex.printStackTrace();
            Log.d(DEBUG_TAG, "mqttError: publishData");
        }
    }

    // Query's the AlarmManager to check if there is a keep alive currently scheduled
    private synchronized boolean hasScheduledKeepAlive() {
        Intent i = new Intent();
        i.setClass(this, MqttService.class);
        i.setAction(ACTION_KEEPALIVE);
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, i, PendingIntent.FLAG_NO_CREATE);
        return pi != null;
    }

    @Override
    public void connectionLost(Throwable cause) {
        Log.d(DEBUG_TAG, "connectionLost");
        // we protect against the phone switching off while we're doing this by requesting a wake lock
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MQTT");
        wl.acquire();
        stopKeepAlive();
        //setStarted(false);
        mClient = null;
        if(isNetworkAvailable()) {
            Log.d(DEBUG_TAG, "Attempting reconnect");
            reconnectIfNecessary();
        }
        // we're finished - if the phone is switched off, it's okay for the CPU to sleep now
        wl.release();
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    // Sets whether or not the service has been started in the preferences.
    private void setStarted(boolean started) {
        mStarted = started;
    }

    public static void actionStart(Context ctx) {
        Log.d(DEBUG_TAG, "actionStart");
        Intent i = new Intent(ctx, MqttService.class);
        i.setAction(ACTION_START);
        ctx.startService(i);
    }

    public static void actionStop(Context ctx) {
        Intent i = new Intent(ctx, MqttService.class);
        i.setAction(ACTION_STOP);
        ctx.startService(i);
    }

    public static void actionKeepalive(Context ctx) {
        Intent i = new Intent(ctx, MqttService.class);
        i.setAction(ACTION_KEEPALIVE);
        ctx.startService(i);
    }

    public static void actionReconnect(Context ctx) {
        Intent i = new Intent(ctx, MqttService.class);
        i.setAction(ACTION_RECONNECT);
        ctx.startService(i);
    }

    public void createNotification() {
        Log.d("MainActivity", "startNotify");
        getColors();
        // setup images paths for buttons
        Uri imageHallBlue, imageHallWork, imageKitchenLight, imageKitchenKettle;
        imageHallBlue = Uri.parse(PreferenceManager.getDefaultSharedPreferences(this)
                .getString("button_hall_blue_image",""));
        imageHallWork = Uri.parse(PreferenceManager.getDefaultSharedPreferences(this)
                .getString("button_hall_work_image",""));
        imageKitchenLight = Uri.parse(PreferenceManager.getDefaultSharedPreferences(this)
                .getString("button_kitchen_light_image",""));
        imageKitchenKettle = Uri.parse(PreferenceManager.getDefaultSharedPreferences(this)
                .getString("button_kitchen_kettle_image",""));
        //Layout of notification
        RemoteViews layoutNotification = new RemoteViews(this.getPackageName(), R.layout.notification);
        // setup images for layout
        Bitmap bitmapHallBlue, bitmapHallWork, bitmapKitchenLight, bitmapKitchenKettle;
        try {
            if (imageHallBlue.getPath().isEmpty())
                bitmapHallBlue = BitmapFactory.decodeResource(this.getResources(), R.drawable.hall_btn);
            else
                bitmapHallBlue = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageHallBlue);

            if (imageHallBlue.getPath().isEmpty())
                bitmapHallWork = BitmapFactory.decodeResource(this.getResources(), R.drawable.workspace_btn);
            else
                bitmapHallWork = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageHallWork);

            if (imageHallBlue.getPath().isEmpty())
                bitmapKitchenLight = BitmapFactory.decodeResource(this.getResources(), R.drawable.kitchen_light);
            else
                bitmapKitchenLight = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageKitchenLight);

            if (imageHallBlue.getPath().isEmpty())
                bitmapKitchenKettle = BitmapFactory.decodeResource(this.getResources(), R.drawable.kitchen_kettle);
            else
                bitmapKitchenKettle = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageKitchenKettle);
            bitmapHallBlue = changeBitmapColor(bitmapHallBlue,
                    mIsHallRgbR ? Color.RED : mIsHallRgbG ? Color.GREEN : mIsHallRgbB ? Color.BLUE : mColorHallBlueOff); // for rgb
            bitmapHallWork = changeBitmapColor(bitmapHallWork,
                    mIsHallWork ? mColorHallWork : mColorHallWorkOff);
            bitmapKitchenLight = changeBitmapColor(bitmapKitchenLight,
                    mIsKitchenLight ? mColorKitchenLight : mColorKitchenLightOff);
            bitmapKitchenKettle = changeBitmapColor(bitmapKitchenKettle,
                    mIsKitchenKettle ? mColorKitchenKettle : mColorKitchenKettleOff);
            layoutNotification.setBitmap(R.id.blue_light_button_notification, "setImageBitmap", bitmapHallBlue);
            layoutNotification.setBitmap(R.id.work_light_button_notification, "setImageBitmap", bitmapHallWork);
            layoutNotification.setBitmap(R.id.kitchen_light_button_notification, "setImageBitmap", bitmapKitchenLight);
            layoutNotification.setBitmap(R.id.kitchen_kettle_button_notification, "setImageBitmap", bitmapKitchenKettle);

        } catch (IOException e) {
            e.printStackTrace();
        }
        //Set onClick for first button
        layoutNotification.setOnClickPendingIntent(R.id.work_light_button_notification,
                getPIByValues("hall/led/work", mIsHallWork, Const.NOTIFICATION_LED_HALL_WORK));
        //Set onClick for second button
        layoutNotification.setOnClickPendingIntent(R.id.blue_light_button_notification,
                getPIByValues("hall/led/blue", mIsHallBlue, Const.NOTIFICATION_LED_HALL_BLUE));
        //Set onClick for third button (kitchen/led)
        layoutNotification.setOnClickPendingIntent(R.id.kitchen_light_button_notification,
                getPIByValues("kitchen/light", mIsKitchenLight, Const.NOTIFICATION_LED_KITCHEN_LIGHT));
        //Set onClick for fourth button (kitchen/kettle)
        layoutNotification.setOnClickPendingIntent(R.id.kitchen_kettle_button_notification,
                getPIByValues("kitchen/kettle", mIsKitchenKettle, Const.NOTIFICATION_LED_KITCHEN_KETTLE));

        //Set backgroundColor
        layoutNotification.setInt(R.id.background_notification, "setBackgroundColor",
                mColorBackground);
        //Set hallTemperature

        layoutNotification.setTextViewText(R.id.hall_temperature_textview_notification, mHallTemperature);

        //Building notification
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, "1")
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setPriority(NotificationCompat.PRIORITY_MIN)
                        .setCustomBigContentView(layoutNotification)
                        .setOngoing(true);
        Notification notification = builder.build();
        NotificationManager notificationManager =
                (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null)
            startForeground(Const.NOTIFICATION_ID, notification);
    }

    private void getColors() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        mIsSingleColor = preferences.getBoolean("button_single_color", false);
        // setup colors for buttons
        if (mIsSingleColor) {
            mColorHallBlue = preferences.getInt("button_hall_blue_color", Color.WHITE);
            mColorHallWork = mColorHallBlue;
            mColorKitchenLight = mColorHallBlue;
            mColorKitchenKettle = mColorHallBlue;
            mColorHallBlueOff = preferences.getInt("button_hall_blue_color_off", Color.GRAY);
            mColorHallWorkOff = mColorHallBlueOff;
            mColorKitchenLightOff = mColorHallBlueOff;
            mColorKitchenKettleOff = mColorHallBlueOff;
        } else {
            mColorHallBlue = preferences.getInt("button_hall_blue_color", Color.WHITE);
            mColorHallWork = preferences.getInt("button_hall_work_color", Color.WHITE);
            mColorKitchenLight = preferences.getInt("button_kitchen_light_color", Color.WHITE);
            mColorKitchenKettle = preferences.getInt("button_kitchen_kettle_color", Color.WHITE);
            mColorHallBlueOff = preferences.getInt("button_hall_blue_color_off", Color.GRAY);
            mColorHallWorkOff = preferences.getInt("button_hall_work_color_off", Color.GRAY);;
            mColorKitchenLightOff = preferences.getInt("button_kitchen_light_color_off", Color.GRAY);
            mColorKitchenKettleOff = preferences.getInt("button_kitchen_kettle_color_off", Color.GRAY);
        }
        mColorBackground = preferences.getInt("background_color", Color.BLACK);
    }

    //Set colorize filter for images
    private static Bitmap changeBitmapColor(Bitmap source, int color) {
        Bitmap result = Bitmap.createBitmap(source, 0, 0,
                source.getWidth() - 1, source.getHeight() - 1);
        Paint p = new Paint();
        ColorFilter filter = new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY);
        p.setColorFilter(filter);
        Canvas canvas = new Canvas(result);
        canvas.drawBitmap(result, 0, 0, p);
        return result;
    }

    //Get pending intent for every button in layout
    private PendingIntent getPIByValues(String extraValue, boolean state, int pendingId) {
        Intent intent = new Intent(ACTION_RECEIVER_BUTTON);
        intent.putExtra("topic", extraValue);
        intent.putExtra("state", state);
        return PendingIntent.getBroadcast(this, pendingId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {return null;}
}
