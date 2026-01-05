package com.autoai.watermarkvideoplayer;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

/**
 * Configuration manager for video player settings
 */
public class VideoConfig {
    private static final String PREF_NAME = "video_config";
    private static final String KEY_VIDEO_URI = "video_uri";
    private static final String KEY_WATERMARK_HEIGHT = "watermark_height";
    private static final String KEY_CAMERA_POSITION = "camera_position";

    private final SharedPreferences preferences;

    public VideoConfig(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Save last selected video URI
     */
    public void saveVideoUri(Uri uri) {
        if (uri != null) {
            preferences.edit()
                    .putString(KEY_VIDEO_URI, uri.toString())
                    .apply();
        }
    }

    /**
     * Get last selected video URI
     */
    public Uri getVideoUri() {
        String uriString = preferences.getString(KEY_VIDEO_URI, null);
        if (uriString != null) {
            return Uri.parse(uriString);
        }
        return null;
    }

    /**
     * Save watermark height ratio (0.0 - 0.3)
     */
    public void saveWatermarkHeight(float height) {
        preferences.edit()
                .putFloat(KEY_WATERMARK_HEIGHT, height)
                .apply();
    }

    /**
     * Get watermark height ratio
     */
    public float getWatermarkHeight() {
        return preferences.getFloat(KEY_WATERMARK_HEIGHT, 0.1f);
    }

    /**
     * Save camera position
     */
    public void saveCameraPosition(GLCameraVideoView.CameraPosition position) {
        preferences.edit()
                .putString(KEY_CAMERA_POSITION, position.name())
                .apply();
    }

    /**
     * Get camera position
     */
    public GLCameraVideoView.CameraPosition getCameraPosition() {
        String positionName = preferences.getString(KEY_CAMERA_POSITION,
                GLCameraVideoView.CameraPosition.ALL.name());
        try {
            return GLCameraVideoView.CameraPosition.valueOf(positionName);
        } catch (IllegalArgumentException e) {
            return GLCameraVideoView.CameraPosition.ALL;
        }
    }

    /**
     * Clear all saved settings
     */
    public void clear() {
        preferences.edit().clear().apply();
    }
}
