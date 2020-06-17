package com.valmont.ui;


import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.valmont.R;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_main);
    }
}
