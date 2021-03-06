/*
 * Anyline Cordova Plugin
 * MrzActivity.java
 *
 * Copyright (c) 2015 Anyline GmbH
 *
 * Created by martin at 2015-07-21
 */
package io.anyline.cordova;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import at.nineyards.anyline.AnylineDebugListener;
import at.nineyards.anyline.camera.AnylineViewConfig;
import at.nineyards.anyline.camera.CameraConfig;
import at.nineyards.anyline.models.AnylineImage;
import at.nineyards.anyline.core.RunFailure;
import at.nineyards.anyline.core.exception_error_codes;
import at.nineyards.anyline.modules.mrz.Identification;
import at.nineyards.anyline.modules.mrz.MrzResultListener;
import at.nineyards.anyline.modules.mrz.MrzScanView;
import at.nineyards.anyline.modules.mrz.MrzResult;
import at.nineyards.anyline.util.TempFileUtil;

public class MrzActivity extends AnylineBaseActivity {
    private static final String TAG = MrzActivity.class.getSimpleName();

    private MrzScanView mrzScanView;
    private static Toast notificationToast;
    private JSONObject json;
    private String cropAndTransformError;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        mrzScanView = new MrzScanView(this, null);
        try {
            json = new JSONObject(configJson);
            // set Config to View
            mrzScanView.setConfig(new AnylineViewConfig(this, json));

            // set individual camera settings for this example by getting the current preferred settings and adapting them
            CameraConfig camConfig = mrzScanView.getPreferredCameraConfig();
            setFocusConfig(json, camConfig);

        } catch (Exception e) {
            //JSONException or IllegalArgumentException is possible, return it to javascript
            finishWithError(Resources.getString(this, "error_invalid_json_data") + "\n" + e.getLocalizedMessage());
            return;
        }
        setContentView(mrzScanView);


        initAnyline();

        // get MRZ config
        if (json.has("mrz")) {
            try {
                // set MRZ strict mode
                JSONObject mrzConf = json.getJSONObject("mrz");
                if (mrzConf.has("strictMode")) {
                    mrzScanView.setStrictMode(mrzConf.getBoolean("strictMode"));
                } else {
                    mrzScanView.setStrictMode(false);
                }

                // set crop and transform
                if (mrzConf.has("cropAndTransformID")) {
                    mrzScanView.setCropAndTransformID(mrzConf.getBoolean("cropAndTransformID"));
                } else {
                    mrzScanView.setCropAndTransformID(false);
                }


                // set crop and transform Error Message
                if (mrzConf.has("cropAndTransformErrorMessage")) {
                    setDebugListener();
                    cropAndTransformError = mrzConf.getString("cropAndTransformErrorMessage");
                }

            } catch (JSONException e){
                Log.d(TAG, e.getMessage());
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mrzScanView.startScanning();
    }

    @Override
    protected void onPause() {
        super.onPause();

        mrzScanView.cancelScanning();
        mrzScanView.releaseCameraInBackground();
    }

    private void setDebugListener() {
        mrzScanView.setDebugListener(new AnylineDebugListener() {
            @Override
            public void onDebug(String name, Object value) {
            }

            @Override
            public void onRunSkipped(RunFailure runFailure) {
                // Show Toast, if cropAndTransform is on true, but not all corners are detected 
                if (runFailure != null && runFailure.errorCode() == exception_error_codes.PointsOutOfCutout.swigValue()) {
                    showToast(cropAndTransformError);
                }
            }
        });
    }

    private void initAnyline() {
        mrzScanView.setCameraOpenListener(this);

        mrzScanView.initAnyline(licenseKey, new MrzResultListener() {

            @Override
            public void onResult(MrzResult mrzResult) {

                JSONObject jsonResult = mrzResult.getResult().toJSONObject();

                try {
                    File imageFile = TempFileUtil.createTempFileCheckCache(MrzActivity.this,
                            UUID.randomUUID().toString(), ".jpg");
                    mrzResult.getCutoutImage().save(imageFile, 90);
                    jsonResult.put("imagePath", imageFile.getAbsolutePath());

                    jsonResult.put("outline", jsonForOutline(mrzResult.getOutline()));
                    jsonResult.put("confidence", mrzResult.getConfidence());

                } catch (IOException e) {
                    Log.e(TAG, "Image file could not be saved.", e);

                } catch (JSONException jsonException) {
                    //should not be possible
                    Log.e(TAG, "Error while putting image path to json.", jsonException);
                }

                if (mrzScanView.getConfig().isCancelOnResult()) {
                    ResultReporter.onResult(jsonResult, true);
                    setResult(AnylinePlugin.RESULT_OK);
                    finish();
                } else {
                    ResultReporter.onResult(jsonResult, false);
                }
            }
        });
        mrzScanView.getAnylineController().setWorkerThreadUncaughtExceptionHandler(this);
    }

    private void showToast(String st) {
        try {
            notificationToast.getView().isShown();
            notificationToast.setText(st);
        } catch (Exception e) {
            notificationToast = Toast.makeText(this, st, Toast.LENGTH_SHORT);
        }
        notificationToast.show();
    }

}
