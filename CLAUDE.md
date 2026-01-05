# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

AVM水印视频播放器 - 一个Android测试应用，用于播放由四个摄像头录制而成的4合1视频文件。视频录制时已固化水印，应用支持显示某个摄像头的视频，同时在顶部显示完整水印，并支持不同摄像头的切换。

## 核心架构

### 技术方案：单MediaPlayer + OpenGL ES

采用单个MediaPlayer配合OpenGL ES进行GPU硬件加速渲染，这是推荐方案，具有以下优势：
- 只需一次解码，性能最优
- GPU硬件加速，在8155/8295平台上优势明显
- 精确控制渲染区域，无需同步
- 可扩展性强（支持滤镜、特效、PIP等）

### 核心组件

1. **GLCameraVideoView** (`GLCameraVideoView.java`)
   - 继承自 GLSurfaceView
   - 主要视图组件，管理MediaPlayer和OpenGL渲染器
   - 负责视频加载、播放控制和摄像头位置切换

2. **VideoRenderer** (GLCameraVideoView的内部类)
   - 实现 GLSurfaceView.Renderer 和 SurfaceTexture.OnFrameAvailableListener
   - 管理OpenGL ES的渲染逻辑
   - 支持两种渲染模式：单区域模式和双区域模式

3. **CameraPosition** (枚举类)
   - 定义五个摄像头位置：ALL, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
   - 每个位置包含归一化坐标 (x, y, width, height)

### 视频布局规范

4合1视频的固定布局：
- 完整视频（ALL）：显示整个视频 (0.0, 0.0, 1.0, 1.0)
- 左上摄像头（TOP_LEFT）：(0.0, 0.1, 0.5, 0.45)
- 右上摄像头（TOP_RIGHT）：(0.5, 0.1, 0.5, 0.45)
- 左下摄像头（BOTTOM_LEFT）：(0.0, 0.55, 0.5, 0.45)
- 右下摄像头（BOTTOM_RIGHT）：(0.5, 0.55, 0.5, 0.45)
- 水印区域：顶部占比可调，默认为0.1（10%高度）

### OpenGL ES 渲染实现

#### Shader程序

项目使用两套Shader程序：

1. **单区域Shader** (VERTEX_SHADER + FRAGMENT_SHADER)
   - 用于显示完整视频（ALL模式）
   - 通过 uCropRegion uniform控制裁剪区域

2. **双区域Shader** (VERTEX_SHADER + DUAL_FRAGMENT_SHADER)
   - 用于同时显示水印和选中的摄像头
   - 通过 uWatermarkRegion、uCameraRegion 和 uWatermarkHeight 控制两个区域的渲染
   - 根据Y坐标自动选择渲染水印区域或摄像头区域

#### 纹理处理

- 使用 GL_OES_EGL_image_external 扩展处理视频纹理
- 通过 SurfaceTexture 将 MediaPlayer 的输出绑定到OpenGL纹理
- 纹理参数：GL_LINEAR过滤，GL_CLAMP_TO_EDGE包裹模式

## 开发指南

### 添加新功能时的注意事项

1. **性能优化**
   - 保持单MediaPlayer架构，避免多播放器同步问题
   - 利用GPU硬件加速，避免CPU密集型操作
   - 在8155/8295平台上目标是流畅60fps

2. **渲染模式切换**
   - 使用 `setCameraPosition()` 切换摄像头视图
   - ALL模式使用单区域渲染（更高效）
   - 其他位置使用双区域渲染（水印+摄像头）

3. **资源管理**
   - 在 `onPause()` 时暂停播放
   - 在 `onResume()` 时恢复播放
   - 调用 `release()` 释放MediaPlayer和SurfaceTexture资源

4. **扩展Shader功能**
   - 可以添加滤镜、转场、画中画等效果
   - 修改Fragment Shader实现自定义视觉效果
   - 注意保持Shader代码的性能

### 坐标系统

- 使用归一化坐标系统 (0.0 - 1.0)
- OpenGL顶点坐标：左下角为(-1, -1)，右上角为(1, 1)
- 纹理坐标：左下角为(0, 1)，右上角为(1, 0)
- 视频裁剪区域：左上角为起点 (x, y, width, height)

### Mermaid图表输出规范

根据用户配置，所有架构图和流程图应使用Mermaid格式或DrawIO格式输出。

## 常见开发场景

### 调整水印高度比例

```java
videoView.setWatermarkHeightRatio(0.15f); // 设置水印占15%高度
```

### 切换摄像头视图

```java
// 显示完整视频
videoView.setCameraPosition(GLCameraVideoView.CameraPosition.ALL);

// 显示左上摄像头
videoView.setCameraPosition(GLCameraVideoView.CameraPosition.TOP_LEFT);
```

### 设置视频路径

