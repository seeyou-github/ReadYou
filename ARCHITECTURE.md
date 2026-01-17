# ReadYou 项目架构文档

## 项目概述

ReadYou 是一个基于 Material Design 3 的 Android RSS 阅读器应用，采用 **Clean Architecture** + **MVVM** 架构模式。

---

## 架构分层

```
┌─────────────────────────────────────────────────────────────────┐
│                          UI Layer                                │
│                    (Presentation Layer)                          │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│   │   Compose    │  │  ViewModels  │  │  Navigation  │       │
│   │   Components │  │              │  │              │       │
│   └──────────────┘  └──────────────┘  └──────────────┘       │
└─────────────────────────────────────────────────────────────────┘
                              ↓ Flow/StateFlow
┌─────────────────────────────────────────────────────────────────┐
│                      Domain Layer                                │
│                      (Business Logic)                           │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│   │   Use Cases  │  │  Services    │  │   Models     │       │
│   │              │  │              │  │              │       │
│   └──────────────┘  └──────────────┘  └──────────────┘       │
│   ┌──────────────────────────────────────────────┐            │
│   │          Repository Interfaces               │            │
│   │  (ArticleDao, FeedDao, GroupDao, AccountDao) │            │
│   └──────────────────────────────────────────────┘            │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                 Infrastructure Layer                            │
│                     (Data & External)                           │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│   │     Room     │  │  Retrofit    │  │  DataStore   │       │
│   │   Database   │  │    Network   │  │ Preferences  │       │
│   └──────────────┘  └──────────────┘  └──────────────┘       │
│   ┌──────────────┐  ┌──────────────┐                        │
│   │     Hilt     │  │   RSS/Feed   │                        │
│   │      DI      │  │   Services   │                        │
│   └──────────────┘  └──────────────┘                        │
└─────────────────────────────────────────────────────────────────┘
```

---

## Package 模块说明

### 1. `me.ash.reader.domain` - 领域层

#### 1.1 `domain.model` - 数据模型

**核心模型类：**

- **`Account`** (`domain/model/account/Account.kt:15`)
  - 账户实体，支持多种账户类型（Local, Fever, GoogleReader, FreshRSS, Feedly, Inoreader）
  - 包含同步配置：同步间隔、WiFi限制、充电限制、归档策略等
  - 存储账户的安全密钥（DES加密）

- **`AccountType`** (`domain/model/account/AccountType.kt`)
  - 枚举类型，定义支持的账户类型

- **`Article`** (`domain/model/article/Article.kt:20`)
  - 文章实体，包含标题、内容、链接、图片、作者、日期等
  - 状态字段：isUnread（未读）、isStarred（收藏）、isReadLater（稍后阅读）
  - 外键关联到Feed表（级联删除）

- **`ArticleWithFeed`** (`domain/model/article/ArticleWithFeed.kt`)
  - 文章与Feed的关联模型，包含Feed信息

- **`ArticleMeta`** (`domain/model/article/ArticleMeta.kt`)
  - 文章元数据，仅包含id、isUnread、isStarred，用于快速查询

- **`Feed`** (`domain/model/feed/Feed.kt:19`)
  - Feed订阅源实体
  - 属性：名称、图标、URL、分组ID、账户ID
  - 配置：isNotification（通知）、isFullContent（全文）、isBrowser（浏览器打开）
  - 外键关联到Group表（级联删除）

- **`Group`** (`domain/model/group/Group.kt`)
  - 分组实体，用于组织Feed

- **`ArchivedArticle`** (`domain/model/article/ArchivedArticle.kt`)
  - 归档文章模型

#### 1.2 `domain.repository` - 数据访问接口

**Repository 接口（Room DAO）：**

- **`ArticleDao`** (`domain/repository/ArticleDao.kt:22`)
  - 文章数据访问对象
  - 核心方法：
    - `queryArticleWithFeedWhenIsAll/Unread/Starred()` - 分页查询文章
    - `searchArticle...()` - 文章搜索（支持标题、描述、全文）
    - `markAsReadByArticleId()` - 标记已读/未读
    - `markAsStarredByArticleId()` - 标记收藏/取消收藏
    - `markAllAsRead()` - 批量标记已读
    - `deleteByFeedId/GroupId()` - 删除文章
    - `queryImportantCount...()` - 查询未读/收藏数量
    - `insertListIfNotExist()` - 批量插入（去重）

