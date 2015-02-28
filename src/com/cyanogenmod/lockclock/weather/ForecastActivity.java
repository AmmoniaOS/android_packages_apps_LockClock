/*
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

import android.annotation.SuppressLint;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.ActionBar;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.MenuItem;
import android.view.Menu;
import android.view.View.OnClickListener;

import com.cyanogenmod.lockclock.misc.Constants;
import com.cyanogenmod.lockclock.misc.Preferences;
import com.cyanogenmod.lockclock.R;
import com.android.internal.util.one.OneUtils;

public class ForecastActivity extends Activity {
    private static final String TAG = "ForecastActivity";

    private static final String KEY_LAST_HOUR_COLOR = "last_hour_color";
    private int mLastHourColor = 0;

    private ProgressDialog mProgressDialog;
    private SharedPreferences prefs;

    private BroadcastReceiver mUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
                mProgressDialog = null;
            }
            if (!intent.getBooleanExtra(WeatherUpdateService.EXTRA_UPDATE_CANCELLED, false)) {
                updateForecastPanel();
            }
        }
    };

    @SuppressLint("InlinedApi")
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        getWindow().setBackgroundDrawable(null);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mLastHourColor = prefs.getInt(KEY_LAST_HOUR_COLOR, 0);
        if (mLastHourColor != 0) {
            getWindow().getDecorView().setBackgroundColor(mLastHourColor);
        }

        setBackgroundColor();
        registerReceiver(mUpdateReceiver, new IntentFilter(WeatherUpdateService.ACTION_UPDATE_FINISHED));
        updateForecastPanel();
        
        final ActionBar bar = getActionBar();
        bar.setDisplayHomeAsUpEnabled(true);
        invalidateOptionsMenu();
    }

    private void setBackgroundColor() {
        if (mLastHourColor == 0) {
            mLastHourColor = getResources().getColor(R.color.default_background);
        }
        int currHourColor = OneUtils.getCurrentHourColor();
        ObjectAnimator animator = ObjectAnimator.ofInt(getWindow().getDecorView(),
                    "backgroundColor", mLastHourColor, currHourColor);
        animator.setDuration(3000);
        animator.setEvaluator(new ArgbEvaluator());
        animator.start();
        mLastHourColor = currHourColor;
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mUpdateReceiver);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(KEY_LAST_HOUR_COLOR, mLastHourColor);
        editor.apply();
        super.onDestroy();
    }

    private void updateForecastPanel() {
        // Get the forecasts data
        WeatherInfo weather = Preferences.getCachedWeatherInfo(this);
        if (weather == null) {
            Log.e(TAG, "Error retrieving forecast data, exiting");
            finish();
            return;
        }

        View fullLayout = ForecastBuilder.buildFullPanel(this, R.layout.forecast_activity, weather);
        setContentView(fullLayout);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 0, 0, R.string.weather_refresh)
                .setShowAsActionFlags(
                        MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final Intent i = new Intent(this, WeatherUpdateService.class);
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case 0:
                mProgressDialog = new ProgressDialog(this);
                mProgressDialog.setMessage(getString(R.string.weather_refreshing));
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(true);
                mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                   @Override
                   public void onCancel(DialogInterface dialog) {
                       mProgressDialog.dismiss();
                       mProgressDialog = null;
                       stopService(i);
                   }
                });
                mProgressDialog.show();

                i.setAction(WeatherUpdateService.ACTION_FORCE_UPDATE);
                startService(i);
                return true;
        }
        return true;
    }
}
