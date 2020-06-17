package com.valmont.ui;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;


import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.valmont.R;

import static java.security.AccessController.getContext;

public class RecogFromCamera extends AppCompatActivity {

    static String TAG = "TCWC";
    static boolean isAutoNumbering = true;
    static Integer startingLotNumber = 0;
    Fragment fragment;

    static {
        try {
            System.loadLibrary("opencv_java3");
//            System.loadLibrary("FaceEngine");
            Log.i(TAG, "Successfully load library");
        } catch (UnsatisfiedLinkError e) {
            Log.i(TAG, "Failed to load library error");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_recog_from_camera);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setTitle("Scanning...");
        getSupportActionBar().hide();

        if (getIntent().getExtras() != null) {
            isAutoNumbering = (Boolean) getIntent().getSerializableExtra("numberingmode");
            if(isAutoNumbering)
                startingLotNumber = (Integer)getIntent().getSerializableExtra("startingnumber");
        }
        if (savedInstanceState != null) {

            fragment = getSupportFragmentManager().getFragment(savedInstanceState, "currentFragment");

        } else {
            fragment = new FragmentRecogFromCamera();
        }
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction().replace(R.id.container_body, fragment).commit();

//        checkPermissions();
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        getSupportFragmentManager().putFragment(outState, "currentFragment", fragment);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        fragment.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 2 && data != null)
        {

        }
    }

    @Override
    public void onBackPressed(){

        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {

            case android.R.id.home: {

                finish();
                return true;
            }

            default: {

                return super.onOptionsItemSelected(item);
            }
        }
    }

    private void checkPermissions(){
        Log.e("Face Recognition", "JNI : Check Permission Start");
        String [] permissions={Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA

        };
        int n=0;
        for (int i=0;i<permissions.length;i++){
            if (ActivityCompat.checkSelfPermission(this, permissions[i]) != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.shouldShowRequestPermissionRationale((Activity) this,	permissions[i]);
                n++;
            }
        }

        if (n>0){
            Log.e("Face Recognition", "JNI : Check Permission Successed");
            ActivityCompat.requestPermissions((Activity) this, permissions,	10);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {

                    startActivity(new Intent(getApplicationContext(), RecogFromCamera.class));
                    finish();
                }
            }, 2000); //fu***ng 3s delay
        }else{
            Log.e("Face Recognition", "JNI : Check Permission Successed");
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {

                    startActivity(new Intent(getApplicationContext(), RecogFromCamera.class));
                    finish();
                }
            }, 2000); //fu***ng 3s delay
        }

    }
}
