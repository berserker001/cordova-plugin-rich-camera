package com.evreturn;

import android.Manifest;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.hardware.Camera;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;

import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class RichCamera extends CordovaPlugin implements CameraActivity.RichCameraListener {

    private final String TAG = "RichCamera";

    //region Permission

    protected final static String[] permissions = {
            Manifest.permission.CAMERA,
            Manifest.permission.FLASHLIGHT,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    //endregion

    //region Action Names

    private final String setColorEffectAction = "setColorEffect";

    private final String startCameraAction = "startCamera";
    private final String stopCameraAction = "stopCamera";
    private final String switchCameraAction = "switchCamera";

    private final String takePictureAction = "takePicture";
    private final String setOnPictureTakenHandlerAction = "setOnPictureTakenHandler";

    private final String showCameraAction = "showCamera";
    private final String hideCameraAction = "hideCamera";

    private final String setSquareModeAction = "setSquareMode";
    private final String setFlashModeAction = "setFlashMode";

    private final String forceRenderAction = "forceRender";

    private final String startVideoCaptureAction = "startVideoCapture";
    private final String stopVideoCaptureAction = "stopVideoCapture";

    private final String requestPermissionAction = "requestPermission";

    //endregion

    public static final int START_CAMERA_SEC = 0;

    public CallbackContext callbackContext;
    private CallbackContext cc_requestPermission;

    private CameraActivity fragment;
    private CallbackContext takePictureCallbackContext;
    private int containerViewId = 1;

    private FrameLayout containerView;

    public static String VIDEO_FOLDER;
    public static String PHOTO_FOLDER;

    public static final String VIDEO_EXTENSION = ".mp4";
    public static final String PHOTO_EXTENSION = ".png";

    private static final int START_REQUEST_CODE = 0;

    public RichCamera() {
        super();
    }

    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        this.callbackContext = callbackContext;

        if (setOnPictureTakenHandlerAction.equals(action)) {
            return setOnPictureTakenHandler(args, callbackContext);
        } else if (setSquareModeAction.equals(action)) {
            return setSquareMode(args, callbackContext);
        } else if (forceRenderAction.equals(action)) {
            return forceRender(args, callbackContext);
        } else if (startCameraAction.equals(action)) {
            return callStartCamera(args, callbackContext);
        } else if (takePictureAction.equals(action)) {
            return takePicture(args, callbackContext);
        } else if (setFlashModeAction.equals(action)) {
            return setFlashMode(args, callbackContext);
        } else if (setColorEffectAction.equals(action)) {
            return setColorEffect(args, callbackContext);
        } else if (stopCameraAction.equals(action)) {
            return stopCamera(args, callbackContext);
        } else if (hideCameraAction.equals(action)) {
            return hideCamera(args, callbackContext);
        } else if (showCameraAction.equals(action)) {
            return showCamera(args, callbackContext);
        } else if (switchCameraAction.equals(action)) {
            return switchCamera(args, callbackContext);
        } else if (startVideoCaptureAction.equals(action)) {
            return startVideoCapture(args, callbackContext);
        } else if (stopVideoCaptureAction.equals(action)) {
            return stopVideoCapture();
        } else if (requestPermissionAction.equals(action)) {
            return requestPermission(args, callbackContext);
        }

        return false;
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException {
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                if (this.cc_requestPermission != null)
                    this.cc_requestPermission.error(0);
                return;
            }
        }

        if (this.cc_requestPermission != null)
            this.cc_requestPermission.success(1);

        this.initStorageFolder();

    }

    //region Overrides

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {

        super.initialize(cordova, webView);

        this.initStorageFolder();

    }

    @Override
    public void onPause(boolean multitasking) {

        this.stopVideoCapture();

        super.onPause(multitasking);
    }

    @Override
    public void onDestroy() {

        this.stopVideoCapture();


        super.onDestroy();
    }

    //endregion

    //region Action Entrances

    public boolean callStartCamera(final JSONArray args, CallbackContext callbackContext) {
        boolean saveAlbumPermission = PermissionHelper.hasPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        boolean startCameraPermission = PermissionHelper.hasPermission(this, Manifest.permission.CAMERA);

        if (!startCameraPermission) {
            startCameraPermission = true;
            try {
                PackageManager packageManager = this.cordova.getActivity().getPackageManager();
                String[] permissionsInPackage = packageManager.getPackageInfo(this.cordova.getActivity().getPackageName(), PackageManager.GET_PERMISSIONS).requestedPermissions;
                if (permissionsInPackage != null) {
                    for (String permission : permissionsInPackage) {
                        if (permission.equals(Manifest.permission.CAMERA)) {
                            startCameraPermission = false;
                            break;
                        }
                    }
                }
            } catch (NameNotFoundException e) {
                // We are requesting the info for our package, so this should
                // never be caught
            }
        }

        if (startCameraPermission && saveAlbumPermission) {
            return startCamera(args, callbackContext);
        } else if (saveAlbumPermission && !startCameraPermission) {
            PermissionHelper.requestPermission(this, START_CAMERA_SEC, Manifest.permission.CAMERA);
        } else if (!saveAlbumPermission && startCameraPermission) {
            PermissionHelper.requestPermission(this, START_CAMERA_SEC, Manifest.permission.READ_EXTERNAL_STORAGE);
        } else {
            PermissionHelper.requestPermissions(this, START_CAMERA_SEC, permissions);
        }

        return false;
    }

    public boolean setSquareMode(final JSONArray args, CallbackContext callbackContext) {

        try {
            if (fragment != null) {
                fragment.enabledSquareMode = args.getBoolean(0);

                if (args.length() > 1) {
                    int offset = args.getInt(1);

                    if (offset > 0)
                        fragment.squareModeOffset = offset;
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

    private boolean startCamera(final JSONArray args, final CallbackContext callbackContext) {
        if (fragment != null) {
            return false;
        }
        fragment = new CameraActivity();
        fragment.setEventListener(this);

        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {

                try {
                    DisplayMetrics metrics = cordova.getActivity().getResources().getDisplayMetrics();
                    int x = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, args.getInt(0), metrics);
                    int y = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, args.getInt(1), metrics);
                    int width = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, args.getInt(2), metrics);
                    int height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, args.getInt(3), metrics);
                    String defaultCamera = args.getString(4);
                    Boolean tapToTakePicture = args.getBoolean(5);
                    Boolean dragEnabled = args.getBoolean(6);
                    Boolean toBack = args.getBoolean(7);

                    fragment.defaultCamera = defaultCamera;
                    fragment.tapToTakePicture = tapToTakePicture;
                    fragment.dragEnabled = dragEnabled;
                    fragment.setRect(x, y, width, height);

                    //create or update the layout params for the container view
                    containerView = (FrameLayout) cordova.getActivity().findViewById(containerViewId);

                    if (containerView == null) {
                        containerView = new FrameLayout(cordova.getActivity().getApplicationContext());
                        containerView.setId(containerViewId);

                        FrameLayout.LayoutParams containerLayoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
                        cordova.getActivity().addContentView(containerView, containerLayoutParams);
                    }
                    //display camera bellow the webview
                    if (toBack) {
                        webView.getView().setBackgroundColor(0x00000000);
                        ((ViewGroup) webView.getView()).bringToFront();

                        containerView.setAlpha(0.0f);
                    } else {
                        //set camera back to front
                        containerView.setAlpha(Float.parseFloat(args.getString(8)));
                        containerView.bringToFront();
                    }

                    //add the fragment to the container
                    FragmentManager fragmentManager = cordova.getActivity().getFragmentManager();
                    FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                    fragmentTransaction.add(containerView.getId(), fragment);
                    fragmentTransaction.commit();

                    PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
                    callbackContext.sendPluginResult(pluginResult);


                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        return true;
    }

    private boolean takePicture(final JSONArray args, CallbackContext callbackContext) {
        if (fragment == null) {
            return false;
        }
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
        pluginResult.setKeepCallback(true);
        callbackContext.sendPluginResult(pluginResult);
        try {
            double maxWidth = args.getDouble(0);
            double maxHeight = args.getDouble(1);
            fragment.takePicture(maxWidth, maxHeight);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public void onPictureTaken(String originalPicturePath, String previewPicturePath) {
        JSONArray data = new JSONArray();
        data.put(originalPicturePath).put(previewPicturePath);
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, data);
        pluginResult.setKeepCallback(true);
        takePictureCallbackContext.sendPluginResult(pluginResult);
    }

    private boolean setColorEffect(final JSONArray args, CallbackContext callbackContext) {
        if (fragment == null) {
            return false;
        }

        Camera camera = fragment.getCamera();
        if (camera == null) {
            return true;
        }

        Camera.Parameters params = camera.getParameters();

        try {
            String effect = args.getString(0);

            if (effect.equals("aqua")) {
                params.setColorEffect(Camera.Parameters.EFFECT_AQUA);
            } else if (effect.equals("blackboard")) {
                params.setColorEffect(Camera.Parameters.EFFECT_BLACKBOARD);
            } else if (effect.equals("mono")) {
                params.setColorEffect(Camera.Parameters.EFFECT_MONO);
            } else if (effect.equals("negative")) {
                params.setColorEffect(Camera.Parameters.EFFECT_NEGATIVE);
            } else if (effect.equals("none")) {
                params.setColorEffect(Camera.Parameters.EFFECT_NONE);
            } else if (effect.equals("posterize")) {
                params.setColorEffect(Camera.Parameters.EFFECT_POSTERIZE);
            } else if (effect.equals("sepia")) {
                params.setColorEffect(Camera.Parameters.EFFECT_SEPIA);
            } else if (effect.equals("solarize")) {
                params.setColorEffect(Camera.Parameters.EFFECT_SOLARIZE);
            } else if (effect.equals("whiteboard")) {
                params.setColorEffect(Camera.Parameters.EFFECT_WHITEBOARD);
            }

            fragment.setCameraParameters(params);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean setFlashMode(final JSONArray args, CallbackContext callbackContext) {

        if (fragment == null) {
            return false;
        }

        Camera camera = fragment.getCamera();

        if (camera == null) {
            return false;
        }

        if (CameraActivity.CAMERA_ID == Camera.CameraInfo.CAMERA_FACING_FRONT)
            return false;

        Camera.Parameters params = camera.getParameters();

        try {
            int mode = (int) args.getInt(0);

            switch (mode) {
                case 0:
                    params.setFlashMode(params.FLASH_MODE_OFF);
                    break;

                case 1:
                    params.setFlashMode(params.FLASH_MODE_ON);
                    break;

                case 2:
                    params.setFlashMode(params.FLASH_MODE_TORCH);
                    break;

                case 3:
                    params.setFlashMode(params.FLASH_MODE_AUTO);
                    break;
            }

            fragment.lastFlashMode = params.getFlashMode();

            fragment.setCameraParameters(params);

            return true;
        } catch (Exception e) {
            e.printStackTrace();

            return false;
        }
    }

    private boolean stopCamera(final JSONArray args, CallbackContext callbackContext) {
        if (fragment == null) {
            return false;
        }

        FragmentManager fragmentManager = cordova.getActivity().getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.remove(fragment);
        fragmentTransaction.commit();
        fragment = null;

        return true;
    }

    private boolean showCamera(final JSONArray args, CallbackContext callbackContext) {

        if (fragment == null) {
            return false;
        }

        containerView.setAlpha(1.0f);

        FragmentManager fragmentManager = cordova.getActivity().getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.show(fragment);

        fragmentTransaction.commit();

        return true;
    }

    private boolean forceRender(final JSONArray args, final CallbackContext callbackContext) {

        try {
//            if (fragment != null)
//                fragment.forceRender();

            fragment.getCamera().setOneShotPreviewCallback(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {

                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (containerView != null)
                                containerView.setAlpha(1.0f);

                            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
                            callbackContext.sendPluginResult(pluginResult);
                        }
                    });

                }
            });

            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;

    }

    private boolean hideCamera(final JSONArray args, CallbackContext callbackContext) {
        if (fragment == null) {
            return false;
        }

        FragmentManager fragmentManager = cordova.getActivity().getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.hide(fragment);
        fragmentTransaction.commit();

        return true;
    }

    private boolean switchCamera(final JSONArray args, CallbackContext callbackContext) {
        if (fragment == null) {
            return false;
        }
        fragment.lastFlashMode = "";
        fragment.switchCamera();
        return true;
    }

    private boolean setOnPictureTakenHandler(JSONArray args, CallbackContext callbackContext) {
        Log.d(TAG, "setOnPictureTakenHandler");
        takePictureCallbackContext = callbackContext;
        return true;
    }

    private boolean requestPermission(JSONArray args, CallbackContext callbackContext) {

        this.cc_requestPermission = callbackContext;

        Log.d(TAG, requestPermissionAction);
        List<String> hasNotPermissions = new ArrayList<String>();


        for (String permission : permissions) {
            if (!cordova.hasPermission(permission))
                hasNotPermissions.add(permission);
        }

        if (hasNotPermissions.size() > 0) {
            cordova.requestPermissions(this, START_REQUEST_CODE, hasNotPermissions.toArray(new String[0]));
        } else
            cc_requestPermission.success(1);

        return true;
    }

    private boolean startVideoCapture(final JSONArray args, CallbackContext callbackContext) {

        try {

            if (fragment != null) {

                final boolean enabledRecordAudio = args.getBoolean(0);

                fragment.PREVIEW.setRecordAudio(enabledRecordAudio);

                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            fragment.PREVIEW.startVideoCapture();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });

            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

    private boolean stopVideoCapture() {

        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (fragment != null && fragment.PREVIEW != null)
                        fragment.PREVIEW.stopVideoCapture();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        return true;
    }

    //endregion

    //region Supplement

    public static String makeVideoFilePath() {
        return VIDEO_FOLDER + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + VIDEO_EXTENSION;
    }

    public static String makePhotoFilePath() {
        return PHOTO_FOLDER + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + PHOTO_EXTENSION;
    }

    private void initStorageFolder() {

        //region Init Video Folder Path

        String filePath = "";
        File storageDir;

        //Default is in Application-Inner-Resources Folder
        filePath = cordova.getActivity().getFilesDir().toString() + "/Video/";

        if (cordova.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

            //Try to get folder path from ExternalStorage
            filePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).toString() + "/" + cordova.getActivity().getApplicationContext().getPackageName() + "/";

        }

        storageDir = new File(filePath);

        //If the specified folder is exists or created successed.
        if (storageDir.exists() || storageDir.mkdirs()) {
            VIDEO_FOLDER = filePath;
        }

        //endregion

        //region Init Photo Folder Path

        filePath = cordova.getActivity().getFilesDir().toString() + "/Photo/";

        if (cordova.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

            //Try to get folder path from ExternalStorage
            filePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString() + "/" + cordova.getActivity().getApplicationContext().getPackageName() + "/";

        }

        storageDir = new File(filePath);

        //If the specified folder is exists or created successed.
        if (storageDir.exists() || storageDir.mkdirs()) {
            PHOTO_FOLDER = filePath;
        }


        //endregion

    }

    //endregion

}