```java
videoView.setVideoPath("/sdcard/Movies/camera_4in1.mp4");
```

## 技术约束

- 最低Android API级别：支持OpenGL ES 2.0（API 8+）
- EGL Context版本：2
- 目标硬件平台：8155/8295车机芯片组
- 渲染模式：RENDERMODE_WHEN_DIRTY（按需渲染，节省电量）

## 性能指标

- 单次视频解码
- GPU加速渲染
- 相比双MediaPlayer方案节省50%内存
- 目标帧率：60fps
- 无播放器同步问题

## 当前实现状态

### 应用配置
- **包名**: com.autoai.watermarkvideoplayer
- **屏幕方向**: 竖屏（portrait）
- **目标SDK**: 34
- **最低SDK**: 21
- **固定视频路径**: /sdcard/Movies/161.mp4
- **自动播放**: 应用启动时自动加载并播放视频

### 最近更新

#### 1. 屏幕方向调整（2026-01-05）
- 从横屏（landscape）改为竖屏（portrait）
- 修改 AndroidManifest.xml 中的 screenOrientation 配置

#### 2. 宽高比适配优化
**问题**: 视频宽高比与显示区域不匹配导致右侧出现彩色花屏条纹

**解决方案**:
- 添加 `updateVertexCoordinates()` 方法动态计算宽高比
- 根据视频和显示区域的宽高比自动调整顶点坐标
- 采用letterbox方式（添加黑边）而非裁剪，保留完整视频内容
- 添加纹理坐标边距（TEX_MARGIN = 0.001f）避免边缘采样问题

**核心代码**:
```java
private void updateVertexCoordinates() {
    float videoAspect = (float) videoWidth / videoHeight;
    float surfaceAspect = (float) surfaceWidth / surfaceHeight;

    float scaleX = 1.0f;
    float scaleY = 1.0f;

    if (surfaceAspect > videoAspect) {
        // Surface更宽，视频在水平方向缩小，上下填充黑边
        scaleX = videoAspect / surfaceAspect;
    } else {
        // Surface更高，视频在垂直方向缩小，左右填充黑边
        scaleY = surfaceAspect / videoAspect;
    }

    // 调整顶点坐标实现letterbox效果
    float[] adjustedVertexCoords = {
        -scaleX, -scaleY, // 左下
        scaleX, -scaleY,  // 右下
        -scaleX, scaleY,  // 左上
        scaleX, scaleY,   // 右上
    };
    // 更新vertexBuffer...
}
```

#### 3. 纹理边距优化
- 在默认纹理坐标中添加0.001f的小边距
- 避免在纹理边界精确采样导致的渲染问题
- 保持完整视频内容显示

```java
private static final float TEX_MARGIN = 0.001f;
private final float[] TEXTURE_COORDS = {
    TEX_MARGIN, 1.0f - TEX_MARGIN, // 左下
    1.0f - TEX_MARGIN, 1.0f - TEX_MARGIN, // 右下
    TEX_MARGIN, TEX_MARGIN, // 左上
    1.0f - TEX_MARGIN, TEX_MARGIN, // 右上
};
```

### 视频配置管理

`VideoConfig.java` 类负责持久化配置：
- 上次选择的视频URI（使用 SharedPreferences）
- 水印高度偏好设置
- 上次选择的摄像头位置

### 权限配置

AndroidManifest.xml 中声明的权限：
- `READ_EXTERNAL_STORAGE` (Android 6-12)
- `READ_MEDIA_VIDEO` (Android 13+)
- `requestLegacyExternalStorage="true"` 兼容Android 10+

### 文件选择功能

使用 Storage Access Framework (SAF)：
- `ACTION_OPEN_DOCUMENT` Intent
- `takePersistableUriPermission()` 保持访问权限
- 支持 content:// 协议的URI

### 已知问题

1. **花屏问题**: 在某些设备/配置下，视频右侧可能出现彩色条纹
   - 已添加纹理边距和宽高比适配
   - 如问题仍存在，可能需要进一步调整OpenGL渲染参数

2. **多用户环境**: Android模拟器多用户环境下的文件访问需要确保文件在正确的用户空间

### 环境配置

Android SDK环境变量（已配置在 ~/.bash_profile 和 ~/.zshrc）:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/emulator
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/tools
export PATH=$PATH:$ANDROID_HOME/tools/bin
```

### 构建和运行

```bash
# 编译
./gradlew assembleDebug

# 安装到设备（用户0）
adb install --user 0 app/build/outputs/apk/debug/app-debug.apk

# 授予权限
adb shell pm grant com.autoai.watermarkvideoplayer android.permission.READ_EXTERNAL_STORAGE

# 启动应用
adb shell am start --user 0 -n com.autoai.watermarkvideoplayer/.MainActivity
```