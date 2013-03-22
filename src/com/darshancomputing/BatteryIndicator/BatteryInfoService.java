/*
    Copyright (c) 2009-2013 Darshan-Josiah Barber

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.
*/

package com.darshancomputing.BatteryIndicator;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;
import java.util.Date;

public class BatteryInfoService extends Service {
    private final IntentFilter batteryChanged = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    private final IntentFilter userPresent    = new IntentFilter(Intent.ACTION_USER_PRESENT);
    private PendingIntent mainWindowPendingIntent;
    private PendingIntent updatePredictorPendingIntent;

    private NotificationManager mNotificationManager;
    private AlarmManager alarmManager;
    private SharedPreferences settings;
    private SharedPreferences sp_store;

    private Context context;
    private Resources res;
    private Str str;
    private BatteryLevel bl;
    private BatteryInfo info;
    private java.util.HashSet<Messenger> clientMessengers;
    private final Messenger messenger = new Messenger(new MessageHandler());

    private static final String LOG_TAG = "com.darshancomputing.BatteryIndicator - BatteryInfoService";

    private static final int NOTIFICATION_PRIMARY      = 1;
    private static final int NOTIFICATION_KG_UNLOCKED  = 2;
    private static final int NOTIFICATION_ALARM_CHARGE = 3;
    private static final int NOTIFICATION_ALARM_HEALTH = 4;
    private static final int NOTIFICATION_ALARM_TEMP   = 5;

    public static final String KEY_PREVIOUS_CHARGE = "previous_charge";
    public static final String KEY_PREVIOUS_TEMP = "previous_temp";
    public static final String KEY_PREVIOUS_HEALTH = "previous_health";
    public static final String KEY_DISABLE_LOCKING = "disable_lock_screen";
    public static final String KEY_SERVICE_DESIRED = "serviceDesired";

    private static final String EXTRA_UPDATE_PREDICTOR = "com.darshancomputing.BatteryBot.EXTRA_UPDATE_PREDICTOR";


    private static final Object[] EMPTY_OBJECT_ARRAY = {};
    private static final  Class[]  EMPTY_CLASS_ARRAY = {};

    private static final int plainIcon0 = R.drawable.plain000;
    private static final int small_plainIcon0 = R.drawable.small_plain000;
    private static final int chargingIcon0 = R.drawable.charging000;
    private static final int small_chargingIcon0 = R.drawable.small_charging000;

    /* Global variables for these Notification Runnables */
    private Notification mainNotification;
    private String mainNotificationTitle, mainNotificationText;
    private RemoteViews notificationRV;

    private Predictor predictor;

    private final Handler mHandler = new Handler();

    private final Runnable mNotify = new Runnable() {
        public void run() {
            startForeground(NOTIFICATION_PRIMARY, mainNotification);
            mHandler.removeCallbacks(mNotify);
        }
    };

    @Override
    public void onCreate() {
        res = getResources();
        str = new Str(res);
        context = getApplicationContext();

        info = new BatteryInfo();

        clientMessengers = new java.util.HashSet<Messenger>();

        predictor = new Predictor(context);
        bl = new BatteryLevel(context, BatteryLevel.SIZE_NOTIFICATION);
        notificationRV = new RemoteViews(getPackageName(), R.layout.main_notification);
        notificationRV.setImageViewBitmap(R.id.battery_level_view, bl.getBitmap());

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        loadSettingsFiles();

        Intent mainWindowIntent = new Intent(context, BatteryInfoActivity.class);
        mainWindowPendingIntent = PendingIntent.getActivity(context, 0, mainWindowIntent, 0);

        Intent updatePredictorIntent = new Intent(context, BatteryInfoService.class);
        updatePredictorIntent.putExtra(EXTRA_UPDATE_PREDICTOR, true);
        updatePredictorPendingIntent = PendingIntent.getService(context, 0, updatePredictorIntent, 0);

        registerReceiver(mBatteryInfoReceiver, batteryChanged);
    }

