package com.fjbatresv.example.mlkit;

import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;


import com.fjbatresv.example.mlkit.camera.CameraSource;
import com.fjbatresv.example.mlkit.camera.CameraSourcePreview;
import com.fjbatresv.example.mlkit.face.FaceContourDetectorProcessor;

import java.io.IOException;

public class FaceContour extends AppCompatActivity implements CompoundButton.OnCheckedChangeListener, ActivityCompat.OnRequestPermissionsResultCallback {

    private static final String TAG = "LIVE";

    private CameraSource cameraSource = null;
    private CameraSourcePreview preview;
    private GraphicOverlay graphicOverlay;
    private ImageView change_camera;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_contour);
        change_camera = findViewById(R.id.change_camera);
        preview = findViewById(R.id.firePreview);
        graphicOverlay = findViewById(R.id.fireFaceOverlay);
        change_camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cameraSource != null){
                    if (cameraSource.getCameraFacing() == CameraSource.CAMERA_FACING_BACK){
                        cameraSource.setFacing(CameraSource.CAMERA_FACING_FRONT);
                    }else{
                        cameraSource.setFacing(CameraSource.CAMERA_FACING_BACK);
                    }
                }
                preview.stop();
                startCameraSource();
            }
        });
        createCameraSource();
    }

    private void createCameraSource() {
        if (cameraSource == null){
            cameraSource = new CameraSource(this, graphicOverlay);
        }
        try {
            cameraSource.setMachineLearningFrameProcessor(new FaceContourDetectorProcessor());
        }  catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    private void startCameraSource() {
        if (cameraSource != null) {
            try {
                if (preview == null) {
                    Log.d(TAG, "resume: Preview is null");
                }
                if (graphicOverlay == null) {
                    Log.d(TAG, "resume: graphOverlay is null");
                }
                preview.start(cameraSource, graphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                cameraSource.release();
                cameraSource = null;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        startCameraSource();
    }

    /** Stops the camera. */
    @Override
    protected void onPause() {
        super.onPause();
        preview.stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraSource != null) {
            cameraSource.release();
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        Log.d(TAG, "Set facing");
        if (cameraSource != null) {
            if (isChecked) {
                cameraSource.setFacing(CameraSource.CAMERA_FACING_FRONT);
            } else {
                cameraSource.setFacing(CameraSource.CAMERA_FACING_BACK);
            }
        }
        preview.stop();
        startCameraSource();
    }

}
