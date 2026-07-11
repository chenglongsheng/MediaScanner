# Android Automotive 媒体扫描系统设计方案

> **版本**: v1.2  
> **状态**: 已同步 PRD V1.3（新增卷发现策略抽象化）  
> **目标平台**: Android Automotive OS (API 28+)  
> **部署方式**: `/system/priv-app/MediaScanner/MediaScanner.apk`

---

## 目录

1. [项目背景与目标](#1-项目背景与目标)
2. [系统定位与约束](#2-系统定位与约束)
3. [总体架构](#3-总体架构)
4. [工程目录结构](#4-工程目录结构)
5. [核心模块详细设计](#5-核心模块详细设计)
    - [5.1 MediaScannerService - 核心服务](#51-mediascannerservice---核心服务)
    - [5.2 StorageManager - 存储卷管理](#52-storagemanager---存储卷管理)
    - [5.3 ScanScheduler - 扫描调度器](#53-scanscheduler---扫描调度器)
    - [5.4 ScannerEngine - 扫描引擎](#54-scannerengine---扫描引擎)
    - [5.5 Parser - 元数据解析器](#55-parser---元数据解析器)
    - [5.6 Database - 数据库设计](#56-database---数据库设计)
    - [5.7 MediaProvider - 内容提供者](#57-mediaprovider---内容提供者)
6. [核心流程设计](#6-核心流程设计)
    - [6.1 USB 插入扫描流程](#61-usb-插入扫描流程)
    - [6.2 USB 拔出处理流程](#62-usb-拔出处理流程)
    - [6.3 开机恢复流程](#63-开机恢复流程)
    - [6.4 增量扫描流程](#64-增量扫描流程)
7. [性能与优化策略](#7-性能与优化策略)
8. [安全与权限设计](#8-安全与权限设计)
9. [错误处理与容错](#9-错误处理与容错)
10. [实施计划](#10-实施计划)
11. [验收标准](#11-验收标准)
12. [附录](#12-附录)

---

## 1. 项目背景与目标

### 1.1 背景

Android Automotive 系统裁剪了原生 Android 的 `MediaScannerService` 和 `MediaProvider`
相关能力。车载场景下仍需要完整的媒体文件索引能力，供播放器、相册等应用查询和播放本地媒体文件。

### 1.2 目标

| 目标           | 说明                                                                |
|--------------|-------------------------------------------------------------------|
| **多存储卷支持**   | 同时管理内部存储、USB 设备、SD 卡等多种存储卷                                        |
| **全类型媒体索引**  | 支持音频、视频、图片三大类媒体文件的元数据提取与索引                                        |
| **增量扫描**     | 基于文件修改时间戳实现增量更新，避免重复扫描                                            |
| **热插拔响应**    | 实时监听 USB/SD 卡插拔事件，自动触发/取消扫描                                       |
| **稳定对外 API** | 提供 ContentProvider + AIDL 双通道 API，兼容原生 MediaStore 主要字段，方便播放器低成本接入 |
| **系统级部署**    | 作为 system/priv-app 运行，拥有系统权限                                      |

### 1.3 非目标

- 不修改 Android Framework 层
- 不依赖系统 MediaStore 数据库
- 不支持网络流媒体索引（当前阶段）
- 不支持 DRM 保护内容

---

## 2. 系统定位与约束

### 2.1 定位

```
┌──────────────────────────────────────────┐
│              Application Layer            │
│  ┌──────────┐  ┌──────────┐  ┌────────┐  │
│  │  Player  │  │  Gallery │  │  Other │  │
│  └────┬─────┘  └────┬─────┘  └───┬────┘  │
│       │             │            │        │
│       └──────┬──────┘            │        │
│              │                   │        │
│       ┌──────▼───────────────────▼─────┐  │
│       │       MediaScanner App         │  │
│       │  ┌──────────┐  ┌────────────┐  │  │
│       │  │ Provider │  │ AIDL Service│  │  │
│       │  └────┬─────┘  └──────┬─────┘  │  │
│       │       │               │        │  │
│       │       └───────┬───────┘        │  │
│       │               │                │  │
│       │       ┌───────▼────────┐       │  │
│       │       │  Scan Engine   │       │  │
│       │       └───────┬────────┘       │  │
│       │               │                │  │
│       │       ┌───────▼────────┐       │  │
│       │       │   Database     │       │  │
│       │       └────────────────┘       │  │
│       └────────────────────────────────┘  │
│                                           │
├───────────────────────────────────────────┤
│              Framework Layer              │
│  ┌─────────────────────────────────────┐  │
│  │  vold / StorageManagerService       │  │
│  │  (MEDIA_MOUNTED / MEDIA_UNMOUNTED)  │  │
│  └─────────────────────────────────────┘  │
└──────────────────────────────────────────┘
```

### 2.2 约束条件

| 约束                 | 说明                                  |
|--------------------|-------------------------------------|
| **独立进程**           | MediaScanner 运行在独立进程，不影响其他应用        |
| **不依赖 MediaStore** | 自建数据库，完全独立于系统 MediaProvider         |
| **兼容原生字段**         | 数据库 Schema 主要字段对齐 MediaStore，降低接入成本 |
| **最小权限原则**         | 仅申请必要权限，作为 priv-app 运行在 system 进程   |
| **API 28+**        | 最低支持 Android 9.0 (API 28)           |

---

## 3. 总体架构

### 3.1 架构分层

```
┌─────────────────────────────────────────────────────────────┐
│                        API Layer                            │
│  ┌──────────────────┐          ┌─────────────────────────┐  │
│  │  ContentProvider  │          │  AIDL (IMediaScanner)   │  │
│  │  (数据查询)        │          │  (扫描控制)              │  │
│  └────────┬─────────┘          └───────────┬─────────────┘  │
├───────────┼────────────────────────────────┼────────────────┤
│           │            Core Layer           │                │
│           │  ┌──────────────────────────┐   │                │
│           │  │    MediaScannerService    │   │                │
│           │  │  (生命周期管理 / 协调中心)  │   │                │
│           │  └──────────┬───────────────┘   │                │
│           │             │                   │                │
│           │  ┌──────────┴──────────────┐    │                │
│           │  │                        │    │                │
│           │  ▼                        ▼    │                │
│           │  ┌──────────────┐  ┌──────────────┐            │
│           │  │StorageManager│  │ ScanScheduler │            │
│           │  │  (卷管理)     │  │  (任务调度)    │            │
│           │  └──────┬───────┘  └──────┬───────┘            │
│           │         │                  │                     │
│           │         ▼                  ▼                     │
│           │  ┌──────────────────────────────────────┐       │
│           │  │           ScannerEngine               │       │
│           │  │  ┌──────────┐ ┌──────────┐ ┌───────┐ │       │
│           │  │  │FileWalker│ │ScanFilter│ │Parser │ │       │
│           │  │  └──────────┘ └──────────┘ └───┬───┘ │       │
│           │  └──────────────────────────────────┼────┘       │
│           │                                     │            │
│           │                                     ▼            │
│           │  ┌──────────────────────────────────────┐       │
│           │  │           Room Database                │       │
│           │  │  ┌──────────┐ ┌──────────┐ ┌───────┐ │       │
│           │  │  │  Media   │ │ Storage  │ │ Scan  │ │       │
│           │  │  │  Entity  │ │  Entity  │ │ Task  │ │       │
│           │  │  └──────────┘ └──────────┘ │ Entity│ │       │
│           │  └────────────────────────────└───────┘ │       │
├───────────┴──────────────────────────────────────────┴───────┤
│                     System Layer                            │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  vold → MEDIA_MOUNTED / MEDIA_UNMOUNTED 广播          │   │
│  │  StorageManagerService → getStorageVolumes() API      │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 架构原则

1. **单一职责**: 每个模块只负责一个明确的功能领域
2. **依赖倒置**: 上层依赖抽象，不依赖具体实现
3. **数据驱动**: 扫描结果通过数据库持久化，UI 通过 ContentProvider 观察数据变化
4. **异步优先**: 所有 IO 操作异步执行，不阻塞主线程
5. **容错设计**: 单个文件解析失败不影响整体扫描流程
6. **策略可替换**: 卷发现、格式检测等平台差异点通过接口抽象，支持运行时切换实现，适配不同供应商的车载 ROM

---

## 4. 工程目录结构

```
MediaScanner/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── aidl/com/txzing/media/scanner/
│       │   ├── IMediaScanner.aidl           # 扫描服务 AIDL 接口
│       │   └── IMediaScanCallback.aidl      # 扫描进度回调接口
│       │
│       └── java/com/txzing/media/scanner/
│           ├── MediaScannerApplication.kt   # Application 入口
│           ├── MediaScannerService.kt       # 核心后台服务
│           ├── StorageReceiver.kt           # 存储设备广播接收器
│           ├── BootReceiver.kt              # 开机完成广播接收器
│           │
│           ├── discovery/                      # 卷发现策略层（可插拔）
│           │   ├── VolumeDiscoveryService.kt        # 卷发现抽象接口
│           │   ├── NativeAndroidVolumeDiscovery.kt  # 原生 Android 实现（vold 广播）
│           │   └── CustomPathVolumeDiscovery.kt     # 自定义路径实现（供应商适配）
│           │
│           ├── storage/                     # 存储卷管理层
│           │   ├── StorageManager.kt        # 卷管理协调器
│           │   ├── StorageVolume.kt         # 存储卷数据模型
│           │   └── VolumeRepository.kt      # 卷信息仓库
│           │
│           ├── scheduler/                   # 扫描调度层
│           │   ├── ScanScheduler.kt         # 扫描任务调度器
│           │   ├── ScanTask.kt              # 扫描任务模型
│           │   └── ScanWorker.kt            # WorkManager Worker
│           │
│           ├── scanner_core/                # 扫描核心引擎
│           │   ├── ScannerEngine.kt         # 扫描引擎入口
│           │   ├── FileWalker.kt            # 文件系统遍历器
│           │   └── ScanFilter.kt            # 文件类型过滤器
│           │
│           ├── parser/                      # 元数据解析层
│           │   ├── AudioParser.kt           # 音频文件解析器
│           │   ├── VideoParser.kt           # 视频文件解析器
│           │   └── ImageParser.kt           # 图片文件解析器
│           │
│           ├── database/                    # 数据库层
│           │   ├── MediaDatabase.kt         # Room 数据库定义
│           │   ├── MediaDao.kt              # 数据访问对象
│           │   ├── MediaEntity.kt           # 媒体文件实体
│           │   ├── StorageEntity.kt         # 存储卷实体
│           │   └── ScanTaskEntity.kt        # 扫描任务实体
│           │
│           └── provider/                    # 对外数据接口层
│               └── MediaProvider.kt         # ContentProvider 实现
│
├── gradle/
│   └── libs.versions.toml                  # 依赖版本目录
├── build.gradle.kts                         # 根构建脚本
├── settings.gradle.kts                      # 项目设置
└── DESIGN.md                                # 本文档
```

---

## 5. 核心模块详细设计

### 5.1 MediaScannerService - 核心服务

**职责**: 作为整个系统的协调中心，管理扫描生命周期，对外暴露 Binder 接口。

**设计要点**:

| 特性   | 方案                                         |
|------|--------------------------------------------|
| 进程   | 独立 APK，天然独立进程，扫描任务通过 Kotlin Coroutines (Dispatchers.IO) 异步执行 |
| 启动方式 | `startService()` + `bindService()`         |
| 前台服务 | `foregroundServiceType="dataSync"`，防止被系统杀死 |
| 生命周期 | 跟随 Application，开机自启动                       |

**AIDL 接口设计**:

```aidl
// IMediaScanner.aidl
interface IMediaScanner {
    // 扫描控制
    void scan(String path);
    void scanVolume(String volumeId);
    void cancelScan(String volumeId);
    void cancelAllScans();
    
    // 状态查询
    int getScanState();
    List<ScanTaskInfo> getActiveTasks();
    
    // 回调注册
    void registerCallback(IMediaScanCallback callback);
    void unregisterCallback(IMediaScanCallback callback);
}

// IMediaScanCallback.aidl
oneway interface IMediaScanCallback {
    void onScanStarted(String volumeId, String path);
    void onProgress(String volumeId, int current, int total, String currentFile);
    void onFileFound(String volumeId, String path, int mediaType);
    void onScanCompleted(String volumeId, int totalFiles);
    void onScanError(String volumeId, int errorCode, String message);
}
```

**内部状态机**:

```
                    ┌──────────┐
        ┌──────────►│  IDLE    │◄──────────┐
        │           └────┬─────┘           │
        │                │                 │
        │          startScan()       cancelAll()
        │                │                 │
        │                ▼                 │
        │           ┌──────────┐    ┌─────┴──────┐
        │    ┌──────│ SCANNING │───►│ CANCELLING │
        │    │      └────┬─────┘    └────────────┘
        │    │           │                
        │    │      scanComplete()       
        │    │           │                
        │    │           ▼                
        │    │      ┌──────────┐          
        │    └──────│  READY   │          
        │           └──────────┘          
        │                                 
        │           ┌──────────┐          
        └───────────│  ERROR   │          
                    └──────────┘          
```

### 5.2 StorageManager - 存储卷管理

**职责**: 监听系统存储事件，维护所有挂载存储卷的注册表，协调卷的挂载/卸载生命周期。

**数据模型**:

```kotlin
data class StorageVolume(
    val id: String,           // 唯一标识 "primary" | "usb:XXXX-XXXX"
    val uuid: String?,        // 卷身份标识（文件系统 UUID / 设备 serial），用于识别同设备重新插入
    val path: String,         // 挂载路径 "/storage/XXXX-XXXX"
    val label: String,        // 显示名称 "USB Drive"
    val type: VolumeType,     // 存储类型
    val state: VolumeState,   // 当前状态
    val totalBytes: Long,     // 总容量
    val availableBytes: Long, // 可用容量
    val lastScanTime: Long,   // 上次扫描时间戳
    val mediaCount: Int       // 已索引媒体数量
)

enum class VolumeType {
    INTERNAL,    // 内部存储
    USB,         // USB 外置存储
    SDCARD,      // SD 卡
    NETWORK      // 网络存储（未来支持）
}

enum class VolumeState {
    MOUNTED,     // 已挂载，等待扫描（新卷入口）
    ACTIVE,      // 已知卷重新插入，待增量扫描
    SCANNING,    // 正在扫描中
    READY,       // 扫描完成，数据可用
    REMOVED,     // 已移除（软删除，保留 scan_snapshot）
    FAILED       // 扫描失败
}
```

**卷发现策略抽象**: `VolumeDiscoveryService`

`StorageManager` 不直接依赖系统广播，而是通过 `VolumeDiscoveryService` 接口获取卷发现能力：

```kotlin
interface VolumeDiscoveryService {
    /** 发现当前所有已挂载的存储卷（开机/初始化调用） */
    suspend fun discoverVolumes(): List<DiscoveredVolume>

    /** 实时监听卷挂载/卸载/异常拔出事件（热插拔场景） */
    fun observeVolumeEvents(): Flow<VolumeEvent>

    /** 检查指定路径是否属于已挂载卷 */
    suspend fun resolveVolume(path: String): DiscoveredVolume?
}

data class DiscoveredVolume(
    val identity: VolumeIdentity,
    val mountPath: String,
    val label: String,
    val type: VolumeType,          // USB / SDCARD / INTERNAL / CUSTOM
    val totalBytes: Long,
    val availableBytes: Long,
    val isReadOnly: Boolean = false
)

data class VolumeIdentity(
    val uuid: String?,             // 文件系统 UUID
    val serial: String?,           // 设备序列号
    val customId: String? = null   // 供应商自定义标识
)

sealed class VolumeEvent {
    data class Mounted(val volume: DiscoveredVolume) : VolumeEvent()
    data class Unmounted(val identity: VolumeIdentity, val mountPath: String) : VolumeEvent()
    data class BadRemoval(val identity: VolumeIdentity, val mountPath: String) : VolumeEvent()
}
```

**默认实现**: `NativeAndroidVolumeDiscovery`
- 通过 `StorageManager.getStorageVolumes()` 发现卷
- 通过 `MEDIA_MOUNTED` / `MEDIA_UNMOUNTED` / `MEDIA_BAD_REMOVAL` 广播监听热插拔
- 广播接收逻辑内聚在实现类内部，不再暴露独立的 `StorageReceiver`

**供应商自定义实现**: `CustomPathVolumeDiscovery`
- 适用于 USB 挂载到非标准路径的场景（如 `/nmt/media_raw`、`/mnt/usb_storage` 等）
- 通过 `FileObserver` + 周期性轮询监控指定目录
- 配置路径: `/system/etc/media_scanner_volume_paths.json`
- 卷身份标识: 优先使用 `blkid` 获取文件系统 UUID，兜底使用挂载路径 hash

**StorageManager 依赖注入**:

```kotlin
class StorageManager(
    private val database: MediaDatabase,
    private val discoveryService: VolumeDiscoveryService  // ★ 注入抽象
) {
    suspend fun initialize() {
        // 开机恢复：通过抽象接口发现卷
        val discovered = discoveryService.discoverVolumes()
        reconcileVolumes(discovered)

        // 热插拔监听：通过抽象接口订阅事件
        discoveryService.observeVolumeEvents().collect { event ->
            when (event) {
                is VolumeEvent.Mounted -> onVolumeMounted(event.volume)
                is VolumeEvent.Unmounted -> onVolumeUnmounted(event.identity, event.mountPath)
                is VolumeEvent.BadRemoval -> onVolumeBadRemoval(event.identity, event.mountPath)
            }
        }
    }
}
```

**策略选择**: `MediaScannerApplication.onCreate()` 中根据配置文件选择实现：

```kotlin
private fun createDiscoveryService(): VolumeDiscoveryService {
    val configFile = File("/system/etc/media_scanner_volume_paths.json")
    return if (configFile.exists()) {
        CustomPathVolumeDiscovery(parseConfig(configFile))
    } else {
        NativeAndroidVolumeDiscovery(this, getSystemService(StorageManager::class.java))
    }
}
```

**关键设计决策**:

1. **Volume 持久化**: `StorageEntity` 存入 Room，开机后可恢复上次状态
2. **重复挂载去重**: 通过 `VolumeIdentity` (uuid + serial + mountPath) 组合唯一索引防止重复注册
3. **并发安全**: 使用 `ConcurrentHashMap` 管理 Volume 注册表
4. **策略可替换**: 卷发现通过 `VolumeDiscoveryService` 接口抽象，新增供应商只需实现接口，不改动核心逻辑
5. **FileObserver 兜底**: `CustomPathVolumeDiscovery` 以 FileObserver 为主、轮询为兜底，防止 inotify 事件遗漏

### 5.3 ScanScheduler - 扫描调度器

**职责**: 管理扫描任务队列，控制并发度，调度任务优先级。

**任务模型**:

```kotlin
data class ScanTask(
    val taskId: String,           // UUID
    val volumeId: String,         // 关联的存储卷
    val path: String,             // 扫描根路径
    val type: ScanType,           // 扫描类型
    val priority: Priority,       // 优先级
    val state: TaskState,         // 任务状态
    val totalFiles: Int,          // 总文件数
    val scannedFiles: Int,        // 已扫描文件数
    val newFilesFound: Int,       // 新发现文件数
    val currentFile: String?,     // 当前正在处理的文件
    val createdAt: Long,          // 创建时间
    val startedAt: Long?,         // 开始时间
    val completedAt: Long?,       // 完成时间
    val errorMessage: String?     // 错误信息
)

enum class ScanType {
    FULL_SCAN,          // 全量扫描
    INCREMENTAL_SCAN,   // 增量扫描
    DELETE_CLEANUP      // 清理已删除文件
}

enum class Priority(val value: Int) {
    USER_TRIGGERED(3),   // 用户主动触发，最高优先级
    VOLUME_MOUNTED(2),   // 新卷挂载自动触发
    BACKGROUND_SYNC(1)   // 后台维护扫描，最低优先级
}
```

**调度策略**:

```
                  ┌─────────────────┐
                  │   Task Queue     │
                  │  (PriorityQueue) │
                  └────────┬────────┘
                           │
                    ┌──────▼──────┐
                    │  Dispatcher  │
                    │ (时间片轮转)  │
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
              ▼            ▼            ▼
         ┌─────────┐ ┌─────────┐ ┌─────────┐
         │ 时间片1  │ │ 时间片2  │ │ 时间片3  │
         │ (卷A)   │ │ (卷B)   │ │ (卷A)   │
         └─────────┘ └─────────┘ └─────────┘
         
         调度配置:
         - 单 Worker 线程
         - 时间片粒度: 默认 5 秒
         - 时间片到期保存上下文，切换下一卷
         - 无锁设计，无 IO 竞争
```

**关键设计决策**:

1. **单 Worker 时间片轮转**: 唯一扫描线程按时间片（默认 5s）交替服务各卷，避免多线程 USB IO 竞争
2. **WorkManager 集成**: 利用 WorkManager 保证任务在进程被杀后恢复
3. **唯一性约束**: `ExistingWorkPolicy.KEEP` 确保同一 Volume 不会重复扫描
4. **进度通知**: Worker 通过 `setProgress()` 上报进度，UI 通过 LiveData/Flow 观察

### 5.4 ScannerEngine - 扫描引擎

**职责**: 核心扫描逻辑，组合 FileWalker、ScanFilter 和 Parser 完成文件发现和元数据提取。

**组件协作**:

```
┌─────────────────────────────────────────────────┐
│                  ScannerEngine                   │
│                                                 │
│  ┌──────────────┐    ┌──────────────┐           │
│  │  FileWalker  │───►│  ScanFilter  │           │
│  │  (遍历目录)   │    │  (扩展名过滤)  │           │
│  └──────────────┘    └──────┬───────┘           │
│                             │                   │
│                             ▼                   │
│                      ┌──────────────┐           │
│                      │   Dispatcher │           │
│                      │  (类型分发)   │           │
│                      └──┬───┬───┬───┘           │
│                         │   │   │               │
│              ┌──────────┘   │   └──────────┐    │
│              ▼              ▼              ▼    │
│       ┌──────────┐  ┌──────────┐  ┌──────────┐ │
│       │  Audio   │  │  Video   │  │  Image   │ │
│       │  Parser  │  │  Parser  │  │  Parser  │ │
│       └────┬─────┘  └────┬─────┘  └────┬─────┘ │
│            │             │             │       │
│            └──────┬──────┴──────┬──────┘       │
│                   │             │              │
│                   ▼             ▼              │
│            ┌──────────┐  ┌──────────┐         │
│            │  Media   │  │ Progress │         │
│            │  Entity  │  │ Callback │         │
│            └──────────┘  └──────────┘         │
└─────────────────────────────────────────────────┘
```

**FileWalker 设计**:

```kotlin
class FileWalker {
    /**
     * 遍历策略:
     * 1. 广度优先遍历 (BFS)
     * 2. 跳过隐藏文件/目录 (以 . 开头)
     * 3. 可配置排除目录列表 (.thumbnails, Android, cache 等)
     * 4. 可配置最大遍历深度
     * 5. 支持断点续扫 (记录 lastScannedPath)
     */
    fun walk(
        rootPath: String,
        excludeDirs: Set<String>,
        maxDepth: Int = -1,
        startFrom: String? = null
    ): Sequence<File>
}
```

**ScanFilter 设计**:

```kotlin
class ScanFilter {
    // 支持的扩展名映射
    private val extensionMap: Map<MediaType, Set<String>>

    // MIME 类型映射表
    private val mimeTypeMap: Map<String, String>

    /**
     * 过滤逻辑:
     * 1. 扩展名白名单匹配
     * 2. 文件大小最小值过滤 (跳过 0 字节文件)
     * 3. MIME 类型二次校验 (通过文件头 magic bytes)
     */
    fun filter(files: Sequence<File>, types: Set<MediaType>): Sequence<FilteredFile>
}
```

**关键设计决策**:

1. **Sequence 惰性求值**: 使用 Kotlin `Sequence` 避免一次性加载所有文件到内存
2. **文件头校验**: 不单纯信任扩展名，对关键类型做 magic bytes 校验防止误判
3. **批量写入**: 每 100 个文件批量写入数据库，减少 IO 次数
4. **可取消**: 遍历过程中检查 `isCancelled` 标志，支持中途取消

### 5.5 Parser - 元数据解析器

**职责**: 提取媒体文件的元数据信息，使用 Android 原生 API 和必要的第三方解析。

**解析能力矩阵**:

| 字段           | Audio                  | Video                  | Image                             |
|--------------|------------------------|------------------------|-----------------------------------|
| title        | MediaMetadataRetriever | MediaMetadataRetriever | -                                 |
| artist       | MediaMetadataRetriever | -                      | -                                 |
| album        | MediaMetadataRetriever | -                      | -                                 |
| duration     | MediaMetadataRetriever | MediaMetadataRetriever | -                                 |
| width/height | -                      | MediaMetadataRetriever | BitmapFactory.decodeBounds        |
| mimeType     | MediaMetadataRetriever | MediaMetadataRetriever | BitmapFactory.Options.outMimeType |
| bitrate      | MediaMetadataRetriever | MediaMetadataRetriever | -                                 |
| sampleRate   | MediaMetadataRetriever | -                      | -                                 |
| channels     | MediaMetadataRetriever | -                      | -                                 |
| frameRate    | -                      | MediaMetadataRetriever | -                                 |
| rotation     | -                      | MediaMetadataRetriever | ExifInterface                     |
| cover art    | MediaMetadataRetriever | MediaMetadataRetriever | -                                 |

**设计要点**:

1. **MediaMetadataRetriever 复用**: 创建对象池复用，避免频繁创建销毁
2. **解析超时**: 设置 5 秒超时，防止损坏文件导致卡死
3. **降级策略**: 解析失败时至少保留文件名和路径作为 title
4. **封面提取**: 音频封面保存到本地缓存目录，数据库记录 `cover_path`

### 5.6 Database - 数据库设计

**ER 图**:

```
┌─────────────────┐       ┌──────────────────────────────┐
│  storage_volume  │       │           media              │
├─────────────────┤       ├──────────────────────────────┤
│ PK  id          │──┐    │ PK  id                       │
│     volume_id   │  │    │ FK  volume_id ────────────────┘
│     path        │  │    │     path (UNIQUE INDEX)
│     label       │  │    │     name
│     type        │  │    │     size
│     state       │  │    │     media_type (INDEX)
│     total_bytes │  │    │     mime_type
│     avail_bytes │  │    │     duration
│     last_scan   │  │    │     width
│     media_count │  │    │     height
│     created_at  │  │    │     title
│     updated_at  │  │    │     artist
└─────────────────┘  │    │     album
                     │    │     genre
┌─────────────────┐  │    │     bitrate
│   scan_task     │  │    │     sample_rate
├─────────────────┤  │    │     channels
│ PK  id          │  │    │     cover_path
│     task_id     │  │    │     date_modified (INDEX)
│ FK  volume_id ──┘    │     created_at
│     root_path   │     │     updated_at
│     type        │     └──────────────────────────────┘
│     priority    │
│     state       │     索引策略:
│     total_files │     - path UNIQUE: 防止重复 + 快速查找
│     scanned     │     - media_type: 按类型过滤查询
│     new_found   │     - volume_id: 按卷查询 + USB拔出删除
│     current_file│     - date_modified: 排序 + 增量扫描比较
│     error_msg   │
│     created_at  │
│     started_at  │
│     completed_at│
└─────────────────┘
```

**SQL 表结构**:

```sql
-- 存储卷表
CREATE TABLE storage_volume (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    volume_id     TEXT NOT NULL UNIQUE,
    path          TEXT NOT NULL,
    label         TEXT,
    type          TEXT NOT NULL,       -- INTERNAL/USB/SDCARD/NETWORK
    state         TEXT NOT NULL,       -- MOUNTED/SCANNING/READY/REMOVED/FAILED
    total_bytes   INTEGER DEFAULT 0,
    avail_bytes   INTEGER DEFAULT 0,
    last_scan     INTEGER DEFAULT 0,
    media_count   INTEGER DEFAULT 0,
    created_at    INTEGER NOT NULL,
    updated_at    INTEGER NOT NULL
);

-- 媒体文件表 (核心)
CREATE TABLE media (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    volume_id     TEXT NOT NULL,
    path          TEXT NOT NULL UNIQUE,
    name          TEXT NOT NULL,
    size          INTEGER DEFAULT 0,
    media_type    TEXT NOT NULL,       -- audio/video/image
    mime_type     TEXT,
    duration      INTEGER DEFAULT 0,   -- ms
    width         INTEGER DEFAULT 0,
    height        INTEGER DEFAULT 0,
    title         TEXT,
    artist        TEXT,
    album         TEXT,
    genre         TEXT,
    bitrate       INTEGER DEFAULT 0,
    sample_rate   INTEGER DEFAULT 0,
    channels      INTEGER DEFAULT 0,
    cover_path    TEXT,
    date_modified INTEGER NOT NULL,
    created_at    INTEGER NOT NULL,
    updated_at    INTEGER NOT NULL,
    FOREIGN KEY (volume_id) REFERENCES storage_volume(volume_id)
);
CREATE INDEX idx_media_type ON media(media_type);
CREATE INDEX idx_media_volume ON media(volume_id);
CREATE INDEX idx_media_date ON media(date_modified);

-- 扫描任务表
CREATE TABLE scan_task (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id       TEXT NOT NULL UNIQUE,
    volume_id     TEXT NOT NULL,
    root_path     TEXT NOT NULL,
    type          TEXT NOT NULL,       -- FULL_SCAN/INCREMENTAL_SCAN/DELETE_CLEANUP
    priority      INTEGER DEFAULT 0,
    state         TEXT NOT NULL,       -- PENDING/RUNNING/PAUSED/COMPLETED/FAILED/CANCELLED
    total_files   INTEGER DEFAULT 0,
    scanned_files INTEGER DEFAULT 0,
    new_found     INTEGER DEFAULT 0,
    current_file  TEXT,
    error_msg     TEXT,
    created_at    INTEGER NOT NULL,
    started_at    INTEGER,
    completed_at  INTEGER,
    FOREIGN KEY (volume_id) REFERENCES storage_volume(volume_id)
);
```

**关键设计决策**:

1. **volume_id 强制绑定**: 所有 media 记录必须关联 volume_id，USB 拔出时按 volume_id 批量删除
2. **path 唯一索引**: 防止同一文件重复插入，同时加速路径查找
3. **date_modified 索引**: 增量扫描核心依赖，按时间戳快速比较文件变化
4. **Room + Flow**: DAO 返回 `Flow<List<T>>`，ContentProvider 观察数据库变化并通知客户端

### 5.7 MediaProvider - 内容提供者

**Authority**: `com.txzing.media.scanner.provider`

**URI 设计**:

| URI                              | 说明      | 查询参数                  |
|----------------------------------|---------|-----------------------|
| `content://.../media`            | 所有媒体    | type, volume_id, sort |
| `content://.../media/#`          | 按 ID 查询 | -                     |
| `content://.../media/type/audio` | 音频列表    | artist, album, sort   |
| `content://.../media/type/video` | 视频列表    | min_duration, sort    |
| `content://.../media/type/image` | 图片列表    | min_width, min_height |
| `content://.../storage`          | 存储卷列表   | state                 |
| `content://.../scan_tasks`       | 扫描任务列表  | state                 |

**查询示例 (播放器接入)**:

```kotlin
// 查询所有音频，按标题排序
val uri = Uri.parse("content://com.txzing.media.scanner.provider/media/type/audio")
val cursor = contentResolver.query(
    uri,
    arrayOf("_id", "path", "title", "artist", "album", "duration", "size"),
    null, null,
    "title ASC"
)

// 查询指定 U 盘的音乐
val uri = Uri.parse("content://com.txzing.media.scanner.provider/media/type/audio")
    .buildUpon()
    .appendQueryParameter("volume_id", "usb:ABCD-1234")
    .build()
```

**兼容性映射 (对齐 MediaStore)**:

| MediaProvider 字段 | MediaStore 对应字段                        | 说明      |
|------------------|----------------------------------------|---------|
| `_id`            | `MediaStore.Audio.Media._ID`           | 主键      |
| `path`           | `MediaStore.Audio.Media.DATA`          | 文件路径    |
| `title`          | `MediaStore.Audio.Media.TITLE`         | 标题      |
| `artist`         | `MediaStore.Audio.Media.ARTIST`        | 艺术家     |
| `album`          | `MediaStore.Audio.Media.ALBUM`         | 专辑      |
| `duration`       | `MediaStore.Audio.Media.DURATION`      | 时长(ms)  |
| `size`           | `MediaStore.Audio.Media.SIZE`          | 文件大小    |
| `mime_type`      | `MediaStore.Audio.Media.MIME_TYPE`     | MIME 类型 |
| `date_modified`  | `MediaStore.Audio.Media.DATE_MODIFIED` | 修改时间    |

> 播放器可通过简单的字段名映射完成从 MediaStore 到本 Provider 的迁移。

---

## 6. 核心流程设计

### 6.1 USB 插入扫描流程

```
用户插入 USB 设备
        │
        ▼
┌──────────────────┐
│  vold 检测设备    │
│  挂载文件系统     │
└────────┬─────────┘
         │
         │ 发送 MEDIA_MOUNTED 广播
         ▼
┌──────────────────┐
│ VolumeDiscovery   │
│ Service           │
│ (observeVolume-   │
│  Events)          │
└────────┬─────────┘
         │
         │ VolumeEvent.Mounted
         ▼
┌──────────────────┐
│  StorageManager   │
│  1. 解析挂载路径   │
│  2. 创建 Volume    │
│  3. 持久化到 DB    │
│  4. 状态→MOUNTED   │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  ScanScheduler    │
│  1. 检查 Volume 身份│
│     (UUID/serial)  │
│  2. 已知卷→增量扫描 │
│     新卷→全量扫描  │
│  3. 入队           │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  ScanWorker       │
│  (WorkManager)    │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  ScannerEngine    │
│  ┌─────────────┐  │
│  │ FileWalker   │  │  遍历目录树
│  │ (BFS,跳过隐藏) │──┼─ 逐文件
│  └──────┬──────┘  │
│         ▼         │
│  ┌─────────────┐  │
│  │ ScanFilter   │  │  扩展名 + MIME 过滤
│  │ (白名单匹配)  │──┼─ 仅媒体文件
│  └──────┬──────┘  │
│         ▼         │
│  ┌─────────────┐  │
│  │   Parser     │  │  提取元数据
│  │ (Audio/Video/│──┼─ MediaEntity
│  │  Image)      │  │
│  └──────┬──────┘  │
│         ▼         │
│  ┌─────────────┐  │
│  │  MediaDao    │  │  批量写入 DB
│  │  (每100条)   │  │
│  └──────┬──────┘  │
│         ▼         │
│  ┌─────────────┐  │
│  │ Progress     │  │  通知进度
│  │ Callback     │  │
│  └─────────────┘  │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  扫描完成         │
│  1. 更新 Volume   │
│     state→READY   │
│  2. 通知客户端     │
│     onScanCompleted│
└──────────────────┘
```

### 6.2 USB 拔出处理流程

```
用户拔出 USB 设备
        │
        ▼
┌──────────────────┐
│  vold 检测拔出    │
└────────┬─────────┘
         │
         │ 发送 MEDIA_UNMOUNTED 广播
         ▼
┌──────────────────┐
│ VolumeDiscovery   │
│ Service           │
│ → VolumeEvent     │
│   .Unmounted      │
└────────┬─────────┘
         │
         ▼
┌──────────────────────────────┐
│  StorageManager               │
│  1. 根据 path 查找 Volume     │
│  2. 停止该 Volume 的 ScanTask  │
└────────┬─────────────────────┘
         │
         ▼
┌──────────────────────────────┐
│  ScanScheduler                │
│  WorkManager.cancelUniqueWork │
│  (volume_id)                  │
└────────┬─────────────────────┘
         │
         ▼
┌──────────────────────────────┐
│  MediaDao                     │
│  DELETE FROM media            │
│  WHERE volume_id = ?          │
└────────┬─────────────────────┘
         │
         ▼
┌──────────────────────────────┐
│  StorageManager               │
│  1. 更新 Volume state→REMOVED │
│     (软删除，保留 scan_snapshot)│
│  2. 通知 UI 刷新              │
└──────────────────────────────┘
```

**关键要点**: 拔出时必须在同一个数据库事务中完成 `停止任务 → 删除媒体数据 → Volume 软删除（保留 scan_snapshot）`，支撑重新插入时的增量恢复。
，保证数据一致性。

### 6.3 开机恢复流程

```
设备开机
        │
        ▼
┌──────────────────┐
│  BOOT_COMPLETED   │
│  广播             │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  BootReceiver     │
│  onReceive()      │
└────────┬─────────┘
         │
         ▼
┌──────────────────────────────────┐
│  MediaScannerApplication          │
│  onCreate()                      │
│  1. 初始化 Room Database         │
│  2. 选择 VolumeDiscoveryService  │
│     实现（标准/CustomPath）      │
│  3. 注入 StorageManager          │
│  4. 初始化 ScanScheduler         │
└────────┬─────────────────────────┘
         │
         ▼
┌──────────────────────────────────┐
│  StorageManager.initialize()      │
│  1. 通过 VolumeDiscoveryService  │
│     .discoverVolumes() 发现卷    │
│  2. 与 DB 中记录的 Volume 对比    │
│     - 新卷: 创建并触发扫描        │
│     - 已存在: 恢复状态 ACTIVE     │
│     - 已拔出: 标记 REMOVED        │
└────────┬─────────────────────────┘
         │
         ▼
┌──────────────────────────────────┐
│  ScanScheduler                    │
│  1. 查询 DB 中未完成的 ScanTask   │
│     (state = PENDING/RUNNING)     │
│  2. 对已挂载的 Volume 恢复扫描    │
│  3. 对已拔出的 Volume 清理任务    │
└──────────────────────────────────┘
```

### 6.4 增量扫描流程

增量扫描是性能优化的核心，避免每次都对整个存储卷全量扫描。

```
触发增量扫描 (定时/手动)
        │
        ▼
┌──────────────────────────────────┐
│  1. 遍历目录获取当前文件列表       │
│     currentFiles: Map<path, File> │
└────────┬─────────────────────────┘
         │
         ▼
┌──────────────────────────────────┐
│  2. 从 DB 加载已有记录            │
│     existingMedia: Map<path,      │
│       MediaEntity>                │
└────────┬─────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────┐
│  3. 三方比对                                  │
│                                              │
│  ┌─────────────────────────────────────┐     │
│  │ 当前存在 && DB 不存在                  │     │
│  │ → 新文件 → 解析 + 插入               │     │
│  ├─────────────────────────────────────┤     │
│  │ 当前存在 && DB 存在 && mtime 不同     │     │
│  │ → 文件修改 → 重新解析 + 更新         │     │
│  ├─────────────────────────────────────┤     │
│  │ 当前存在 && DB 存在 && mtime 相同     │     │
│  │ → 无变化 → 跳过 (占大多数)           │     │
│  ├─────────────────────────────────────┤     │
│  │ 当前不存在 && DB 存在                │     │
│  │ → 文件已删除 → 从 DB 删除            │     │
│  └─────────────────────────────────────┘     │
└──────────────────────────────────────────────┘
```

**增量扫描触发时机**:

| 时机     | 类型              | 说明                    |
|--------|-----------------|-----------------------|
| 应用启动   | BACKGROUND_SYNC | 检查所有 READY 状态的 Volume |
| 定时任务   | BACKGROUND_SYNC | 每 30 分钟执行一次           |
| 用户手动刷新 | USER_TRIGGERED  | 用户在 UI 中点击刷新按钮        |

---

## 7. 性能与优化策略

### 7.1 线程模型

```
┌─────────────────────────────────────────────┐
│                 Main Thread                  │
│  UI / BroadcastReceiver / Binder Callback   │
└──────────────────┬──────────────────────────┘
                   │
     ┌─────────────┼─────────────┐
     │             │             │
     ▼             ▼             ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ Scan Worker  │  │ Room Query   │  │ AIDL Callback│
│ (单线程, IO) │  │ (Dispatchers │  │ (Dispatchers │
│ 时间片轮转    │  │  .IO)        │  │  .Main)       │
└──────────────┘  └──────────────┘  └──────────────┘
```

### 7.2 优化措施

| 优化项                           | 方案                                                 | 预期效果         |
|-------------------------------|----------------------------------------------------|--------------|
| **惰性遍历**                      | Kotlin Sequence 流式处理，避免全量加载                        | 内存峰值降低 70%   |
| **批量写入**                      | 每 100 条 media 记录批量 insert                          | IO 次数减少 99%  |
| **事务包装**                      | 批量操作放在 Room `@Transaction` 中                       | 写入速度提升 10x   |
| **索引优化**                      | path(UNIQUE), volume_id, media_type, date_modified | 查询速度提升 50x   |
| **MediaMetadataRetriever 池化** | 对象池复用，避免重复创建                                       | 解析速度提升 30%   |
| **跳过无关目录**                    | 预定义排除列表 (cache, thumbnails, Android 等)             | 遍历文件数减少 40%  |
| **单线程时间片轮转**               | 唯一扫描线程按时间片交替扫描多卷，保存/恢复上下文 | 消除 IO 竞争，无锁设计 |
| **进度通知节流**                    | 每 500ms 最多通知一次进度                                   | 减少 IPC 开销    |

### 7.3 大容量设备策略

对于超过 10 万文件的存储设备:

1. **分批扫描**: 每 5000 个文件为一个批次，批次间暂停 100ms 释放 IO
2. **前台服务保活**: 确保长时间扫描不被系统杀死
3. **优先级降级**: 大容量扫描使用 BACKGROUND_SYNC 优先级
4. **用户可暂停**: 通过 AIDL 接口支持暂停/恢复

---

## 8. 安全与权限设计

### 8.1 权限清单

```xml
<!-- 基础存储权限 -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" /><uses-permission
android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

    <!-- Android 13+ 细粒度媒体权限 -->
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" /><uses-permission
android:name="android.permission.READ_MEDIA_VIDEO" /><uses-permission
android:name="android.permission.READ_MEDIA_IMAGES" />

    <!-- 系统级权限 (priv-app) -->
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />

    <!-- 开机启动 -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

    <!-- 前台服务 -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" /><uses-permission
android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
```

### 8.2 部署配置

```
/system/priv-app/MediaScanner/
├── MediaScanner.apk
└── permissions_com.txzing.media.scanner.xml
```

**priv-app 特权配置**:

```xml
<!-- /system/etc/permissions/privapp-permissions-com.txzing.media.scanner.xml -->
<permissions>
    <privapp-permissions package="com.txzing.media.scanner">
        <permission name="android.permission.MANAGE_EXTERNAL_STORAGE" />
        <permission name="android.permission.READ_EXTERNAL_STORAGE" />
        <permission name="android.permission.WRITE_EXTERNAL_STORAGE" />
        <permission name="android.permission.RECEIVE_BOOT_COMPLETED" />
    </privapp-permissions>
</permissions>
```

### 8.3 访问控制

| 接口              | 权限保护           | 说明            |
|-----------------|----------------|---------------|
| ContentProvider | `signatureOrSystem` + 包名白名单 | 系统签名应用 + 白名单授权应用可访问 |
| AIDL Service    | `signatureOrSystem` + 包名白名单 | 系统签名应用 + 白名单授权应用可绑定 |
| 文件访问            | 系统文件权限         | priv-app 自动获得 |
| 白名单配置       | `/system/etc/media_scanner_allowed_packages.xml` | ROM 编译时固化，OTA 更新 |

---

## 9. 错误处理与容错

### 9.1 异常分级

| 级别        | 场景             | 处理策略                  |
|-----------|----------------|-----------------------|
| **FATAL** | 数据库损坏、磁盘满      | 通知用户，停止服务             |
| **ERROR** | 存储卷不可读、权限不足    | 标记 Volume FAILED，跳过该卷 |
| **WARN**  | 单个文件解析失败、文件不存在 | 跳过该文件，继续扫描            |
| **INFO**  | 不支持的格式、0 字节文件  | 静默跳过                  |

### 9.2 容错机制

```
┌──────────────────────────────────────┐
│          ScannerEngine.scan()        │
│                                      │
│  try {                               │
│      for (file in filteredFiles) {   │
│          try {                       │
│              parseFile(file)  ◄──────┼── 单文件失败不影响整体
│          } catch (e: ParseError) {   │
│              logWarning(file, e)     │
│              continue                │
│          }                           │
│      }                               │
│  } catch (e: FatalError) {          │
│      markTaskFailed()                │
│      notifyError()                   │
│  }                                   │
└──────────────────────────────────────┘
```

### 9.3 恢复策略

| 场景       | 恢复方式                                          |
|----------|-----------------------------------------------|
| 进程被杀     | WorkManager 自动重试，最多 3 次                       |
| 扫描中断     | 记录 `lastScannedPath`，恢复时从此路径继续                |
| 数据库损坏    | `fallbackToDestructiveMigration()` 重建数据库，重新扫描 |
| USB 异常拔出 | 清理该 Volume 数据，不影响其他卷                          |

---

## 10. 实施计划

### Phase 1 - 基础设施 (预计 2-3 天)

- [ ] Android 工程创建与配置
- [ ] gradle 依赖管理 (Room, WorkManager, ExifInterface)
- [ ] AndroidManifest 权限与组件声明
- [ ] AIDL 接口定义 (IMediaScanner, IMediaScanCallback)
- [ ] MediaScannerApplication 初始化框架
- [ ] MediaScannerService 骨架实现
- [ ] StorageReceiver / BootReceiver 骨架

### Phase 2 - 存储卷管理 (预计 2-3 天)

- [ ] StorageVolume 数据模型
- [ ] VolumeRepository 卷发现逻辑
- [ ] StorageManager 卷生命周期管理
- [ ] 广播接收器完整实现
- [ ] 卷状态持久化 (StorageEntity)

### Phase 3 - 扫描引擎 (预计 3-4 天)

- [ ] FileWalker 目录遍历
- [ ] ScanFilter 文件过滤
- [ ] AudioParser 音频元数据提取
- [ ] VideoParser 视频元数据提取
- [ ] ImageParser 图片元数据提取
- [ ] ScannerEngine 组装联调

### Phase 4 - 数据库与 API (预计 2-3 天)

- [ ] Room 数据库完整 Schema
- [ ] MediaDao 完整 CRUD 实现
- [ ] ScanTask 调度与管理
- [ ] ScanWorker WorkManager 集成
- [ ] MediaProvider ContentProvider 实现
- [ ] 对外 API 联调测试

### Phase 5 - 高级特性 (预计 3-4 天)

- [ ] 增量扫描逻辑实现
- [ ] 热插拔完整流程联调
- [ ] 开机恢复逻辑
- [ ] 性能优化 (批量写入, 对象池, 索引)
- [ ] 错误处理与日志完善
- [ ] 端到端测试

---

## 11. 验收标准

### 11.1 功能验收

| 编号   | 验收项        | 标准                                   |
|------|------------|--------------------------------------|
| F-01 | USB 插入自动扫描 | 插入 U 盘后 3 秒内开始扫描                     |
| F-02 | 多卷并发       | 2 个 USB 同时插入，各自独立扫描互不影响              |
| F-03 | USB 拔出数据清理 | 拔出后该卷媒体数据在 5 秒内清除                    |
| F-04 | 开机恢复       | 重启后自动恢复上次扫描状态                        |
| F-05 | 增量更新       | 文件增删改后增量扫描正确更新数据库                    |
| F-06 | 媒体查询       | 播放器通过 ContentProvider 可查询完整媒体列表      |
| F-07 | 元数据准确性     | 音频 title/artist/album/duration 与文件一致 |

### 11.2 性能验收

| 编号   | 验收项          | 标准                  |
|------|--------------|---------------------|
| P-01 | 1000 文件扫描时间  | < 30 秒              |
| P-02 | 10000 文件扫描时间 | < 5 分钟              |
| P-03 | 内存峰值         | < 50MB              |
| P-04 | 扫描时 UI 流畅度   | 不出现 ANR，帧率 > 30fps  |
| P-05 | 数据库查询响应      | 10000 条记录查询 < 200ms |

### 11.3 稳定性验收

| 编号   | 验收项      | 标准                          |
|------|----------|-----------------------------|
| S-01 | 连续插拔     | 100 次插拔无崩溃、无数据残留            |
| S-02 | 损坏文件处理   | 目录中包含损坏文件不影响扫描              |
| S-03 | 进程被杀恢复   | kill 进程后 WorkManager 自动恢复扫描 |
| S-04 | 磁盘满处理    | 磁盘空间不足时优雅降级                 |
| S-05 | 24 小时稳定性 | Monkey 测试 24 小时无崩溃          |

---

## 12. 附录

### A. 依赖清单

| 依赖                                     | 版本     | 用途                 |
|----------------------------------------|--------|--------------------|
| `androidx.room:room-runtime`           | 2.6.1  | ORM 数据库            |
| `androidx.room:room-ktx`               | 2.6.1  | Room Kotlin 扩展     |
| `androidx.room:room-compiler`          | 2.6.1  | Room 编译时注解处理       |
| `androidx.work:work-runtime-ktx`       | 2.9.0  | 后台任务调度             |
| `androidx.exifinterface:exifinterface` | 1.3.7  | 图片 EXIF 解析         |
| `androidx.appcompat:appcompat`         | 1.7.1  | 基础兼容库              |
| `androidx.core:core-ktx`               | 1.19.0 | Kotlin 扩展          |
| `com.google.android.material:material` | 1.14.0 | Material Design 组件 |

### B. 术语表

| 术语                         | 说明                                |
|----------------------------|-----------------------------------|
| **Volume**                 | 存储卷，指一个挂载的文件系统                    |
| **vold**                   | Android Volume Daemon，管理存储设备挂载/卸载 |
| **priv-app**               | 特权系统应用，拥有系统级权限                    |
| **MediaMetadataRetriever** | Android 原生元数据提取 API               |
| **BFS**                    | 广度优先搜索，目录遍历策略                     |
| **ANR**                    | Application Not Responding，应用无响应  |

### C. 参考资料

- [Android Storage Access Framework](https://developer.android.com/training/data-storage)
- [Room Database Guide](https://developer.android.com/training/data-storage/room)
- [WorkManager Guide](https://developer.android.com/topic/libraries/architecture/workmanager)
- [MediaMetadataRetriever API](https://developer.android.com/reference/android/media/MediaMetadataRetriever)
- [Android Automotive OS](https://developer.android.com/training/cars)

---

> **文档维护**: 本设计文档随项目迭代持续更新。架构变更需同步修改本文档。
