# AVM水印视频播放器

一个基于OpenGL ES的Android应用，用于播放4合1摄像头视频并支持水印显示和摄像头切换功能。

## 项目概述

本应用采用**单MediaPlayer + OpenGL ES**架构，专为车载平台（8155/8295）优化，能够高效播放由四个摄像头录制合成的4合1视频文件。

### 核心特性

- ✅ 单次解码，性能最优
- ✅ GPU硬件加速渲染
- ✅ 精确控制渲染区域
- ✅ 支持水印显示和摄像头切换
- ✅ 文件选择和配置持久化
- ✅ 适配Android 6.0 - 14+

## 技术架构

### 核心组件

1. **GLCameraVideoView**
   - 自定义GLSurfaceView
   - 管理MediaPlayer和OpenGL渲染
   - 支持5种摄像头位置切换

2. **VideoRenderer**
   - 内部OpenGL ES 2.0渲染器
   - 双Shader模式（单区域/双区域）
   - 外部纹理处理

3. **MainActivity**
   - UI控制和事件处理
   - 权限管理（存储访问）
   - 文件选择功能

4. **VideoConfig**
   - SharedPreferences配置管理
   - 保存视频URI、水印高度、摄像头位置

### 视频布局

4合1视频的标准布局（归一化坐标）：

```
┌─────────────────────────────┐
│      水印区域 (0-30%)         │  ← 可调节高度
├──────────────┬──────────────┤
│              │              │
│   左上(0.0,0.1,0.5,0.45)   │   右上(0.5,0.1,0.5,0.45)
│              │              │
├──────────────┼──────────────┤
│              │              │
│   左下(0.0,0.55,0.5,0.45)  │   右下(0.5,0.55,0.5,0.45)
│              │              │
└──────────────┴──────────────┘
```

## 构建和运行

### 环境要求

- Android Studio Arctic Fox 或更高版本
- Gradle 8.2+
- Android SDK API 34
- JDK 8+

### 构建步骤

```bash
# 1. 克隆项目
cd watermarkVidioPlayer

# 2. 打开Android Studio并导入项目

# 3. 同步Gradle
./gradlew build

# 4. 连接设备或启动模拟器

# 5. 运行应用
./gradlew installDebug
```

### 权限要求

应用需要以下权限：
- `READ_EXTERNAL_STORAGE` (Android 6-12)
- `READ_MEDIA_VIDEO` (Android 13+)

## 使用说明

1. **选择视频文件**
   - 点击"选择视频文件"按钮
   - 从存储中选择4合1视频文件
   - 应用会自动记住上次选择的视频

2. **调节水印高度**
   - 使用SeekBar调整水印显示区域高度（0-30%）
   - 实时预览效果

3. **切换摄像头视图**
   - **全视图**：显示完整的4合1视频
   - **左上/右上/左下/右下**：显示水印+单个摄像头视频

## 技术亮点

### OpenGL ES优化

- **外部纹理**：使用`GL_OES_EGL_image_external`处理MediaPlayer输出
- **Shader切换**：根据显示模式动态切换单/双区域Fragment Shader
- **按需渲染**：`RENDERMODE_WHEN_DIRTY`模式节省电量

### 性能指标

- 单MediaPlayer实例，无同步问题
- 相比双播放器方案节省50%内存
- 目标帧率：60fps（在8155/8295平台）

## 项目结构

```
app/src/main/
├── java/com/autoai/watermarkvideoplayer/
│   ├── MainActivity.java          # 主Activity
│   ├── GLCameraVideoView.java     # OpenGL视频视图
│   └── VideoConfig.java           # 配置管理
├── res/
│   ├── layout/
│   │   └── activity_main.xml      # 主界面布局
│   └── values/
│       ├── strings.xml            # 字符串资源
│       ├── colors.xml             # 颜色定义
│       └── themes.xml             # 主题样式
└── AndroidManifest.xml            # 应用清单
```

## 开发指南

详细的开发指南和架构说明请参阅 [CLAUDE.md](./CLAUDE.md)

## 许可证

本项目为测试应用，仅供学习和研究使用。
