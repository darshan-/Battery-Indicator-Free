/*
    Copyright (c) 2013-2021 Darshan Computing, LLC

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

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.KeyEvent;

import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.PagerTabStrip;
import androidx.viewpager.widget.ViewPager;

public class BatteryInfoActivity extends AppCompatActivity {
    private BatteryInfoPagerAdapter pagerAdapter;
    private ViewPager viewPager;

    //private static final String LOG_TAG = "BatteryBot";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTheme(R.style.bi_main_theme);
        getSupportActionBar().setElevation(0);
        PersistentFragment.getInstance(getSupportFragmentManager());

        setContentView(R.layout.battery_info);

        pagerAdapter = new BatteryInfoPagerAdapter(getSupportFragmentManager());
        pagerAdapter.setContext(this);
        viewPager = (ViewPager) findViewById(R.id.pager);
        viewPager.setAdapter(pagerAdapter);

        viewPager.setCurrentItem(0);
    }

    @Override
    protected void onResume() {
        super.onResume();

        PagerTabStrip tabStrip = (PagerTabStrip) findViewById(R.id.pager_tab_strip);
        tabStrip.setTabIndicatorColor(Str.accent_color);
    }

    @Override
    public void onStart() {
        super.onStart();

        pagerAdapter.setContext(this);
    }
 
    @Override
    public void onStop() {
        super.onStop();

        pagerAdapter.setContext(null);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && viewPager.getCurrentItem() != 0) {
            viewPager.setCurrentItem(0);
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    private static class BatteryInfoPagerAdapter extends FragmentPagerAdapter {
        private Context context;

        BatteryInfoPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        public void setContext(Context c) {
            context = c;
        }

        @Override
        public int getCount() {
            return 1;
        }

        @Override
        public Fragment getItem(int position) {
            if (position == 0)
                return new CurrentInfoFragment();
            else
                return null;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            if (context == null)
                return null;

            Resources res = context.getResources();

            if (position == 0)
                return res.getString(R.string.tab_current_info).toUpperCase();
            else
                return null;
        }
    }
}
