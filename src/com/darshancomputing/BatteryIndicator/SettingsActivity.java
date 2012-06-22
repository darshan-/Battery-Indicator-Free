/*
    Copyright (c) 2009, 2010 Josiah Barber (aka Darshan)

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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class SettingsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
    public static final String KEY_DISABLE_LOCKING = "disable_lock_screen";
    public static final String KEY_CONFIRM_DISABLE_LOCKING = "confirm_disable_lock_screen";
    public static final String KEY_FINISH_AFTER_TOGGLE_LOCK = "finish_after_toggle_lock";
    public static final String KEY_AUTO_DISABLE_LOCKING = "auto_disable_lock_screen";
    public static final String KEY_CONVERT_F = "convert_to_fahrenheit";
    public static final String KEY_ONE_PERCENT_HACK = "one_percent_hack";
    public static final String KEY_CHARGE_AS_TEXT = "charge_as_text";
    public static final String KEY_STATUS_DUR_EST = "status_dur_est";

    private static final String[]    PARENTS = {};
    private static final String[] DEPENDENTS = {};

    private static final String[] LIST_PREFS = {KEY_STATUS_DUR_EST};

    private static final String[] RESET_SERVICE = {KEY_CONVERT_F, KEY_CHARGE_AS_TEXT, KEY_STATUS_DUR_EST,
                                                   KEY_AUTO_DISABLE_LOCKING, KEY_ONE_PERCENT_HACK};

    public static final int CHARGE_COUNTER_LEGIT_MAX = 115; /* From what I understand, charge_counter can go somewhat over 100; I'm guessing at 115 being an appropriate cutoff point. */

    private Intent biServiceIntent;
    private BIServiceConnection biServiceConnection;

    private Resources res;
    private PreferenceScreen mPreferenceScreen;
    private SharedPreferences mSharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        res = getResources();

        // Stranglely disabled by default for API level 14+
        ///*v11*/ if (res.getBoolean(R.bool.api_level_14_plus))
        ///*v11*/     getActionBar().setHomeButtonEnabled(true);

        mSharedPreferences = getPreferenceManager().getSharedPreferences();

        setPrefScreen(R.xml.main_pref_screen);

        enableOnePercentIfAppropriate();

        for (int i=0; i < PARENTS.length; i++)
            setEnablednessOfDeps(i);

        for (int i=0; i < LIST_PREFS.length; i++)
            updateListPrefSummary(LIST_PREFS[i]);

        updateConvertFSummary();
        setEnablednessOfMutuallyExclusive(KEY_CONFIRM_DISABLE_LOCKING, KEY_FINISH_AFTER_TOGGLE_LOCK);

        biServiceIntent = new Intent(this, BatteryIndicatorService.class);
        biServiceConnection = new BIServiceConnection();
        bindService(biServiceIntent, biServiceConnection, 0);
    }

    private void setPrefScreen(int resource) {
        addPreferencesFromResource(resource);

        mPreferenceScreen  = getPreferenceScreen();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unbindService(biServiceConnection);
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
        inflater.inflate(R.menu.settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_help:
            ComponentName comp = new ComponentName(getPackageName(), SettingsHelpActivity.class.getName());
            Intent intent = new Intent().setComponent(comp);
            startActivity(intent);

            return true;
        case android.R.id.home:
            startActivity(new Intent(this, BatteryIndicator.class));
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);

        for (int i=0; i < PARENTS.length; i++) {
            if (key.equals(PARENTS[i])) {
                setEnablednessOfDeps(i);
                if (i == 0) setEnablednessOfDeps(1); /* Doubled charge key */
                break;
            }
        }

        for (int i=0; i < LIST_PREFS.length; i++) {
            if (key.equals(LIST_PREFS[i])) {
                updateListPrefSummary(LIST_PREFS[i]);
                break;
            }
        }

        if (key.equals(KEY_CONFIRM_DISABLE_LOCKING) || key.equals(KEY_FINISH_AFTER_TOGGLE_LOCK))
            setEnablednessOfMutuallyExclusive(KEY_CONFIRM_DISABLE_LOCKING, KEY_FINISH_AFTER_TOGGLE_LOCK);

        if (key.equals(KEY_CONVERT_F)) {
            updateConvertFSummary();
        }

        for (int i=0; i < RESET_SERVICE.length; i++) {
            if (key.equals(RESET_SERVICE[i])) {
                biServiceConnection.biService.reloadSettings();
                break;
            }
        }

        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    private void updateConvertFSummary() {
        Preference pref = (CheckBoxPreference) mPreferenceScreen.findPreference(KEY_CONVERT_F);
        if (pref == null) return;

        pref.setSummary(res.getString(R.string.currently_using) + " " +
                        (mSharedPreferences.getBoolean(KEY_CONVERT_F, false) ?
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

    private void setEnablednessOfMutuallyExclusive(String key1, String key2) {
        Preference pref1 = mPreferenceScreen.findPreference(key1);
        Preference pref2 = mPreferenceScreen.findPreference(key2);

        if (pref1 == null) return;

        if (mSharedPreferences.getBoolean(key1, false))
            pref2.setEnabled(false);
        else if (mSharedPreferences.getBoolean(key2, false))
            pref1.setEnabled(false);
        else {
            pref1.setEnabled(true);
            pref2.setEnabled(true);
        }
    }

    private void enableOnePercentIfAppropriate() {
        Preference pref = mPreferenceScreen.findPreference(KEY_ONE_PERCENT_HACK);

        try {
            java.io.FileReader fReader = new java.io.FileReader("/sys/class/power_supply/battery/charge_counter");
            java.io.BufferedReader bReader = new java.io.BufferedReader(fReader);
            if (Integer.valueOf(bReader.readLine()) <= CHARGE_COUNTER_LEGIT_MAX)
                pref.setEnabled(true);
        } catch (Exception e) {}
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
            pref.setSummary(res.getString(R.string.currently_set_to) + " " + pref.getEntry());
        } else {
            pref.setSummary(res.getString(R.string.currently_disabled));
        }
    }
}
