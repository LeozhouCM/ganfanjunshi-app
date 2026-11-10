# 干饭军师 (GanfanJunshi) — Code Wiki

## 目录

1. [项目概述](#1-项目概述)
2. [项目整体架构](#2-项目整体架构)
3. [模块划分与职责](#3-模块划分与职责)
   - [3.1 Android 客户端 (app)](#31-android-客户端-app)
   - [3.2 Ktor 后端 (server)](#32-ktor-后端-server)
4. [关键类与函数说明](#4-关键类与函数说明)
   - [4.1 客户端核心类](#41-客户端核心类)
   - [4.2 后端核心类](#42-后端核心类)
5. [数据模型](#5-数据模型)
6. [API 接口文档](#6-api-接口文档)
7. [依赖关系](#7-依赖关系)
8. [项目运行方式](#8-项目运行方式)
9. [构建配置](#9-构建配置)

---

## 1. 项目概述

**项目名称**：干饭军师 (GanfanJunshi)  
**用途**：专治「吃什么」选择困难 + 约人探店社交的 Android 应用  
**仓库结构**：多模块 Gradle 项目（Android 客户端 + Ktor 后端）

| 属性 | 值 |
|------|-----|
| 语言 | Kotlin |
| 客户端框架 | Android Native (XML Views + ViewBinding) |
| 后端框架 | Ktor 2.3.13 (Netty) |
| 数据库 | Room (客户端) / Exposed + H2/PostgreSQL (后端) |
| 最低 SDK | 24 (Android 7.0) |
| 目标 SDK | 35 (Android 15) |
| JDK | 11+ |
| 构建工具 | Gradle 9.3.1 (Kotlin DSL) |

---

## 2. 项目整体架构

```
┌─────────────────────────────────────────────────────┐
│                  Android 客户端 (app)                  │
│                                                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────┐  │
│  │   UI 层   │  │  Data 层  │  │  Component 层    │  │
│  │ Activity  │  │Repository│  │ Service/Receiver │  │
│  │ Fragment  │  │  Room DB  │  │ ContentProvider  │  │
│  │ Adapter   │  │  Retrofit │  │                  │  │
│  │ ViewModel │  │  Models   │  │                  │  │
│  └──────────┘  └──────────┘  └──────────────────┘  │
│         │              │              │              │
│         └──────────────┼──────────────┘              │
│                        │                             │
│              ┌─────────▼─────────┐                   │
│              │  GanfanRepository  │                   │
│              │   (单例数据中枢)     │                   │
│              └─────────┬─────────┘                   │
│                        │ REST + WebSocket             │
├────────────────────────┼─────────────────────────────┤
│                        │                              │
│                  Ktor 后端 (server)                    │
│  ┌──────────────────────────────────────────────────┐│
│  │  Plugins: Serialization / Auth / CORS / WS       ││
│  ├──────────────────────────────────────────────────┤│
│  │  Routing: REST API (auth/users/restaurants/...)  ││
│  ├──────────────────────────────────────────────────┤│
│  │  Services: AiService / MatchService /            ││
│  │            PushService / SocialFeedService       ││
│  ├──────────────────────────────────────────────────┤│
│  │  Database: Exposed ORM + HikariCP               ││
│  │  (H2 本地 / PostgreSQL 生产)                      ││
│  └──────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────┘
```

**数据流**：
1. UI 层通过 `GanfanRepository` 统一获取数据
2. Repository 优先请求后端 API（Retrofit），失败时降级为本地 Room 数据库 + 种子数据
3. 聊天消息支持 WebSocket 实时推送
4. 后端通过 JWT 进行身份认证，Exposed ORM 操作数据库

---

## 3. 模块划分与职责

### 3.1 Android 客户端 (app)

```
app/src/main/java/com/ganfan/junshi/
├── Constants.kt                    # 全局常量定义
├── GanfanJunshiApplication.kt      # Application 入口
├── component/                      # Android 四大组件
│   ├── provider/
│   │   └── UserTasteProvider.kt    # 口味画像 ContentProvider
│   ├── receiver/
│   │   └── Receivers.kt           # 广播接收器
│   └── service/
│       └── Services.kt            # 前台/后台服务
├── data/                           # 数据层
│   ├── GanfanRepository.kt        # 数据仓库（核心业务逻辑）
│   ├── local/
│   │   └── AppDatabase.kt         # Room 数据库定义
│   ├── model/
│   │   └── Models.kt              # DTO 数据类
│   └── remote/
│       └── ApiClient.kt           # Retrofit API + WebSocket 客户端
├── ui/                             # 界面层
│   ├── adapter/                    # RecyclerView 适配器 (11个)
│   ├── camera/                     # 相机打卡
│   ├── chat/                       # 聊天页
│   ├── fragment/                   # 主页 Fragment (7个)
│   ├── login/                      # 登录/注册
│   ├── main/                       # 主 Activity
│   ├── match/                      # 拼桌详情/发布
│   ├── profile/                    # 个人资料/口味
│   ├── restaurant/                 # 餐厅详情
│   ├── search/                     # 搜索用户
│   ├── settings/                   # 设置页
│   └── splash/                     # 启动页
└── util/                           # 工具类
    ├── AmapRestaurantSearch.kt    # 高德地图 POI 搜索
    ├── Helpers.kt                 # 通知/网络工具
    └── StatusBarInsets.kt         # 状态栏适配
```

**各层职责**：

| 层级 | 职责 | 关键技术 |
|------|------|----------|
| UI 层 | 页面展示、用户交互 | Activity, Fragment, ViewBinding, RecyclerView |
| Data 层 | 数据获取、缓存、同步 | Room, Retrofit, Repository 模式 |
| Component 层 | 系统服务、广播、跨进程数据共享 | Service, BroadcastReceiver, ContentProvider |
| Util 层 | 通用工具函数 | 高德地图 SDK 封装、通知管理 |

### 3.2 Ktor 后端 (server)

```
server/src/main/kotlin/com/ganfan/junshi/server/
├── Application.kt                  # 入口函数 main()
├── database/
│   ├── DatabaseFactory.kt         # 数据库连接池 (HikariCP)
│   └── tables/
│       └── Tables.kt              # Exposed 表定义
├── model/
│   └── Models.kt                  # 服务端 DTO
├── plugins/
│   ├── Plugins.kt                 # 序列化/认证/CORS/状态页
│   ├── Routing.kt                 # REST API 路由 (~800行)
│   └── WebSockets.kt              # WebSocket 连接管理
└── service/
    ├── AiService.kt               # AI 军师回复
    ├── MatchService.kt            # 拼桌匹配业务
    ├── PushService.kt             # 推送通知
    └── SocialFeedService.kt       # 社交 Feed 流
```

---

## 4. 关键类与函数说明

### 4.1 客户端核心类

#### `GanfanJunshiApplication` ([Application.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/app/src/main/java/com/ganfan/junshi/GanfanJunshiApplication.kt))

- **职责**：应用入口，初始化高德地图隐私合规、通知渠道、种子数据
- **关键方法**：
  - `onCreate()` — 初始化高德 SDK 隐私设置、创建通知渠道、异步加载种子数据

#### `GanfanRepository` ([GanfanRepository.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/app/src/main/java/com/ganfan/junshi/data/GanfanRepository.kt))

- **职责**：数据仓库单例，是客户端核心业务逻辑中枢（~1045行）
- **设计模式**：单例模式 + Repository 模式
- **关键方法**：

| 方法 | 说明 |
|------|------|
| `login(phone, password)` | 用户登录，失败时自动降级为 Mock 数据 |
| `register(...)` | 用户注册，成功后自动登录 |
| `refreshRestaurants(...)` | 刷新餐厅列表（高德搜索 → 后端 API → 种子数据三级降级） |
| `refreshMatchPosts()` | 刷新拼桌帖列表 |
| `createMatchPost(request)` | 创建拼桌帖 |
| `applyToMatch(postId, message)` | 申请加入拼桌 |
| `approveApplication(postId, appId)` | 通过拼桌申请，自动创建聊天会话 |
| `confirmMeal(matchPostId)` | 确认赴约，双方确认后升级为密友 |
| `sendMessage(conversationId, content)` | 发送聊天消息 |
| `aiChat(message)` | AI 军师对话 |
| `toggleFavorite(restaurant)` | 收藏/取消收藏餐厅 |
| `followUser(followingId)` | 关注/取消关注用户（需先约饭成功） |
| `createReview(...)` | 发表餐厅评价 |
| `seedLocalDataIfNeeded()` | 首次启动时填充种子数据 |

#### `SessionManager` ([GanfanRepository.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/app/src/main/java/com/ganfan/junshi/data/GanfanRepository.kt#L14-L58))

- **职责**：基于 SharedPreferences 的会话管理
- **关键字段**：`token`, `userId`, `nickname`, `userId2`, `phone`, `isLoggedIn`

#### `ApiClient` ([ApiClient.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/app/src/main/java/com/ganfan/junshi/data/remote/ApiClient.kt))

- **职责**：Retrofit 网络客户端配置
- **配置**：OkHttp + JWT Bearer Token 拦截器 + 日志拦截器
- **超时**：连接 30s，读取 30s

#### `ChatWebSocketClient` ([ApiClient.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/app/src/main/java/com/ganfan/junshi/data/remote/ApiClient.kt#L160-L212))

- **职责**：WebSocket 客户端，连接 `/ws/chat/{conversationId}` 接收实时消息
- **关键方法**：`connect()`, `disconnect()`

#### `AppDatabase` ([AppDatabase.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/app/src/main/java/com/ganfan/junshi/data/local/AppDatabase.kt))

- **职责**：Room 数据库，包含 16 个 Entity 和 16 个 DAO
- **版本**：v5，使用 `fallbackToDestructiveMigration()`
- **Entity 列表**：User, Restaurant, Favorite, UserTaste, MatchPost, ChatMessage, Review, ReviewDraft, Follow, ReviewLike, Conversation, Notification, MatchApplication, Buddy, MealConfirmation, BuddyMatchInvite

#### `AmapRestaurantSearch` ([AmapRestaurantSearch.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/app/src/main/java/com/ganfan/junshi/util/AmapRestaurantSearch.kt))

- **职责**：高德地图 POI 搜索封装
- **关键方法**：
  - `getLocationInfo(context)` — 获取当前定位
  - `searchNearby(...)` — 周边餐厅搜索
  - `searchInCity(...)` — 城市范围搜索
  - `summarizeCategories(...)` — 统计菜系分类

#### UI 层关键类

| 类名 | 文件 | 职责 |
|------|------|------|
| `SplashActivity` | [SplashActivity.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/app/src/main/java/com/ganfan/junshi/ui/splash/SplashActivity.kt) | 启动页，检查登录状态后跳转 |
| `LoginActivity` | [LoginActivity.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/app/src/main/java/com/ganfan/junshi/ui/login/LoginActivity.kt) | 登录/注册页（Tab 切换） |
| `MainActivity` | [MainActivity.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/app/src/main/java/com/ganfan/junshi/ui/main/MainActivity.kt) | 主容器，底部导航 5 Tab，Fragment 替换 |
| `MapFragment` | [MapFragment.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/app/src/main/java/com/ganfan/junshi/ui/fragment/MapFragment.kt) | 地图页：定位、搜索、筛选、标记 |
| `SocialFragment` | [SocialFragment.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/app/src/main/java/com/ganfan/junshi/ui/fragment/SocialFragment.kt) | 觅食圈：拼桌帖列表 |
| `HomeFragment` | [HomeFragment.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/app/src/main/java/com/ganfan/junshi/ui/fragment/HomeFragment.kt) | 军师页：AI 对话 + 命运转盘 |
| `MessagesFragment` | [MessagesFragment.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/app/src/main/java/com/ganfan/junshi/ui/fragment/MessagesFragment.kt) | 消息页：聊天列表 + 邀约 Tab |
| `ProfileFragment` | [ProfileFragment.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/app/src/main/java/com/ganfan/junshi/ui/fragment/ProfileFragment.kt) | 我的页：个人资料、口味、收藏 |
| `MatchDetailActivity` | [MatchDetailActivity.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/app/src/main/java/com/ganfan/junshi/ui/match/MatchDetailActivity.kt) | 拼桌详情：申请、审批、确认赴约、分享密友 |
| `RestaurantDetailActivity` | 餐厅详情：评价、收藏、发起拼桌 |
| `ChatActivity` | 聊天页：WebSocket 实时消息 |

#### Component 层关键类

| 类名 | 文件 | 职责 |
|------|------|------|
| `MatchForegroundService` | [Services.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/app/src/main/java/com/ganfan/junshi/component/service/Services.kt#L12-L55) | 前台服务：模拟拼桌匹配轮询 |
| `LocationReportService` | [Services.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/app/src/main/java/com/ganfan/junshi/component/service/Services.kt#L57-L88) | 前台服务：位置上报 |
| `AiReplyFetchService` | [Services.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/app/src/main/java/com/ganfan/junshi/component/service/Services.kt#L90-L116) | 后台服务：AI 异步回复 |
| `NetworkChangeReceiver` | [Receivers.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/app/src/main/java/com/ganfan/junshi/component/receiver/Receivers.kt#L9-L15) | 网络变化广播 |
| `MealReminderReceiver` | [Receivers.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/app/src/main/java/com/ganfan/junshi/component/receiver/Receivers.kt#L17-L23) | 饭点提醒广播（静态注册） |
| `MatchSuccessReceiver` | [Receivers.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/app/src/main/java/com/ganfan/junshi/component/receiver/Receivers.kt#L25-L32) | 匹配成功广播 |
| `UserTasteProvider` | [UserTasteProvider.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/app/src/main/java/com/ganfan/junshi/component/provider/UserTasteProvider.kt) | 口味画像 ContentProvider |

### 4.2 后端核心类

#### `Application.kt` ([Application.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/server/src/main/kotlin/com/ganfan/junshi/server/Application.kt))

- **职责**：服务入口，启动 Netty 嵌入式服务器
- **默认端口**：8082（可通过 `SERVER_PORT` 环境变量覆盖）
- **模块初始化顺序**：Database → Serialization → Security → HTTP → Routing → WebSockets

#### `Plugins.kt` ([Plugins.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/server/src/main/kotlin/com/ganfan/junshi/server/plugins/Plugins.kt))

- `configureSerialization()` — Gson 内容协商
- `configureHTTP()` — CORS 跨域（anyHost）+ 全局异常处理
- `configureSecurity()` — JWT 认证（HMAC256）
- `generateToken(user)` — 生成 JWT Token
- `ApplicationCall.userId()` — 从 JWT 中提取用户 ID

#### `Routing.kt` ([Routing.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/server/src/main/kotlin/com/ganfan/junshi/server/plugins/Routing.kt))

- **职责**：所有 REST API 路由定义（~800行）
- **路由分组**：

| 路由前缀 | 认证 | 功能 |
|----------|------|------|
| `POST /api/auth/login` | 无 | 手机号+密码登录 |
| `POST /api/auth/register` | 无 | 注册新用户 |
| `GET /api/users/me` | JWT | 获取当前用户信息 |
| `PUT /api/users/me` | JWT | 更新用户信息 |
| `GET/PUT /api/users/me/taste-profile` | JWT | 口味画像 CRUD |
| `POST/DELETE /api/users/{id}/follow` | JWT | 关注/取关 |
| `GET /api/restaurants/nearby` | JWT | 附近餐厅（计算距离） |
| `GET /api/restaurants/{id}` | JWT | 餐厅详情 |
| `GET/POST /api/match/posts` | JWT | 拼桌帖列表/创建 |
| `POST /api/match/posts/{id}/apply` | JWT | 申请入桌 |
| `POST .../applications/{appId}/approve` | JWT | 通过申请 |
| `POST .../applications/{appId}/reject` | JWT | 拒绝申请 |
| `GET /api/reviews` | JWT | 评价列表 |
| `POST /api/reviews` | JWT | 发表评价 |
| `GET/POST /api/chat/{id}/messages` | JWT | 聊天消息 |
| `GET /api/conversations` | JWT | 会话列表 |
| `POST /api/ai/chat` | JWT | AI 对话 |
| `POST/DELETE /api/favorites/{id}` | JWT | 收藏/取消收藏 |
| `GET /api/feed` | JWT | 社交 Feed 流 |
| `GET /api/notifications` | JWT | 通知列表 |

#### `WebSockets.kt` ([WebSockets.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/server/src/main/kotlin/com/ganfan/junshi/server/plugins/WebSockets.kt))

- **路由**：
  - `/ws/chat/{conversationId}` — 聊天 WebSocket（群组广播）
  - `/ws/match` — 拼桌实时通知 WebSocket
- **连接管理**：`ConcurrentHashMap` 维护会话级和用户级连接池

#### `DatabaseFactory` ([DatabaseFactory.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/server/src/main/kotlin/com/ganfan/junshi/server/database/DatabaseFactory.kt))

- **职责**：HikariCP 连接池配置
- **默认连接**：PostgreSQL `jdbc:postgresql://localhost:5432/ganfan_db`
- **可通过环境变量覆盖**：`DB_URL`, `DB_USER`, `DB_PASS`

#### `MatchService` ([MatchService.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/server/src/main/kotlin/com/ganfan/junshi/server/service/MatchService.kt))

- **职责**：拼桌匹配核心业务逻辑
- **状态机**：`recruiting → matched → completed / cancelled`
- **关键方法**：
  - `apply(postId, applicantId, message)` — 提交申请，防重复
  - `approve(postId, appId, operatorId)` — 通过申请，满员自动转 matched
  - `reject(postId, appId, operatorId)` — 拒绝申请
  - `close(postId, operatorId, reason)` — 关闭拼桌
  - `listApplications(postId, operatorId)` — 获取申请列表

#### `AiService` ([AiService.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/server/src/main/kotlin/com/ganfan/junshi/server/service/AiService.kt))

- **职责**：AI 军师回复（基于关键词规则匹配）
- `reply(message)` — 根据关键词（辣/清淡/火锅/日料等）返回推荐回复

#### `PushService` ([PushService.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/server/src/main/kotlin/com/ganfan/junshi/server/service/PushService.kt))

- **职责**：内存推送通知管理
- `push(userId, type, title, content)` — 发送通知
- `getNotifications(userId, page, size)` — 分页获取通知
- `getUnreadCount(userId)` — 未读数
- `markRead(userId, notificationId)` — 标记已读

#### `SocialFeedService` ([SocialFeedService.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/server/src/main/kotlin/com/ganfan/junshi/server/service/SocialFeedService.kt))

- **职责**：首页 Feed 流聚合
- `getFeed(userId, page, size)` — 聚合关注用户的拼桌帖 + 精选点评，时间倒序

---

## 5. 数据模型

### 客户端核心 DTO ([Models.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/app/src/main/java/com/ganfan/junshi/data/model/Models.kt))

| 数据类 | 字段 | 说明 |
|--------|------|------|
| `UserDto` | id, phone, nickname, avatarUrl, bio, city, creditScore, userId2 | 用户信息 |
| `LoginRequest` | phone, password | 登录请求 |
| `LoginResponse` | token, user | 登录响应 |
| `RestaurantDto` | id, name, lat, lng, address, avgPrice, cuisine, rating, tags, distance | 餐厅信息 |
| `MatchPostDto` | id, publisherId, title, mealTime, maxPeople, currentPeople, budget, status | 拼桌帖 |
| `CreateMatchRequest` | title, mealTime, maxPeople, budget, restaurantId, lat, lng | 创建拼桌请求 |
| `ReviewDto` | id, userId, restaurantId, rating, content, images, parentId, likesCount | 评价 |
| `ChatMessageDto` | id, conversationId, senderId, msgType, content, sentAt | 聊天消息 |
| `ConversationDto` | id, userId, partnerId, partnerName, lastMessage, matchPostId | 会话 |
| `AiChatRequest` | message, context | AI 对话请求 |
| `AiChatResponse` | reply | AI 对话响应 |
| `ApiResult<T>` | code, message, data | 统一 API 响应包装 |
| `MatchInviteItem` | id, type, title, subtitle, status, postId | 邀约统一展示项 |
| `RestaurantMatchStats` | openPostCount, totalSeekingPeople, maxTableSize | 餐厅拼桌统计 |

### 服务端核心 DTO ([Models.kt](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/server/src/main/kotlin/com/ganfan/junshi/server/model/Models.kt))

| 数据类 | 说明 |
|--------|------|
| `UserDto` | 用户（含 gender, status） |
| `RegisterRequest` | 注册请求（含 userId2, nickname） |
| `RestaurantDto` | 餐厅（含 coverUrl, phone, openingHours） |
| `MatchPostDto` | 拼桌帖（含 note） |
| `CreateMatchRequest` | 创建拼桌（含 note） |
| `ReviewDto` | 评价（含 status, likeCount, updatedAt） |
| `TasteProfileDto` | 口味画像（spiceLevel, avgBudget, favoriteCuisines, dietaryRestrictions） |
| `NotificationDto` | 通知（含 actionType, actionId, isRead） |
| `ConversationDto` | 会话（含 partnerAvatar） |

---

## 6. API 接口文档

| 方法 | 路径 | 认证 | 请求体 | 响应 |
|------|------|------|--------|------|
| POST | `/api/auth/login` | 无 | `LoginRequest` | `ApiResult<LoginResponse>` |
| POST | `/api/auth/register` | 无 | `RegisterRequest` | `ApiResult<LoginResponse>` |
| GET | `/api/users/me` | JWT | - | `ApiResult<UserDto>` |
| PUT | `/api/users/me` | JWT | `UpdateUserRequest` | `ApiResult<UserDto>` |
| GET | `/api/users/search?keyword=` | JWT | - | `ApiResult<List<UserDto>>` |
| GET | `/api/users/{id}` | JWT | - | `ApiResult<UserDto>` |
| POST | `/api/users/{id}/follow` | JWT | - | `ApiResult<Unit>` |
| DELETE | `/api/users/{id}/follow` | JWT | - | `ApiResult<Unit>` |
| GET | `/api/users/{id}/follow-status` | JWT | - | `ApiResult<FollowStatusResponse>` |
| GET | `/api/restaurants/nearby?lat=&lng=&radius=` | JWT | - | `ApiResult<List<RestaurantDto>>` |
| GET | `/api/restaurants/{id}` | JWT | - | `ApiResult<RestaurantDto>` |
| GET | `/api/match/posts` | JWT | - | `ApiResult<List<MatchPostDto>>` |
| POST | `/api/match/posts` | JWT | `CreateMatchRequest` | `ApiResult<MatchPostDto>` |
| POST | `/api/match/posts/{id}/apply` | JWT | `{message}` | `ApiResult<Unit>` |
| POST | `/api/match/posts/{id}/join` | JWT | `{}` | `ApiResult<Unit>` |
| GET | `/api/match/posts/{id}/applications` | JWT | - | `ApiResult<List<MatchApplicationDto>>` |
| POST | `/api/match/posts/{postId}/applications/{appId}/approve` | JWT | - | `ApiResult<Unit>` |
| POST | `/api/match/posts/{postId}/applications/{appId}/reject` | JWT | - | `ApiResult<Unit>` |
| POST | `/api/match/posts/{id}/close` | JWT | `{reason}` | `ApiResult<Unit>` |
| GET | `/api/reviews` | JWT | - | `ApiResult<List<ReviewDto>>` |
| GET | `/api/reviews/restaurant/{id}` | JWT | - | `ApiResult<List<ReviewDto>>` |
| POST | `/api/reviews` | JWT | `CreateReviewRequest` | `ApiResult<ReviewDto>` |
| POST | `/api/reviews/{id}/like` | JWT | - | `ApiResult<Unit>` |
| DELETE | `/api/reviews/{id}/like` | JWT | - | `ApiResult<Unit>` |
| GET | `/api/chat/{id}/messages` | JWT | - | `ApiResult<List<ChatMessageDto>>` |
| POST | `/api/chat/{id}/messages` | JWT | `{content}` | `ApiResult<ChatMessageDto>` |
| GET | `/api/conversations` | JWT | - | `ApiResult<List<ConversationDto>>` |
| POST | `/api/conversations` | JWT | `{...}` | `ApiResult<ConversationDto>` |
| POST | `/api/ai/chat` | JWT | `AiChatRequest` | `ApiResult<AiChatResponse>` |
| GET | `/api/favorites` | JWT | - | `ApiResult<List<FavoritesDto>>` |
| POST | `/api/favorites/{id}` | JWT | - | `ApiResult<Unit>` |
| DELETE | `/api/favorites/{id}` | JWT | - | `ApiResult<Unit>` |
| GET | `/api/feed` | JWT | `?page=&size=` | `ApiResult<List<FeedItem>>` |
| GET | `/api/social/feed` | JWT | `?page=&size=` | `ApiResult<List<FeedItem>>` |
| GET | `/api/notifications` | JWT | `?page=&size=` | `ApiResult<List<NotificationDto>>` |
| GET | `/api/notifications/unread-count` | JWT | - | `ApiResult<{count}>` |
| PUT | `/api/notifications/{id}/read` | JWT | - | `ApiResult<Unit>` |
| PUT | `/api/notifications/read-all` | JWT | - | `ApiResult<{marked}>` |
| GET/PUT | `/api/users/me/taste-profile` | JWT | `UpdateTasteProfileRequest` | `ApiResult<TasteProfileDto>` |
| GET/PUT | `/api/taste/profile` | JWT | `UpdateTasteProfileRequest` | `ApiResult<TasteProfileDto>` |

### WebSocket 端点

| 路径 | 用途 |
|------|------|
| `/ws/chat/{conversationId}?userId=` | 聊天实时消息 |
| `/ws/match?userId=` | 拼桌实时通知 |

---

## 7. 依赖关系

### 7.1 客户端依赖

| 类别 | 库 | 版本 | 用途 |
|------|-----|------|------|
| **核心** | Kotlin | 2.2.10 | 开发语言 |
| | Android Gradle Plugin | 9.1.0 | 构建 |
| | KSP | 2.3.2 | 注解处理 |
| **UI** | AndroidX Core KTX | 1.15.0 | 核心扩展 |
| | AppCompat | 1.7.0 | 兼容性 |
| | Material Design | 1.12.0 | UI 组件 |
| | ConstraintLayout | 2.2.0 | 约束布局 |
| | RecyclerView | 1.3.2 | 列表 |
| | ViewPager2 | 1.1.0 | 翻页 |
| | Fragment KTX | 1.8.5 | Fragment 扩展 |
| **架构** | Lifecycle (ViewModel/LiveData) | 2.8.7 | MVVM 架构 |
| | Room | 2.7.1 | 本地数据库 |
| **网络** | Retrofit | 2.11.0 | HTTP 客户端 |
| | OkHttp | 4.12.0 | HTTP 引擎 |
| | Gson | 2.11.0 | JSON 序列化 |
| **异步** | Coroutines | 1.9.0 | 协程 |
| **图片** | Glide | 4.16.0 | 图片加载 |
| **相机** | CameraX | 1.4.0 | 相机打卡 |
| **后台** | WorkManager | 2.10.0 | 后台任务 |
| **地图** | 高德 3D 地图 | 10.0.600 | 地图展示 |
| | 高德定位 | 6.4.9 | 定位 |
| | 高德搜索 | 9.7.0 | POI 搜索 |

### 7.2 后端依赖

| 类别 | 库 | 版本 | 用途 |
|------|-----|------|------|
| **核心** | Ktor Server Core | 2.3.13 | Web 框架 |
| | Ktor Server Netty | 2.3.13 | 服务器引擎 |
| | Ktor Content Negotiation | 2.3.13 | 内容协商 |
| | Ktor Auth / Auth JWT | 2.3.13 | JWT 认证 |
| | Ktor WebSockets | 2.3.13 | WebSocket |
| | Ktor CORS | 2.3.13 | 跨域 |
| | Ktor Call Logging | 2.3.13 | 请求日志 |
| | Ktor Status Pages | 2.3.13 | 异常处理 |
| **数据库** | Exposed (Core/DAO/JDBC/Time) | 0.56.0 | ORM |
| | HikariCP | 6.0.0 | 连接池 |
| | PostgreSQL JDBC | 42.7.4 | PG 驱动 |
| | H2 | 2.3.232 | 内存数据库 |
| **安全** | java-jwt | 4.4.0 | JWT |
| | bcrypt | 0.10.2 | 密码哈希 |
| **缓存** | Jedis | 5.2.0 | Redis 客户端 |
| **日志** | Logback | 1.5.12 | 日志框架 |

### 7.3 客户端内部依赖关系

```
MainActivity
  ├── MapFragment ───── GanfanRepository ──── Room (AppDatabase)
  ├── SocialFragment ── GanfanRepository ──── Retrofit (ApiClient)
  ├── HomeFragment ──── GanfanRepository ──── 高德地图 SDK
  ├── MessagesFragment  GanfanRepository ──── ChatWebSocketClient
  ├── ProfileFragment ─ GanfanRepository ──── ContentProvider
  └── ...Activities ─── GanfanRepository
```

---

## 8. 项目运行方式

### 8.1 环境要求

| 工具 | 版本要求 |
|------|----------|
| Android Studio | Ladybug (2024.2) 或更新 |
| JDK | 11+ |
| Android SDK | compileSdk 35, minSdk 24 |
| Gradle | 9.3.1 (自动下载) |

### 8.2 配置步骤

1. **创建 `local.properties`**（项目根目录）：
   ```properties
   sdk.dir=D\:\\Android\\SDK
   AMAP_API_KEY=你的高德Key
   ```

2. **申请高德地图 Key**（[高德开放平台](https://lbs.amap.com/)）：
   - PackageName: `com.ganfan.junshi`
   - 需要填写调试 SHA1

### 8.3 启动后端

```powershell
# Windows (本地 H2 模式)
.\gradlew :server:run

# 或 Docker 部署
docker-compose up -d
```

默认端口：**8082**，可通过 `SERVER_PORT` 环境变量覆盖。

### 8.4 运行 Android 客户端

1. 用 Android Studio 打开项目根目录
2. 等待 Gradle Sync 完成
3. 选择 `app` 运行配置，选择模拟器（API 30+）
4. 点击 Run ▶

### 8.5 常用构建命令

```powershell
# 编译 Debug APK
.\gradlew :app:assembleDebug

# 仅编译 Kotlin（语法检查）
.\gradlew :app:compileDebugKotlin

# 运行后端
.\gradlew :server:run

# 清理
.\gradlew clean
```

### 8.6 底部导航与功能入口

| Tab | Fragment | 核心功能 |
|-----|----------|----------|
| 地图 | `MapFragment` | 定位、搜索餐厅、距离/菜系/价格/拼桌筛选、地图标记 |
| 觅食圈 | `SocialFragment` | 拼桌帖列表、搜索用户 |
| 军师 | `HomeFragment` | AI 对话、命运转盘 |
| 消息 | `MessagesFragment` | 聊天列表 + 邀约管理（双 Tab） |
| 我的 | `ProfileFragment` | 个人资料、口味档案、种草清单、打卡、设置 |

---

## 9. 构建配置

### 9.1 Gradle 配置

| 文件 | 用途 |
|------|------|
| [settings.gradle.kts](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/settings.gradle.kts) | 项目模块定义 (`:app`, `:server`)，仓库镜像配置 |
| [build.gradle.kts](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/build.gradle.kts) | 根工程插件声明 |
| [app/build.gradle.kts](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/app/build.gradle.kts) | Android 客户端配置 |
| [server/build.gradle.kts](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/server/build.gradle.kts) | Ktor 后端配置 |
| [gradle/libs.versions.toml](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/gradle/libs.versions.toml) | 版本目录（统一依赖管理） |
| [gradle.properties](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/gradle.properties) | Gradle 属性 |
| [local.properties](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/local.properties) | 本地配置（sdk.dir, AMAP_API_KEY） |

### 9.2 镜像加速

项目已配置阿里云、华为云、腾讯云、网易 Maven 镜像，国内构建无需额外配置。

### 9.3 Docker 部署

[docker-compose.yml](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/docker-compose.yml) 包含三个服务：
- **postgres**：PostgreSQL 15 数据库
- **redis**：Redis 7 缓存
- **server**：Ktor 后端（8081端口）

[Dockerfile](file:///d:/HS_AS/AndroidStudioProjec_latested(1)/Dockerfile) 使用多阶段构建（Gradle 编译 + JRE 运行）。

---

## 附录：文件清单

### Android 客户端源码文件

```
app/src/main/java/com/ganfan/junshi/
├── Constants.kt
├── GanfanJunshiApplication.kt
├── component/provider/UserTasteProvider.kt
├── component/receiver/Receivers.kt
├── component/service/Services.kt
├── data/GanfanRepository.kt
├── data/local/AppDatabase.kt
├── data/model/Models.kt
├── data/remote/ApiClient.kt
├── ui/adapter/ChatMessageAdapter.kt
├── ui/adapter/ConversationAdapter.kt
├── ui/adapter/FeaturedRestaurantAdapter.kt
├── ui/adapter/MatchApplicationAdapter.kt
├── ui/adapter/MatchInviteAdapter.kt
├── ui/adapter/MatchPostAdapter.kt
├── ui/adapter/RestaurantAdapter.kt
├── ui/adapter/RestaurantMatchAdapter.kt
├── ui/adapter/ReviewAdapter.kt
├── ui/adapter/ReviewCardAdapter.kt
├── ui/adapter/UserSearchAdapter.kt
├── ui/camera/CameraCheckInActivity.kt
├── ui/chat/ChatActivity.kt
├── ui/fragment/FeaturedFragment.kt
├── ui/fragment/HomeFragment.kt
├── ui/fragment/MapFragment.kt
├── ui/fragment/MatchFragment.kt
├── ui/fragment/MessagesFragment.kt
├── ui/fragment/ProfileFragment.kt
├── ui/fragment/SocialFragment.kt
├── ui/login/LoginActivity.kt
├── ui/main/MainActivity.kt
├── ui/match/MatchDetailActivity.kt
├── ui/match/PublishMatchActivity.kt
├── ui/profile/ProfileEditActivity.kt
├── ui/profile/TasteProfileActivity.kt
├── ui/profile/UserProfileActivity.kt
├── ui/restaurant/PublishMatchBottomSheet.kt
├── ui/restaurant/RestaurantDetailActivity.kt
├── ui/search/SearchUserActivity.kt
├── ui/settings/SettingsActivity.kt
├── ui/splash/SplashActivity.kt
├── util/AmapRestaurantSearch.kt
├── util/Helpers.kt
└── util/StatusBarInsets.kt
```

### 后端源码文件

```
server/src/main/kotlin/com/ganfan/junshi/server/
├── Application.kt
├── database/DatabaseFactory.kt
├── database/tables/Tables.kt
├── model/Models.kt
├── plugins/Plugins.kt
├── plugins/Routing.kt
├── plugins/WebSockets.kt
├── service/AiService.kt
├── service/MatchService.kt
├── service/PushService.kt
└── service/SocialFeedService.kt
```

### 布局文件 (XML)

| 文件 | 对应页面 |
|------|----------|
| `activity_splash.xml` | 启动页 |
| `activity_login.xml` | 登录/注册 |
| `activity_main.xml` | 主页（底部导航） |
| `fragment_map.xml` | 地图 Tab |
| `fragment_social.xml` | 觅食圈 Tab |
| `fragment_home.xml` | 军师 Tab |
| `fragment_messages.xml` | 消息 Tab |
| `fragment_profile.xml` | 我的 Tab |
| `activity_restaurant_detail.xml` | 餐厅详情 |
| `activity_match_detail.xml` | 拼桌详情 |
| `activity_chat.xml` | 聊天页 |
| `activity_camera_checkin.xml` | 相机打卡 |
| `activity_profile_edit.xml` | 编辑资料 |
| `activity_taste_profile.xml` | 口味档案 |
| `activity_user_profile.xml` | 用户主页 |
| `activity_search_user.xml` | 搜索用户 |
| `activity_settings.xml` | 设置 |
| `activity_publish_match.xml` | 发布拼桌 |
| `bottom_sheet_publish_match.xml` | 发布拼桌 BottomSheet |
| `dialog_write_review.xml` | 写评价弹窗 |
| `item_restaurant.xml` | 餐厅列表项 |
| `item_match_post.xml` | 拼桌帖列表项 |
| `item_chat_message.xml` | 聊天消息项 |
| `item_conversation.xml` | 会话列表项 |
| `item_review.xml` | 评价列表项 |
| `item_user_search.xml` | 用户搜索项 |
| 其他 item_*.xml | 其他列表项布局 |