    @Override
    public void onDestroy() {
        alarmManager.cancel(updatePredictorPendingIntent);
        unregisterReceiver(mBatteryInfoReceiver);
        mHandler.removeCallbacks(mNotify);
        mNotificationManager.cancelAll();
        stopForeground(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra(EXTRA_UPDATE_PREDICTOR, false))
            update(null);

        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    public class MessageHandler extends Handler {
        @Override
        public void handleMessage(Message incoming) {
            switch (incoming.what) {
            case RemoteConnection.SERVICE_CLIENT_CONNECTED:
                sendClientMessage(incoming.replyTo, RemoteConnection.CLIENT_SERVICE_CONNECTED);
                break;
            case RemoteConnection.SERVICE_REGISTER_CLIENT:
                clientMessengers.add(incoming.replyTo);
                sendClientMessage(incoming.replyTo, RemoteConnection.CLIENT_BATTERY_INFO_UPDATED, info.toBundle());
                break;
            case RemoteConnection.SERVICE_UNREGISTER_CLIENT:
                clientMessengers.remove(incoming.replyTo);
                break;
            case RemoteConnection.SERVICE_RELOAD_SETTINGS:
                reloadSettings(false);
                break;
            case RemoteConnection.SERVICE_CANCEL_NOTIFICATION_AND_RELOAD_SETTINGS:
                reloadSettings(true);
                break;
            default:
                super.handleMessage(incoming);
            }
        }
    }

    private void sendClientMessage(Messenger clientMessenger, int what) {
        sendClientMessage(clientMessenger, what, null);
    }

    private void sendClientMessage(Messenger clientMessenger, int what, Bundle data) {
        Message outgoing = Message.obtain();
        outgoing.what = what;
        outgoing.replyTo = messenger;
        outgoing.setData(data);
        try { clientMessenger.send(outgoing); } catch (android.os.RemoteException e) {}
    }

    public static class RemoteConnection implements ServiceConnection {
        // Messages clients send to the service
        public static final int SERVICE_CLIENT_CONNECTED = 0;
        public static final int SERVICE_REGISTER_CLIENT = 1;
        public static final int SERVICE_UNREGISTER_CLIENT = 2;
        public static final int SERVICE_RELOAD_SETTINGS = 3;
        public static final int SERVICE_CANCEL_NOTIFICATION_AND_RELOAD_SETTINGS = 4;

        // Messages the service sends to clients
        public static final int CLIENT_SERVICE_CONNECTED = 0;
        public static final int CLIENT_BATTERY_INFO_UPDATED = 1;

        public Messenger serviceMessenger;
        private Messenger clientMessenger;

        public RemoteConnection(Messenger m) {
            clientMessenger = m;
        }

        public void onServiceConnected(ComponentName name, IBinder iBinder) {
            serviceMessenger = new Messenger(iBinder);

            Message outgoing = Message.obtain();
            outgoing.what = SERVICE_CLIENT_CONNECTED;
            outgoing.replyTo = clientMessenger;
            try { serviceMessenger.send(outgoing); } catch (android.os.RemoteException e) {}
        }

        public void onServiceDisconnected(ComponentName name) {
            serviceMessenger = null;
        }
    }

    private void loadSettingsFiles() {
        settings = context.getSharedPreferences(SettingsActivity.SETTINGS_FILE, Context.MODE_MULTI_PROCESS);
        sp_store = context.getSharedPreferences(SettingsActivity.SP_STORE_FILE, Context.MODE_MULTI_PROCESS);
    }

    private void reloadSettings(boolean cancelFirst) {
        loadSettingsFiles();

        str = new Str(res); // Language override may have changed

        if (cancelFirst) stopForeground(true);

        registerReceiver(mBatteryInfoReceiver, batteryChanged);
    }

    private final BroadcastReceiver mBatteryInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            if (! Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) return;

            update(intent);
        }
    };

    private void update(Intent intent) {
        if (intent != null)
            updateBatteryInfo(intent);
        else
            predictor.update(info);

        if (statusHasChanged())
            handleUpdateWithChangedStatus();
        else
            handleUpdateWithSameStatus();

        for (Messenger messenger : clientMessengers) {
            // TODO: Can I send the same message to multiple clients instead of sending duplicates?
            sendClientMessage(messenger, RemoteConnection.CLIENT_BATTERY_INFO_UPDATED, info.toBundle());
        }

        prepareNotification();
        doNotify();

        alarmManager.set(AlarmManager.ELAPSED_REALTIME, android.os.SystemClock.elapsedRealtime() + (2 * 60 * 1000), updatePredictorPendingIntent);
    }

    private void prepareNotification() {
        if (settings.getBoolean(SettingsActivity.KEY_NOTIFY_STATUS_DURATION, false)) {
            long statusDuration = System.currentTimeMillis() - info.last_status_cTM;
            int statusDurationHours = (int)((statusDuration + (1000 * 60 * 30)) / (1000 * 60 * 60));

            mainNotificationTitle = str.statuses[info.status] + " ";
            if (statusDuration < 1000 * 60 * 60)
                mainNotificationTitle += str.since + " " + formatTime(new Date(info.last_status_cTM));
            else
                mainNotificationTitle += str.for_n_hours(statusDurationHours);
        } else if (info.prediction.what == BatteryInfo.Prediction.NONE) {
            mainNotificationTitle = str.statuses[info.status];
        } else {
            if (info.prediction.days > 0)
                mainNotificationTitle = str.n_days_m_hours(info.prediction.days, info.prediction.hours);
            else if (info.prediction.hours > 0)
                mainNotificationTitle = str.n_hours_m_minutes_short(info.prediction.hours, info.prediction.minutes);
            else
                mainNotificationTitle = str.n_minutes_long(info.prediction.minutes);

            if (info.prediction.what == BatteryInfo.Prediction.UNTIL_CHARGED)
                mainNotificationTitle += res.getString(R.string.notification_until_charged);
            else
                mainNotificationTitle += res.getString(R.string.notification_until_drained);
        }

        Boolean convertF = settings.getBoolean(SettingsActivity.KEY_CONVERT_F, false);
        mainNotificationText = str.healths[info.health] + " / " + str.formatTemp(info.temperature, convertF);

        if (info.voltage > 500)
            mainNotificationText += " / " + str.formatVoltage(info.voltage);

        // TODO: Is it necessary to call new() every time here, or can I get away with just setting the icon on existing Notif.?
        mainNotification = new Notification(iconFor(info.percent), null, 0l);

        //if (android.os.Build.VERSION.SDK_INT < 11) {
            notificationRV = new RemoteViews(getPackageName(), R.layout.main_notification);
            notificationRV.setImageViewBitmap(R.id.battery_level_view, bl.getBitmap());
        //}

        if (android.os.Build.VERSION.SDK_INT >= 16) {
            mainNotification.priority = Integer.valueOf(settings.getString(SettingsActivity.KEY_MAIN_NOTIFICATION_PRIORITY,
                                                                           str.default_main_notification_priority));
        }

        mainNotification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;

        bl.setLevel(info.percent);

        notificationRV.setTextViewText(R.id.percent, "" + info.percent + str.percent_symbol);
        notificationRV.setTextViewText(R.id.top_line, android.text.Html.fromHtml(mainNotificationTitle));
        notificationRV.setTextViewText(R.id.bottom_line, mainNotificationText);

        mainNotification.contentIntent = mainWindowPendingIntent;
        mainNotification.contentView = notificationRV;
    }

    private void doNotify() {
        mHandler.post(mNotify);
    }

    // I take advantage of (count on) R.java having resources alphabetical and incrementing by one.
    private int iconFor(int percent) {
        String default_set = "builtin.classic";
        if (android.os.Build.VERSION.SDK_INT >= 11)
            default_set = "builtin.plain_number";

        String icon_set = settings.getString(SettingsActivity.KEY_ICON_SET, "null");
        if (! icon_set.startsWith("builtin.")) icon_set = "null"; // TODO: Remove this line to re-enable plugins

        if (icon_set.equals("null")) {
            icon_set = default_set;

            SharedPreferences.Editor settings_editor = settings.edit();
            settings_editor.putString(SettingsActivity.KEY_ICON_SET, default_set);
            settings_editor.commit();
        }

        Boolean indicate_charging = settings.getBoolean(SettingsActivity.KEY_INDICATE_CHARGING, true);

        if (icon_set.equals("builtin.plain_number")) {
            return ((info.status == BatteryInfo.STATUS_CHARGING && indicate_charging) ? chargingIcon0 : plainIcon0) + info.percent;
        } else if (icon_set.equals("builtin.smaller_number")) {
            return ((info.status == BatteryInfo.STATUS_CHARGING && indicate_charging) ? small_chargingIcon0 : small_plainIcon0) + info.percent;
        } else {
            return R.drawable.b000 + info.percent;
        }
    }

    private void updateBatteryInfo(Intent intent) {
        info.load(intent, sp_store);
        predictor.update(info);
    }

    private boolean statusHasChanged() {
        int previous_charge = sp_store.getInt(KEY_PREVIOUS_CHARGE, 100);

        return (info.last_status != info.status ||
                info.last_status_cTM == BatteryInfo.DEFAULT_LAST_STATUS_CTM ||
                info.last_percent == BatteryInfo.DEFAULT_LAST_PERCENT ||
                info.last_status_cTM > System.currentTimeMillis() ||
                info.last_plugged != info.plugged ||
                (info.plugged == BatteryInfo.PLUGGED_UNPLUGGED && info.percent > previous_charge + 20));
    }

    private void handleUpdateWithChangedStatus() {
        SharedPreferences.Editor editor = sp_store.edit();
        long time = System.currentTimeMillis();

        editor.putLong(BatteryInfo.KEY_LAST_STATUS_CTM, time);
        editor.putInt(BatteryInfo.KEY_LAST_STATUS, info.status);
        editor.putInt(BatteryInfo.KEY_LAST_PERCENT, info.percent);
        editor.putInt(BatteryInfo.KEY_LAST_PLUGGED, info.plugged);
        editor.putInt(KEY_PREVIOUS_CHARGE, info.percent);
        editor.putInt(KEY_PREVIOUS_TEMP, info.temperature);
        editor.putInt(KEY_PREVIOUS_HEALTH, info.health);

        editor.commit();
    }

    private void handleUpdateWithSameStatus() {
        SharedPreferences.Editor editor = sp_store.edit();
        long time = System.currentTimeMillis();

        if (info.percent % 10 == 0) {
            editor.putInt(KEY_PREVIOUS_CHARGE, info.percent);
            editor.putInt(KEY_PREVIOUS_TEMP, info.temperature);
            editor.putInt(KEY_PREVIOUS_HEALTH, info.health);
        }

        editor.commit();
    }

    private String formatTime(Date d) {
        String format = android.provider.Settings.System.getString(getContentResolver(),
                                                                   android.provider.Settings.System.TIME_12_24);
        if (format == null || format.equals("12")) {
            return java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT,
                                                        java.util.Locale.getDefault()).format(d);
        } else {
            return (new java.text.SimpleDateFormat("HH:mm")).format(d);
        }
    }
}
