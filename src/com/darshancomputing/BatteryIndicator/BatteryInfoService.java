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
import android.appwidget.AppWidgetManager;
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
import android.view.View;
import android.widget.RemoteViews;

import java.util.Date;
import java.util.HashSet;

public class BatteryInfoService extends Service {
    private final IntentFilter batteryChanged = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    private final IntentFilter userPresent    = new IntentFilter(Intent.ACTION_USER_PRESENT);
    private PendingIntent mainWindowPendingIntent;
    private PendingIntent updatePredictorPendingIntent;

    private NotificationManager mNotificationManager;
    private AlarmManager alarmManager;
    private static SharedPreferences settings;
    private static SharedPreferences sp_store;
    private static SharedPreferences.Editor sps_editor;

    private Context context;
    private Resources res;
    private Str str;
    private BatteryLevel bl;
    private CircleWidgetBackground cwbg;
    private BatteryInfo info;
    private long now;
    private boolean updated_lasts;
    private static java.util.HashSet<Messenger> clientMessengers;
    private static Messenger messenger;

    private static HashSet<Integer> widgetIds = new HashSet<Integer>();
    private static AppWidgetManager widgetManager;
    private static boolean widgetsPresent = false;


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
    public static final String KEY_SHOW_NOTIFICATION = "show_notification";

    public static final String KEY_WIDGETS_PRESENT = "widgets_present";

    private static final String EXTRA_UPDATE_PREDICTOR = "com.darshancomputing.BatteryBot.EXTRA_UPDATE_PREDICTOR";


    private static final Object[] EMPTY_OBJECT_ARRAY = {};
    private static final  Class[]  EMPTY_CLASS_ARRAY = {};

    private static final int plainIcon0 = R.drawable.plain000;
    private static final int small_plainIcon0 = R.drawable.small_plain000;
    private static final int chargingIcon0 = R.drawable.charging000;
    private static final int small_chargingIcon0 = R.drawable.small_charging000;

    /* Global variables for these Notification Runnables */
    private Notification mainNotification;
    private String mainNotificationTopLine, mainNotificationBottomLine;
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

        messenger = new Messenger(new MessageHandler());
        clientMessengers = new java.util.HashSet<Messenger>();

        predictor = new Predictor(context);
        bl = new BatteryLevel(context, BatteryLevel.SIZE_NOTIFICATION);
        cwbg = new CircleWidgetBackground(context);

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        loadSettingsFiles(context);

        Intent mainWindowIntent = new Intent(context, BatteryInfoActivity.class);
        mainWindowPendingIntent = PendingIntent.getActivity(context, 0, mainWindowIntent, 0);

        Intent updatePredictorIntent = new Intent(context, BatteryInfoService.class);
        updatePredictorIntent.putExtra(EXTRA_UPDATE_PREDICTOR, true);
        updatePredictorPendingIntent = PendingIntent.getService(context, 0, updatePredictorIntent, 0);

        widgetManager = AppWidgetManager.getInstance(context);

        Class[] appWidgetProviders = {BatteryInfoAppWidgetProvider.class /* Circle widget! */
                                      };

         for (int i = 0; i < appWidgetProviders.length; i++) {
            int[] ids = widgetManager.getAppWidgetIds(new ComponentName(context, appWidgetProviders[i]));

            for (int j = 0; j < ids.length; j++) {
                widgetIds.add(ids[j]);
            }
        }

        widgetsPresent = sp_store.getBoolean(KEY_WIDGETS_PRESENT, false);

        Intent bc_intent = registerReceiver(mBatteryInfoReceiver, batteryChanged);
        info.load(bc_intent, sp_store);
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
        // TODO: Do I need a filter, or is it okay to just update(null) every time?
        //if (intent != null && intent.getBooleanExtra(EXTRA_UPDATE_PREDICTOR, false))
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

                if (widgetsPresent)
                    sendClientMessage(incoming.replyTo, RemoteConnection.CLIENT_SERVICE_UNCLOSEABLE);
                else
                    sendClientMessage(incoming.replyTo, RemoteConnection.CLIENT_SERVICE_CLOSEABLE);

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
        public static final int CLIENT_SERVICE_CLOSEABLE = 2;
        public static final int CLIENT_SERVICE_UNCLOSEABLE = 3;

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

    private static void loadSettingsFiles(Context context) {
        settings = context.getSharedPreferences(SettingsActivity.SETTINGS_FILE, Context.MODE_MULTI_PROCESS);
        sp_store = context.getSharedPreferences(SettingsActivity.SP_STORE_FILE, Context.MODE_MULTI_PROCESS);
    }

    private void reloadSettings(boolean cancelFirst) {
        loadSettingsFiles(context);

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
        now = System.currentTimeMillis();
        sps_editor = sp_store.edit();
        updated_lasts = false;

        if (intent != null)
            info.load(intent, sp_store);

        predictor.update(info);
        info.prediction.updateRelativeTime();

        if (statusHasChanged())
            handleUpdateWithChangedStatus();
        else
            handleUpdateWithSameStatus();

        if (sp_store.getBoolean(KEY_SHOW_NOTIFICATION, true)) {
            prepareNotification();
            doNotify();
        }

        updateWidgets();

        syncSpsEditor(); // Important to sync after other Service code that uses 'lasts' but before sending info to client

        for (Messenger messenger : clientMessengers) {
            // TODO: Can I send the same message to multiple clients instead of sending duplicates?
            sendClientMessage(messenger, RemoteConnection.CLIENT_BATTERY_INFO_UPDATED, info.toBundle());
        }

        alarmManager.set(AlarmManager.ELAPSED_REALTIME, android.os.SystemClock.elapsedRealtime() + (2 * 60 * 1000), updatePredictorPendingIntent);
    }

