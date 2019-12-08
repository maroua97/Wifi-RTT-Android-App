/*
 * Copyright (C) 2018 Google Inc. All Rights Reserved.
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
package com.example.android.wifirttscan;

import android.Manifest.permission;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.opencsv.CSVWriter;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

/**
 * Displays ranging information about a particular access point chosen by the user. Uses {@link
 * Handler} to trigger new requests based on
 */
public class AccessPointRangingResultsActivity extends AppCompatActivity {
    private static final String TAG = "APRRActivity";

//    public static final String SCAN_RESULT_EXTRA =
//            "com.example.android.wifirttscan.extra.SCAN_RESULT";
//

    // UI Elements.

    private TextView mNumberOfRequestsTextView;
    private TextView mInfoTextView;
    private EditText mDistanceRangedView;
    private EditText mMillisecondsDelayBeforeNewRangingRequestEditText;

    // Non UI variables.
    private ArrayList<ScanResult> mScanResult;
    private String mMAC;

    private int mNumberOfRangeRequests;
    private float mMillisecondsDelayBeforeNewRangingRequest = 0;



    private String mDistance = null;


    private WifiRttManager mWifiRttManager;
    private RttRangingResultCallback mRttRangingResultCallback;

    private String[] csvData ;
    private FileWriter Writer;
    private CSVWriter csvWriter = null;
    private boolean mStop = false;

    private Button mStartButton;
    private Button mStopButton;



    // Triggers additional RangingRequests with delay (mMillisecondsDelayBeforeNewRangingRequest).
    final Handler mRangeRequestDelayHandler = new Handler();


    private View.OnClickListener mOnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            if (v == mStartButton) {
                onStartRanging();
            }
            if (v == mStopButton){
                onStopRanging();
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_access_point_ranging_results);

        // Initializes UI elements.

        mNumberOfRequestsTextView = findViewById(R.id.number_of_requests_value);
        mInfoTextView=findViewById(R.id.ranging_finished);
        mDistanceRangedView = findViewById(R.id.distance_ranged_value);
        mMillisecondsDelayBeforeNewRangingRequestEditText =findViewById(R.id.ranging_period_edit_value);
        mStartButton= findViewById(R.id.start_ranging_button);
        mStopButton= findViewById(R.id.stop_ranging_button);

        // Retrieve ScanResult from Intent.
        Bundle bundle = getIntent().getExtras();
        mScanResult = bundle.getParcelableArrayList("ScanResults");
        Log.d(TAG, "Scan result is: "+ mScanResult);

        if (mScanResult == null) {
            finish();
        }

        mWifiRttManager = (WifiRttManager) getSystemService(Context.WIFI_RTT_RANGING_SERVICE);
        mRttRangingResultCallback = new RttRangingResultCallback();


        if(Build.VERSION.SDK_INT>22){
            requestPermissions(new String[] {WRITE_EXTERNAL_STORAGE}, 1);
        }
        String filePath = (Environment.getExternalStorageDirectory().getAbsolutePath() + "/rtt_logs.csv");
        String[] header = new String[]{"timestamp_rtt", "timestamp_system", "actual_distance", "ranging_period", "distance_rtt", "std_deviation", "rssi", "num_attempted_measures", "num_successful_measures", "mac", "status"};
        try {
            Writer = new FileWriter(filePath);
            csvWriter = new CSVWriter(Writer);
            csvWriter.writeNext(header);
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG, "Opening file not successful");
        }
        mStartButton.setOnClickListener(mOnClickListener);
        mStopButton.setOnClickListener(mOnClickListener);

    }



    private void startRangingRequest() {
        // Permission for fine location should already be granted via MainActivity (you can't get
        // to this class unless you already have permission. If they get to this class, then disable
        // fine location permission, we kick them back to main activity.

        if (ActivityCompat.checkSelfPermission(this, permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            finish();
        }
        mNumberOfRangeRequests++;
        RangingRequest.Builder builder = new RangingRequest.Builder();

        for (ScanResult scanResult:  mScanResult){
            builder.addAccessPoint(scanResult);
        }

        RangingRequest rangingRequest = builder.build();

        mWifiRttManager.startRanging(
                rangingRequest, getApplication().getMainExecutor(), mRttRangingResultCallback);
    }


    public void onStartRanging() {
        mStop=false;
        mDistance=mDistanceRangedView.getText().toString();
        mMillisecondsDelayBeforeNewRangingRequest= Float.parseFloat((mMillisecondsDelayBeforeNewRangingRequestEditText.getText().toString()));
        mNumberOfRangeRequests = 0;
        startRangingRequest();
        mInfoTextView.setText("Let the ranging begin");
    }

    public void onStopRanging(){
        mStop=true;
        mInfoTextView.setText("The ranging has stopped");
    }

    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onStop()");
        try {
            csvWriter.flush();
            csvWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG, "Flushing and closing csv file not successful");
        }
    }

    // Class that handles callbacks for all RangingRequests and issues new RangingRequests.
    private class RttRangingResultCallback extends RangingResultCallback {

        private void queueNextRangingRequest() {
            mRangeRequestDelayHandler.postDelayed(
                    new Runnable() {
                        @Override
                        public void run() {
                            startRangingRequest();
                        }
                    },
                    (long) mMillisecondsDelayBeforeNewRangingRequest);
        }

        @Override
        public void onRangingFailure(int code) {
            Log.d(TAG, "onRangingFailure() code: " + code);
            queueNextRangingRequest();
        }

        @Override
        public void onRangingResults(@NonNull List<RangingResult> list) {

            // Because we are only requesting RangingResult for one access point (not multiple
            // access points), this will only ever be one. (Use loops when requesting RangingResults
            // for multiple access points.)
            for (RangingResult rangingResult : list) {
                Log.d(TAG, "onRangingResults: "+ rangingResult);

                if (rangingResult.getStatus() == RangingResult.STATUS_SUCCESS) {


                    mNumberOfRequestsTextView.setText(mNumberOfRangeRequests + "");

                    csvData = new String[]{Long.toString(rangingResult.getRangingTimestampMillis()),
                            Long.toString(System.currentTimeMillis()), mDistance,
                            String.valueOf((mMillisecondsDelayBeforeNewRangingRequest)),
                            Integer.toString(rangingResult.getDistanceMm()),
                            Integer.toString(rangingResult.getDistanceStdDevMm()),
                            Integer.toString(rangingResult.getRssi()),
                            Integer.toString(rangingResult.getNumAttemptedMeasurements()),
                            Integer.toString(rangingResult.getNumSuccessfulMeasurements()),
                            String.valueOf(rangingResult.getMacAddress()),
                            String.valueOf(rangingResult.getStatus())};

                    csvWriter.writeNext(csvData);

                } else if (rangingResult.getStatus()
                        == RangingResult.STATUS_RESPONDER_DOES_NOT_SUPPORT_IEEE80211MC) {
                    Log.d(TAG, "RangingResult failed (AP doesn't support IEEE80211 MC.");
                    csvData = new String[]{"None","None", mDistance, String.valueOf(mMillisecondsDelayBeforeNewRangingRequest), "None", "None", "None", "None", "None","None", Integer.toString(2)};
                    csvWriter.writeNext(csvData);
                } else {
                    Log.d(TAG, "RangingResult failed.");
                    csvData = new String[]{"None","None", mDistance, String.valueOf(mMillisecondsDelayBeforeNewRangingRequest), "None", "None", "None", "None", "None", "None", Integer.toString(1)};
                    csvWriter.writeNext(csvData);
                }
            }
            if (!mStop) {
                queueNextRangingRequest();
            }
        }
    }
}
