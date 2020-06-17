package com.valmont.cameraview;

import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import org.opencv.R;
import org.opencv.android.FpsMeter;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;


/**
 * This class is an implementation of the Bridge View between OpenCV and Java Camera.
 * This class relays on the functionality available in base class and only implements
 * required functions:
 * connectCamera - opens Java camera and sets the PreviewCallback to be delivered.
 * disconnectCamera - closes the camera and stops preview.
 * When frame is delivered via callback from Camera - it processed via OpenCV to be
 * converted to RGBA32 and then passed to the external callback for modifications if required.
 */
public class OpenCvCameraView extends SurfaceView implements
SurfaceHolder.Callback, Camera.PreviewCallback {

	private static final int MAX_UNSPECIFIED = -1;
	private static final int STOPPED = 0;
	private static final int STARTED = 1;

	private int mState = STOPPED;
	private Bitmap mCacheBitmap;
	private CvCameraViewListener2 mListener;
	private boolean mSurfaceExist;
	private Object mSyncObject = new Object();

	protected int mFrameWidth;
	protected int mFrameHeight;
	protected int mMaxHeight;
	protected int mMaxWidth;
	protected float mScale = 0;
	protected int mPreviewFormat = RGBA;
	protected int mCameraIndex = CAMERA_ID_ANY;
	protected boolean mEnabled;
	protected FpsMeter mFpsMeter = null;

	public static final int CAMERA_ID_ANY   = -1;
	public static final int CAMERA_ID_BACK  = 99;
	public static final int CAMERA_ID_FRONT = 98;
	public static final int RGBA = 1;
	public static final int GRAY = 2;

	private static final int MAGIC_TEXTURE_ID = 10;
	private static final String TAG = "CarLicenseRecog";

	private byte mBuffer[];
	private Mat[] mFrameChain;
	private CameraRawFrame[] mRawFrames;
	private int mChainIdx = 0;
	private Thread mThread;
	private boolean mStopThread;

	protected Camera mCamera;
	protected CvCameraViewFrame[] mCameraFrame;
	private SurfaceTexture mSurfaceTexture;

	private boolean mUseRawFrame = false;
	public boolean mRotated = false;
	protected int mPreviewWidth;
	protected int mPreviewHeight;
	protected int mMaxLandscapeWidth = 352;// Maxim image processing width when landscape
	protected int mMaxPortraitWidth = 264;
	public OpenCvCameraView(Context context, int cameraId) {
		super(context);
		mCameraIndex = cameraId;
		getHolder().addCallback(this);
		mMaxWidth = MAX_UNSPECIFIED;
		mMaxHeight = MAX_UNSPECIFIED;
	}

	public OpenCvCameraView(Context context, AttributeSet attrs) {
		super(context, attrs);

		int count = attrs.getAttributeCount();
		Log.e(TAG, "Attr count: " + Integer.valueOf(count));

		TypedArray styledAttrs = getContext().obtainStyledAttributes(attrs, R.styleable.CameraBridgeViewBase);
		if (styledAttrs.getBoolean(R.styleable.CameraBridgeViewBase_show_fps, false))
			enableFpsMeter();

		mCameraIndex = styledAttrs.getInt(R.styleable.CameraBridgeViewBase_camera_id, -1);

		getHolder().addCallback(this);
		mMaxWidth = MAX_UNSPECIFIED;
		mMaxHeight = MAX_UNSPECIFIED;
		styledAttrs.recycle();
	}

	public void setMaxPreviewWidth(int maxLandscapeWidth, int maxPortraitWidth){
		mMaxLandscapeWidth = maxLandscapeWidth;
		mMaxPortraitWidth = maxPortraitWidth;
	}
	/**
	 * Sets the camera index
	 * @param cameraIndex new camera index
	 */
	public void setCameraIndex(int cameraIndex) {
		this.mCameraIndex = cameraIndex;
	}

	public interface CvCameraViewListener2 {
		/**
		 * This method is invoked when camera preview has started. After this method is invoked
		 * the frames will start to be delivered to client via the onCameraFrame() callback.
		 * @param width -  the width of the frames that will be delivered
		 * @param height - the height of the frames that will be delivered
		 */
		public void onCameraViewStarted(int width, int height, boolean rotated, int cameraIndex);

		/**
		 * This method is invoked when camera preview has been stopped for some reason.
		 * No frames will be delivered via onCameraFrame() callback after this method is called.
		 */
		public void onCameraViewStopped();

		/**
		 * This method is invoked when delivery of the frame needs to be done.
		 * The returned values - is a modified frame which needs to be displayed on the screen.
		 * TODO: pass the parameters specifying the format of the frame (BPP, YUV or RGB and etc)
		 */
		public Mat onCameraFrame(CvCameraViewFrame inputFrame);
		/**
		 * This method is invoked when delivery of the frame needs to be done.
		 * The returned values - is a modified frame which needs to be displayed on the screen.
		 * TODO: pass the parameters specifying the format of the frame (BPP, YUV or RGB and etc)
		 */
		public Mat onCameraRawFrame(CameraRawFrame rawFrame);
	};
	/**
	 * This class interface is abstract representation of single frame from camera for onCameraFrame callback
	 * Attention: Do not use objects, that represents this interface out of onCameraFrame callback!
	 */
	public class CvCameraViewFrame {

		public Mat gray() {
			return mYuvFrameData.submat(0, mHeight, 0, mWidth);
		}

		public Mat rgba() {
			// COLOR_YUV420sp2RGBA = COLOR_YUV2RGBA_NV21
			Imgproc.cvtColor(mYuvFrameData, mRgba, Imgproc.COLOR_YUV2RGBA_NV21, 4);
			return mRgba;
		}

		public Mat rgb() {
			// COLOR_YUV420sp2RGB = COLOR_YUV2RGB_NV21
			Imgproc.cvtColor(mYuvFrameData, mRgba, Imgproc.COLOR_YUV2RGB_NV21, 3);
			return mRgba;
		}

		public Mat bgr() {
			// COLOR_YUV420sp2BGR = COLOR_YUV2BGR_NV21
			Imgproc.cvtColor(mYuvFrameData, mRgba, Imgproc.COLOR_YUV2BGR_NV21, 3);
			return mRgba;
		}

		public Mat yuv420sp() {
			return mYuvFrameData;
		}

		public CvCameraViewFrame(Mat Yuv420sp, int width, int height) {
			mWidth = width;
			mHeight = height;
			mYuvFrameData = Yuv420sp;
			mRgba = new Mat();
		}

		public void release() {
			mRgba.release();
		}

		private Mat mYuvFrameData;
		private Mat mRgba;
		private int mWidth;
		private int mHeight;
	};

	public class CameraRawFrame {
		private byte[] mRawFrame;
		private Mat mYuvFrameData;
		
		public CameraRawFrame(int frameHeight, int frameWidth) {
			mRawFrame = new byte[(frameHeight + (frameHeight/2)) * frameWidth];
			mYuvFrameData = new Mat(frameHeight + (frameHeight/2), frameWidth, CvType.CV_8UC1);
		}
		
		public void putData(byte[] rawFrame) {
			System.arraycopy(rawFrame, 0, mRawFrame, 0, rawFrame.length);
		}
		
		public Mat yuv420sp() {
			mYuvFrameData.put(0, 0, mRawFrame);
			return mYuvFrameData;
		}
		
		public void release() {
			mYuvFrameData.release();
		}
		
		public byte[] rawFrame(){
			return mRawFrame;
		}
	};

	public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
		Log.e(TAG, "call surfaceChanged event");
		synchronized(mSyncObject) {
			if (!mSurfaceExist) {
				mSurfaceExist = true;
				checkCurrentState();
			} else {
				/** Surface changed. We need to stop camera and restart with new parameters */
				/* Pretend that old surface has been destroyed */
				mSurfaceExist = false;
				checkCurrentState();
				/* Now use new surface. Say we have it now */
				mSurfaceExist = true;
				checkCurrentState();
			}
		}
	}

	public void surfaceCreated(SurfaceHolder holder) {
		/* Do nothing. Wait until surfaceChanged delivered */
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		synchronized(mSyncObject) {
			mSurfaceExist = false;
			checkCurrentState();
		}
	}

	/**
	 * This method is provided for clients, so they can enable the camera connection.
	 * The actual onCameraViewStarted callback will be delivered only after both this method is called and surface is available
	 */
	public void enableView() {
		synchronized(mSyncObject) {
			mEnabled = true;
			checkCurrentState();
		}
	}

	/**
	 * This method is provided for clients, so they can disable camera connection and stop
	 * the delivery of frames even though the surface view itself is not destroyed and still stays on the scren
	 */
	public void disableView() {
		synchronized(mSyncObject) {
			mEnabled = false;
			checkCurrentState();
		}
	}

	/**
	 * This method enables label with fps value on the screen
	 */
	public void enableFpsMeter() {
		if (mFpsMeter == null) {
			mFpsMeter = new FpsMeter();
			mFpsMeter.setResolution(mFrameWidth, mFrameHeight);
		}
	}

	public void disableFpsMeter() {
		mFpsMeter = null;
	}

	/**
	 *
	 * @param listener
	 */

	public void setCvCameraViewListener(CvCameraViewListener2 listener) {
		mListener = listener;
	}
	/**
	 * This method sets the maximum size that camera frame is allowed to be. When selecting
	 * size - the biggest size which less or equal the size set will be selected.
	 * As an example - we set setMaxFrameSize(200,200) and we have 176x152 and 320x240 sizes. The
	 * preview frame will be selected with 176x152 size.
	 * This method is useful when need to restrict the size of preview frame for some reason (for example for video recording)
	 * @param maxWidth - the maximum width allowed for camera frame.
	 * @param maxHeight - the maximum height allowed for camera frame
	 */
	public void setMaxFrameSize(int maxWidth, int maxHeight) {
		mMaxWidth = maxWidth;
		mMaxHeight = maxHeight;
	}

	/**
	 * Called when mSyncObject lock is held
	 */
	private void checkCurrentState() {
		Log.e(TAG, "call checkCurrentState");
		int targetState;

		if (mEnabled && mSurfaceExist && getVisibility() == VISIBLE) {
			targetState = STARTED;
		} else {
			targetState = STOPPED;
		}

		if (targetState != mState) {
			/* The state change detected. Need to exit the current state and enter target state */
			processExitState(mState);
			mState = targetState;
			processEnterState(mState);
		}
	}

	private void processEnterState(int state) {
		Log.e(TAG, "call processEnterState: " + state);
		switch(state) {
		case STARTED:
			onEnterStartedState();
			if (mListener != null) {
				mListener.onCameraViewStarted(mPreviewWidth, mPreviewHeight, mRotated, mCameraIndex);
			}
			break;
		case STOPPED:
			onEnterStoppedState();
			if (mListener != null) {
				mListener.onCameraViewStopped();
			}
			break;
		};
	}

	private void processExitState(int state) {
		Log.e(TAG, "call processExitState: " + state);
		switch(state) {
		case STARTED:
			onExitStartedState();
			break;
		case STOPPED:
			onExitStoppedState();
			break;
		};
	}

	private void onEnterStoppedState() {
		/* nothing to do */
	}

	private void onExitStoppedState() {
		/* nothing to do */
	}

	// NOTE: The order of bitmap constructor and camera connection is important for android 4.1.x
	// Bitmap must be constructed before surface
	private void onEnterStartedState() {
		Log.e(TAG, "call onEnterStartedState");
		/* Connect camera */
		if (!connectCamera(getWidth(), getHeight())) {
            Log.e(TAG, "Failed to connect Camera!!!");
			AlertDialog ad = new AlertDialog.Builder(getContext()).create();
			ad.setCancelable(false); // This blocks the 'BACK' button
			ad.setMessage("It seems that you device does not support camera (or it is locked). Application will be closed.");
			ad.setButton(DialogInterface.BUTTON_NEUTRAL,  "OK", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
					((Activity) getContext()).finish();
				}
			});
			ad.show();
		}
	}

	private void onExitStartedState() {
		disconnectCamera();
		if (mCacheBitmap != null) {
			mCacheBitmap.recycle();
		}
	}
	
	protected void deliverAndDrawRawFrame(CameraRawFrame rawframe) {
		Mat modified;
		if (mListener != null) {
			modified = mListener.onCameraRawFrame(rawframe);
		} else {
			return;
		}

		boolean bmpValid = true;
		if (modified != null) {
			try {
				Utils.matToBitmap(modified, mCacheBitmap);
			} catch(Exception e) {
				Log.e(TAG, "Mat type: " + modified);
				Log.e(TAG, "Bitmap type: " + mCacheBitmap.getWidth() + "*" + mCacheBitmap.getHeight());
				Log.e(TAG, "Utils.matToBitmap() throws an exception: " + e.getMessage());
				bmpValid = false;
			}
		}

		if (bmpValid && mCacheBitmap != null) {
			Canvas canvas = getHolder().lockCanvas();
			if (canvas != null) {
				canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
				//Log.e(TAG, "mStretch value: " + mScale);

				if (mScale != 0) {
					canvas.drawBitmap(mCacheBitmap, new Rect(0,0,mCacheBitmap.getWidth(), mCacheBitmap.getHeight()),
							new Rect((int)((canvas.getWidth() - mScale*mCacheBitmap.getWidth()) / 2),
									(int)((canvas.getHeight() - mScale*mCacheBitmap.getHeight()) / 2),
									(int)((canvas.getWidth() - mScale*mCacheBitmap.getWidth()) / 2 + mScale*mCacheBitmap.getWidth()),
									(int)((canvas.getHeight() - mScale*mCacheBitmap.getHeight()) / 2 + mScale*mCacheBitmap.getHeight())), null);
				} else {
					canvas.drawBitmap(mCacheBitmap, new Rect(0,0,mCacheBitmap.getWidth(), mCacheBitmap.getHeight()),
							new Rect((canvas.getWidth() - mCacheBitmap.getWidth()) / 2,
									(canvas.getHeight() - mCacheBitmap.getHeight()) / 2,
									(canvas.getWidth() - mCacheBitmap.getWidth()) / 2 + mCacheBitmap.getWidth(),
									(canvas.getHeight() - mCacheBitmap.getHeight()) / 2 + mCacheBitmap.getHeight()), null);
				}

				if (mFpsMeter != null) {
					mFpsMeter.measure();
					mFpsMeter.draw(canvas, 20, 30);
				}
				getHolder().unlockCanvasAndPost(canvas);
			}
		}
	}
	
	/**
	 * This method shall be called by the subclasses when they have valid
	 * object and want it to be delivered to external client (via callback) and
	 * then displayed on the screen.
	 * @param frame - the current frame to be delivered
	 */
	protected void deliverAndDrawFrame(CvCameraViewFrame frame) {
		Mat modified;

		if (mListener != null) {
			modified = mListener.onCameraFrame(frame);
		} else {
			modified = frame.rgba();
		}

		boolean bmpValid = true;
		if (modified != null) {
			try {
				Utils.matToBitmap(modified, mCacheBitmap);
			} catch(Exception e) {
				Log.e(TAG, "Mat type: " + modified);
				Log.e(TAG, "Bitmap type: " + mCacheBitmap.getWidth() + "*" + mCacheBitmap.getHeight());
				Log.e(TAG, "Utils.matToBitmap() throws an exception: " + e.getMessage());
				bmpValid = false;
			}
		}

		if (bmpValid && mCacheBitmap != null) {
			Canvas canvas = getHolder().lockCanvas();
			if (canvas != null) {
				canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
				//Log.e(TAG, "mStretch value: " + mScale);

				if (mScale != 0) {
					canvas.drawBitmap(mCacheBitmap, new Rect(0,0,mCacheBitmap.getWidth(), mCacheBitmap.getHeight()),
							new Rect((int)((canvas.getWidth() - mScale*mCacheBitmap.getWidth()) / 2),
									(int)((canvas.getHeight() - mScale*mCacheBitmap.getHeight()) / 2),
									(int)((canvas.getWidth() - mScale*mCacheBitmap.getWidth()) / 2 + mScale*mCacheBitmap.getWidth()),
									(int)((canvas.getHeight() - mScale*mCacheBitmap.getHeight()) / 2 + mScale*mCacheBitmap.getHeight())), null);
				} else {
					canvas.drawBitmap(mCacheBitmap, new Rect(0,0,mCacheBitmap.getWidth(), mCacheBitmap.getHeight()),
							new Rect((canvas.getWidth() - mCacheBitmap.getWidth()) / 2,
									(canvas.getHeight() - mCacheBitmap.getHeight()) / 2,
									(canvas.getWidth() - mCacheBitmap.getWidth()) / 2 + mCacheBitmap.getWidth(),
									(canvas.getHeight() - mCacheBitmap.getHeight()) / 2 + mCacheBitmap.getHeight()), null);
				}

				if (mFpsMeter != null) {
					mFpsMeter.measure();
					mFpsMeter.draw(canvas, 20, 30);
				}
				getHolder().unlockCanvasAndPost(canvas);
			}
		}
	}

	// NOTE: On Android 4.1.x the function must be called before SurfaceTextre constructor!
	protected void AllocateCache()
	{
		mCacheBitmap = Bitmap.createBitmap(mPreviewWidth, mPreviewHeight, Bitmap.Config.ARGB_8888);
	}

	public interface ListItemAccessor {
		public int getWidth(Object obj);
		public int getHeight(Object obj);
	};

	/**
	 * This helper method can be called by subclasses to select camera preview size.
	 * It goes over the list of the supported preview sizes and selects the maximum one which
	 * fits both values set via setMaxFrameSize() and surface frame allocated for this view
	 * @param supportedSizes
	 * @param surfaceWidth
	 * @param surfaceHeight
	 * @return optimal frame size
	 */
	protected Size calculateCameraFrameSize(List<?> supportedSizes, ListItemAccessor accessor, int surfaceWidth, int surfaceHeight) {
		int calcWidth = 0;
		int calcHeight = 0;

		int maxAllowedWidth = (mMaxWidth != MAX_UNSPECIFIED && mMaxWidth < surfaceWidth)? mMaxWidth : surfaceWidth;
		int maxAllowedHeight = (mMaxHeight != MAX_UNSPECIFIED && mMaxHeight < surfaceHeight)? mMaxHeight : surfaceHeight;

//		int maxAllowedWidth = mMaxWidth;
//		int maxAllowedHeight = mMaxHeight;

		if (maxAllowedWidth > maxAllowedHeight){
			for (Object size : supportedSizes) {
				int width0 = accessor.getWidth(size);
				int height0 = accessor.getHeight(size);
				int width = width0;
				int height= height0;
				mRotated = false;
				if (width0 < height0){
					mRotated = true;
					width = height0;
					height = width0;
				}
				if (width <= maxAllowedWidth && height <= maxAllowedHeight) {
					if (width >= calcWidth && height >= calcHeight) {
						calcWidth = (int) width;
						calcHeight = (int) height;
					}
				}
			}
		}
		else{
			for (Object size : supportedSizes) {
				int width0 = accessor.getWidth(size);
				int height0 = accessor.getHeight(size);
				int width = width0;
				int height= height0;
				mRotated = false;
				if (width0 > height0){
					mRotated = true;
					width = height0;
					height = width0;
				}
				if (width <= maxAllowedWidth && height <= maxAllowedHeight) {
					if (width >= calcWidth && height >= calcHeight) {
						calcWidth = (int) width;
						calcHeight = (int) height;
					}
				}
			}
		}
		return new Size(calcWidth, calcHeight);
	}

	public static class JavaCameraSizeAccessor implements ListItemAccessor {

		@Override
		public int getWidth(Object obj) {
			Camera.Size size = (Camera.Size) obj;
			return size.width;
		}

		@Override
		public int getHeight(Object obj) {
			Camera.Size size = (Camera.Size) obj;
			return size.height;
		}
	}

	/**
	 * @param width - the width of SurfaceView
	 * @param height - the height of SurfaceView
	 */
	protected boolean initializeCamera(int width, int height) {
		Log.e(TAG, "Initialize java camera");
		boolean result = true;
		synchronized (this) {
			mCamera = null;

			if (mCameraIndex == CAMERA_ID_ANY) {
				Log.e(TAG, "Trying to open camera with old open()");
				try {
					mCamera = Camera.open();
				}
				catch (Exception e){
					Log.e(TAG, "Camera is not available (in use or does not exist): " + e.getLocalizedMessage());
				}

				if(mCamera == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
					boolean connected = false;
					for (int camIdx = 0; camIdx < Camera.getNumberOfCameras(); ++camIdx) {
						Log.e(TAG, "Trying to open camera with new open(" + Integer.valueOf(camIdx) + ")");
						try {
							mCamera = Camera.open(camIdx);
							connected = true;
						} catch (RuntimeException e) {
							Log.e(TAG, "Camera #" + camIdx + "failed to open: " + e.getLocalizedMessage());
						}
						if (connected) break;
					}
				}
			} else {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
					int localCameraIndex = mCameraIndex;
					if (mCameraIndex == CAMERA_ID_BACK) {
						Log.i(TAG, "Trying to open back camera");
						Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
						for (int camIdx = 0; camIdx < Camera.getNumberOfCameras(); ++camIdx) {
							Camera.getCameraInfo( camIdx, cameraInfo );
							if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
								localCameraIndex = camIdx;
								break;
							}
						}
					} else if (mCameraIndex == CAMERA_ID_FRONT) {
						Log.i(TAG, "Trying to open front camera");
						Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
						for (int camIdx = 0; camIdx < Camera.getNumberOfCameras(); ++camIdx) {
							Camera.getCameraInfo( camIdx, cameraInfo );
							if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
								localCameraIndex = camIdx;
								break;
							}
						}
					}
					if (localCameraIndex == CAMERA_ID_BACK) {
						Log.e(TAG, "Back camera not found!");
					} else if (localCameraIndex == CAMERA_ID_FRONT) {
						Log.e(TAG, "Front camera not found!");
					} else {
						Log.e(TAG, "Trying to open camera with new open(" + Integer.valueOf(localCameraIndex) + ")");
						try {
							mCamera = Camera.open(localCameraIndex);
						} catch (RuntimeException e) {
							Log.e(TAG, "Camera #" + localCameraIndex + "failed to open: " + e.getLocalizedMessage());
						}
					}
				}
			}

			if (mCamera == null)
				return false;

			/* Now set camera parameters */
			try {
				Camera.Parameters params = mCamera.getParameters();

				params.setZoom(2);

				Log.e(TAG, "getSupportedPreviewSizes()");
				List<android.hardware.Camera.Size> sizes = params.getSupportedPreviewSizes();

				if (sizes != null) {
					/* Select the size that fits surface considering maximum size allowed */
					Size frameSize0 = calculateCameraFrameSize(sizes, new JavaCameraSizeAccessor(), width, height);

					mPreviewWidth = (int)frameSize0.width;
					mPreviewHeight = (int)frameSize0.height;

					Size frameSize;
					if (!mRotated){
						frameSize = new Size(frameSize0.width, frameSize0.height);
						if (mPreviewWidth > mMaxLandscapeWidth){
							mPreviewHeight = (int)((float)mPreviewHeight * mMaxLandscapeWidth / mPreviewWidth);
							mPreviewWidth = mMaxLandscapeWidth;
						}
					}
					else{
						frameSize = new Size(frameSize0.height, frameSize0.width);
						if (mPreviewWidth > mMaxPortraitWidth){
							mPreviewHeight = (int)((float)mPreviewHeight * mMaxPortraitWidth / mPreviewWidth);
							mPreviewWidth = mMaxPortraitWidth;
						}
						Log.e(TAG, "Preview size is Rotated");
					}

					params.setPreviewFormat(ImageFormat.NV21);
					Log.e(TAG, "Set preview size to " + Integer.valueOf((int)frameSize.width) + "x" + Integer.valueOf((int)frameSize.height));
					params.setPreviewSize((int)frameSize.width, (int)frameSize.height);

					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH && !android.os.Build.MODEL.equals("GT-I9100"))
						params.setRecordingHint(true);

					List<String> FocusModes = params.getSupportedFocusModes();
					if (FocusModes != null && FocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO))
					{
						params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
					}

					mCamera.setParameters(params);
					params = mCamera.getParameters();

					mFrameWidth = params.getPreviewSize().width;
					mFrameHeight = params.getPreviewSize().height;

					mScale = Math.min(((float)height)/mPreviewHeight, ((float)width)/mPreviewWidth);

					if (mFpsMeter != null) { //KYH
						mFpsMeter.setResolution(mPreviewWidth, mPreviewHeight);
					}

					int size = mFrameWidth * mFrameHeight;
					size  = size * ImageFormat.getBitsPerPixel(params.getPreviewFormat()) / 8;
					mBuffer = new byte[size];

					mCamera.addCallbackBuffer(mBuffer);
					mCamera.setPreviewCallbackWithBuffer(this);

					mRawFrames = new CameraRawFrame[2];
					mRawFrames[0] = new CameraRawFrame(mFrameHeight, mFrameWidth);
					mRawFrames[1] = new CameraRawFrame(mFrameHeight, mFrameWidth);
					
					mFrameChain = new Mat[2];
					mFrameChain[0] = new Mat(mFrameHeight + (mFrameHeight/2), mFrameWidth, CvType.CV_8UC1);
					mFrameChain[1] = new Mat(mFrameHeight + (mFrameHeight/2), mFrameWidth, CvType.CV_8UC1);

					AllocateCache();

					mCameraFrame = new CvCameraViewFrame[2];
					mCameraFrame[0] = new CvCameraViewFrame(mFrameChain[0], mFrameWidth, mFrameHeight);
					mCameraFrame[1] = new CvCameraViewFrame(mFrameChain[1], mFrameWidth, mFrameHeight);

					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
						mSurfaceTexture = new SurfaceTexture(MAGIC_TEXTURE_ID);
						mCamera.setPreviewTexture(mSurfaceTexture);
					} else
						mCamera.setPreviewDisplay(null);

					/* Finally we are ready to start the preview */
					Log.e(TAG, "startPreview");
					mCamera.startPreview();
				}
				else
					result = false;
			} catch (Exception e) {
				result = false;
				e.printStackTrace();
			}
		}

		return result;
	}

	protected void releaseCamera() {
		synchronized (this) {
			if (mCamera != null) {
				mCamera.stopPreview();
				mCamera.setPreviewCallback(null);

				mCamera.release();
			}
			mCamera = null;
			if (mRawFrames != null) {
				mRawFrames[0].release();
				mRawFrames[1].release();
			}
			if (mCameraFrame != null) {
				mCameraFrame[0].release();
				mCameraFrame[1].release();
			}
		}
	}

	private boolean mCameraFrameReady = false;

	/**
	 * This method is invoked shall perform concrete operation to initialize the camera.
	 * CONTRACT: as a result of this method variables mFrameWidth and mFrameHeight MUST be
	 * initialized with the size of the Camera frames that will be delivered to external processor.
	 * @param width - the width of this SurfaceView
	 * @param height - the height of this SurfaceView
	 */
	protected boolean connectCamera(int width, int height) {

		/* 1. We need to instantiate camera
		 * 2. We need to start thread which will be getting frames
		 */
		/* First step - initialize camera connection */
		Log.e(TAG, "Connecting to camera");
		if (!initializeCamera(width, height))
			return false;

		mCameraFrameReady = false;

		/* now we can start update thread */
		Log.e(TAG, "Starting processing thread");
		mStopThread = false;
		mThread = new Thread(new CameraWorker());
		mThread.start();

		return true;
	}

	/**
	 * Disconnects and release the particular camera object being connected to this surface view.
	 * Called when syncObject lock is held
	 */
	protected void disconnectCamera() {
		/* 1. We need to stop thread which updating the frames
		 * 2. Stop camera and release it
		 */
		Log.e(TAG, "Disconnecting from camera");
		try {
			mStopThread = true;
			Log.e(TAG, "Notify thread");
			synchronized (this) {
				this.notify();
			}
			Log.e(TAG, "Wating for thread");
			if (mThread != null)
				mThread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			mThread =  null;
		}

		/* Now release camera */
		releaseCamera();

		mCameraFrameReady = false;
	}

	@Override
	public void onPreviewFrame(byte[] frame, Camera arg1) {
		//Log.e(TAG, "Preview Frame received. Frame size: " + frame.length);
		synchronized (this) {
			if (mUseRawFrame){
				mRawFrames[mChainIdx].putData(frame);
			}
			else{
				mFrameChain[mChainIdx].put(0, 0, frame);
			}
			mCameraFrameReady = true;
			this.notify();
		}
		if (mCamera != null)
			mCamera.addCallbackBuffer(mBuffer);
	}

	private class CameraWorker implements Runnable {

		@Override
		public void run() {
			do {
				synchronized (OpenCvCameraView.this) {
					try {
						while (!mCameraFrameReady && !mStopThread) {
							OpenCvCameraView.this.wait();
						}
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					if (mCameraFrameReady)
						mChainIdx = 1 - mChainIdx;
				}

				if (!mStopThread && mCameraFrameReady) {
					mCameraFrameReady = false;
					if (mUseRawFrame){
						if (mRawFrames[1 - mChainIdx] != null){
							deliverAndDrawRawFrame(mRawFrames[1 - mChainIdx]);
						}
					}
					else{
						if (!mFrameChain[1 - mChainIdx].empty())
							deliverAndDrawFrame(mCameraFrame[1 - mChainIdx]);
					}
				}
			} while (!mStopThread);
			Log.e(TAG, "Finish processing thread");
		}
	}
}
