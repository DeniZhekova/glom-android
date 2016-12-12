package com.abborg.glom.hardware.camera;

import android.graphics.SurfaceTexture;
import android.os.Handler;

/**
 * Abstracts Camera API differences from Camera1 and Camera2 classes
 */
public interface CameraCompat {

    int CAMERA_READY = 0;
    int CAMERA_RELEASED = 1;
    int CAMERA_ERROR = 2;

    void setHandler(Handler handler);

    void open();

    void open(int cameraId);

    void close();

    int getOrientation(int cameraId);

    void setPreviewTexture(SurfaceTexture surface);

    void startPreview();

    void stopPreview();

    int getFrontCameraId();

    int getBackCameraId();
}
