# ReadYou 项目文档

## 项目概述

**ReadYou** 是一个基于 Material Design 3 (Material You) 风格的 Android RSS 阅读器应用。该项目采用现代 Android 开发技术栈，遵循 Clean Architecture + MVVM 架构模式，提供优雅的用户界面和强大的 RSS 订阅功能。

### 核心特性

- **多账户支持**：支持本地 RSS、Fever、Google Reader API（兼容 FreshRSS、Inoreader、Feedly）等多种账户类型
- **文章管理**：订阅、导入/导出 OPML、文章搜索、收藏、稍后阅读等功能
- **全文解析**：使用 Readability4j 实现网页正文提取
- **后台同步**：基于 WorkManager 的定时同步，支持 WiFi 和充电限制
- **Material You 设计**：完全采用 Jetpack Compose 和 Material 3 组件库
- **响应式数据流**：全面使用 Flow/StateFlow 实现数据驱动 UI
- **分页加载**：使用 Paging 3 实现高效的文章列表加载

### 技术栈

- **语言**：Kotlin 2.2.0
- **UI 框架**：Jetpack Compose + Material 3
- **架构模式**：Clean Architecture + MVVM
- **依赖注入**：Hilt
- **数据库**：Room 2.7.2
- **网络请求**：Retrofit 2.11.0 + OkHttp 5.0.0
- **异步编程**：Kotlin Coroutines + Flow
- **后台任务**：WorkManager
- **图片加载**：Coil 2.5.0
- **日志**：Timber 5.0.1

### 项目信息

- **包名**：`me.ash.reader`
- **最低 SDK**：26 (Android 8.0)
- **目标 SDK**：34 (Android 14)
- **编译 SDK**：36
- **当前版本**：0.15.3 (versionCode: 44)
- **许可证**：GNU GPL v3.0

---

## 项目结构

```
app/src/main/java/me/ash/reader/
├── domain/                    # 领域层（业务逻辑）
│   ├── data/                 # 用例（Use Cases）
│   │   ├── ArticlePagingListUseCase.kt
│   │   ├── FilterStateUseCase.kt
│   │   └── ...
│   ├── model/                # 数据模型
│   │   ├── account/          # 账户相关模型
│   │   ├── article/          # 文章相关模型
│   │   ├── feed/             # Feed 相关模型
│   │   ├── group/            # 分组相关模型
│   │   └── ...
│   ├── repository/           # 数据访问接口（Room DAO）
│   │   ├── ArticleDao.kt
│   │   ├── FeedDao.kt
│   │   ├── GroupDao.kt
│   │   └── AccountDao.kt
│   └── service/              # 业务服务
│       ├── AccountService.kt
│       ├── RssService.kt
│       ├── LocalRssService.kt
│       ├── FeverRssService.kt
│       ├── GoogleReaderRssService.kt
│       ├── OpmlService.kt
│       └── SyncWorker.kt
├── infrastructure/           # 基础设施层（数据与外部依赖）
│   ├── android/              # Android 平台相关
│   │   ├── AndroidApp.kt     # Application 类
│   │   └── MainActivity.kt
│   ├── db/                   # 数据库实现
│   │   └── AndroidDatabase.kt
│   ├── di/                   # 依赖注入模块
│   │   ├── DatabaseModule.kt
│   │   ├── UseCaseModule.kt
│   │   ├── OkHttpClientModule.kt
│   │   └── ...
│   ├── preference/           # 配置管理
│   │   └── SettingsProvider.kt
│   ├── rss/                  # RSS 解析相关
│   │   ├── RssHelper.kt
│   │   ├── Favicon.kt
│   │   └── ...
│   ├── rss/provider/         # RSS 服务提供者
│   │   ├── greader/          # Google Reader API
│   │   └── fever/            # Fever API
│   └── ...
└── ui/                       # 表现层（UI）
    ├── page/                 # 页面
    │   ├── home/             # 首页
    │   │   ├── feeds/        # Feed 管理
    │   │   ├── flow/         # 文章流
    │   │   └── reading/      # 阅读页面
    │   ├── settings/         # 设置页面
    │   ├── startup/          # 启动页面
    │   └── ...
    ├── component/            # 可复用组件
    │   ├── base/             # 基础组件
    │   ├── reader/           # 阅读器组件
    │   └── ...
    ├── theme/                # 主题
    │   └── palette/          # 调色板
    └── ext/                  # 扩展函数
```

---

## 架构分层

### 1. 表现层（UI Layer）

负责展示数据和用户交互，使用 Jetpack Compose 构建。

**核心组件：**
- **Compose 组件**：所有 UI 使用 Compose 声明式构建
- **ViewModels**：管理 UI 状态和业务逻辑
- **Navigation**：使用 Navigation 3 实现类型安全的路由

