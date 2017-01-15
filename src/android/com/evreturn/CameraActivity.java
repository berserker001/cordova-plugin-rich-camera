package com.evreturn;

import android.app.Activity;
import android.app.Fragment;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.webkit.MimeTypeMap;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import org.apache.cordova.LOG;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class CameraActivity extends Fragment {

    public interface RichCameraListener {
        public void onPictureTaken(String originalPicturePath, String previewPicturePath);
    }

    private RichCameraListener eventListener;
    private static final String TAG = "CameraActivity";
    public FrameLayout mainLayout;
    public FrameLayout frameContainerLayout;

    private Preview mPreview;
    private boolean canTakePicture = true;

    private View view;
    private Camera.Parameters cameraParameters;
    private Camera mCamera;
    private int numberOfCameras;
    private int cameraCurrentlyLocked;

    // The first rear facing camera
    private int defaultCameraId;
    public String defaultCamera;
    public boolean tapToTakePicture;
    public boolean dragEnabled;

    public boolean enabledSquareMode = false;
    public int squareModeOffset = 0;

    public int width;
    public int height;
    public int x;
    public int y;

    public String lastFlashMode = "";

    public void setEventListener(RichCameraListener listener) {
        eventListener = listener;
    }

    private String appResourcesPackage;

    public static Camera CAMERA;
    public static int CAMERA_ID;
    public static Preview PREVIEW;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        appResourcesPackage = getActivity().getPackageName();

        // Inflate the layout for this fragment
        view = inflater.inflate(getResources().getIdentifier("camera_activity", "layout", appResourcesPackage), container, false);
        createRichCamera();
        return view;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public void setRect(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    private void createRichCamera() {
        if (mPreview == null) {
            setDefaultCameraId();

            //set box position and size
            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);
            layoutParams.setMargins(x, y, 0, 0);
            frameContainerLayout = (FrameLayout) view.findViewById(getResources().getIdentifier("frame_container", "id", appResourcesPackage));
            frameContainerLayout.setLayoutParams(layoutParams);

            //video view
            mPreview = new Preview(getActivity());

            CameraActivity.PREVIEW = mPreview;

            mainLayout = (FrameLayout) view.findViewById(getResources().getIdentifier("video_view", "id", appResourcesPackage));
            mainLayout.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
            mainLayout.addView(mPreview);
            mainLayout.setEnabled(false);

            final GestureDetector gestureDetector = new GestureDetector(getActivity().getApplicationContext(), new TapGestureDetector());

            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    frameContainerLayout.setClickable(true);
                    frameContainerLayout.setOnTouchListener(new View.OnTouchListener() {

                        private int mLastTouchX;
                        private int mLastTouchY;
                        private int mPosX = 0;
                        private int mPosY = 0;

                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) frameContainerLayout.getLayoutParams();


                            boolean isSingleTapTouch = gestureDetector.onTouchEvent(event);
                            if (event.getAction() != MotionEvent.ACTION_MOVE && isSingleTapTouch) {
                                if (tapToTakePicture) {
                                    takePicture(0, 0);
                                }
                                return true;
                            } else {
                                if (dragEnabled) {
                                    int x;
                                    int y;

                                    switch (event.getAction()) {
                                        case MotionEvent.ACTION_DOWN:
                                            if (mLastTouchX == 0 || mLastTouchY == 0) {
                                                mLastTouchX = (int) event.getRawX() - layoutParams.leftMargin;
                                                mLastTouchY = (int) event.getRawY() - layoutParams.topMargin;
                                            } else {
                                                mLastTouchX = (int) event.getRawX();
                                                mLastTouchY = (int) event.getRawY();
                                            }
                                            break;
                                        case MotionEvent.ACTION_MOVE:

                                            x = (int) event.getRawX();
                                            y = (int) event.getRawY();

                                            final float dx = x - mLastTouchX;
                                            final float dy = y - mLastTouchY;

                                            mPosX += dx;
                                            mPosY += dy;

                                            layoutParams.leftMargin = mPosX;
                                            layoutParams.topMargin = mPosY;

                                            frameContainerLayout.setLayoutParams(layoutParams);

                                            // Remember this touch position for the next move event
                                            mLastTouchX = x;
                                            mLastTouchY = y;

                                            break;
                                        default:
                                            break;
                                    }
                                }
                            }
                            return true;
                        }
                    });
                }
            });
        }
    }

    private void setDefaultCameraId() {

        // Find the total number of cameras available
        numberOfCameras = Camera.getNumberOfCameras();

        int camId = defaultCamera.equals("front") ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;

        // Find the ID of the default camera
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == camId) {
                defaultCameraId = camId;
                break;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Sets the Default Camera as the current one (initializes mCamera instance)
        setCurrentCamera(defaultCameraId);

        if (mPreview.mPreviewSize == null) {
            mPreview.setCamera(mCamera, cameraCurrentlyLocked);
        } else {
            mPreview.switchCamera(mCamera, cameraCurrentlyLocked);
            mCamera.startPreview();
        }

        Log.d(TAG, "cameraCurrentlyLocked:" + cameraCurrentlyLocked);

        final FrameLayout frameContainerLayout = (FrameLayout) view.findViewById(getResources().getIdentifier("frame_container", "id", appResourcesPackage));
        ViewTreeObserver viewTreeObserver = frameContainerLayout.getViewTreeObserver();
        if (viewTreeObserver.isAlive()) {
            viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    frameContainerLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    frameContainerLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                    final RelativeLayout frameCamContainerLayout = (RelativeLayout) view.findViewById(getResources().getIdentifier("frame_camera_cont", "id", appResourcesPackage));

                    FrameLayout.LayoutParams camViewLayout = new FrameLayout.LayoutParams(frameContainerLayout.getWidth(), frameContainerLayout.getHeight());
                    camViewLayout.gravity = Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL;
                    frameCamContainerLayout.setLayoutParams(camViewLayout);
                }
            });
        }
    }

    // Sets the current camera - allows to set cameraParameters from a single place (e.g. can be used to set AutoFocus and Autoflash)
    private void setCurrentCamera(int cameraId) {
        mCamera = Camera.open(cameraId);

        Camera.Parameters parameters = mCamera.getParameters();

        if (!this.lastFlashMode.isEmpty())
            parameters.setFlashMode(this.lastFlashMode);

        mCamera.setParameters(parameters);

        cameraCurrentlyLocked = cameraId;
    }

    @Override
    public void onPause() {
        super.onPause();

        // Because the Camera object is a shared resource, it's very
        // important to release it when the activity is paused.
        if (mCamera != null) {
            mPreview.setCamera(null, -1);
            mCamera.release();
            mCamera = null;
        }
    }

    public Camera getCamera() {
        return mCamera;
    }

    public void switchCamera() {
        // check for availability of multiple cameras
        if (numberOfCameras == 1) {
            //There is only one camera available
        }
        Log.d(TAG, "numberOfCameras: " + numberOfCameras);

        // OK, we have multiple cameras.
        // Release this camera -> cameraCurrentlyLocked
        if (mCamera != null) {
            mCamera.stopPreview();
            mPreview.setCamera(null, -1);
            mCamera.release();
            mCamera = null;
        }

        // Acquire the next camera and request Preview to reconfigure
        // parameters.

        int nextCameraId = (cameraCurrentlyLocked + 1) % numberOfCameras;

        // Set the next camera as the current one and apply the cameraParameters
        setCurrentCamera(nextCameraId);

        mPreview.switchCamera(mCamera, cameraCurrentlyLocked);

        CameraActivity.CAMERA = mCamera;
        CameraActivity.CAMERA_ID = nextCameraId;

        //VideoOverlay.clear();

        Log.d(TAG, "cameraCurrentlyLocked new: " + cameraCurrentlyLocked);

        // startVideoCapture the preview
        mCamera.startPreview();
    }

    public void setCameraParameters(Camera.Parameters params) {
        cameraParameters = params;

        if (mCamera != null && cameraParameters != null) {
            mCamera.setParameters(cameraParameters);
        }
    }

    public boolean hasFrontCamera() {
        return getActivity().getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);
    }

    public Bitmap cropBitmap(Bitmap bitmap, Rect rect) {
        int w = rect.right - rect.left;
        int h = rect.bottom - rect.top;
        Bitmap ret = Bitmap.createBitmap(w, h, bitmap.getConfig());
        Canvas canvas = new Canvas(ret);
        canvas.drawBitmap(bitmap, -rect.left, -rect.top, null);
        return ret;
    }

    public void takePicture(final double maxWidth, final double maxHeight) {
        final ImageView pictureView = (ImageView) view.findViewById(getResources().getIdentifier("picture_view", "id", appResourcesPackage));
        if (mPreview != null) {

            if (!canTakePicture)
                return;

            canTakePicture = false;

            mPreview.setOneShotPreviewCallback(new Camera.PreviewCallback() {

                @Override
                public void onPreviewFrame(final byte[] data, final Camera camera) {

                    new Thread() {
                        public void run() {

                            //raw picture
                            byte[] bytes = mPreview.getFramePicture(data, camera);
                            final Bitmap pic = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

                            //scale down
                            float scale = (float) pictureView.getWidth() / (float) pic.getWidth();
                            Bitmap scaledBitmap = Bitmap.createScaledBitmap(pic, (int) (pic.getWidth() * scale), (int) (pic.getHeight() * scale), false);

                            final Matrix matrix = new Matrix();
                            if (cameraCurrentlyLocked == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                                Log.d(TAG, "mirror y axis");
                                matrix.preScale(-1.0f, -1.0f);
                            }
                            Log.d(TAG, "preRotate " + mPreview.getDisplayOrientation() + "deg");
                            matrix.postRotate(mPreview.getDisplayOrientation());

                            final Bitmap fixedPic = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix, false);
                            final Rect rect = new Rect(mPreview.mSurfaceView.getLeft(), mPreview.mSurfaceView.getTop(), mPreview.mSurfaceView.getRight(), mPreview.mSurfaceView.getBottom());

                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    pictureView.setImageBitmap(fixedPic);
                                    pictureView.layout(rect.left, rect.top, rect.right, rect.bottom);

                                    Bitmap finalPic = null;
                                    // If we are going to rotate the picture, width and height are reversed
                                    boolean swapAspects = mPreview.getDisplayOrientation() % 180 != 0;
                                    double rotatedWidth = swapAspects ? pic.getHeight() : pic.getWidth();
                                    double rotatedHeight = swapAspects ? pic.getWidth() : pic.getHeight();
                                    boolean shouldScaleWidth = maxWidth > 0 && rotatedWidth > maxWidth;
                                    boolean shouldScaleHeight = maxHeight > 0 && rotatedHeight > maxHeight;

                                    //scale final picture
                                    if (shouldScaleWidth || shouldScaleHeight) {
                                        double scaleHeight = shouldScaleHeight ? maxHeight / (double) rotatedHeight : 1;
                                        double scaleWidth = shouldScaleWidth ? maxWidth / (double) rotatedWidth : 1;

                                        double scale = scaleHeight < scaleWidth ? scaleHeight : scaleWidth;
                                        finalPic = Bitmap.createScaledBitmap(pic, (int) (pic.getWidth() * scale), (int) (pic.getHeight() * scale), false);
                                    } else {
                                        finalPic = pic;
                                    }

                                    int _x = 0;
                                    int _y = 0;

                                    int _width = (int) finalPic.getWidth();
                                    int _height = (int) finalPic.getHeight();

                                    int _offset = 0;

                                    if (enabledSquareMode) {

                                        int defaultOffset = 114 + (114 / 2);

                                        if (_width > _height) {

                                            _offset = (_width - _height) / 2;

                                            _width = _height;

                                            if (CAMERA_ID == Camera.CameraInfo.CAMERA_FACING_FRONT)
                                                _x = _offset + (squareModeOffset > 0 ? squareModeOffset : defaultOffset);
                                            else
                                                _x = _offset - (squareModeOffset > 0 ? squareModeOffset : defaultOffset);

                                        } else if (_height > _width) {

                                            _offset = (_height - _width) / 2;

                                            _height = _width;

                                            _y = _offset - (squareModeOffset > 0 ? squareModeOffset : defaultOffset);

                                        }

                                    }

                                    Bitmap originalPicture = Bitmap.createBitmap(finalPic, _x, _y, _width, _height, matrix, false);

                                    //get bitmap and compress
                                    //Bitmap picture = loadBitmapFromView(view.findViewById(getResources().getIdentifier("frame_camera_cont", "id", appResourcesPackage)));
                                    //ByteArrayOutputStream stream = new ByteArrayOutputStream();
                                    //picture.compress(Bitmap.CompressFormat.PNG, 80, stream);

                                    //generatePictureFromView(originalPicture, picture);
                                    generatePictureFromView(originalPicture, null);
                                    canTakePicture = true;
                                }
                            });
                        }
                    }.start();
                }
            });
        } else {
            canTakePicture = true;
        }
    }

    private void generatePictureFromView(final Bitmap originalPicture, final Bitmap picture) {

        final FrameLayout cameraLoader = (FrameLayout) view.findViewById(getResources().getIdentifier("camera_loader", "id", appResourcesPackage));
        cameraLoader.setVisibility(View.VISIBLE);
        final ImageView pictureView = (ImageView) view.findViewById(getResources().getIdentifier("picture_view", "id", appResourcesPackage));
        new Thread() {
            public void run() {

                try {
                    final File originalPictureFile = storeImage(originalPicture);

                    MediaScanner mediaScanner = new MediaScanner(getActivity().getApplicationContext());
                    String[] filePaths = new String[]{originalPictureFile.getAbsolutePath()};
                    String[] mimeTypes = new String[]{MimeTypeMap.getSingleton().getMimeTypeFromExtension("png")};
                    mediaScanner.scanFiles(filePaths, mimeTypes);

                    eventListener.onPictureTaken(originalPictureFile.getAbsolutePath(), "");

                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            cameraLoader.setVisibility(View.INVISIBLE);
                            pictureView.setImageBitmap(null);
                        }
                    });
                } catch (Exception e) {
                    //An unexpected error occurred while saving the picture.
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            cameraLoader.setVisibility(View.INVISIBLE);
                            pictureView.setImageBitmap(null);
                        }
                    });
                }
            }
        }.start();
    }

    private File storeImage(Bitmap image) {
        File pictureFile = new File(RichCamera.makePhotoFilePath());
        if (pictureFile != null) {
            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                image.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.close();
                return pictureFile;
            } catch (Exception ex) {
            }
        }
        return null;
    }

    public int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private Bitmap loadBitmapFromView(View v) {
        Bitmap b = Bitmap.createBitmap(v.getMeasuredWidth(), v.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        v.layout(v.getLeft(), v.getTop(), v.getRight(), v.getBottom());
        v.draw(c);
        return b;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}

class TapGestureDetector extends GestureDetector.SimpleOnGestureListener {

    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return true;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        return true;
    }
}