- **`FeedDao`** (`domain/repository/FeedDao.kt`)
  - Feed数据访问对象
  - 核心方法：
    - `queryAll()` - 查询所有Feed
    - `queryByAccountId()` - 按账户查询
    - `queryByGroupId()` - 按分组查询
    - `insertOnConflictIgnore()` - 插入（忽略冲突）
    - `deleteByAccountId()` - 删除账户的所有Feed

- **`GroupDao`** (`domain/repository/GroupDao.kt`)
  - 分组数据访问对象
  - 核心方法：增删改查分组

- **`AccountDao`** (`domain/repository/AccountDao.kt`)
  - 账户数据访问对象
  - 核心方法：增删改查账户

#### 1.3 `domain.service` - 业务服务

**核心服务类：**

- **`AccountService`** (`domain/service/AccountService.kt:35`)
  - 账户管理服务
  - 核心功能：
    - `getAccounts()` - 获取所有账户列表（Flow）
    - `getCurrentAccount()` - 获取当前账户
    - `addAccount()` - 添加新账户
    - `switch()` - 切换账户
    - `delete()` - 删除账户（级联删除相关数据）
    - `initWithDefaultAccount()` - 初始化默认账户
  - 管理当前账户ID（存储在DataStore）

- **`RssService`** (`domain/service/RssService.kt:13`)
  - RSS服务门面，根据账户类型路由到具体服务
  - 支持的服务：
    - `LocalRssService` - 本地RSS
    - `FeverRssService` - Fever API
    - `GoogleReaderRssService` - Google Reader API（兼容FreshRSS）

- **`AbstractRssRepository`** (`domain/service/AbstractRssRepository.kt`)
  - RSS服务抽象基类
  - 定义统一接口：
    - `pullArticles()` - 拉取文章列表
    - `searchArticles()` - 搜索文章
    - `sync()` - 同步RSS源

- **`LocalRssService`** (`domain/service/LocalRssService.kt`)
  - 本地RSS实现
  - 使用Rome库解析RSS/Atom feed
  - 直接从网络拉取数据

- **`FeverRssService`** (`domain/service/FeverRssService.kt`)
  - Fever API实现
  - 使用Fever API与服务器交互

- **`GoogleReaderRssService`** (`domain/service/GoogleReaderRssService.kt`)
  - Google Reader API实现（兼容FreshRSS、Inoreader、Feedly）
  - 使用Google Reader API与服务器交互

- **`OpmlService`** (`domain/service/OpmlService.kt`)
  - OPML导入/导出服务
  - 支持导入/导出订阅列表

- **`SyncWorker`** (`domain/service/SyncWorker.kt`)
  - WorkManager后台同步任务
  - 根据账户配置定时同步RSS

- **`ReaderWorker`** (`domain/service/ReaderWorker.kt`)
  - 全文阅读工作器
  - 使用Readability4j提取网页正文

#### 1.4 `domain.data` - 用例

**用例类：**

- **`ArticlePagingListUseCase`** (`domain/data/ArticlePagingListUseCase.kt:34`)
  - 文章分页列表用例
  - 功能：
    - 管理文章分页数据（Paging 3）
    - 根据筛选状态动态创建Pager
    - 支持搜索和筛选（未读/收藏/全部）
    - 监听筛选状态变化并自动更新
  - 返回 `Flow<PagingData<ArticleFlowItem>>`

- **`FilterStateUseCase`** (`domain/data/FilterStateUseCase.kt`)
  - 筛选状态管理用例
  - 管理文章列表的筛选状态（分组、Feed、筛选类型、搜索内容）

- **`GroupWithFeedsListUseCase`** (`domain/data/GroupWithFeedsListUseCase.kt`)
  - 分组及其Feed列表用例