    private void updateWidgets() {
        Intent mainWindowIntent = new Intent(context, BatteryInfoActivity.class);
        PendingIntent mainWindowPendingIntent = PendingIntent.getActivity(context, 0, mainWindowIntent, 0);

        cwbg.setLevel(info.percent);

        for (Integer widgetId : widgetIds) {
            // TODO: remove id from Set if something goes wrong?
            RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.circle_app_widget);

            //if (android.os.Build.VERSION.SDK_INT < 11) { // No resizeable widgets
                rv.setTextViewText(R.id.level, "" + info.percent + str.percent_symbol);
            //}

            rv.setImageViewBitmap(R.id.circle_widget_image_view, cwbg.getBitmap());
            rv.setOnClickPendingIntent(R.id.widget_layout, mainWindowPendingIntent);
            widgetManager.updateAppWidget(widgetId, rv);
        }
    }

    private void syncSpsEditor() {
        sps_editor.commit();

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

        // TODO: Is it necessary to call new() every time here, or can I get away with just setting the icon on existing Notif.?
        mainNotification = new Notification(iconFor(info.percent), null, 0l);

        if (android.os.Build.VERSION.SDK_INT >= 16)
            mainNotification.priority = Notification.PRIORITY_LOW;

        mainNotification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;

        if (settings.getBoolean(SettingsActivity.KEY_USE_SYSTEM_NOTIFICATION_LAYOUT,
                                res.getBoolean(R.bool.default_use_system_notification_layout))) {
            mainNotification.setLatestEventInfo(context, mainNotificationTopLine, mainNotificationBottomLine, mainWindowPendingIntent);

            /* This must be set AFTER setLatestEventInfo(), which resets it to PRIVATE */
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                mainNotification.visibility = Notification.VISIBILITY_PUBLIC;
            }
        } else {
            notificationRV = new RemoteViews(getPackageName(), R.layout.main_notification);
            //notificationRV.setImageViewBitmap(R.id.battery, bl.getBitmap());
            bl.setLevel(info.percent);

            notificationRV.setTextViewText(R.id.percent, "" + info.percent + str.percent_symbol);
            notificationRV.setTextViewText(R.id.top_line, android.text.Html.fromHtml(mainNotificationTopLine));
            notificationRV.setTextViewText(R.id.bottom_line, mainNotificationBottomLine);

            mainNotification.contentIntent = mainWindowPendingIntent;
            mainNotification.contentView = notificationRV;
        }
    }

    private String predictionLine() {
        String line;
        BatteryInfo.RelativeTime predicted = info.prediction.last_rtime;

        if (info.prediction.what == BatteryInfo.Prediction.NONE) {
            line = str.statuses[info.status];
        } else {
            if (predicted.days > 0)
                line = str.n_days_m_hours(predicted.days, predicted.hours);
            else if (predicted.hours > 0) {
                line = str.n_hours_long_m_minutes_medium(predicted.hours, predicted.minutes);
            } else
                line = str.n_minutes_long(predicted.minutes);

            if (info.prediction.what == BatteryInfo.Prediction.UNTIL_CHARGED)
                line += res.getString(R.string.notification_until_charged);
            else
                line += res.getString(R.string.notification_until_drained);
        }

        return line;
    }

    private String vitalStatsLine() {
        Boolean convertF = settings.getBoolean(SettingsActivity.KEY_CONVERT_F, false);
        String line = str.healths[info.health] + " / " + str.formatTemp(info.temperature, convertF);

        if (info.voltage > 500)
            line += " / " + str.formatVoltage(info.voltage);

        return line;
    }

    private String statusDurationLine() {
        long statusDuration = now - info.last_status_cTM;
        int statusDurationHours = (int) ((statusDuration + (1000 * 60 * 30)) / (1000 * 60 * 60));
        String line = str.statuses[info.status] + " ";

        if (statusDuration < 1000 * 60 * 60)
            line += str.since + " " + formatTime(new Date(info.last_status_cTM));
        else
            line += str.for_n_hours(statusDurationHours);

        return line;
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
        } else if (android.os.Build.VERSION.SDK_INT >= 21 &&
                   !settings.getBoolean(SettingsActivity.KEY_CLASSIC_COLOR_MODE, false)) {
            return R.drawable.w000 + info.percent;
        } else {
            return R.drawable.b000 + info.percent;
        }
    }

    private boolean statusHasChanged() {
        int previous_charge = sp_store.getInt(KEY_PREVIOUS_CHARGE, 100);

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

    public static void onWidgetEnabled(Context context) {
        widgetsPresent = true;

        if (sp_store == null) loadSettingsFiles(context);
        sps_editor = sp_store.edit();
        sps_editor.putBoolean(KEY_WIDGETS_PRESENT, widgetsPresent);
        sps_editor.commit();

        if (clientMessengers == null) return;

        for (Messenger messenger : clientMessengers) {
            sendClientMessage(messenger, RemoteConnection.CLIENT_SERVICE_UNCLOSEABLE);
        }
    }

    public static void onWidgetDisabled(Context context) {
        widgetsPresent = false;

        if (sp_store == null) loadSettingsFiles(context);
        sps_editor = sp_store.edit();
        sps_editor.putBoolean(KEY_WIDGETS_PRESENT, widgetsPresent);
        sps_editor.commit();

        if (clientMessengers == null) return;

        for (Messenger messenger : clientMessengers) {
            sendClientMessage(messenger, RemoteConnection.CLIENT_SERVICE_CLOSEABLE);
        }
    }
}
