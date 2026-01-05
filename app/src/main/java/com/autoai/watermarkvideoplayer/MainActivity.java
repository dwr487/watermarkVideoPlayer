package com.autoai.watermarkvideoplayer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;

/**
 * Main Activity for AVM Watermark Video Player
 */
public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;

    private GLCameraVideoView videoView;
    private TextView tvVideoPath;
    private TextView tvWatermarkHeight;
    private SeekBar seekbarWatermarkHeight;

    private Button btnCameraAll;
    private Button btnCameraTopLeft;
    private Button btnCameraTopRight;
    private Button btnCameraBottomLeft;
    private Button btnCameraBottomRight;

    private VideoConfig videoConfig;
    private GLCameraVideoView.CameraPosition currentPosition = GLCameraVideoView.CameraPosition.ALL;

    // File picker launcher
    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        // Take persistable URI permission
                        try {
                            getContentResolver().takePersistableUriPermission(uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException e) {
                            // Ignore if permission can't be taken
                        }

                        loadVideo(uri);
                        videoConfig.saveVideoUri(uri);
                    }
                }
            }
    );

    // Permission launcher
    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (!granted) {
                        allGranted = false;
                        break;
                    }
                }

                if (allGranted) {
                    // 权限已授予，固定视频会在initViews中自动加载
                    // loadLastVideo(); // 已改为加载固定视频
                } else {
                    Toast.makeText(this, R.string.msg_permission_denied, Toast.LENGTH_LONG).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        videoConfig = new VideoConfig(this);
        initViews();
        setupListeners();
        requestPermissions();
    }

    private void initViews() {
        videoView = findViewById(R.id.gl_camera_video_view);
        tvVideoPath = findViewById(R.id.tv_video_path);
        tvWatermarkHeight = findViewById(R.id.tv_watermark_height);
        seekbarWatermarkHeight = findViewById(R.id.seekbar_watermark_height);

        btnCameraAll = findViewById(R.id.btn_camera_all);
        btnCameraTopLeft = findViewById(R.id.btn_camera_top_left);
        btnCameraTopRight = findViewById(R.id.btn_camera_top_right);
        btnCameraBottomLeft = findViewById(R.id.btn_camera_bottom_left);
        btnCameraBottomRight = findViewById(R.id.btn_camera_bottom_right);

        // Set video state listener
        videoView.setOnVideoStateListener(new GLCameraVideoView.OnVideoStateListener() {
            @Override
            public void onVideoLoaded() {
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, R.string.msg_video_loaded, Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onVideoError(String error) {
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this,
                                getString(R.string.msg_video_load_error) + ": " + error,
                                Toast.LENGTH_LONG).show()
                );
            }
        });

        // Load saved settings
        float savedWatermarkHeight = videoConfig.getWatermarkHeight();
        int progress = (int) (savedWatermarkHeight * 100);
        seekbarWatermarkHeight.setProgress(progress);
        tvWatermarkHeight.setText(String.format("%d%%", progress));
        videoView.setWatermarkHeightRatio(savedWatermarkHeight);

        currentPosition = videoConfig.getCameraPosition();
        updateCameraButtonStates();

        // 直接加载固定视频文件
        loadFixedVideo();
    }

    private void setupListeners() {
        // Select video button
        findViewById(R.id.btn_select_video).setOnClickListener(v -> openFilePicker());

        // Watermark height seekbar
        seekbarWatermarkHeight.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float ratio = progress / 100.0f;
                tvWatermarkHeight.setText(String.format("%d%%", progress));
                videoView.setWatermarkHeightRatio(ratio);
                videoConfig.saveWatermarkHeight(ratio);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Camera position buttons
        btnCameraAll.setOnClickListener(v -> setCameraPosition(GLCameraVideoView.CameraPosition.ALL));
        btnCameraTopLeft.setOnClickListener(v -> setCameraPosition(GLCameraVideoView.CameraPosition.TOP_LEFT));
        btnCameraTopRight.setOnClickListener(v -> setCameraPosition(GLCameraVideoView.CameraPosition.TOP_RIGHT));
        btnCameraBottomLeft.setOnClickListener(v -> setCameraPosition(GLCameraVideoView.CameraPosition.BOTTOM_LEFT));
        btnCameraBottomRight.setOnClickListener(v -> setCameraPosition(GLCameraVideoView.CameraPosition.BOTTOM_RIGHT));
    }

    private void requestPermissions() {
        String[] permissions;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            permissions = new String[]{Manifest.permission.READ_MEDIA_VIDEO};
        } else {
            // Android 6-12
            permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        }

        boolean allGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            // 权限已授予，固定视频会在initViews中自动加载
            // loadLastVideo(); // 已改为加载固定视频
        } else {
            permissionLauncher.launch(permissions);
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        filePickerLauncher.launch(intent);
    }

    private void loadVideo(Uri uri) {
        videoView.setVideoUri(uri);
        tvVideoPath.setText(uri.getLastPathSegment());
    }

    private void loadLastVideo() {
        Uri lastUri = videoConfig.getVideoUri();
        if (lastUri != null) {
            loadVideo(lastUri);
        }
    }

    private void loadFixedVideo() {
        // 使用公共Movies目录，应用有READ_MEDIA_VIDEO权限
        String videoPath = "/sdcard/Movies/161.mp4";
        File videoFile = new File(videoPath);

        if (!videoFile.exists()) {
            Toast.makeText(this, "视频文件不存在: " + videoPath, Toast.LENGTH_LONG).show();
            return;
        }

        // 设置视频路径，这会自动开始准备和播放
        videoView.setVideoPath(videoPath);
        tvVideoPath.setText("161.mp4");

        Toast.makeText(this, "正在加载视频: " + videoPath, Toast.LENGTH_SHORT).show();
    }

    private void setCameraPosition(GLCameraVideoView.CameraPosition position) {
        currentPosition = position;
        videoView.setCameraPosition(position);
        videoConfig.saveCameraPosition(position);
        updateCameraButtonStates();
    }

    private void updateCameraButtonStates() {
        // Reset all buttons to default color
        int defaultColor = getColor(R.color.button_bg);
        int selectedColor = getColor(R.color.button_bg_selected);

        btnCameraAll.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        currentPosition == GLCameraVideoView.CameraPosition.ALL ? selectedColor : defaultColor
                )
        );
        btnCameraTopLeft.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        currentPosition == GLCameraVideoView.CameraPosition.TOP_LEFT ? selectedColor : defaultColor
                )
        );
        btnCameraTopRight.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        currentPosition == GLCameraVideoView.CameraPosition.TOP_RIGHT ? selectedColor : defaultColor
                )
        );
        btnCameraBottomLeft.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        currentPosition == GLCameraVideoView.CameraPosition.BOTTOM_LEFT ? selectedColor : defaultColor
                )
        );
        btnCameraBottomRight.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        currentPosition == GLCameraVideoView.CameraPosition.BOTTOM_RIGHT ? selectedColor : defaultColor
                )
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        videoView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        videoView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        videoView.release();
    }
}
