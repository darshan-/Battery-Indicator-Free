/*
    Copyright (c) 2009-2021 Darshan Computing, LLC

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

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

public class SettingsFragment extends PreferenceFragmentCompat implements OnSharedPreferenceChangeListener {
    public static final String SETTINGS_FILE = "com.darshancomputing.BatteryIndicatorPro_preferences";
    public static final String SP_SERVICE_FILE = "sp_store";   // Only write from Service process
    public static final String SP_MAIN_FILE = "sp_store_main"; // Only write from main process

    public static final String KEY_ICON_PLUGIN = "icon_plugin";
    public static final String KEY_ICON_SET = "icon_set";
    public static final String KEY_CONVERT_F = "convert_to_fahrenheit";
    public static final String KEY_NOTIFY_STATUS_DURATION = "notify_status_duration";
    public static final String KEY_AUTOSTART = "autostart";
    public static final String KEY_PREDICTION_TYPE = "prediction_type";
    public static final String KEY_CLASSIC_COLOR_MODE = "classic_color_mode";
    public static final String KEY_STATUS_DUR_EST = "status_dur_est";
    public static final String KEY_CAT_CLASSIC_COLOR_MODE = "category_classic_color_mode";
    public static final String KEY_CAT_COLOR = "category_color";
    public static final String KEY_CAT_CHARGING_INDICATOR = "category_charging_indicator";
    public static final String KEY_CAT_PLUGIN_SETTINGS = "category_plugin_settings";
    public static final String KEY_PLUGIN_SETTINGS = "plugin_settings";
    public static final String KEY_INDICATE_CHARGING = "indicate_charging";
    public static final String KEY_TOP_LINE = "top_line";
    public static final String KEY_BOTTOM_LINE = "bottom_line";
    public static final String KEY_TIME_REMAINING_VERBOSITY = "time_remaining_verbosity";
    public static final String KEY_STATUS_DURATION_IN_VITAL_SIGNS = "status_duration_in_vital_signs";
    public static final String KEY_FIRST_RUN = "first_run";
    public static final String KEY_MIGRATED_SERVICE_DESIRED = "service_desired_migrated_to_sp_main";
    public static final String KEY_ENABLE_NOTIFS_B = "enable_notifications_button";
    public static final String KEY_ENABLE_NOTIFS_SUMMARY = "enable_notifications_summary";
    public static final String KEY_UNLOCK_PRO_B = "unlock_pro";
    public static final String KEY_UI_COLOR = "ui_color";

    private static final String[] PARENTS    = {
    };
    private static final String[][] DEPENDENTS = {
    };

    private static final String[] CURRENT_HACK_DEPENDENTS = {
    };

    private static final String[] INVERSE_PARENTS    = {
    };
    private static final String[] INVERSE_DEPENDENTS = {
    };

    private static final String[] LIST_PREFS = {KEY_AUTOSTART, KEY_STATUS_DUR_EST,
                                                KEY_ICON_SET,
                                                KEY_TOP_LINE, KEY_BOTTOM_LINE,
                                                KEY_TIME_REMAINING_VERBOSITY,
                                                KEY_PREDICTION_TYPE
    };

    private static final String[] RESET_SERVICE = {KEY_CONVERT_F, KEY_NOTIFY_STATUS_DURATION,
                                                   KEY_ICON_SET,
                                                   KEY_INDICATE_CHARGING,
                                                   KEY_TOP_LINE, KEY_BOTTOM_LINE,
                                                   KEY_TIME_REMAINING_VERBOSITY,
                                                   KEY_STATUS_DURATION_IN_VITAL_SIGNS,
                                                   KEY_UI_COLOR,
                                                   KEY_PREDICTION_TYPE
    };

    private static final String[] RESET_SERVICE_WITH_CANCEL_NOTIFICATION = {
    };

    public static final String EXTRA_SCREEN = "com.darshancomputing.BatteryIndicator.PrefScreen";

    private Messenger serviceMessenger;
    private final Messenger messenger = new Messenger(new MessageHandler(this));
    private final BatteryInfoService.RemoteConnection serviceConnection = new BatteryInfoService.RemoteConnection(messenger);

    private Resources res;
    private PreferenceScreen mPreferenceScreen;
    private SharedPreferences mSharedPreferences;
    private NotificationManager mNotificationManager;
    private NotificationChannel mainChan;
    private boolean appNotifsEnabled;
    private boolean mainNotifsEnabled;

    private int pref_screen;

    private int menu_res = R.menu.settings;

    private static class MessageHandler extends Handler {
        private SettingsFragment sa;

        MessageHandler(SettingsFragment a) {
            sa = a;
        }

        @Override
        public void handleMessage(Message incoming) {
            switch (incoming.what) {
            case BatteryInfoService.RemoteConnection.CLIENT_SERVICE_CONNECTED:
                sa.serviceMessenger = incoming.replyTo;
                break;
            default:
                super.handleMessage(incoming);
            }
        }
    }

    public void setScreen(int screen) {
        pref_screen = screen;

        if (res != null)
            setPreferences();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        res = getResources();

        PreferenceManager pm = getPreferenceManager();
        pm.setSharedPreferencesName(SETTINGS_FILE);
        pm.setSharedPreferencesMode(Context.MODE_MULTI_PROCESS);
        mSharedPreferences = pm.getSharedPreferences();

        if (pref_screen > 0)
            setPreferences();
    }

    @Override
    public void onResume() {
        super.onResume();

        mainChan = mNotificationManager.getNotificationChannel(BatteryInfoService.CHAN_ID_MAIN);

        if (appNotifsEnabled != mNotificationManager.areNotificationsEnabled() ||
            mainNotifsEnabled != mainChan.getImportance() > 0) { // Doesn't seem worth checking which screen
            resetService();
            setPreferences();
        }

        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();

        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    private void resetService() {
        resetService(false);
    }

    private void resetService(boolean cancelFirst) {
        mSharedPreferences.edit().commit(); // commit() synchronously before messaging Service

        Message outgoing = Message.obtain();

        if (cancelFirst)
            outgoing.what = BatteryInfoService.RemoteConnection.SERVICE_CANCEL_NOTIFICATION_AND_RELOAD_SETTINGS;
        else
            outgoing.what = BatteryInfoService.RemoteConnection.SERVICE_RELOAD_SETTINGS;

        try {
            serviceMessenger.send(outgoing);
        } catch (Exception e) {
            getActivity().startForegroundService(new Intent(getActivity(), BatteryInfoService.class));
        }
    }

    // pref_screen is the screen we're conceptually on, while
    // pref_res is the actual resource we're loading.
    // In the case of disabled notifications, for example, we'll load the disabled notifs resource, but
    //   we still want to "be on" whichever page we're on.
    private void setPreferences() {
        mNotificationManager = (NotificationManager) getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
        mainChan = mNotificationManager.getNotificationChannel(BatteryInfoService.CHAN_ID_MAIN);

        appNotifsEnabled = mNotificationManager.areNotificationsEnabled();
        mainNotifsEnabled = mainChan.getImportance() > 0;

        int pref_res = pref_screen;

        if (!appNotifsEnabled || !mainNotifsEnabled) {
            pref_res = R.xml.main_notifs_disabled_pref_screen;
        }

        setPreferencesFromResource(pref_res, null);
        mPreferenceScreen = getPreferenceScreen();

        PreferenceCategory cat;

        if (pref_res == R.xml.main_notifs_disabled_pref_screen) {
            Preference prefb = mPreferenceScreen.findPreference(KEY_ENABLE_NOTIFS_B);
            Preference prefs = mPreferenceScreen.findPreference(KEY_ENABLE_NOTIFS_SUMMARY);

            if (!appNotifsEnabled) {
                prefs.setSummary(R.string.app_notifs_disabled_summary);
                prefb.setSummary(R.string.app_notifs_disabled_b);
            } else {
                prefs.setSummary(R.string.main_notifs_disabled_summary);
                prefb.setSummary(R.string.main_notifs_disabled_b);
            }
        } else {
            Preference prefb = mPreferenceScreen.findPreference(KEY_UNLOCK_PRO_B);
            prefb.setSummary(R.string.upgrade_donate_b);
        }

        for (int i=0; i < PARENTS.length; i++)
            setEnablednessOfDeps(i);

        for (int i=0; i < INVERSE_PARENTS.length; i++)
            setEnablednessOfInverseDeps(i);

        for (int i=0; i < LIST_PREFS.length; i++)
            updateListPrefSummary(LIST_PREFS[i]);

        updateConvertFSummary();

        Intent biServiceIntent = new Intent(getActivity(), BatteryInfoService.class);
        getActivity().bindService(biServiceIntent, serviceConnection, 0);
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);

        if (key.equals(KEY_CLASSIC_COLOR_MODE)) {
            resetService();
            //(No longer have actual colors, so settings page doesn't change.)
            //setPreferences(); // To show/hide icon-set/plugin settings
        }

        if (key.equals(KEY_ICON_SET)) {
            resetService();
            setPreferences(); // To show/hide icon-set/plugin settings
        }

        for (int i=0; i < PARENTS.length; i++) {
            if (key.equals(PARENTS[i])) {
                setEnablednessOfDeps(i);
                break;
            }
        }

        for (int i=0; i < INVERSE_PARENTS.length; i++) {
            if (key.equals(INVERSE_PARENTS[i])) {
                setEnablednessOfInverseDeps(i);
                break;
            }
        }

        for (int i=0; i < LIST_PREFS.length; i++) {
            if (key.equals(LIST_PREFS[i])) {
                updateListPrefSummary(LIST_PREFS[i]);
                break;
            }
        }

        if (key.equals(KEY_CONVERT_F)) {
            updateConvertFSummary();
        }

        for (int i=0; i < RESET_SERVICE.length; i++) {
            if (key.equals(RESET_SERVICE[i])) {
                resetService();
                break;
            }
        }

        for (int i=0; i < RESET_SERVICE_WITH_CANCEL_NOTIFICATION.length; i++) {
            if (key.equals(RESET_SERVICE_WITH_CANCEL_NOTIFICATION[i])) {
                resetService(true);
                break;
            }
        }

        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    private void updateConvertFSummary() {
        Preference pref = mPreferenceScreen.findPreference(KEY_CONVERT_F);
        if (pref == null) return;

        pref.setSummary(res.getString(R.string.currently_using) + " " +
                        (mSharedPreferences.getBoolean(KEY_CONVERT_F, res.getBoolean(R.bool.default_convert_to_fahrenheit)) ?
                         res.getString(R.string.fahrenheit) : res.getString(R.string.celsius)));
    }

    private void setEnablednessOfDeps(int index) {
        for (int i = 0; i < DEPENDENTS[index].length; i++) {
            Preference dependent = mPreferenceScreen.findPreference(DEPENDENTS[index][i]);
            if (dependent == null) return;

            if (mSharedPreferences.getBoolean(PARENTS[index], false))
                dependent.setEnabled(true);
            else
                dependent.setEnabled(false);

            updateListPrefSummary(DEPENDENTS[index][i]);
        }
    }

    private void setEnablednessOfInverseDeps(int index) {
        Preference dependent = mPreferenceScreen.findPreference(INVERSE_DEPENDENTS[index]);
        if (dependent == null) return;

        if (mSharedPreferences.getBoolean(INVERSE_PARENTS[index], false))
            dependent.setEnabled(false);
        else
            dependent.setEnabled(true);

        updateListPrefSummary(INVERSE_DEPENDENTS[index]);
    }

    private void updateListPrefSummary(String key) {
        ListPreference pref;
        try { /* Code is simplest elsewhere if we call this on all dependents, but some aren't ListPreferences. */
            pref = (ListPreference) mPreferenceScreen.findPreference(key);
        } catch (java.lang.ClassCastException e) {
            return;
        }

        if (pref == null) return;

        if (pref.isEnabled()) {
            pref.setSummary(res.getString(R.string.currently_set_to) + pref.getEntry());
        } else {
            pref.setSummary(res.getString(R.string.currently_disabled));
        }
    }

    // TODO: Now that plugins have long been unsupported, get rid of this and just do normal list pref?
    private void setPluginPrefEntriesAndValues(ListPreference lpref) {
        java.util.List<String> entriesList = new java.util.ArrayList<String>();
        java.util.List<String>  valuesList = new java.util.ArrayList<String>();

        String[] icon_set_entries = res.getStringArray(R.array.icon_set_entries);
        String[] icon_set_values  = res.getStringArray(R.array.icon_set_values);

        for (int i = 0; i < icon_set_entries.length; i++) {
            entriesList.add(icon_set_entries[i]);
             valuesList.add(icon_set_values[i]);
        }

        lpref.setEntries    (entriesList.toArray(new String[entriesList.size()]));
        lpref.setEntryValues(valuesList.toArray(new String[entriesList.size()]));
    }

    public void enableNotifsButtonClick() {
        Intent intent;
        if (!appNotifsEnabled) {
            intent = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        } else {
            intent = new Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
            intent.putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, mainChan.getId());
        }

        intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getActivity().getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(intent);
    }

    public void upgradeButtonClick() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                                     Uri.parse("market://details?id=com.darshancomputing.BatteryIndicatorPro")));
        } catch (Exception e) {
            Toast.makeText(getActivity().getApplicationContext(),
                           "Sorry, can't launch Market!", Toast.LENGTH_SHORT).show();
        }
    }
}
