package com.valmont.ui;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;



import com.valmont.R;
import com.valmont.cameraview.CameraViewFragment;
import com.valmont.cameraview.OpenCvCameraView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.lang.reflect.Array;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;

@SuppressLint("ValidFragment")
public class FragmentRecogFromCamera extends CameraViewFragment {

    String TAG = "Face Recognition";

    private View rootView;

    protected Button btnStopCapturing;
    ImageView ivRedBand;
    TextView tvDate, tvTime;

    Thread timeThread;

    Context context;


    @SuppressLint("ValidFragment")
    public FragmentRecogFromCamera() {
        Log.d(TAG, "FragmentRecogFromCamera class constructor called...");
        // Required empty public constructor

    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(context) {
        @Override
        public void onManagerConnected(int status) {

            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    mOpenCvCameraView.enableView();
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.e(TAG, "Camera View Created...");
        // Inflate the layout for this fragment

//        context = getActivity();
        rootView = inflater.inflate(R.layout.fragment_recog_from_camera, container, false);


        mOpenCvCameraView = (OpenCvCameraView) rootView.findViewById(R.id.CameraView);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);


        mOpenCvCameraView.setCvCameraViewListener(this);



        mOpenCvCameraView.setCvCameraViewListener(this);
        initCameraProperty();
        frag_type = 3;
        //WaitLiveFace();

        ivRedBand = rootView.findViewById(R.id.ivRedBandX);
        Animation animation = new AlphaAnimation(1, 0.4f); //to change visibility from visible to invisible
        animation.setDuration(500); //1 second duration for each animation cycle
        animation.setInterpolator(new LinearInterpolator());
        animation.setRepeatCount(Animation.INFINITE); //repeating indefinitely
        animation.setRepeatMode(Animation.REVERSE); //animation will start from end point once ended.
        ivRedBand.startAnimation(animation); //to start animation

        tvDate = rootView.findViewById(R.id.tvDate);
        tvTime = rootView.findViewById(R.id.tvTime);

        btnStopCapturing = rootView.findViewById(R.id.BtnStopCapturing);
//        btnStopCapturing.setOnClickListener(new View.OnClickListener() {
//            @RequiresApi(api = Build.VERSION_CODES.M)
//            @Override
//            public void onClick(View v) {
//                Log.e(TAG, "Scanning stopped");
//                Intent intent = new Intent(getContext(), ScannerActivity.class);
//                ArrayList<String> plateArray = new ArrayList<String>(ResultScanning.size());
//                ArrayList<String> lotInfoArray = new ArrayList<String>(ResultLotInfo.size());
//                for(int i=0; i<ResultScanning.size(); i++) {
//                    plateArray.add(ResultScanning.get(i));
//                    lotInfoArray.add(ResultLotInfo.get(i));
//                }
//                intent.putExtra("plateArray", plateArray);
//                intent.putExtra("lotInfoArray", lotInfoArray);
//                startActivity(intent);
//            }
//        });


        // zoomcameraview.setCvCameraViewListener(this);

        Runnable runnable = new CountDownRunner();
        timeThread= new Thread(runnable);
        timeThread.start();

        return rootView;
    }

    public Mat onCameraFrame(OpenCvCameraView.CvCameraViewFrame inputFrame) {
        Log.d(TAG, "OnCameraFrame called...");
        Mat frame = super.onCameraFrame(inputFrame);
        Mat bgr_image = new Mat();
        frame.copyTo(bgr_image);
        Imgproc.cvtColor(bgr_image, bgr_image, Imgproc.COLOR_RGB2BGR);

        return frame;
    }

    public void doWork() {
        try{
            Calendar calendar = Calendar.getInstance();
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
            String date = dateFormat.format(calendar.getTime());
            tvDate.setText(date);

            Date time = new Date();
            String strTimeFormat = "hh:mm:ss a";
            DateFormat timeFormat = new SimpleDateFormat(strTimeFormat);
            String formattedTime= timeFormat.format(time);
            tvTime.setText(formattedTime);
        }catch (Exception e) {}
    }


    class CountDownRunner implements Runnable{
        // @Override
        public void run() {
            while(!Thread.currentThread().isInterrupted()){
                try {
                    doWork();
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }catch(Exception e){
                }
            }
        }
    }

}
