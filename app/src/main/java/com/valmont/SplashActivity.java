package com.valmont;


import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.valmont.ui.RecogFromCamera;

public class SplashActivity extends AppCompatActivity {

	final int PERMISSION_REQUEST_CODE = 10;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		checkPermissions();

        setContentView(R.layout.activity_splash);
        getSupportActionBar().hide();
//        new Handler().postDelayed(new Runnable() {
//            @Override
//            public void run() {
//
//                startActivity(new Intent(getApplicationContext(), RecogFromCamera.class));
//                finish();
//            }
//        }, 2000); //fu***ng 3s delay


    }

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		switch (requestCode) {
			case PERMISSION_REQUEST_CODE:
				// If request is cancelled, the result arrays are empty.
				if (grantResults.length > 0 &&
						grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					// Permission is granted. Continue the action or workflow
					// in your app.
					new Handler().postDelayed(new Runnable() {
						@Override
						public void run() {

							startActivity(new Intent(getApplicationContext(), RecogFromCamera.class));
							finish();
						}
					}, 2000); //fu***ng 3s delay
				}  else {
					// Explain to the user that the feature is unavailable because
					// the features requires a permission that the user has denied.
					// At the same time, respect the user's decision. Don't link to
					// system settings in an effort to convince the user to change
					// their decision.
				}
				return;
		}
		// Other 'case' lines to check for other
		// permissions this app might request.

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