**主要页面：**
- `ui.page.home.feeds` - Feed 订阅管理
- `ui.page.home.flow` - 文章流列表
- `ui.page.home.reading` - 文章阅读
- `ui.page.settings` - 设置页面

### 2. 领域层（Domain Layer）

包含业务逻辑和用例，不依赖任何框架。

**核心组件：**
- **Use Cases**：封装业务用例，如 `ArticlePagingListUseCase`
- **Services**：业务服务，如 `AccountService`、`RssService`
- **Repository Interfaces**：数据访问接口（Room DAO）
- **Models**：领域模型，如 `Account`、`Article`、`Feed`、`Group`

**关键服务：**
- `RssService` - RSS 服务门面，根据账户类型路由到具体服务
- `AccountService` - 账户管理服务
- `OpmlService` - OPML 导入/导出服务
- `SyncWorker` - 后台同步任务

### 3. 基础设施层（Infrastructure Layer）

提供数据访问、网络请求、依赖注入等基础设施支持。

**核心组件：**
- **Room Database**：`AndroidDatabase` - 本地数据库
- **Retrofit**：网络请求 API
- **OkHttp**：HTTP 客户端
- **DataStore**：配置存储
- **Hilt**：依赖注入框架
- **Coil**：图片加载

**关键模块：**
- `infrastructure.di` - Hilt 依赖注入模块
- `infrastructure.db` - Room 数据库实现
- `infrastructure.rss` - RSS 解析实现
- `infrastructure.preference` - 配置管理

---

## 数据模型

### 核心实体

#### Account（账户）
- 支持多种账户类型：Local、Fever、GoogleReader、FreshRSS、Feedly、Inoreader
- 包含同步配置：同步间隔、WiFi 限制、充电限制、归档策略
- 存储安全密钥（DES 加密）

#### Article（文章）
- 包含标题、内容、链接、图片、作者、日期
- 状态字段：`isUnread`（未读）、`isStarred`（收藏）、`isReadLater`（稍后阅读）
- 外键关联到 Feed 表（级联删除）

#### Feed（订阅源）
- 包含名称、图标、URL、分组 ID、账户 ID
- 配置：`isNotification`（通知）、`isFullContent`（全文）、`isBrowser`（浏览器打开）
- 外键关联到 Group 表（级联删除）

#### Group（分组）
- 用于组织 Feed

---

## 构建和运行

### 前置条件

- **Android Studio**（最新版本）
- **JDK 11** 或更高版本
- **Android SDK**（API 26+）

### 构建步骤

1. **克隆仓库**

```shell
git clone https://github.com/ReadYouApp/ReadYou.git
cd ReadYou
```

2. **使用 Android Studio 打开项目**

   - 启动 Android Studio
   - 选择 "Open" 或 "File > Open"
   - 选择项目根目录

3. **构建和运行**

   - 在 Android Studio 中，点击 `▶ Run` 按钮
   - 或使用 Gradle 命令：

```shell
# Debug 构建
./gradlew assembleDebug

# Release 构建
./gradlew assembleRelease

# 安装到设备
./gradlew installDebug
```

4. **构建 APK**

```shell
# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK（需要签名）
./gradlew assembleRelease

# APK 输出位置：app/build/outputs/apk/
```

### 签名配置

Release 版本需要签名配置。在 `signature/keystore.properties` 中配置：

```properties
storeFile=path/to/keystore.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

### 测试

```shell
# 运行单元测试
./gradlew test

