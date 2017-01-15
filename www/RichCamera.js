var argscheck = require('cordova/argscheck'),
    utils = require('cordova/utils'),
    exec = require('cordova/exec');

var PLUGIN_NAME = "RichCamera";

var RichCamera = function () {
};

RichCamera.forceRender = function (fn_success, fn_error) {
    exec(fn_success, fn_error, PLUGIN_NAME, "forceRender", []);
};

RichCamera.setSquareMode = function (isOn, offset) {
    exec(null, null, PLUGIN_NAME, "setSquareMode", [isOn, offset]);
};

RichCamera.setOnCameraStaredHandler = function (onCameraStarted) {
    exec(onCameraStarted, onCameraStarted, PLUGIN_NAME, "setOnCameraStaredHandler", []);
};

/**
 * @param mode
 * 0 -> OFF
 * 1 -> ON
 * 2 -> TORCH
 * 3 -> AUTO
 */
RichCamera.setFlashMode = function (mode) {
    exec(null, null, PLUGIN_NAME, "setFlashMode", [mode]);
}

RichCamera.setOnPictureTakenHandler = function (onPictureTaken) {
    exec(onPictureTaken, onPictureTaken, PLUGIN_NAME, "setOnPictureTakenHandler", []);
};

//@param rect {x: 0, y: 0, width: 100, height:100}
//@param defaultCamera "front" | "back"
RichCamera.startCamera = function (rect, defaultCamera, tapEnabled, dragEnabled, toBack, alpha) {
    if (typeof(alpha) === 'undefined') alpha = 1;
    exec(null, null, PLUGIN_NAME, "startCamera", [rect.x, rect.y, rect.width, rect.height, defaultCamera, !!tapEnabled, !!dragEnabled, !!toBack, alpha]);
};

RichCamera.startCamera2 = function (rect, defaultCamera, fn_success, fn_error) {
    exec(fn_success, fn_error, PLUGIN_NAME, "startCamera", [rect.x, rect.y, rect.width, rect.height, defaultCamera, false, false, true, 1]);
};

RichCamera.stopCamera = function () {
    exec(null, null, PLUGIN_NAME, "stopCamera", []);
};

//@param size {maxWidth: 100, maxHeight:100}
RichCamera.takePicture = function (size) {
    var params = [0, 0];
    if (size) {
        params = [size.maxWidth, size.maxHeight];
    }
    exec(null, null, PLUGIN_NAME, "takePicture", params);
};

RichCamera.setColorEffect = function (effect) {
    exec(null, null, PLUGIN_NAME, "setColorEffect", [effect]);
};

RichCamera.switchCamera = function () {
    exec(null, null, PLUGIN_NAME, "switchCamera", []);
};

RichCamera.hide = function () {
    exec(null, null, PLUGIN_NAME, "hideCamera", []);
};

RichCamera.show = function () {
    exec(null, null, PLUGIN_NAME, "showCamera", []);
};

RichCamera.disable = function (disable) {
    exec(null, null, PLUGIN_NAME, "disable", [disable]);
};

RichCamera.requestPermission = function (fn_success, fn_error) {
    exec(fn_success, fn_error, PLUGIN_NAME, "requestPermission", []);
};

RichCamera.startVideoCapture = function (options, fn_success, fn_error) {

    options = options || {'enabledRecordAudio': true};

    exec(fn_success, fn_error, PLUGIN_NAME, "startVideoCapture", [options.enabledRecordAudio]);

};

RichCamera.stopVideoCapture = function (deleteFile, fn_success, fn_error) {
    exec(fn_success, fn_error, PLUGIN_NAME, "stopVideoCapture", [!!deleteFile]);
};

module.exports = RichCamera;