- **`DiffMapHolder`** (`domain/data/DiffMapHolder.kt`)
  - 差异映射持有者，用于列表Diff优化

- **`SyncLogger`** (`domain/data/SyncLogger.kt`)
  - 同步日志记录器

---

### 2. `me.ash.reader.infrastructure` - 基础设施层

#### 2.1 `infrastructure.db` - 数据库实现

**核心类：**

- **`AndroidDatabase`** (`infrastructure/db/AndroidDatabase.kt:40`)
  - Room数据库抽象类
  - 版本：7
  - 实体：Account, Feed, Article, Group, ArchivedArticle
  - 自动迁移：5→6, 5→7, 6→7
  - 手动迁移：1→2, 2→3, 3→4, 4→5
  - 类型转换器：Date、AccountType、SyncInterval等
  - 数据库文件名：`Reader`

#### 2.2 `infrastructure.di` - 依赖注入

**Hilt 模块：**

- **`DatabaseModule`** (`infrastructure/di/DatabaseModule.kt:26`)
  - 提供数据库和DAO单例
  - 提供：AndroidDatabase、ArticleDao、FeedDao、GroupDao、AccountDao

- **`OkHttpClientModule`** (`infrastructure/di/OkHttpClientModule.kt`)
  - 提供OkHttp客户端配置
  - 配置连接超时、拦截器等

- **`RetrofitModule`** (`infrastructure/di/RetrofitModule.kt`)
  - 提供Retrofit实例
  - 配置API服务接口

- **`UseCaseModule`** (`infrastructure/di/UseCaseModule.kt`)
  - 提供所有用例单例
  - ArticlePagingListUseCase、FilterStateUseCase等

- **`SettingsProviderModule`** (`infrastructure/di/SettingsProviderModule.kt`)
  - 提供设置提供者

- **`CoroutineScopeModule`** (`infrastructure/di/CoroutineScopeModule.kt`)
  - 提供ApplicationScope、IODispatcher、MainDispatcher

- **`WorkerModule`** (`infrastructure/di/WorkerModule.kt`)
  - 提供WorkManager工厂

- **`ImageLoaderModule`** (`infrastructure/di/ImageLoaderModule.kt`)
  - 提供Coil图片加载器

- **`CacheHolderModule`** (`infrastructure/di/CacheHolderModule.kt`)
  - 提供缓存持有者

- **`AccountServiceModule`** (`infrastructure/di/AccountServiceModule.kt`)
  - 提供账户服务

#### 2.3 `infrastructure.rss` - RSS相关实现

**核心类：**

- **`RssHelper`** (`infrastructure/rss/RssHelper.kt`)
  - RSS解析辅助工具
  - 使用Rome库解析RSS/Atom feed
  - 提取文章列表、Feed信息等

- **`Favicon`** (`infrastructure/rss/Favicon.kt`)
  - 获取网站图标

- **`BestIconFinder`** (`infrastructure/rss/BestIconFinder.kt`)
  - 查找最佳图标

- **`OPMLDataSource`** (`infrastructure/rss/OPMLDataSource.kt`)
  - OPML数据源

- **`ReaderCacheHelper`** (`infrastructure/rss/ReaderCacheHelper.kt`)
  - Reader缓存辅助工具

#### 2.4 `infrastructure.rss.provider` - RSS服务提供者

**API 实现：**

- **`ProviderAPI`** (`infrastructure/rss/provider/ProviderAPI.kt`)
  - RSS提供者API接口

- **`GoogleReaderAPI`** (`infrastructure/rss/provider/greader/GoogleReaderAPI.kt`)
  - Google Reader API实现
  - Retrofit接口定义

- **`GoogleReaderDTO`** (`infrastructure/rss/provider/greader/GoogleReaderDTO.kt`)
  - Google Reader数据传输对象

- **`FeverAPI`** (`infrastructure/rss/provider/fever/FeverAPI.kt`)
  - Fever API实现
  - Retrofit接口定义

- **`FeverDTO`** (`infrastructure/rss/provider/fever/FeverDTO.kt`)
  - Fever数据传输对象

#### 2.5 `infrastructure.preference` - 配置管理