# 运行 Android 测试
./gradlew connectedAndroidTest
```

### 清理

```shell
./gradlew clean
```

---

## 开发约定

### 代码风格

- **Kotlin**：遵循 Kotlin 官方编码规范
- **命名**：使用驼峰命名法（camelCase）
- **文件组织**：按功能模块组织，遵循 Clean Architecture 分层
- **注释**：使用 KDoc 格式注释公共 API

### 架构原则

- **依赖方向**：UI → Domain → Infrastructure，依赖方向单向
- **单一职责**：每个类/函数只做一件事
- **开闭原则**：对扩展开放，对修改关闭
- **接口隔离**：使用接口定义契约

### Git 提交规范

- 提交信息清晰简洁，描述"为什么"而不是"是什么"
- 使用英文提交信息
- 提交前确保代码通过编译和基本测试

### 依赖管理

- 所有依赖版本在 `gradle/libs.versions.toml` 中统一管理
- 添加新依赖时，先在 `libs.versions.toml` 中定义版本，然后在 `build.gradle.kts` 中引用

### 数据流

- **UI → ViewModel**：用户交互触发 ViewModel 方法
- **ViewModel → UseCase**：ViewModel 调用 UseCase 执行业务逻辑
- **UseCase → Repository**：UseCase 通过 Repository 接口访问数据
- **Repository → Database/Network**：Repository 访问数据库或网络
- **数据返回**：使用 Flow/StateFlow 实现响应式数据流

### 异步编程

- 使用 Kotlin Coroutines 处理异步操作
- 使用 Flow/StateFlow 处理数据流
- IO 操作在 IO Dispatcher 中执行
- UI 操作在 Main Dispatcher 中执行

### 依赖注入

- 使用 Hilt 进行依赖注入
- 每个模块提供单例服务
- 使用 `@Inject` 注解标记依赖
- 使用 `@Module` 和 `@Provides` 提供依赖

---

## 关键技术点

### 1. 多账户支持

- 每个账户独立存储数据（通过 `accountId` 隔离）
- 账户切换使用 DataStore 存储 `currentAccountId`
- `RssService` 根据账户类型路由到具体服务实现

### 2. 分页加载

- 使用 Paging 3 实现无限滚动
- `ArticleDao` 返回 `PagingSource`
- `ArticlePagingListUseCase` 管理 Pager
- 动态响应筛选和搜索条件变化

### 3. 响应式数据流

- 大量使用 Flow/StateFlow
- Room 查询返回 Flow
- 设置使用 DataStore Flow
- 数据变化自动更新 UI

### 4. 后台同步

- 使用 WorkManager 实现定时同步
- `SyncWorker` 根据账户配置决定同步策略
- 支持 WiFi 限制、充电限制等条件

### 5. 主题系统

- 支持动态颜色（Material 3）
- 自定义调色板系统
- 支持多种色彩空间（CIELAB、CIEXYZ、JzAzBz、OKLab）

### 6. 安全性

- 账户密钥使用 DES 加密存储
- 支持多种安全密钥类型（Local、Fever、GoogleReader、FreshRSS）

---

## 常见任务

### 添加新的 RSS 服务

1. 在 `domain/model/account/AccountType.kt` 中添加新的账户类型
2. 创建新的服务类继承 `AbstractRssRepository`
3. 在 `RssService` 中添加路由逻辑
4. 在 `infrastructure/rss/provider/` 中实现 API 接口

### 添加新的配置项

1. 在 `infrastructure/preference/` 中创建新的 Preference 类
2. 在 `SettingsProvider` 中注册新的配置
3. 在 UI 中添加设置界面

### 修改数据库

1. 在 `AndroidDatabase` 中更新版本号
2. 创建新的 Migration 类或使用 AutoMigration
3. 更新 Entity 类
4. 运行测试确保迁移正确

### 添加新的页面

1. 在 `ui/page/` 中创建新的页面目录
2. 创建 Composable 组件
3. 创建 ViewModel（如果需要）
4. 在 Navigation 中添加路由

---

## 参考资源

### 官方文档

- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material 3](https://m3.material.io/)
- [Hilt](https://dagger.dev/hilt/)
- [Room](https://developer.android.com/training/data-storage/room)
- [Paging 3](https://developer.android.com/topic/libraries/architecture/paging/v3-overview)
- [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)

### 项目链接

- [GitHub 仓库](https://github.com/ReadYouApp/ReadYou)
- [Telegram 频道](https://t.me/ReadYouApp)
- [Figma 设计](https://www.figma.com/file/ViBW8GbUgkTMmK6a80h8X1/Read-You)
- [Weblate 翻译](https://hosted.weblate.org/engage/readyou/)

### 开源项目致谢

- [MusicYou](https://github.com/Kyant0/MusicYou) - 设计灵感
- [ParseRSS](https://github.com/muhrifqii/ParseRSS)
- [Readability4J](https://github.com/dankito/Readability4J)
- [opml-parser](https://github.com/mdewilde/opml-parser)
- [compose-html](https://github.com/ireward/compose-html)
- [Rome](https://github.com/rometools/rome)
- [Feeder](https://gitlab.com/spacecowboy/Feeder)
- [Seal](https://github.com/JunkFood02/Seal)

---

## 注意事项

1. **NDK 架构**：当前配置仅支持 `arm64-v8a` 架构
2. **资源配置**：开发模式下可以启用 `resourceConfigurations += "zh"` 以减少资源合并时间
3. **签名配置**：Release 构建需要在 `signature/keystore.properties` 中配置签名信息
4. **网络请求**：应用允许明文流量（`android:usesCleartextTraffic="true"`）
5. **权限**：应用需要 INTERNET、POST_NOTIFICATIONS 和 WRITE_EXTERNAL_STORAGE 权限

---

## 许可证

GNU GPL v3.0 © [Read You](https://github.com/ReadYouApp/ReadYou/blob/main/LICENSE)