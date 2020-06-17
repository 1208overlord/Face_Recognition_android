package com.valmont.cameraview;


import android.app.ProgressDialog;
import android.hardware.Camera;
import android.os.Bundle;
//import android.support.annotation.Nullable;
//import android.support.v4.app.Fragment;
import android.util.Log;
import android.widget.Button;


import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import com.valmont.logger.LogRecorder;


/**
 * A simple {@link Fragment} subclass.
 */
public class CameraViewFragment extends Fragment implements OpenCvCameraView.CvCameraViewListener2{

    static String TAG = "TCWC";
    private ProgressDialog pDialog;

    protected Button btnCaptureImage;;

    static final private int CODE_SHARE_RESULT = 1;
    static final private int CODE_LIBRARY_RESULT = 2;

    protected OpenCvCameraView mOpenCvCameraView;




    private int numberOfCameras;
    private int cameraCurrentlyLocked;
    // The first rear facing camera
    private int defaultCameraId;

    // ----------- Configuration Parameters ------------------
    //Max preview and image processing width when landscape
    private int mMaxLandPreviewWidth = 640; //
    //Max frame width of the camera  when landscape
    private int mMaxLandFrameWidth = 800;
    private boolean mRotate = false;
    private float mAspectRatio = 1.3333333f; // 4:3

    private boolean mUseDefaultGalley = true;

    private Mat mRgbFrame0, mRgbFrame1, mRgbFrame2, mDetectedFrame;
    protected Mat mRgbFrame;

    private Mat mRotationMat;

    private int mPreviewWidth = 0;
    private int mPreviewHeight = 0;
    private boolean mRotated;


    private int mCameraIndex;

    protected int frag_type;

    private int nFrameCount =0;
    private final int mPredictionPeriod = 6;
//    private PredictionThread predictionThread;// Thread where tensorflow prediction is processed
    private final Object lockCroppedRgb = new Object();// Object used for synchronizing mCroppedRgb
    private final Object lockPredict = new Object();// Object used for synchronizing result
    private Mat mCroppedRgb,mCroppedMat, mCroppedMatBgr;
    private Size mVehicleSizeInput;
    private static final int DN_INPUT_SIZE = 224;



    public CameraViewFragment() {
        // Required empty public constructor

    }

