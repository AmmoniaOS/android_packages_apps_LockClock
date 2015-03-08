/*
 * Copyright (C) 2012 The CyanogenMod Project (DvTonder)
 * Copyright (C) 2015 The OneUI Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cyanogenmod.lockclock.weather;

import android.app.AlertDialog;
import android.app.ActionBar;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.view.MenuItem;
import android.text.TextUtils;
import android.util.Log;

import com.cyanogenmod.lockclock.ClockWidgetProvider;
import com.cyanogenmod.lockclock.R;
import com.cyanogenmod.lockclock.misc.Constants;
import com.cyanogenmod.lockclock.misc.Preferences;
import com.cyanogenmod.lockclock.preference.IconSelectionPreference;
import com.cyanogenmod.lockclock.weather.WeatherUpdateService;

import com.android.internal.util.one.OneUtils;

public class WeatherSettingsActivity extends PreferenceActivity implements
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "WeatherSettingsActivity";

    private static final String[] LOCATION_PREF_KEYS = new String[] {
        Constants.WEATHER_USE_CUSTOM_LOCATION,
        Constants.WEATHER_CUSTOM_LOCATION_CITY
    };
    private static final String[] WEATHER_REFRESH_KEYS = new String[] {
        Constants.SHOW_WEATHER,
        Constants.WEATHER_REFRESH_INTERVAL
    };

    private SwitchPreference mUseCustomLoc;
    private EditTextPreference mCustomWeatherLoc;
    private ListPreference mFontColor;
    private ListPreference mTimestampFontColor;
    private SwitchPreference mUseMetric;
    private IconSelectionPreference mIconSet;
    private SwitchPreference mUseCustomlocation;

    private ContentResolver mResolver;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setBackgroundColor(OneUtils.getCurrentHourColor());
        getPreferenceManager().setSharedPreferencesName(Constants.PREF_NAME);
        addPreferencesFromResource(R.xml.preferences_weather);
        mResolver = getContentResolver();

        // Load items that need custom summaries etc.
        mUseCustomLoc = (SwitchPreference) findPreference(Constants.WEATHER_USE_CUSTOM_LOCATION);
        mCustomWeatherLoc = (EditTextPreference) findPreference(Constants.WEATHER_CUSTOM_LOCATION_CITY);
        mFontColor = (ListPreference) findPreference(Constants.WEATHER_FONT_COLOR);
        mTimestampFontColor = (ListPreference) findPreference(Constants.WEATHER_TIMESTAMP_FONT_COLOR);
        mIconSet = (IconSelectionPreference) findPreference(Constants.WEATHER_ICONS);
        mUseMetric = (SwitchPreference) findPreference(Constants.WEATHER_USE_METRIC);
        mUseCustomlocation = (SwitchPreference) findPreference(Constants.WEATHER_USE_CUSTOM_LOCATION);

        // At first placement/start default the use of Metric units based on locale
        // If we had a previously set value already, this will just reset the same value
        Boolean defValue = Preferences.useMetricUnits(this);
        Preferences.setUseMetricUnits(this, defValue);
        mUseMetric.setChecked(defValue);

        // Show a warning if location manager is disabled and there is no custom location set
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (!lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) && !mUseCustomLoc.isChecked()) {
            showDialog();
        }
        
        final ActionBar bar = getActionBar();
        bar.setDisplayHomeAsUpEnabled(true);
        invalidateOptionsMenu();
    }

    @Override
    public void onResume() {
        super.onResume();

        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        updateLocationSummary();
        updateFontColorsSummary();
        updateIconSetSummary();
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return true;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference pref = findPreference(key);
        if (pref instanceof ListPreference) {
            ListPreference listPref = (ListPreference) pref;
            pref.setSummary(listPref.getEntry());
        }

        boolean needWeatherUpdate = false;
        boolean forceWeatherUpdate = false;

        if (pref == mUseCustomLoc || pref == mCustomWeatherLoc) {
            updateLocationSummary();
        }

        if (pref == mIconSet) {
            updateIconSetSummary();
        }

        if (pref == mUseMetric) {
            // The display format of the temperatures have changed
            // Force a weather update to refresh the display
            forceWeatherUpdate = true;
        }

        // If the weather source has changes, invalidate the custom location settings and change
        // back to GeoLocation to force the user to specify a new custom location if needed
        if (TextUtils.equals(key, Constants.WEATHER_SOURCE)) {
            Preferences.setCustomWeatherLocationId(this, null);
            Preferences.setCustomWeatherLocationCity(this, null);
            Preferences.setUseCustomWeatherLocation(this, false);
            mUseCustomlocation.setChecked(false);
            updateLocationSummary();
        }

        if (key.equals(Constants.WEATHER_USE_CUSTOM_LOCATION)
                || key.equals(Constants.WEATHER_CUSTOM_LOCATION_CITY)) {
            forceWeatherUpdate = true;
        }

        if (key.equals(Constants.SHOW_WEATHER) || key.equals(Constants.WEATHER_REFRESH_INTERVAL)) {
            needWeatherUpdate = true;
        }

        if (Constants.DEBUG) {
            Log.v(TAG, "Preference " + key + " changed, need update " +
                    needWeatherUpdate + " force update "  + forceWeatherUpdate);
        }

        if (Preferences.showWeather(this) && (needWeatherUpdate || forceWeatherUpdate)) {
            Intent updateIntent = new Intent(this, WeatherUpdateService.class);
            if (forceWeatherUpdate) {
                updateIntent.setAction(WeatherUpdateService.ACTION_FORCE_UPDATE);
            }
            startService(updateIntent);
        }

        Intent updateIntent = new Intent(this, ClockWidgetProvider.class);
        sendBroadcast(updateIntent);
    }

    //===============================================================================================
    // Utility classes and supporting methods
    //===============================================================================================

    private void updateLocationSummary() {
        if (mUseCustomLoc.isChecked()) {
            String location = Preferences.customWeatherLocationCity(this);
            if (location == null) {
                location = getResources().getString(R.string.unknown);
            }
            mCustomWeatherLoc.setSummary(location);
        } else {
            mCustomWeatherLoc.setSummary(R.string.weather_geolocated);
        }
    }

    private void showDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final Dialog dialog;

        // Build and show the dialog
        builder.setTitle(R.string.weather_retrieve_location_dialog_title);
        builder.setMessage(R.string.weather_retrieve_location_dialog_message);
        builder.setCancelable(false);
        builder.setPositiveButton(R.string.weather_retrieve_location_dialog_enable_button,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                    }
                });
        builder.setNegativeButton(R.string.cancel, null);
        dialog = builder.create();
        dialog.show();
    }

    private void updateFontColorsSummary() {
        if (mFontColor != null) {
            mFontColor.setSummary(mFontColor.getEntry());
        }
        if (mTimestampFontColor != null) {
            mTimestampFontColor.setSummary(mTimestampFontColor.getEntry());
        }
    }

    private void updateIconSetSummary() {
        if (mIconSet != null) {
            mIconSet.setSummary(mIconSet.getEntry());
        }
    }
}
