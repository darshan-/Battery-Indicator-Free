/*
    Copyright (c) 2010-2018 Darshan-Josiah Barber

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General License for more details.
*/

package com.darshancomputing.BatteryIndicator;

import android.content.res.Resources;

class Str {
    private static Resources res;

    static String degree_symbol;
    static String fahrenheit_symbol;
    static String celsius_symbol;
    static String volt_symbol;
    static String percent_symbol;
    static String since;

    static String yes;
    static String cancel;
    static String okay;

    static String currently_set_to;

    static String status_boot_completed;
    
    static String[] statuses;
    static String[] healths;
    static String[] pluggeds;

    static void setResources(Resources r) {
        res = r;

        degree_symbol          = res.getString(R.string.degree_symbol);
        fahrenheit_symbol      = res.getString(R.string.fahrenheit_symbol);
        celsius_symbol         = res.getString(R.string.celsius_symbol);
        volt_symbol            = res.getString(R.string.volt_symbol);
        percent_symbol         = res.getString(R.string.percent_symbol);
        since                  = res.getString(R.string.since);

        yes                = res.getString(R.string.yes);
        cancel             = res.getString(R.string.cancel);
        okay               = res.getString(R.string.okay);

        currently_set_to    = res.getString(R.string.currently_set_to);

        statuses            = res.getStringArray(R.array.statuses);
        healths             = res.getStringArray(R.array.healths);
        pluggeds            = res.getStringArray(R.array.pluggeds);
    }

    static String for_n_hours(int n) {
        return String.format(res.getQuantityString(R.plurals.for_n_hours, n), n);
    }

    static String n_hours_m_minutes_long(int n, int m) {
        return (String.format(res.getQuantityString(R.plurals.n_hours_long, n), n) +
                String.format(res.getQuantityString(R.plurals.n_minutes_long, m), m));
    }

    static String n_minutes_long(int n) {
        return String.format(res.getQuantityString(R.plurals.n_minutes_long, n), n);
    }

    static String n_hours_m_minutes_medium(int n, int m) {
        return (String.format(res.getQuantityString(R.plurals.n_hours_medium, n), n) +
                String.format(res.getQuantityString(R.plurals.n_minutes_medium, m), m));
    }

    static String n_hours_long_m_minutes_medium(int n, int m) {
        return (String.format(res.getQuantityString(R.plurals.n_hours_long, n), n) +
                String.format(res.getQuantityString(R.plurals.n_minutes_medium, m), m));
    }

    static String n_hours_m_minutes_short(int n, int m) {
        return (String.format(res.getQuantityString(R.plurals.n_hours_short, n), n) +
                String.format(res.getQuantityString(R.plurals.n_minutes_short, m), m));
    }

    static String n_days_m_hours(int n, int m) {
        return (String.format(res.getQuantityString(R.plurals.n_days, n), n) +
                String.format(res.getQuantityString(R.plurals.n_hours, m), m));
    }

    static String n_log_items(int n) {
        return String.format(res.getQuantityString(R.plurals.n_log_items, n), n);
    }

    /* temperature is the integer number of tenths of degrees Celcius, as returned by BatteryManager */
    static String formatTemp(int temperature, boolean convertF, boolean includeTenths) {
        double d;
        String s;

        if (convertF){
            d = java.lang.Math.round(temperature * 9 / 5.0) / 10.0 + 32.0;
            s = degree_symbol + fahrenheit_symbol;
        } else {
            d = temperature / 10.0;
            s = degree_symbol + celsius_symbol;
        }

        return (includeTenths ? String.valueOf(d) : String.valueOf(java.lang.Math.round(d))) + s;
    }

    static String formatTemp(int temperature, boolean convertF) {
        return formatTemp(temperature, convertF, true);
    }

    static String formatVoltage(int voltage) {
        return String.valueOf(voltage / 1000.0) + volt_symbol;
    }

    static int indexOf(String[] a, String key) {
        for (int i=0, size=a.length; i < size; i++)
            if (key.equals(a[i])) return i;

        return -1;
    }

    static android.text.Spanned timeRemaining(BatteryInfo info) {
        if (info.prediction.what == BatteryInfo.Prediction.NONE) {
            return android.text.Html.fromHtml("<font color=\"#6fc14b\">" + statuses[info.status] + "</font>");
        } else {
            BatteryInfo.RelativeTime predicted = info.prediction.last_rtime;

            if (predicted.days > 0)
                return android.text.Html.fromHtml("<font color=\"#6fc14b\">" + predicted.days + "d</font> " +
                                                  "<font color=\"#33b5e5\"><small>" + predicted.hours + "h</small></font>");
            else if (predicted.hours > 0)
                return android.text.Html.fromHtml("<font color=\"#6fc14b\">" + predicted.hours + "h</font> " +
                                                  "<font color=\"#33b5e5\"><small>" + predicted.minutes + "m</small></font>");
            else
                return android.text.Html.fromHtml("<font color=\"#33b5e5\"><small>" + predicted.minutes + " mins</small></font>");
        }
    }

    // Shows mdash rather than "Fully Charged" when no prediction.
    //   The widget still wants the old behavior.
    static android.text.Spanned timeRemainingMainScreen(BatteryInfo info) {
        if (info.prediction.what == BatteryInfo.Prediction.NONE)
            return android.text.Html.fromHtml("&nbsp;&nbsp;&nbsp;&mdash;&nbsp;&nbsp;&nbsp;");
        else
            return timeRemaining(info);
    }

    static String untilWhat(BatteryInfo info) {
        if (info.prediction.what == BatteryInfo.Prediction.NONE)
            return "";
        else if (info.prediction.what == BatteryInfo.Prediction.UNTIL_CHARGED)
            return res.getString(R.string.activity_until_charged);
        else
            return res.getString(R.string.activity_until_drained);
    }
}
