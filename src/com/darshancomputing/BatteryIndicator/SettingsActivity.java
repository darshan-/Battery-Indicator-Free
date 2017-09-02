/*
    Copyright (c) 2009-2017 Darshan-Josiah Barber

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

import android.app.ActionBar;
import android.app.Dialog;
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
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import java.util.Locale;

public class SettingsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
    public static final String SETTINGS_FILE = "com.darshancomputing.BatteryIndicator_preferences";
    public static final String SP_SERVICE_FILE = "sp_store";   // Only write from Service process
    public static final String SP_MAIN_FILE = "sp_store_main"; // Only write from main process

    public static final String KEY_THEME_SETTINGS = "theme_settings";
    public static final String KEY_ALARM_SETTINGS = "alarm_settings";
    public static final String KEY_ALARM_EDIT_SETTINGS = "alarm_edit_settings";
    public static final String KEY_OTHER_SETTINGS = "other_settings";
    public static final String KEY_MAIN_NOTIFICATION_PRIORITY = "main_notification_priority";
    public static final String KEY_ICON_PLUGIN = "icon_plugin";
    public static final String KEY_ICON_SET = "icon_set";
    public static final String KEY_CONVERT_F = "convert_to_fahrenheit";
    public static final String KEY_NOTIFY_STATUS_DURATION = "notify_status_duration";
    public static final String KEY_AUTOSTART = "autostart";
    public static final String KEY_TEN_PERCENT_MODE = "ten_percent_mode";
    public static final String KEY_CLASSIC_COLOR_MODE = "classic_color_mode";
    public static final String KEY_STATUS_DUR_EST = "status_dur_est";
    public static final String KEY_CAT_CLASSIC_COLOR_MODE = "category_classic_color_mode";
    public static final String KEY_CAT_COLOR = "category_color";
    public static final String KEY_CAT_CHARGING_INDICATOR = "category_charging_indicator";
    public static final String KEY_CAT_NOTIFICATION_SETTINGS = "category_notification_settings";
    public static final String KEY_INDICATE_CHARGING = "indicate_charging";
    public static final String KEY_RED = "use_red";
    public static final String KEY_RED_THRESH = "red_threshold";
    public static final String KEY_AMBER = "use_amber";
    public static final String KEY_AMBER_THRESH = "amber_threshold";
    public static final String KEY_GREEN = "use_green";
    public static final String KEY_GREEN_THRESH = "green_threshold";
    public static final String KEY_COLOR_PREVIEW = "color_preview";
    public static final String KEY_FIRST_RUN = "first_run";
    public static final String KEY_NOTIFICATION_WIZARD_EVER_RUN = "key_notification_wizard_ever_run";
    public static final String KEY_MIGRATED_SERVICE_DESIRED = "service_desired_migrated_to_sp_main";

    private static final String[] PARENTS    = {KEY_RED,        KEY_AMBER,        KEY_GREEN};
    private static final String[] DEPENDENTS = {KEY_RED_THRESH, KEY_AMBER_THRESH, KEY_GREEN_THRESH};

    private static final String[] LIST_PREFS = {KEY_AUTOSTART, KEY_STATUS_DUR_EST,
                                                KEY_RED_THRESH, KEY_AMBER_THRESH, KEY_GREEN_THRESH,
                                                KEY_MAIN_NOTIFICATION_PRIORITY, KEY_ICON_SET,
                                                };

    private static final String[] RESET_SERVICE = {KEY_CONVERT_F, KEY_NOTIFY_STATUS_DURATION,
                                                   KEY_RED, KEY_RED_THRESH,
                                                   KEY_AMBER, KEY_AMBER_THRESH, KEY_GREEN, KEY_GREEN_THRESH,
                                                   KEY_ICON_SET,
                                                   KEY_CLASSIC_COLOR_MODE,
                                                   KEY_INDICATE_CHARGING, KEY_TEN_PERCENT_MODE}; /* 10% mode changes color settings */

    private static final String[] RESET_SERVICE_WITH_CANCEL_NOTIFICATION = {KEY_MAIN_NOTIFICATION_PRIORITY,
    };

    public static final String EXTRA_SCREEN = "com.darshancomputing.BatteryIndicator.PrefScreen";

    private Messenger serviceMessenger;
    private final Messenger messenger = new Messenger(new MessageHandler(this));
    private final BatteryInfoService.RemoteConnection serviceConnection = new BatteryInfoService.RemoteConnection(messenger);

    private Resources res;
    private PreferenceScreen mPreferenceScreen;
    private SharedPreferences mSharedPreferences;

    private String pref_screen;

    private int menu_res = R.menu.settings;

    private static class MessageHandler extends Handler {
        private SettingsActivity sa;

        MessageHandler(SettingsActivity a) {
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        pref_screen = intent.getStringExtra(EXTRA_SCREEN);
        res = getResources();

        ActionBar ab = getActionBar();
        if (ab != null) {
            ab.setHomeButtonEnabled(true);
            ab.setDisplayHomeAsUpEnabled(true);
        }

        PreferenceManager pm = getPreferenceManager();
        pm.setSharedPreferencesName(SETTINGS_FILE);
        pm.setSharedPreferencesMode(Context.MODE_MULTI_PROCESS);
        mSharedPreferences = pm.getSharedPreferences();

        if (pref_screen == null) {
            setPrefScreen(R.xml.main_pref_screen);
            setWindowSubtitle(res.getString(R.string.settings_activity_subtitle));
        } else {
            setPrefScreen(R.xml.main_pref_screen);
        }

        if (android.os.Build.VERSION.SDK_INT < 21 ||
            !mSharedPreferences.getString(SettingsActivity.KEY_ICON_SET, "null").equals("builtin.classic")) {
            PreferenceCategory cat = (PreferenceCategory) mPreferenceScreen.findPreference(KEY_CAT_NOTIFICATION_SETTINGS);
            Preference pref = (Preference) mPreferenceScreen.findPreference(KEY_CLASSIC_COLOR_MODE);
            cat.removePreference(pref);
            pref.setLayoutResource(R.layout.none);
        }

        for (int i=0; i < PARENTS.length; i++)
            setEnablednessOfDeps(i);

        for (int i=0; i < LIST_PREFS.length; i++)
            updateListPrefSummary(LIST_PREFS[i]);

        updateConvertFSummary();

        Intent biServiceIntent = new Intent(this, BatteryInfoService.class);
        bindService(biServiceIntent, serviceConnection, 0);
    }

    public static Locale codeToLocale (String code) {
        String[] parts = code.split("_");

        if (parts.length > 1)
            return new Locale(parts[0], parts[1]);
        else
            return new Locale(parts[0]);
    }

    private void setWindowSubtitle(String subtitle) {
        if (res.getBoolean(R.bool.long_activity_names))
            setTitle(res.getString(R.string.app_full_name) + " - " + subtitle);
        else
            setTitle(subtitle);
    }

    private void setPrefScreen(int resource) {
        addPreferencesFromResource(resource);

        mPreferenceScreen  = getPreferenceScreen();
    }

    private void restartThisScreen() {
        ComponentName comp = new ComponentName(getPackageName(), SettingsActivity.class.getName());
        Intent intent = new Intent().setComponent(comp);
        intent.putExtra(EXTRA_SCREEN, pref_screen);
        startActivity(intent);
        finish();
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
            startService(new Intent(this, BatteryInfoService.class));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (serviceConnection != null) unbindService(serviceConnection);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(menu_res, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_help:
            ComponentName comp = new ComponentName(getPackageName(), SettingsHelpActivity.class.getName());
            Intent intent = new Intent().setComponent(comp);

            if (pref_screen != null) intent.putExtra(EXTRA_SCREEN, pref_screen);

            startActivity(intent);

            return true;
        case android.R.id.home:
            finish();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog;
        Str.setResources(getResources());

        switch (id) {
        default:
            dialog = null;
        }

        return dialog;
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);

        if (key.equals(KEY_ICON_SET)) {
            restartThisScreen();
            resetService();
        }

        for (int i=0; i < PARENTS.length; i++) {
            if (key.equals(PARENTS[i])) {
                setEnablednessOfDeps(i);
                if (i == 0) setEnablednessOfDeps(1); /* Doubled charge key */
                if (i == 2) setEnablednessOfDeps(3); /* Doubled charge key */
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
        Preference dependent = mPreferenceScreen.findPreference(DEPENDENTS[index]);
        if (dependent == null) return;

        if (mSharedPreferences.getBoolean(PARENTS[index], false))
            dependent.setEnabled(true);
        else
            dependent.setEnabled(false);

        updateListPrefSummary(DEPENDENTS[index]);
    }

    // private void setEnablednessOfMutuallyExclusive(String key1, String key2) {
    //     Preference pref1 = mPreferenceScreen.findPreference(key1);
    //     Preference pref2 = mPreferenceScreen.findPreference(key2);

    //     if (pref1 == null) return;

    //     if (mSharedPreferences.getBoolean(key1, false))
    //         pref2.setEnabled(false);
    //     else if (mSharedPreferences.getBoolean(key2, false))
    //         pref1.setEnabled(false);
    //     else {
    //         pref1.setEnabled(true);
    //         pref2.setEnabled(true);
    //     }
    // }

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

    // private void setPluginPrefEntriesAndValues(ListPreference lpref) {
    //     String prefix = "BI Plugin - ";

    //     PackageManager pm = getPackageManager();
    //     java.util.List<PackageInfo> packages = pm.getInstalledPackages(0);

    //     java.util.List<String> entriesList = new java.util.ArrayList<String>();
    //     java.util.List<String>  valuesList = new java.util.ArrayList<String>();

    //     String[] icon_set_entries = res.getStringArray(R.array.icon_set_entries);
    //     String[] icon_set_values  = res.getStringArray(R.array.icon_set_values);

    //     for (int i = 0; i < icon_set_entries.length; i++) {
    //         entriesList.add(icon_set_entries[i]);
    //          valuesList.add(icon_set_values[i]);
    //     }

    //     lpref.setEntries    ((String[]) entriesList.toArray(new String[entriesList.size()]));
    //     lpref.setEntryValues((String[])  valuesList.toArray(new String[entriesList.size()]));

    //     /* TODO: I think it's safe to skip this: if the previously selected plugin is uninstalled, null
    //        should be picked up by the Service and converted to proper default, I think/hope.
    //     // If the previously selected plugin was uninstalled, revert to "None"
    //     //if (! valuesList.contains(lpref.getValue())) lpref.setValueIndex(0);
    //     if (lpref.getEntry() == null) lpref.setValueIndex(0);
    //     */
    // }

    public void upgradeButtonClick(android.view.View v) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                                     Uri.parse("market://details?id=com.darshancomputing.BatteryIndicatorPro")));
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "Sorry, can't launch Market!", Toast.LENGTH_SHORT).show();
        }
    }
}