**配置类：**

- **`SettingsProvider`** (`infrastructure/preference/SettingsProvider.kt`)
  - 设置提供者，管理所有用户设置
  - 使用DataStore存储配置

- **`SyncIntervalPreference`** (`infrastructure/preference/SyncIntervalPreference.kt`)
  - 同步间隔配置

- **`SyncOnStartPreference`** (`infrastructure/preference/SyncOnStartPreference.kt`)
  - 启动同步配置

- **`SyncOnlyOnWiFiPreference`** (`infrastructure/preference/SyncOnlyOnWiFiPreference.kt`)
  - 仅WiFi同步配置

- **`SyncOnlyWhenChargingPreference`** (`infrastructure/preference/SyncOnlyWhenChargingPreference.kt`)
  - 仅充电时同步配置

- **`KeepArchivedPreference`** (`infrastructure/preference/KeepArchivedPreference.kt`)
  - 归档保留时间配置

- **其他配置**（颜色、字体、交互等）

#### 2.6 `infrastructure.datastore` - DataStore实现

- **DataStore相关实现**

#### 2.7 `infrastructure.net` - 网络相关

- 网络请求配置和辅助工具

#### 2.8 `infrastructure.html` - HTML处理

- HTML内容处理工具

#### 2.9 `infrastructure.exception` - 异常处理

- 自定义异常类

#### 2.10 `infrastructure.android` - Android平台相关

- **`AndroidStringsHelper`** (`infrastructure/android/AndroidStringsHelper.kt`)
  - Android字符串辅助工具

#### 2.11 `infrastructure.compose` - Compose相关

- Compose相关扩展和工具

---

### 3. `me.ash.reader.ui` - 表现层

#### 3.1 `ui.page` - 页面

**主要页面：**

- **`ui.page.home`** - 首页
  - `feeds` - Feed管理页面
    - `FeedsViewModel` - Feed列表ViewModel
    - `drawer` - 抽屉（分组列表）
      - `feed/FeedOptionViewModel` - Feed选项ViewModel
      - `group/GroupOptionViewModel` - 分组选项ViewModel
    - `subscribe/SubscribeViewModel` - 订阅页面ViewModel
  - `flow` - 文章流页面
  - `reading` - 阅读页面
    - `drawer` - 阅读抽屉
    - `tts` - 文字转语音

- **`ui.page.settings`** - 设置页面
  - `accounts` - 账户设置
    - `addition/AdditionViewModel` - 添加账户ViewModel
    - `connection/ConnectionViewModel` - 连接配置ViewModel
  - `color` - 颜色设置
    - `feeds/FeedsColorViewModel` - Feed颜色ViewModel
    - `flow/FlowColorViewModel` - 文章流颜色ViewModel
    - `reading/ReadingColorViewModel` - 阅读颜色ViewModel
      - `theme/ThemeViewModel` - 主题ViewModel
  - `interaction` - 交互设置
  - `languages` - 语言设置
  - `tips` - 提示页面
  - `troubleshooting` - 故障排除

- **`ui.page.startup`** - 启动页面
- **`ui.page.nav3`** - Navigation 3页面
  - `key/KeyViewModel` - 导航键ViewModel

- **`ui.page.common`** - 通用页面
- **`ui.page.adaptive`** - 自适应布局

#### 3.2 `ui.component` - 可复用组件

**组件分类：**

- **`base`** - 基础组件
- **`menu`** - 菜单组件
- **`reader`** - 阅读器组件
- **`swipe`** - 滑动组件
- **`webview`** - WebView组件

#### 3.3 `ui.theme` - 主题

**主题相关：**

- **`palette`** - 调色板
  - `colorspace` - 色彩空间
    - `cielab` - CIELAB色彩空间
    - `ciexyz` - CIEXYZ色彩空间
    - `jzazbz` - JzAzBz色彩空间
    - `oklab` - OKLab色彩空间
    - `rgb` - RGB色彩空间
      - `transferfunction` - 传递函数
    - `zcam` - ZCAM色彩模型
  - `core` - 核心调色板
  - `data` - 数据调色板
  - `dynamic` - 动态调色板
  - `util` - 调色板工具

