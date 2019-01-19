package com.fjbatresv.example.mlkit;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;

import com.google.firebase.analytics.FirebaseAnalytics;

public class BaseActivity extends AppCompatActivity {

    private Button live, many;
    public static final int PERMISSION_REQUEST = 1010;
    FirebaseAnalytics analytics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_base);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        permisos();
        this.analytics = FirebaseAnalytics.getInstance(this);
        live = findViewById(R.id.btn_live);
        live.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bundle bundle = new Bundle();
                bundle.putString("OPTION", "LIVE");
                analytics.logEvent("BASE_SELECTION", bundle);
                startActivity(new Intent(BaseActivity.this, FaceContour.class));
            }
        });
        many = findViewById(R.id.btn_many);
        many.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bundle bundle = new Bundle();
                bundle.putString("OPTION", "MANY");
                analytics.logEvent("BASE_SELECTION", bundle);
                startActivity(new Intent(BaseActivity.this, MainActivity.class));
            }
        });
    }

    private void permisos() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[] {Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode){
            case PERMISSION_REQUEST:
                if (grantResults.length >0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                }else{
                    permisos();
                }
        }
        return;
    }

}
