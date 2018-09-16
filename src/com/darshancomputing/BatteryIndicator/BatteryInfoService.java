/*
    Copyright (c) 2009-2018 Darshan-Josiah Barber

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
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.widget.RemoteViews;

import java.util.Date;
import java.util.HashSet;

public class BatteryInfoService extends Service {
    private final IntentFilter batteryChanged = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    //private final IntentFilter userPresent    = new IntentFilter(Intent.ACTION_USER_PRESENT);
    private PendingIntent currentInfoPendingIntent, updatePredictorPendingIntent;

    private NotificationManager mNotificationManager;
    private AlarmManager alarmManager;
    private SharedPreferences settings;
    private SharedPreferences sp_service;
    private SharedPreferences.Editor sps_editor;

    public static final String CHAN_ID_OLD_MAIN = "main";
    public static final String CHAN_ID_MAIN = "main_002";

    private Resources res;
    //private BatteryLevel bl;
    private CircleWidgetBackground cwbg;
    private BatteryInfo info;
    private long now;
    private boolean updated_lasts;
    private static java.util.HashSet<Messenger> clientMessengers;
    private static Messenger messenger;

    private static HashSet<Integer> widgetIds = new HashSet<Integer>();
    private static AppWidgetManager widgetManager;


    //private static final String LOG_TAG = "com.darshancomputing.BatteryIndicator - BatteryInfoService";

    private static final int NOTIFICATION_PRIMARY      = 1;
    //private static final int NOTIFICATION_KG_UNLOCKED  = 2;
    //private static final int NOTIFICATION_ALARM_CHARGE = 3;
    //private static final int NOTIFICATION_ALARM_HEALTH = 4;
    //private static final int NOTIFICATION_ALARM_TEMP   = 5;

    public static final String KEY_PREVIOUS_CHARGE = "previous_charge";
    public static final String KEY_PREVIOUS_TEMP = "previous_temp";
    public static final String KEY_PREVIOUS_HEALTH = "previous_health";
    public static final String KEY_DISABLE_LOCKING = "disable_lock_screen";
    public static final String KEY_SERVICE_DESIRED = "serviceDesired";
    public static final String KEY_SHOW_NOTIFICATION = "show_notification";
    public static final String LAST_SDK_API = "last_sdk_api";

    private static final String EXTRA_UPDATE_PREDICTOR = "com.darshancomputing.BatteryBot.EXTRA_UPDATE_PREDICTOR";


    //private static final Object[] EMPTY_OBJECT_ARRAY = {};
    //private static final  Class[]  EMPTY_CLASS_ARRAY = {};

    private static final int plainIcon0 = R.drawable.plain000;
    private static final int small_plainIcon0 = R.drawable.small_plain000;
    private static final int chargingIcon0 = R.drawable.charging000;
    private static final int small_chargingIcon0 = R.drawable.small_charging000;

    /* Global variables for these Notification Runnables */
    private Notification.Builder mainNotificationB;
    private String mainNotificationTopLine, mainNotificationBottomLine;
    //private RemoteViews notificationRV;

    private Predictor predictor;

    private final Handler mHandler = new Handler();

    private final Runnable mNotify = new Runnable() {
        public void run() {
            startForeground(NOTIFICATION_PRIMARY, mainNotificationB.build());
            mHandler.removeCallbacks(mNotify);
        }
    };

    @Override
    public void onCreate() {
        res = getResources();
        Str.setResources(res);
        info = new BatteryInfo();

        messenger = new Messenger(new MessageHandler(this));
        clientMessengers = new java.util.HashSet<Messenger>();

        predictor = new Predictor(this);
        //bl = BatteryLevel.getInstance(this, BatteryLevel.SIZE_NOTIFICATION);
        cwbg = new CircleWidgetBackground(this);

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        mNotificationManager.deleteNotificationChannel(CHAN_ID_OLD_MAIN);

        int main_importance = NotificationManager.IMPORTANCE_MIN;
        if (android.os.Build.VERSION.SDK_INT < 28) {
            main_importance = NotificationManager.IMPORTANCE_LOW;
        }
        CharSequence main_notif_chan_name = getString(R.string.main_notif_chan_name);
        NotificationChannel mChannel = new NotificationChannel(CHAN_ID_MAIN, main_notif_chan_name, main_importance);
        mChannel.setSound(null, null);
        mChannel.enableLights(false);
        mChannel.enableVibration(false);
        mChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        mNotificationManager.createNotificationChannel(mChannel);
        mainNotificationB = new Notification.Builder(this);

        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        loadSettingsFiles();
        sdkVersioning();

        Intent currentInfoIntent = new Intent(this, BatteryInfoActivity.class);
        currentInfoPendingIntent = PendingIntent.getActivity(this, 0, currentInfoIntent, 0);

        Intent updatePredictorIntent = new Intent(this, BatteryInfoService.class);
        updatePredictorIntent.putExtra(EXTRA_UPDATE_PREDICTOR, true);
        updatePredictorPendingIntent = PendingIntent.getService(this, 0, updatePredictorIntent, 0);

        widgetManager = AppWidgetManager.getInstance(this);

        Class<?>[] appWidgetProviders = {BatteryInfoAppWidgetProvider.class /* Circle widget! */
                                      };

        for (int i = 0; i < appWidgetProviders.length; i++) {
            int[] ids = widgetManager.getAppWidgetIds(new ComponentName(this, appWidgetProviders[i]));

            for (int j = 0; j < ids.length; j++) {
                widgetIds.add(ids[j]);
            }
        }

        Intent bc_intent = registerReceiver(mBatteryInfoReceiver, batteryChanged);
        info.load(bc_intent, sp_service);
    }

    @Override
    public void onDestroy() {
        alarmManager.cancel(updatePredictorPendingIntent);
        unregisterReceiver(mBatteryInfoReceiver);
        mHandler.removeCallbacks(mNotify);
        mNotificationManager.cancelAll();
        updateWidgets(null);
        stopForeground(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Always update
        update(null);

        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    private static class MessageHandler extends Handler {
        private BatteryInfoService bis;

        MessageHandler(BatteryInfoService s) {
            bis = s;
        }

        @Override
        public void handleMessage(Message incoming) {
            switch (incoming.what) {
            case RemoteConnection.SERVICE_CLIENT_CONNECTED:
                sendClientMessage(incoming.replyTo, RemoteConnection.CLIENT_SERVICE_CONNECTED);
                break;
            case RemoteConnection.SERVICE_REGISTER_CLIENT:
                clientMessengers.add(incoming.replyTo);
                sendClientMessage(incoming.replyTo, RemoteConnection.CLIENT_BATTERY_INFO_UPDATED, bis.info.toBundle());

                break;
            case RemoteConnection.SERVICE_UNREGISTER_CLIENT:
                clientMessengers.remove(incoming.replyTo);
                break;
            case RemoteConnection.SERVICE_RELOAD_SETTINGS:
                bis.reloadSettings(false);
                break;
            case RemoteConnection.SERVICE_CANCEL_NOTIFICATION_AND_RELOAD_SETTINGS:
                bis.reloadSettings(true);
                break;
            default:
                super.handleMessage(incoming);
            }
        }
    }

    private static void sendClientMessage(Messenger clientMessenger, int what) {
        sendClientMessage(clientMessenger, what, null);
    }

    private static void sendClientMessage(Messenger clientMessenger, int what, Bundle data) {
        Message outgoing = Message.obtain();
        outgoing.what = what;
        outgoing.replyTo = messenger;
        outgoing.setData(data);
        try { clientMessenger.send(outgoing); } catch (android.os.RemoteException e) {}
    }

    static class RemoteConnection implements ServiceConnection {
        // Messages clients send to the service
        static final int SERVICE_CLIENT_CONNECTED = 0;
        static final int SERVICE_REGISTER_CLIENT = 1;
        static final int SERVICE_UNREGISTER_CLIENT = 2;
        static final int SERVICE_RELOAD_SETTINGS = 3;
        static final int SERVICE_CANCEL_NOTIFICATION_AND_RELOAD_SETTINGS = 4;

        // Messages the service sends to clients
        static final int CLIENT_SERVICE_CONNECTED = 0;
        static final int CLIENT_BATTERY_INFO_UPDATED = 1;

        Messenger serviceMessenger;
        private Messenger clientMessenger;

        RemoteConnection(Messenger m) {
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
        settings = getSharedPreferences(SettingsActivity.SETTINGS_FILE, Context.MODE_MULTI_PROCESS);
        sp_service = getSharedPreferences(SettingsActivity.SP_SERVICE_FILE, Context.MODE_MULTI_PROCESS);
    }

    private void reloadSettings(boolean cancelFirst) {
        loadSettingsFiles();

        Str.setResources(res); // Language override may have changed

        applyNewSettings(cancelFirst);
    }

    private void applyNewSettings(boolean cancelFirst) {
        if (cancelFirst) {
            stopForeground(true);
            mainNotificationB = new Notification.Builder(this);
        }

        registerReceiver(mBatteryInfoReceiver, batteryChanged);
    }

    private final BroadcastReceiver mBatteryInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            if (! Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) return;

            update(intent);
        }
    };

    // Does anything needed when SDK API level increases and sets LAST_SDK_API
    private void sdkVersioning(){
        SharedPreferences.Editor sps_editor = sp_service.edit();
        //SharedPreferences.Editor settings_editor = settings.edit();

        sps_editor.putInt(LAST_SDK_API, android.os.Build.VERSION.SDK_INT);

        sps_editor.apply();
        //Str.apply(settings_editor);
    }

    private void update(Intent intent) {
        now = System.currentTimeMillis();
        sps_editor = sp_service.edit();
        updated_lasts = false;

        if (intent != null)
            info.load(intent, sp_service);

        predictor.update(info);
        info.prediction.updateRelativeTime();

        if (statusHasChanged())
            handleUpdateWithChangedStatus();
        else
            handleUpdateWithSameStatus();

        if (sp_service.getBoolean(KEY_SHOW_NOTIFICATION, true)) {
            prepareNotification();
            doNotify();
        }

        updateWidgets(info);

        syncSpsEditor(); // Important to sync after other Service code that uses 'lasts' but before sending info to client

        for (Messenger messenger : clientMessengers) {
            // TODO: Can I send the same message to multiple clients instead of sending duplicates?
            sendClientMessage(messenger, RemoteConnection.CLIENT_BATTERY_INFO_UPDATED, info.toBundle());
        }

        alarmManager.set(AlarmManager.ELAPSED_REALTIME, android.os.SystemClock.elapsedRealtime() + (2 * 60 * 1000), updatePredictorPendingIntent);
    }

    private void updateWidgets(BatteryInfo info) {
        if (info == null)
            cwbg.setLevel(0);
        else
            cwbg.setLevel(info.percent);

        for (Integer widgetId : widgetIds) {
            android.appwidget.AppWidgetProviderInfo awpInfo = widgetManager.getAppWidgetInfo(widgetId);
            if (awpInfo == null) continue; // Based on Developer Console crash reports, this can be null sometimes

            RemoteViews rv = new RemoteViews(getPackageName(), R.layout.circle_app_widget);

            if (info == null)
                rv.setTextViewText(R.id.level, "XX" + Str.percent_symbol);
            else
                rv.setTextViewText(R.id.level, "" + info.percent + Str.percent_symbol);

            rv.setImageViewBitmap(R.id.circle_widget_image_view, cwbg.getBitmap());
            rv.setOnClickPendingIntent(R.id.widget_layout, currentInfoPendingIntent);
            try {
                widgetManager.updateAppWidget(widgetId, rv);
            } catch(Exception e) {} // Based on crash reports, exception can be thrown that I think is best ignored
        }
    }

    private void syncSpsEditor() {
        sps_editor.apply();

        if (updated_lasts) {
            info.last_status_cTM = now;
            info.last_status = info.status;
            info.last_percent = info.percent;
            info.last_plugged = info.plugged;
        }
    }

    private void prepareNotification() {
        if (settings.getBoolean(SettingsActivity.KEY_NOTIFY_STATUS_DURATION, false))
            mainNotificationTopLine = statusDurationLine();
        else
            mainNotificationTopLine = predictionLine();

        mainNotificationBottomLine = vitalStatsLine();

        mainNotificationB.setSmallIcon(iconFor(info.percent))
            .setOngoing(true)
            .setWhen(0)
            .setShowWhen(false)
            .setChannelId(CHAN_ID_MAIN)
            .setContentTitle(mainNotificationTopLine)
            .setContentText(mainNotificationBottomLine)
            .setContentIntent(currentInfoPendingIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC);
    }

    private String predictionLine() {
        String line;
        BatteryInfo.RelativeTime predicted = info.prediction.last_rtime;

        if (info.prediction.what == BatteryInfo.Prediction.NONE) {
            line = Str.statuses[info.status];
        } else {
            if (predicted.days > 0)
                line = Str.n_days_m_hours(predicted.days, predicted.hours);
            else if (predicted.hours > 0) {
                line = Str.n_hours_long_m_minutes_medium(predicted.hours, predicted.minutes);
            } else
                line = Str.n_minutes_long(predicted.minutes);

            if (info.prediction.what == BatteryInfo.Prediction.UNTIL_CHARGED)
                line += res.getString(R.string.notification_until_charged);
            else
                line += res.getString(R.string.notification_until_drained);
        }

        return line;
    }

    private String vitalStatsLine() {
        Boolean convertF = settings.getBoolean(SettingsActivity.KEY_CONVERT_F,
                                               res.getBoolean(R.bool.default_convert_to_fahrenheit));
        String line = Str.healths[info.health] + " / " + Str.formatTemp(info.temperature, convertF);

        if (info.voltage > 500)
            line += " / " + Str.formatVoltage(info.voltage);

        return line;
    }

    private String statusDurationLine() {
        long statusDuration = now - info.last_status_cTM;
        int statusDurationHours = (int) ((statusDuration + (1000 * 60 * 30)) / (1000 * 60 * 60));
        String line = Str.statuses[info.status] + " ";

        if (statusDuration < 1000 * 60 * 60)
            line += Str.since + " " + formatTime(new Date(info.last_status_cTM));
        else
            line += Str.for_n_hours(statusDurationHours);

        return line;
    }

    private void doNotify() {
        mHandler.post(mNotify);
    }

    // I take advantage of (count on) R.java having resources alphabetical and incrementing by one.
    private int iconFor(int percent) {
        String default_set = "builtin.plain_number";

        String icon_set = settings.getString(SettingsActivity.KEY_ICON_SET, "null");
        if (! icon_set.startsWith("builtin.")) icon_set = "null";

        if (icon_set.equals("null")) {
            icon_set = default_set;

            settings.edit().putString(SettingsActivity.KEY_ICON_SET, default_set).apply();
        }

        Boolean indicate_charging = settings.getBoolean(SettingsActivity.KEY_INDICATE_CHARGING, true);

        if (icon_set.equals("builtin.plain_number")) {
            return ((info.status == BatteryInfo.STATUS_CHARGING && indicate_charging) ? chargingIcon0 : plainIcon0) + info.percent;
        } else if (icon_set.equals("builtin.smaller_number")) {
            return ((info.status == BatteryInfo.STATUS_CHARGING && indicate_charging) ? small_chargingIcon0 : small_plainIcon0) + info.percent;
        } else if (settings.getBoolean(SettingsActivity.KEY_CLASSIC_COLOR_MODE, false)) {
            return R.drawable.b000 + info.percent;
        } else {
            return R.drawable.w000 + info.percent;
        }
    }

    private boolean statusHasChanged() {
        int previous_charge = sp_service.getInt(KEY_PREVIOUS_CHARGE, 100);

        return (info.last_status != info.status ||
                info.last_status_cTM >= now ||
                info.last_plugged != info.plugged ||
                (info.plugged == BatteryInfo.PLUGGED_UNPLUGGED && info.percent > previous_charge + 20));
    }

    private void handleUpdateWithChangedStatus() {
        updated_lasts = true;
        sps_editor.putLong(BatteryInfo.KEY_LAST_STATUS_CTM, now);
        sps_editor.putInt(BatteryInfo.KEY_LAST_STATUS, info.status);
        sps_editor.putInt(BatteryInfo.KEY_LAST_PERCENT, info.percent);
        sps_editor.putInt(BatteryInfo.KEY_LAST_PLUGGED, info.plugged);
        sps_editor.putInt(KEY_PREVIOUS_CHARGE, info.percent);
        sps_editor.putInt(KEY_PREVIOUS_TEMP, info.temperature);
        sps_editor.putInt(KEY_PREVIOUS_HEALTH, info.health);
    }

    private void handleUpdateWithSameStatus() {
        if (info.percent % 10 == 0) {
            sps_editor.putInt(KEY_PREVIOUS_CHARGE, info.percent);
            sps_editor.putInt(KEY_PREVIOUS_TEMP, info.temperature);
            sps_editor.putInt(KEY_PREVIOUS_HEALTH, info.health);
        }
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

    public static void onWidgetUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        widgetManager = appWidgetManager;

        for (int i = 0; i < appWidgetIds.length; i++) {
            widgetIds.add(appWidgetIds[i]);
        }

        context.startService(new Intent(context, BatteryInfoService.class));
    }

    public static void onWidgetDeleted(Context context, int[] appWidgetIds) {
        for (int i = 0; i < appWidgetIds.length; i++) {
            widgetIds.remove(appWidgetIds[i]);
        }
    }
}