#### 3.4 `ui.ext` - 扩展函数

- Kotlin扩展函数
- DataStore扩展
- Compose扩展

#### 3.5 `ui.graphics` - 图形相关

- 图形处理工具

#### 3.6 `ui.interaction` - 交互相关

- 交互逻辑处理

#### 3.7 `ui.motion` - 动画相关

- 动画效果

#### 3.8 `ui.svg` - SVG支持

- SVG解析和渲染

#### 3.9 `ui.widget` - Widget

- 主屏幕小部件

---

## 数据流

### 1. 文章列表加载流程

```
用户操作
    ↓
ViewModel (FeedsViewModel)
    ↓
ArticlePagingListUseCase (domain/data/ArticlePagingListUseCase.kt:34)
    ↓
RssService.get() (domain/service/RssService.kt:13)
    ↓
根据Account类型路由到具体服务：
  - LocalRssService
  - FeverRssService
  - GoogleReaderRssService
    ↓
ArticleDao.queryArticleWithFeed...() (domain/repository/ArticleDao.kt:22)
    ↓
Room数据库
    ↓
返回 PagingData<ArticleFlowItem>
    ↓
Compose UI展示
```

### 2. RSS同步流程

```
SyncWorker (WorkManager后台任务)
    ↓
AccountService.getCurrentAccount()
    ↓
RssService.get() 获取对应RSS服务
    ↓
AbstractRssRepository.sync()
    ↓
网络请求（Retrofit/OkHttp）
    ↓
解析RSS/Atom（Rome）
    ↓
ArticleDao.insertListIfNotExist() (domain/repository/ArticleDao.kt:909)
    ↓
更新数据库
```

### 3. 账户切换流程

```
用户选择账户
    ↓
AccountService.switch(account)
    ↓
更新DataStore中的currentAccountId
    ↓
AccountService.currentAccountFlow 发出新值
    ↓
RssService.currentServiceFlow 更新服务
    ↓
所有依赖currentAccountFlow的用例重新获取数据
    ↓
UI更新
```

---

## 核心类关系图

```
┌─────────────────────────────────────────────────────────────┐
│                        UI Layer                              │
│  ┌──────────────────┐  ┌──────────────────┐                │
│  │ FeedsViewModel   │  │ FlowViewModel    │                │
│  └────────┬─────────┘  └────────┬─────────┘                │
│           │                      │                           │
│           └──────────┬───────────┘                           │
│                      ↓                                       │
│         ┌─────────────────────┐                             │
│         │ ArticlePagingList   │                             │
│         │ UseCase             │                             │
│         └──────────┬──────────┘                             │
└────────────────────┼────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────┐
│                    Domain Layer                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  RssService (Facade)                                 │  │
│  │  ┌────────────────────────────────────────────┐      │  │
│  │  │ AbstractRssRepository (Interface)         │      │  │
│  │  └────────────────────────────────────────────┘      │  │
│  └────────┬───────────────────────────────────────┘      │
│           │                                               │
│           ├──────────┬────────────┬──────────────┐        │
│           ↓          ↓            ↓              ↓        │
│  ┌─────────────┐ ┌──────────┐ ┌──────────┐ ┌────────┐    │
│  │LocalRssService│ │FeverRssService│ │GoogleReaderRssService│ │    │
│  └──────┬──────┘ └─────┬────┘ └─────┬────┘ └───┬────┘    │
│         │              │              │            │        │
│         └──────────────┼──────────────┼────────────┘        │
│                        ↓              ↓                       │
│  ┌─────────────────────────────────────────────────────┐  │
│  │ Repository Interfaces (Room DAOs)                    │  │
│  │ ArticleDao | FeedDao | GroupDao | AccountDao       │  │
│  └─────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────┐
│              Infrastructure Layer                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │Room Database │  │  Retrofit    │  │  DataStore   │     │
│  │  AndroidDB   │  │    Network   │  │ Preferences  │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
│  ┌──────────────┐  ┌──────────────┐                        │
│  │     Hilt     │  │   Rome       │                        │
│  │      DI      │  │   Parser     │                        │
│  └──────────────┘  └──────────────┘                        │
└─────────────────────────────────────────────────────────────┘
```