class MediaScanner implements MediaScannerConnection.MediaScannerConnectionClient {

    /**
     * 扫描对象
     */
    private MediaScannerConnection mediaScanConn = null;

    public MediaScanner(Context context) {
        //实例化
        mediaScanConn = new MediaScannerConnection(context, this);
    }

    /**
     * 文件路径集合
     **/
    private String[] filePaths;
    /**
     * 文件MimeType集合
     **/
    private String[] mimeTypes;

    /**
     * 扫描文件
     *
     * @param filepaths
     * @param mimeTypes
     * @author YOLANDA
     */
    public void scanFiles(String[] filePaths, String[] mimeTypes) {
        this.filePaths = filePaths;
        this.mimeTypes = mimeTypes;
        mediaScanConn.connect();//连接扫描服务
    }

    /**
     * @author YOLANDA
     */
    @Override
    public void onMediaScannerConnected() {

        if (filePaths != null && mimeTypes != null)
            for (int i = 0; i < filePaths.length; i++) {
                mediaScanConn.scanFile(filePaths[i], mimeTypes[i]);//服务回调执行扫描
            }

    }

    private int scanTimes = 0;

    /**
     * 扫描一个文件完成
     *
     * @param path
     * @param uri
     * @author YOLANDA
     */
    @Override
    public void onScanCompleted(String path, Uri uri) {
        scanTimes++;
        if (scanTimes == filePaths.length) {//如果扫描完了全部文件
            mediaScanConn.disconnect();//断开扫描服务
            scanTimes = 0;//复位计数
            filePaths = null;
            mimeTypes = null;
        }
    }
}