    public Mat GetImage(){return mRgbFrame;};

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);



    }

    public void initCameraProperty()
    {
        int maxPortraitWidth = (int) (mMaxLandPreviewWidth / mAspectRatio);
        mOpenCvCameraView.setMaxPreviewWidth(mMaxLandPreviewWidth, maxPortraitWidth);
        defaultCameraId = 1;
        numberOfCameras = Camera.getNumberOfCameras();
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                defaultCameraId = i;
            }
        }

        mVehicleSizeInput = new Size(DN_INPUT_SIZE, DN_INPUT_SIZE);
        mCroppedRgb = new Mat(mVehicleSizeInput, CvType.CV_8UC3);
        mCroppedMat = new Mat(mVehicleSizeInput, CvType.CV_8UC3);
        mCroppedMatBgr = new Mat(mVehicleSizeInput, CvType.CV_8UC3);

        mOpenCvCameraView.setCameraIndex(defaultCameraId);
        mOpenCvCameraView.enableFpsMeter();
    }
    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();

        mOpenCvCameraView.enableView();
        cameraCurrentlyLocked = defaultCameraId;

    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }
    @Override
    public void onCameraViewStarted(int previewWidth, int previewHeight, boolean rotated, int cameraIndex) {
        Log.e(TAG, "JNI : CameraView Started");
        mPreviewWidth = previewWidth;
        mPreviewHeight = previewHeight;
        mRotated = rotated;
        mCameraIndex = cameraIndex;
        //initialize for fornt camera
        int rotAngle = 90;
        Point center = new Point(mPreviewHeight / 2, mPreviewHeight / 2);
        if (cameraIndex == Camera.CameraInfo.CAMERA_FACING_BACK) {
            rotAngle = 270;
            center.x = mPreviewWidth / 2;
            center.y = mPreviewWidth / 2;
        }
        mRotationMat = Imgproc.getRotationMatrix2D(center, rotAngle, 1);

        Log.e(TAG, "JNI : Initializing at size %dx%d" + previewWidth + "  " +  previewHeight);
        mRgbFrame1 = new Mat(new Size(mPreviewHeight, mPreviewWidth), CvType.CV_8UC3);
        mRgbFrame2 = new Mat(new Size(mPreviewWidth, mPreviewHeight), CvType.CV_8UC3);
        mRgbFrame = new Mat(new Size(mPreviewWidth, mPreviewHeight), CvType.CV_8UC3);
        mDetectedFrame = new Mat(new Size(mPreviewWidth, mPreviewHeight), CvType.CV_8UC3);
        //mResult = new Result(0, 0, 0, 0, 0, "");
        //predictionThread = new PredictionThread();
        //predictionThread.start();
    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(OpenCvCameraView.CvCameraViewFrame inputFrame) {
        mRgbFrame0 = inputFrame.rgb();

        //if (mbTakingPhoto)
        //return mSnapshot;
        int frameWidth = mRgbFrame0.width();
        int frameHeight = mRgbFrame0.height();

        if (mRotated) {
            Log.i("OnCameraFrame", "mRotated = true");
            //Log.e("CarLicenseRecog", "JNI : mRotated = true");
            if (frameWidth != mPreviewHeight || frameHeight != mPreviewWidth) {
                Log.i("OnCameraFrame", "frameWidth != mPreviewHeight || frameHeight != mPreviewWidth");
                //Log.e("CarLicenseRecog", "JNI : frameWidth != mPreviewHeight || frameHeight != mPreviewWidth");
                Imgproc.resize(mRgbFrame0, mRgbFrame1, new Size(mPreviewHeight, mPreviewWidth));
                if (mCameraIndex == Camera.CameraInfo.CAMERA_FACING_FRONT){
                    Imgproc.warpAffine(mRgbFrame1, mRgbFrame2, mRotationMat, new Size(mPreviewWidth, mPreviewHeight));
                    Core.flip(mRgbFrame2, mRgbFrame, 1);
                }else{
                    Imgproc.warpAffine(mRgbFrame1, mRgbFrame, mRotationMat, new Size(mPreviewWidth, mPreviewHeight));
                    Imgproc.warpAffine(mRgbFrame1, mRgbFrame, mRotationMat, new Size(mPreviewWidth, mPreviewHeight));
                }
            } else {
                Log.i("OnCameraFrame", "frameWidth == mPreviewHeight && frameHeight == mPreviewWidth");
                //Log.e("CarLicenseRecog", "JNI : frameWidth == mPreviewHeight && frameHeight == mPreviewWidth");
                if (mCameraIndex == Camera.CameraInfo.CAMERA_FACING_FRONT){
                    Imgproc.warpAffine(mRgbFrame0, mRgbFrame2, mRotationMat, new Size(mPreviewWidth, mPreviewHeight));
                    Core.flip(mRgbFrame2, mRgbFrame, 1);
                }else{
                    Imgproc.warpAffine(mRgbFrame0, mRgbFrame, mRotationMat, new Size(mPreviewWidth, mPreviewHeight));
                }
            }

        } else {
            Log.i("OnCameraFrame", "mRotated = false");
            //Log.e("CarLicenseRecog", "JNI : mRotated = false");
            if (frameWidth != mPreviewWidth || frameHeight != mPreviewHeight) {
                Log.i("OnCameraFrame", "frameWidth != mPreviewHeight || frameHeight != mPreviewWidth");
                //Log.e("CarLicenseRecog", "JNI : frameWidth != mPreviewHeight || frameHeight != mPreviewWidth");
                Imgproc.resize(mRgbFrame0, mRgbFrame, new Size(mPreviewWidth, mPreviewHeight));
            } else {
                Log.i("OnCameraFrame", "frameWidth == mPreviewHeight && frameHeight == mPreviewWidth");
                //Log.e("CarLicenseRecog", "JNI : frameWidth == mPreviewHeight && frameHeight == mPreviewWidth");
                mRgbFrame0.copyTo(mRgbFrame);
            }
        }
        mRgbFrame.copyTo(mDetectedFrame);
//        if (nFrameCount % mPredictionPeriod == 0) {
//            //Log.e("CarLicenseRecog", "JNI : Rectangle Drawed" + nFrameCount + " : " + mPredictionPeriod);
//            Imgproc.rectangle(mDetectedFrame, new Point(mDetectedFrame.cols() / 3, mDetectedFrame.rows() / 2 - mDetectedFrame.cols() / 6),
//                    new Point(mDetectedFrame.cols() * 2 / 3, mDetectedFrame.rows() / 2 + mDetectedFrame.cols() / 6)
//                    , new Scalar(0, 255, 0));
//        }
//        nFrameCount++;
        //if (nFrameCount >= (10 * mPredictionPeriod)) nFrameCount = 0;

        return mDetectedFrame;
    }

    @Override
    public Mat onCameraRawFrame(OpenCvCameraView.CameraRawFrame rawFrame) {
        return mRgbFrame;
    }

    /**
     * External predictionThread used to do more time consuming image processing
     */
    /**private class PredictionThread extends Thread {
        // true if a request has been made to stop the predictionThread
        volatile boolean stopRequested = false;
        // true if the predictionThread is running and can process more data
        volatile boolean running = true;
        /**
         * Blocks until the predictionThread has stopped

        public void stopThread() {
            stopRequested = true;
            while( running ) {
                predictionThread.interrupt();
                Thread.yield();
            }
        }
        @Override
        public void run() {

            while( !stopRequested ) {
                // Sleep until it has been told to wake up
                synchronized ( Thread.currentThread() ) {
                    try {
                        wait();
                    } catch (InterruptedException ignored) {}
                }
                // process the most recently converted image by swapping image buffered
                synchronized (lockCroppedRgb) {
                    mCroppedRgb.copyTo(mCroppedMat); //CV_8UC3
                }
                Imgproc.cvtColor(mCroppedMat, mCroppedMatBgr, Imgproc.COLOR_RGB2BGR);
                long time = System.currentTimeMillis();
                Result[] result = darknet.detectLicensePlate(mCroppedMatBgr.nativeObj); // CV_8UC1
                Log.e("CarLicenseRecog", "JNI Plate Detect TIME:dealing " + (System.currentTimeMillis() - time));
                synchronized (lockPredict) {
                    float scale = mPreviewWidth / 224.0f;
                    mResult = new Result(0, 0, 0, 0, 0, "");
                    for (Result rr : result) {
                        mResult = new Result((int) (rr.getLeft() * scale), (int) (rr.getTop() * scale) , (int) (rr.getRight() * scale), (int) (rr.getBot() * scale), rr.getConfidence(), rr.getLabel());
                    }
                }
            }
            running = false;
        }
    }*/
}