---

## 关键技术点

### 1. 多账户支持

- 支持多种账户类型：Local、Fever、GoogleReader、FreshRSS、Feedly、Inoreader
- 每个账户独立存储数据（通过accountId隔离）
- 账户切换使用DataStore存储currentAccountId

### 2. 分页加载

- 使用Paging 3实现无限滚动
- ArticleDao返回PagingSource
- ArticlePagingListUseCase管理Pager
- 动态响应筛选和搜索条件变化

### 3. 响应式数据流

- 大量使用Flow/StateFlow
- Room查询返回Flow
- 设置使用DataStore Flow
- 数据变化自动更新UI

### 4. 后台同步

- 使用WorkManager实现定时同步
- SyncWorker根据账户配置决定同步策略
- 支持WiFi限制、充电限制等条件

### 5. 依赖注入

- 使用Hilt进行依赖注入
- 模块化组织：DatabaseModule、UseCaseModule等
- 提供单例服务

### 6. 类型安全的路由

- 使用Navigation 3（Navigation Compose）
- 类型安全的参数传递

### 7. 主题系统

- 支持动态颜色（Material3）
- 自定义调色板系统
- 支持多种色彩空间

---

## 文件索引

### Domain Layer

| 文件路径 | 说明 |
|---------|------|
| `domain/model/account/Account.kt:15` | 账户实体 |
| `domain/model/article/Article.kt:20` | 文章实体 |
| `domain/model/feed/Feed.kt:19` | Feed实体 |
| `domain/repository/ArticleDao.kt:22` | 文章DAO |
| `domain/repository/FeedDao.kt` | Feed DAO |
| `domain/repository/GroupDao.kt` | 分组DAO |
| `domain/repository/AccountDao.kt` | 账户DAO |
| `domain/service/AccountService.kt:35` | 账户服务 |
| `domain/service/RssService.kt:13` | RSS服务门面 |
| `domain/service/AbstractRssRepository.kt` | RSS服务抽象基类 |
| `domain/service/LocalRssService.kt` | 本地RSS服务 |
| `domain/service/FeverRssService.kt` | Fever RSS服务 |
| `domain/service/GoogleReaderRssService.kt` | Google Reader服务 |
| `domain/service/OpmlService.kt` | OPML服务 |
| `domain/service/SyncWorker.kt` | 同步Worker |
| `domain/data/ArticlePagingListUseCase.kt:34` | 文章分页用例 |
| `domain/data/FilterStateUseCase.kt` | 筛选状态用例 |

### Infrastructure Layer

| 文件路径 | 说明 |
|---------|------|
| `infrastructure/db/AndroidDatabase.kt:40` | Room数据库 |
| `infrastructure/di/DatabaseModule.kt:26` | 数据库模块 |
| `infrastructure/di/UseCaseModule.kt` | 用例模块 |
| `infrastructure/preference/SettingsProvider.kt` | 设置提供者 |
| `infrastructure/rss/RssHelper.kt` | RSS解析工具 |
| `infrastructure/rss/provider/ProviderAPI.kt` | RSS提供者API |

### UI Layer

| 文件路径 | 说明 |
|---------|------|
| `ui/page/home/feeds/FeedsViewModel.kt` | Feed列表ViewModel |
| `ui/page/home/reading/tts` | 文字转语音 |
| `ui/page/settings/accounts` | 账户设置 |
| `ui/component/base` | 基础组件 |
| `ui/theme/palette` | 调色板 |

---

## 总结

ReadYou项目采用清晰的Clean Architecture架构，实现了：

1. **高内聚低耦合**：各层职责明确，依赖方向正确
2. **可测试性**：业务逻辑与UI、数据库完全解耦
3. **可扩展性**：支持多种RSS服务，易于添加新的账户类型
4. **响应式编程**：全面使用Flow实现数据驱动UI
5. **现代Android技术栈**：Compose、Hilt、Room、Paging 3等最新技术
