package com.evreturn;

import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.RelativeLayout;

import org.apache.cordova.LOG;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class Preview extends RelativeLayout implements TextureView.SurfaceTextureListener {

    private final String TAG = "Preview";

    //region Preview

    TextureView mSurfaceView;
    Camera.Size mPreviewSize;
    List<Camera.Size> mSupportedPreviewSizes;
    Camera mCamera;
    int mCameraId;
    int displayOrientation;
    SurfaceTexture mSurfaceTexture;

    boolean inVideoCapturing = false;
    boolean supportedFocusMode_video = false;
    boolean supportedFocusMode_picture = false;

    Preview(Context context) {
        super(context);

        mSurfaceView = new TextureView(context);
        mSurfaceView.setClickable(false);
        mSurfaceView.setSurfaceTextureListener(this);

        addView(mSurfaceView);

        requestLayout();
    }

    public void setCamera(Camera camera, int cameraId) {

        mCamera = camera;

        this.mCameraId = cameraId;

        CameraActivity.CAMERA = mCamera;
        CameraActivity.CAMERA_ID = cameraId;

        if (mCamera != null) {

            mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();

            setCameraDisplayOrientation();

            //requestLayout();

            List<String> mFocusModes = mCamera.getParameters().getSupportedFocusModes();

            Camera.Parameters params = mCamera.getParameters();

            if (mFocusModes.contains("continuous-picture")) {
                this.supportedFocusMode_picture = true;
            }

            if (mFocusModes.contains("continuous-video")) {
                this.supportedFocusMode_video = true;
            }

            if (this.mCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT)
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
            else if (this.mCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {

                if (this.supportedFocusMode_picture)
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                else {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                }

            }

            mCamera.setParameters(params);
        }
    }

    public int getDisplayOrientation() {
        return displayOrientation;
    }

    private void setCameraDisplayOrientation() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        int rotation =
                ((Activity) getContext()).getWindowManager().getDefaultDisplay()
                        .getRotation();
        int degrees = 0;
        DisplayMetrics dm = new DisplayMetrics();

        Camera.getCameraInfo(mCameraId, info);
        ((Activity) getContext()).getWindowManager().getDefaultDisplay().getMetrics(dm);

        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            displayOrientation = (info.orientation + degrees) % 360;
            displayOrientation = (360 - displayOrientation) % 360;
        } else {
            displayOrientation = (info.orientation - degrees + 360) % 360;
        }

        Log.d(TAG, "screen is rotated " + degrees + "deg from natural");
        Log.d(TAG, (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT ? "front" : "back")
                + " camera is oriented -" + info.orientation + "deg from natural");
        Log.d(TAG, "need to rotate preview " + displayOrientation + "deg");

        mCamera.setDisplayOrientation(displayOrientation);

    }

    public void switchCamera(Camera camera, int cameraId) {

        this.setCamera(camera, cameraId);

        try {

            mCamera.setPreviewTexture(this.mSurfaceTexture);

            Camera.Parameters parameters = camera.getParameters();

            parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);

            camera.setParameters(parameters);
        } catch (IOException exception) {
            Log.e(TAG, exception.getMessage());
        }

        //requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // We purposely disregard child measurements because act as a
        // wrapper to a SurfaceView that centers the camera preview instead
        // of stretching it.
        final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        setMeasuredDimension(width, height);

        if (mSupportedPreviewSizes != null) {
            mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, width, height);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {

        if (changed && getChildCount() > 0) {
            final View child = getChildAt(0);

            int width = r - l;
            int height = b - t;

            int previewWidth = width;
            int previewHeight = height;

            if (mPreviewSize != null) {
                previewWidth = mPreviewSize.width;
                previewHeight = mPreviewSize.height;

                if (displayOrientation == 90 || displayOrientation == 270) {
                    previewWidth = mPreviewSize.height;
                    previewHeight = mPreviewSize.width;
                }

                LOG.d(TAG, "previewWidth:" + previewWidth + " previewHeight:" + previewHeight);
            }

            int nW;
            int nH;
            int top;
            int left;

            float scale = 1.0f;

            // Center the child SurfaceView within the parent.
            if (width * previewHeight < height * previewWidth) {
                Log.d(TAG, "center horizontally");
                int scaledChildWidth = (int) ((previewWidth * height / previewHeight) * scale);
                nW = (width + scaledChildWidth) / 2;
                nH = (int) (height * scale);
                top = 0;
                left = (width - scaledChildWidth) / 2;
            } else {
                Log.d(TAG, "center vertically");
                int scaledChildHeight = (int) ((previewHeight * width / previewWidth) * scale);
                nW = (int) (width * scale);
                nH = (height + scaledChildHeight) / 2;
                top = (height - scaledChildHeight) / 2;
                left = 0;
            }
            child.layout(left, top, nW, nH);

            Log.d("layout", "left:" + left);
            Log.d("layout", "top:" + top);
            Log.d("layout", "right:" + nW);
            Log.d("layout", "bottom:" + nH);
        }
    }

    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;
        if (displayOrientation == 90 || displayOrientation == 270) {
            targetRatio = (double) h / w;
        }
        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        // Try to find an size match aspect ratio and size
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }

        optimalSize = sizes.get(0);
        for (int i = 0; i < sizes.size(); i++) {
            if (sizes.get(i).width > optimalSize.width) {
                optimalSize = sizes.get(i);
            }
        }

        Log.d(TAG, "optimal preview size: w: " + optimalSize.width + " h: " + optimalSize.height);
        return optimalSize;
    }

    public byte[] getFramePicture(byte[] data, Camera camera) {
        Camera.Parameters parameters = camera.getParameters();
        int format = parameters.getPreviewFormat();

        //YUV formats require conversion
        if (format == ImageFormat.NV21 || format == ImageFormat.YUY2 || format == ImageFormat.NV16) {
            int w = parameters.getPreviewSize().width;
            int h = parameters.getPreviewSize().height;

            // Get the YuV image
            YuvImage yuvImage = new YuvImage(data, format, w, h, null);
            // Convert YuV to Jpeg
            Rect rect = new Rect(0, 0, w, h);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(rect, 80, outputStream);
            return outputStream.toByteArray();
        }
        return data;
    }

    public void setOneShotPreviewCallback(Camera.PreviewCallback callback) {
        if (mCamera != null) {
            mCamera.setOneShotPreviewCallback(callback);
        }
    }

    //endregion

    //region Video Capture

    private Preview.RecordingState mRecordingState = Preview.RecordingState.INITIALIZING;

    private MediaRecorder mRecorder = null;
    private boolean mStartWhenInitialized = false;

    private String mFilePath;
    private boolean mRecordAudio = true;
    private int mOrientation;

    public static int rotate;

    public void setRecordAudio(boolean recordAudio) {
        mRecordAudio = recordAudio;
    }

    public void startVideoCapture() throws Exception {

        String filePath = RichCamera.makeVideoFilePath();

        if (this.mRecordingState == Preview.RecordingState.STARTED) {
            Log.w(TAG, "Already Recording");
            return;
        }

        if (!TextUtils.isEmpty(filePath)) {
            this.mFilePath = filePath;
        }


        if (this.mRecordingState == Preview.RecordingState.INITIALIZING) {
            this.mStartWhenInitialized = true;
            return;
        }

        if (TextUtils.isEmpty(mFilePath)) {
            throw new IllegalArgumentException("Filename for recording must be set");
        }

        initializeCamera();

        if (mCamera == null) {
            throw new NullPointerException("Cannot start recording, we don't have a camera!");
        }

        // Set camera parameters
        Camera.Parameters cameraParameters = mCamera.getParameters();

        if (this.supportedFocusMode_video) {
            cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            mCamera.setParameters(cameraParameters);
        }

        mCamera.stopPreview(); //Apparently helps with freezing issue on some Samsung devices.
        mCamera.unlock();

        try {
            mRecorder = new MediaRecorder();
            mRecorder.setCamera(mCamera);

            CamcorderProfile profile = CamcorderProfile.get(mCameraId, CamcorderProfile.QUALITY_480P);

            mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

            if (mRecordAudio) {
                // With audio
                mRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
                mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                mRecorder.setVideoFrameRate(profile.videoFrameRate);
                mRecorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
                mRecorder.setVideoEncodingBitRate(profile.videoBitRate);
                mRecorder.setAudioEncodingBitRate(profile.audioBitRate);
                mRecorder.setAudioChannels(profile.audioChannels);
                mRecorder.setAudioSamplingRate(profile.audioSampleRate);
                mRecorder.setVideoEncoder(profile.videoCodec);
                mRecorder.setAudioEncoder(profile.audioCodec);
            } else {
                // Without audio
                mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                mRecorder.setVideoFrameRate(profile.videoFrameRate);
                mRecorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
                mRecorder.setVideoEncodingBitRate(profile.videoBitRate);
                mRecorder.setVideoEncoder(profile.videoCodec);
            }

            mRecorder.setOutputFile(filePath);
            mRecorder.setOrientationHint(mOrientation);
            mRecorder.prepare();

            Log.d(TAG, "Starting recording");
            mRecorder.start();
        } catch (Exception e) {
            this.releaseRecorder();
            Log.e(TAG, "Could not start recording! MediaRecorder Error", e);
            throw e;
        }
    }

    public String stopVideoCapture(boolean deleteFile) throws IOException {
        Log.d(TAG, "stopRecording called");

        if (mRecorder != null) {
            MediaRecorder tempRecorder = mRecorder;
            mRecorder = null;
            try {
                tempRecorder.stop();

                if (deleteFile) {
                    File videoFile = new File(this.mFilePath);
                    videoFile.delete();
                }

            } catch (Exception e) {
                //This can occur when the camera failed to start and then stop is called
                Log.e(TAG, "Could not stop recording.", e);
            }
        }

        //Do not let them release currently camera
        //Because it still in using
        //this.releaseCamera();

        //Only release recorder
        this.releaseRecorder();

        this.resetCameraFocusMode();

        return this.mFilePath;
    }

    private void initializeCamera() {

        if (mCamera == null)
            return;

        try {

            //Calc orientation
            if (mCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT)
                mOrientation = 270;
            else if (mCameraId == Camera.CameraInfo.CAMERA_FACING_BACK)
                mOrientation = CameraHelper.calculateOrientation((Activity) this.getContext(), mCameraId);

            Camera.Parameters cameraParameters = mCamera.getParameters();
            Camera.Size previewSize = CameraHelper.getPreviewSize(cameraParameters);

            cameraParameters.setPreviewSize(previewSize.width, previewSize.height);
            cameraParameters.setRotation(mOrientation);
            cameraParameters.setRecordingHint(true);

            mCamera.setParameters(cameraParameters);

            //Do not need to call setDisplayOrientation again
            //Because it has been called in preview activity
            //mCamera.setDisplayOrientation(mOrientation);

        } catch (Exception ex) {
            this.releaseCamera();
            Log.e(TAG, "Unable to open camera. Another application probably has a lock", ex);
        }

    }

    private void releaseCamera() {

        if (mRecorder != null) {
            mRecorder.reset();
            mRecorder.release();
            mRecorder = null;
        }

        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.lock();
            mCamera.release();
            mCamera = null;
            mCameraId = CameraHelper.NO_CAMERA;
        }

        this.mRecordingState = Preview.RecordingState.STOPPED;
    }

    private void releaseRecorder() {

        if (mRecorder != null) {
            mRecorder.reset();
            mRecorder.release();
            mRecorder = null;
        }

        if (mCamera != null)
            mCamera.lock();

        this.mRecordingState = Preview.RecordingState.STOPPED;

    }

    private void resetCameraFocusMode() {

        Camera.Parameters params = mCamera.getParameters();

        if (this.supportedFocusMode_picture)
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        else {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }

        mCamera.setParameters(params);

    }

    //endregion

    //region Surface Texture Callback

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {

        Log.d(TAG, "Creating Texture Created");

        this.mSurfaceTexture = surface;

        this.mRecordingState = Preview.RecordingState.STOPPED;

        initializeCamera();

        if (mCamera != null) {
            try {
                mCamera.setPreviewTexture(surface);
            } catch (IOException e) {
                Log.e(TAG, "Unable to attach preview to camera!", e);
            }

            mCamera.startPreview();
        }

        if (mStartWhenInitialized) {
            try {
                startVideoCapture();
            } catch (Exception ex) {
                Log.e(TAG, "Error start camera", ex);
            }
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {


    }

    //endregion

    private enum RecordingState {INITIALIZING, STARTED, STOPPED}

}