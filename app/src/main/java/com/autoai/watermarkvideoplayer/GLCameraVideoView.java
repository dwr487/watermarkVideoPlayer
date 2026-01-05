package com.autoai.watermarkvideoplayer;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * OpenGL ES-based video view for displaying 4-in-1 camera video with watermark
 */
public class GLCameraVideoView extends GLSurfaceView {
    private static final String TAG = "GLCameraVideoView";

    private VideoRenderer renderer;
    private MediaPlayer mediaPlayer;
    private SurfaceTexture surfaceTexture;

    public enum CameraPosition {
        ALL(0.0f, 0.0f, 1.0f, 1.0f),
        TOP_LEFT(0.0f, 0.0f, 0.5f, 0.5f), // x, y, width, height (归一化坐标)
        TOP_RIGHT(0.5f, 0.0f, 0.5f, 0.5f),
        BOTTOM_LEFT(0.0f, 0.5f, 0.5f, 0.5f),
        BOTTOM_RIGHT(0.5f, 0.5f, 0.5f, 0.5f);

        final float x, y, width, height;

        CameraPosition(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private CameraPosition currentPosition = CameraPosition.ALL;
    private float watermarkHeight = 0.1f; // 水印高度占比

    public interface OnVideoStateListener {
        void onVideoLoaded();
        void onVideoError(String error);
    }

    private OnVideoStateListener videoStateListener;

    public GLCameraVideoView(Context context) {
        super(context);
        init(context);
    }

    public GLCameraVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setEGLContextClientVersion(2);
        renderer = new VideoRenderer(context);
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    public void setOnVideoStateListener(OnVideoStateListener listener) {
        this.videoStateListener = listener;
    }

    public void setVideoUri(Uri uri) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(getContext(), uri);
            mediaPlayer.setLooping(true);
            mediaPlayer.setOnPreparedListener(mp -> {
                renderer.setVideoSize(mp.getVideoWidth(), mp.getVideoHeight());
                mp.start();
                if (videoStateListener != null) {
                    videoStateListener.onVideoLoaded();
                }
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                if (videoStateListener != null) {
                    videoStateListener.onVideoError("Error code: " + what + ", extra: " + extra);
                }
                return true;
            });
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, "Error setting video URI", e);
            if (videoStateListener != null) {
                videoStateListener.onVideoError(e.getMessage());
            }
        }
    }

    public void setVideoPath(String path) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(path);
            mediaPlayer.setLooping(true);

            mediaPlayer.setOnPreparedListener(mp -> {
                Log.d(TAG, "Video prepared, starting playback");
                renderer.setVideoSize(mp.getVideoWidth(), mp.getVideoHeight());

                // 确保Surface已绑定
                if (renderer.surfaceTexture != null) {
                    mp.setSurface(new Surface(renderer.surfaceTexture));
                }

                // 自动开始播放
                mp.start();
                Log.d(TAG, "Video playback started");

                if (videoStateListener != null) {
                    videoStateListener.onVideoLoaded();
                }
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: what=" + what + ", extra=" + extra);
                if (videoStateListener != null) {
                    videoStateListener.onVideoError("Error code: " + what);
                }
                return true;
            });

            mediaPlayer.prepareAsync();
            Log.d(TAG, "Video preparing: " + path);
        } catch (Exception e) {
            Log.e(TAG, "Error setting video path", e);
            if (videoStateListener != null) {
                videoStateListener.onVideoError(e.getMessage());
            }
        }
    }

    public void setCameraPosition(CameraPosition position) {
        this.currentPosition = position;
        if (position == CameraPosition.ALL) {
            // 显示完整视频
            renderer.setCropRegion(0.0f, 0.0f, 1.0f, 1.0f);
        } else {
            // 显示水印 + 选中的摄像头
            renderer.setDualRegion(
                    0.0f, 0.0f, 1.0f, watermarkHeight, // 水印区域
                    position.x, position.y, position.width, position.height // 摄像头区域
            );
        }
        requestRender();
    }

    public void setWatermarkHeightRatio(float ratio) {
        this.watermarkHeight = ratio;
        setCameraPosition(currentPosition);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            Log.d(TAG, "Resuming video playback");
            mediaPlayer.start();
        }
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public void startPlayback() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            Log.d(TAG, "Starting playback");
            mediaPlayer.start();
        }
    }

    public void pausePlayback() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            Log.d(TAG, "Pausing playback");
            mediaPlayer.pause();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    public void release() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (surfaceTexture != null) {
            surfaceTexture.release();
            surfaceTexture = null;
        }
    }

    // VideoRenderer内部类
    private class VideoRenderer implements GLSurfaceView.Renderer,
            SurfaceTexture.OnFrameAvailableListener {

        private static final String VERTEX_SHADER =
                "attribute vec4 aPosition;\n" +
                "attribute vec4 aTextureCoord;\n" +
                "varying vec2 vTextureCoord;\n" +
                "void main() {\n" +
                "  gl_Position = aPosition;\n" +
                "  vTextureCoord = aTextureCoord.xy;\n" +
                "}\n";

        private static final String FRAGMENT_SHADER =
                "#extension GL_OES_EGL_image_external : require\n" +
                "precision mediump float;\n" +
                "varying vec2 vTextureCoord;\n" +
                "uniform samplerExternalOES sTexture;\n" +
                "uniform vec4 uCropRegion;\n" + // x, y, width, height
                "void main() {\n" +
                "  vec2 texCoord = uCropRegion.xy + vTextureCoord * uCropRegion.zw;\n" +
                "  gl_FragColor = texture2D(sTexture, texCoord);\n" +
                "}\n";

        private static final String DUAL_FRAGMENT_SHADER =
                "#extension GL_OES_EGL_image_external : require\n" +
                "precision mediump float;\n" +
                "varying vec2 vTextureCoord;\n" +
                "uniform samplerExternalOES sTexture;\n" +
                "uniform vec4 uWatermarkRegion;\n" + // 水印区域
                "uniform vec4 uCameraRegion;\n" + // 摄像头区域
                "uniform float uWatermarkHeight;\n" + // 水印显示高度占比
                "void main() {\n" +
                "  vec2 texCoord;\n" +
                "  if (vTextureCoord.y < uWatermarkHeight) {\n" +
                "    // 水印区域\n" +
                "    float normalizedY = vTextureCoord.y / uWatermarkHeight;\n" +
                "    texCoord = uWatermarkRegion.xy + vec2(vTextureCoord.x, normalizedY) * uWatermarkRegion.zw;\n" +
                "  } else {\n" +
                "    // 摄像头区域\n" +
                "    float normalizedY = (vTextureCoord.y - uWatermarkHeight) / (1.0 - uWatermarkHeight);\n" +
                "    texCoord = uCameraRegion.xy + vec2(vTextureCoord.x, normalizedY) * uCameraRegion.zw;\n" +
                "  }\n" +
                "  gl_FragColor = texture2D(sTexture, texCoord);\n" +
                "}\n";

        private final float[] VERTEX_COORDS = {
                -1.0f, -1.0f, // 左下
                1.0f, -1.0f, // 右下
                -1.0f, 1.0f, // 左上
                1.0f, 1.0f, // 右上
        };

        // 添加小的边距避免边缘采样问题
        private static final float TEX_MARGIN = 0.001f;
        private final float[] TEXTURE_COORDS = {
                TEX_MARGIN, 1.0f - TEX_MARGIN, // 左下
                1.0f - TEX_MARGIN, 1.0f - TEX_MARGIN, // 右下
                TEX_MARGIN, TEX_MARGIN, // 左上
                1.0f - TEX_MARGIN, TEX_MARGIN, // 右上
        };

        private FloatBuffer vertexBuffer;
        private FloatBuffer textureBuffer;

        private int program;
        private int dualProgram;
        private int textureId;

        private int aPositionHandle;
        private int aTextureCoordHandle;
        private int uTextureHandle;
        private int uCropRegionHandle;

        // 双区域模式的handles
        private int dualAPositionHandle;
        private int dualATextureCoordHandle;
        private int dualUTextureHandle;
        private int uWatermarkRegionHandle;
        private int uCameraRegionHandle;
        private int uWatermarkHeightHandle;

        SurfaceTexture surfaceTexture; // 包级访问，允许外部类访问
        private boolean updateSurface = false;
        private boolean isDualMode = false;

        private float[] cropRegion = {0.0f, 0.0f, 1.0f, 1.0f};
        private float[] watermarkRegion = {0.0f, 0.0f, 1.0f, 0.1f};
        private float[] cameraRegion = {0.0f, 0.1f, 0.5f, 0.45f};
        private float watermarkDisplayHeight = 0.15f;

        private final Context context;
        private int videoWidth;
        private int videoHeight;
        private int surfaceWidth;
        private int surfaceHeight;

        public VideoRenderer(Context context) {
            this.context = context;
            vertexBuffer = ByteBuffer.allocateDirect(VERTEX_COORDS.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .put(VERTEX_COORDS);
            vertexBuffer.position(0);

            textureBuffer = ByteBuffer.allocateDirect(TEXTURE_COORDS.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .put(TEXTURE_COORDS);
            textureBuffer.position(0);
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

            // 创建单区域程序
            program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
            aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition");
            aTextureCoordHandle = GLES20.glGetAttribLocation(program, "aTextureCoord");
            uTextureHandle = GLES20.glGetUniformLocation(program, "sTexture");
            uCropRegionHandle = GLES20.glGetUniformLocation(program, "uCropRegion");

            // 创建双区域程序
            dualProgram = createProgram(VERTEX_SHADER, DUAL_FRAGMENT_SHADER);
            dualAPositionHandle = GLES20.glGetAttribLocation(dualProgram, "aPosition");
            dualATextureCoordHandle = GLES20.glGetAttribLocation(dualProgram, "aTextureCoord");
            dualUTextureHandle = GLES20.glGetUniformLocation(dualProgram, "sTexture");
            uWatermarkRegionHandle = GLES20.glGetUniformLocation(dualProgram, "uWatermarkRegion");
            uCameraRegionHandle = GLES20.glGetUniformLocation(dualProgram, "uCameraRegion");
            uWatermarkHeightHandle = GLES20.glGetUniformLocation(dualProgram, "uWatermarkHeight");

            // 创建纹理
            textureId = createTexture();

            // 创建SurfaceTexture
            surfaceTexture = new SurfaceTexture(textureId);
            surfaceTexture.setOnFrameAvailableListener(this);

            // 将MediaPlayer的Surface绑定到SurfaceTexture
            if (mediaPlayer != null) {
                mediaPlayer.setSurface(new Surface(surfaceTexture));
            }
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
            surfaceWidth = width;
            surfaceHeight = height;
            updateVertexCoordinates();
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            synchronized (this) {
                if (updateSurface) {
                    surfaceTexture.updateTexImage();
                    updateSurface = false;
                }
            }

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            if (isDualMode) {
                drawDualMode();
            } else {
                drawSingleMode();
            }
        }

        private void drawSingleMode() {
            GLES20.glUseProgram(program);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glUniform1i(uTextureHandle, 0);

            GLES20.glUniform4f(uCropRegionHandle,
                    cropRegion[0], cropRegion[1], cropRegion[2], cropRegion[3]);

            GLES20.glEnableVertexAttribArray(aPositionHandle);
            GLES20.glVertexAttribPointer(aPositionHandle, 2, GLES20.GL_FLOAT,
                    false, 0, vertexBuffer);

            GLES20.glEnableVertexAttribArray(aTextureCoordHandle);
            GLES20.glVertexAttribPointer(aTextureCoordHandle, 2,
                    GLES20.GL_FLOAT, false, 0, textureBuffer);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            GLES20.glDisableVertexAttribArray(aPositionHandle);
            GLES20.glDisableVertexAttribArray(aTextureCoordHandle);
        }

        private void drawDualMode() {
            GLES20.glUseProgram(dualProgram);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glUniform1i(dualUTextureHandle, 0);

            GLES20.glUniform4f(uWatermarkRegionHandle,
                    watermarkRegion[0], watermarkRegion[1], watermarkRegion[2], watermarkRegion[3]);
            GLES20.glUniform4f(uCameraRegionHandle,
                    cameraRegion[0], cameraRegion[1], cameraRegion[2], cameraRegion[3]);
            GLES20.glUniform1f(uWatermarkHeightHandle, watermarkDisplayHeight);

            GLES20.glEnableVertexAttribArray(dualAPositionHandle);
            GLES20.glVertexAttribPointer(dualAPositionHandle, 2,
                    GLES20.GL_FLOAT, false, 0, vertexBuffer);

            GLES20.glEnableVertexAttribArray(dualATextureCoordHandle);
            GLES20.glVertexAttribPointer(dualATextureCoordHandle, 2,
                    GLES20.GL_FLOAT, false, 0, textureBuffer);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            GLES20.glDisableVertexAttribArray(dualAPositionHandle);
            GLES20.glDisableVertexAttribArray(dualATextureCoordHandle);
        }

        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            synchronized (this) {
                updateSurface = true;
            }
            requestRender();
        }

        public void setCropRegion(float x, float y, float width, float height) {
            isDualMode = false;
            cropRegion[0] = x;
            cropRegion[1] = y;
            cropRegion[2] = width;
            cropRegion[3] = height;
        }

        public void setDualRegion(float wx, float wy, float ww, float wh,
                                   float cx, float cy, float cw, float ch) {
            isDualMode = true;
            watermarkRegion[0] = wx;
            watermarkRegion[1] = wy;
            watermarkRegion[2] = ww;
            watermarkRegion[3] = wh;

            cameraRegion[0] = cx;
            cameraRegion[1] = cy;
            cameraRegion[2] = cw;
            cameraRegion[3] = ch;
        }

        public void setVideoSize(int width, int height) {
            Log.d(TAG, "Video size: " + width + "x" + height);
            videoWidth = width;
            videoHeight = height;
            updateVertexCoordinates();
        }

        private void updateVertexCoordinates() {
            if (videoWidth == 0 || videoHeight == 0 || surfaceWidth == 0 || surfaceHeight == 0) {
                return;
            }

            float videoAspect = (float) videoWidth / videoHeight;
            float surfaceAspect = (float) surfaceWidth / surfaceHeight;

            // 重置纹理坐标为默认值，保持完整视频内容
            textureBuffer.clear();
            textureBuffer.put(TEXTURE_COORDS);
            textureBuffer.position(0);

            // 通过调整顶点坐标来适配宽高比（letterbox方式，不裁剪内容）
            float scaleX = 1.0f;
            float scaleY = 1.0f;

            if (surfaceAspect > videoAspect) {
                // Surface更宽，视频在水平方向缩小，上下填充黑边
                scaleX = videoAspect / surfaceAspect;
            } else {
                // Surface更高，视频在垂直方向缩小，左右填充黑边
                scaleY = surfaceAspect / videoAspect;
            }

            float[] adjustedVertexCoords = {
                    -scaleX, -scaleY, // 左下
                    scaleX, -scaleY,  // 右下
                    -scaleX, scaleY,  // 左上
                    scaleX, scaleY,   // 右上
            };

            vertexBuffer.clear();
            vertexBuffer.put(adjustedVertexCoords);
            vertexBuffer.position(0);

            Log.d(TAG, "Updated vertex coords - videoAspect: " + videoAspect +
                  ", surfaceAspect: " + surfaceAspect +
                  ", scaleX: " + scaleX + ", scaleY: " + scaleY);
        }

        private int createProgram(String vertexSource, String fragmentSource) {
            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
            int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);

            int program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vertexShader);
            GLES20.glAttachShader(program, fragmentShader);
            GLES20.glLinkProgram(program);

            return program;
        }

        private int loadShader(int type, String shaderCode) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, shaderCode);
            GLES20.glCompileShader(shader);
            return shader;
        }

        private int createTexture() {
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);

            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0]);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            return textures[0];
        }
    }
}
