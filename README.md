# 干饭军师（GanfanJunshi）

专治「吃什么」选择困难 + 约人探店社交的 Android 应用，含 Ktor 后端服务。

本文档面向**收到项目压缩包后**需要在本地运行、测试并继续开发的组员，按顺序操作即可。

---

## 一、项目结构

```
AndroidStudioProject/
├── app/                    # Android 客户端（Kotlin + XML View）
│   └── src/main/java/com/ganfan/junshi/
│       ├── ui/             # Activity、Fragment、Adapter
│       ├── data/           # Repository、Room、Retrofit
│       ├── component/      # Service、BroadcastReceiver、ContentProvider
│       └── util/           # 工具类（地图搜索、状态栏适配等）
├── server/                 # Ktor 后端（REST + WebSocket）
├── gradle/                 # Gradle Wrapper（无需单独安装 Gradle）
├── docker-compose.yml      # 可选：PostgreSQL + Redis + 后端容器化部署
├── build.gradle.kts        # 根工程配置
├── settings.gradle.kts     # 模块：:app、:server
└── local.properties        # 本地配置（每人自己创建，勿提交 Git）
```

| 模块 | 技术栈 |
|------|--------|
| Android 客户端 | Kotlin、ViewBinding、Room、Retrofit、CameraX、高德地图 SDK |
| 后端 | Ktor、Exposed、H2（默认）/ PostgreSQL（可选） |

---

## 二、环境要求

在开始之前，请确认本机已安装：

| 工具 | 建议版本 |
|------|----------|
| **Android Studio** | Ladybug（2024.2）或更新版本 |
| **JDK** | 11 或以上（推荐直接使用 Android Studio 自带的 JBR） |
| **Android SDK** | compileSdk **35**，需安装 SDK Platform 35 与 Build-Tools |
| **Git**（可选） | 用于版本协作 |

> 首次打开项目时，Android Studio 会通过 Gradle Wrapper 自动下载 Gradle **9.3.1**，无需手动安装。

---

## 三、解压与打开项目

1. 将压缩包解压到**路径较短、不含中文和空格**的目录，例如：
   - `D:\Projects\GanfanJunshi`
   - 避免：`C:\Users\xxx\桌面\xxx`（中文路径偶发导致构建失败）

2. 用 **Android Studio** 打开 **`AndroidStudioProject`** 这一层目录（即包含 `app/`、`server/`、`settings.gradle.kts` 的目录）。

3. 若提示 **Trust Project**，选择 Trust。

4. 等待右下角 **Gradle Sync** 完成（首次可能需 5～15 分钟，取决于网络）。

5. 若 Sync 失败，见文末 [常见问题](#八常见问题)。

---

## 四、首次必做配置

### 4.1 创建 `local.properties`

项目根目录下应有 `local.properties`（压缩包内可能已附带示例，也可能没有）。**每位组员需按本机路径自行填写**：

在项目根目录新建或编辑 `local.properties`：

```properties
# Windows 示例（路径中的 \ 需写成 \\）
sdk.dir=D\:\\Android\\SDK
AMAP_API_KEY=你的高德Key

# macOS / Linux 示例
# sdk.dir=/Users/你的用户名/Library/Android/sdk
# AMAP_API_KEY=你的高德Key
```

- **`sdk.dir`**：Android SDK 安装路径。也可在 Android Studio → **Settings → Languages & Frameworks → Android SDK** 中查看 **Android SDK Location**，复制过来。
- **`AMAP_API_KEY`**：高德开放平台申请的 Key，用于地图、定位、周边餐厅搜索。**没有 Key 时 App 仍可运行，但地图页功能受限。**

> ⚠️ `local.properties` 含本地路径和 Key，**不要提交到 Git**，也不要在群里公开传播。

### 4.2（可选）指定 Gradle 使用的 JDK

若 Sync 报 JDK 相关错误，可在 `gradle.properties` 末尾增加（路径改为你本机 Android Studio 的 JBR）：

```properties
# Windows 示例
org.gradle.java.home=C\:\\Program Files\\Android\\Android Studio\\jbr

# macOS 示例
# org.gradle.java.home=/Applications/Android Studio.app/Contents/jbr/Contents/Home
```

也可在 Android Studio → **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK** 中选择 **Embedded JDK**。

### 4.3 申请高德地图 Key（地图功能必需）

1. 登录 [高德开放平台](https://lbs.amap.com/) 注册/登录。
2. 进入 **应用管理 → 创建新应用 → 添加 Key**。
3. Key 类型选择 **Android 平台**，填写：
   - **PackageName**：`com.ganfan.junshi`
   - **SHA1**：调试证书指纹（见下方获取方式）
4. 将 Key 填入 `local.properties` 的 `AMAP_API_KEY`。
5. 修改 Key 后需 **重新 Build / Run** 一次 App。

**获取调试 SHA1（Windows，在项目根目录执行）：**

```powershell
keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
```

在输出中找到 `SHA1:` 一行，复制到高德控制台。

---

## 五、启动后端服务

App 会优先请求后端 API；若后端未启动，多数功能会**降级为本地 Room 数据库 + 种子数据**，仍可体验基本流程。完整联调建议先启动后端。

### 方式一：本地 H2 数据库（推荐，零依赖）

在项目根目录打开终端（Android Studio 底部 **Terminal** 即可）：

```powershell
# Windows
.\gradlew :server:run

# macOS / Linux
./gradlew :server:run
```

看到类似 `Responding at http://0.0.0.0:8081` 即表示成功。默认端口 **8081**（避免与常见 8080 冲突）。

- 数据文件保存在 `server/data/` 目录。
- 可通过环境变量 `SERVER_PORT` 修改端口。

### 方式二：Docker（PostgreSQL + Redis + 后端）

需已安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/)。

```powershell
docker-compose up -d
```

同样监听 **8081** 端口。

### 验证后端

浏览器访问：`http://localhost:8081/`  
或使用 curl / Postman 测试接口是否正常响应。

---

## 六、运行 Android 客户端

### 6.1 模拟器（推荐初次调试）

1. Android Studio 顶部运行配置选择 **`app`**。
2. 设备选择 **Android Emulator**（建议 API 30+，带 Google Play 或非 Play 均可）。
3. 点击 **Run ▶**。
4. 默认后端地址已配置为 **`http://10.0.2.2:8081/`**（模拟器访问宿主机 localhost 的专用地址），无需修改。

### 6.2 真机调试

1. 手机开启 **开发者选项 → USB 调试**，用数据线连接电脑。
2. 修改 `app/build.gradle.kts` 中 `defaultConfig` 的 `BASE_URL` 为电脑局域网 IP，例如：

   ```kotlin
   buildConfigField("String", "BASE_URL", "\"http://192.168.1.100:8081/\"")
   ```

   > 在 cmd 中执行 `ipconfig`（Windows）或 `ifconfig`（macOS）查看本机 IP。

3. 修改 `app/src/main/res/xml/network_security_config.xml`，在 `<domain-config>` 内增加你的 IP（Android 9+ 默认禁止明文 HTTP）：

   ```xml
   <domain includeSubdomains="true">192.168.1.100</domain>
   ```

4. 确保手机与电脑在**同一 Wi-Fi**，且电脑防火墙允许 **8081** 端口入站。
5. Sync 后重新 Run。

### 6.3 登录与快速体验

启动后进入登录页，可任选：

| 方式 | 说明 |
|------|------|
| 手机号登录 | 输入任意 11 位手机号 + 勾选协议即可（后端联调时会注册；离线时走本地 Mock） |
| 游客登录 | 一键进入，适合快速看 UI |
| 设置饭否 ID | 登录后可设置或跳过，进入主页 |

底部五个 Tab：**地图 · 觅食圈 · 军师 · 消息 · 我的**。

---

## 七、建议测试清单

联调时可按以下路径自测主要功能：

- [ ] **地图**：定位、搜索餐厅、距离筛选（1km / 3km / 5km / 全城）、点击餐厅进详情
- [ ] **觅食圈**：查看拼桌帖、进入详情、查看发布者主页、关注
- [ ] **军师**：AI 唠嗑、命运转盘
- [ ] **消息**：会话列表、拼桌邀请 Tab
- [ ] **我的**：编辑资料、口味档案、打卡、设置
- [ ] **餐厅详情**：评价、收藏、发起拼桌
- [ ] **后端联调**：后端运行时登录/发消息/发拼桌，确认数据能同步

---

## 八、常见问题

### Gradle Sync 失败

- 检查网络与代理；国内可配置 Gradle 镜像或使用 VPN。
- 确认 `sdk.dir` 路径正确。
- **File → Invalidate Caches → Invalidate and Restart**。
- 删除项目下的 `.gradle` 文件夹后重新 Sync（不要删 `gradle/wrapper`）。

### 找不到 SDK / compileSdk 35

打开 **SDK Manager**，安装 **Android 15（API 35）** Platform 与最新 Build-Tools。

### 地图空白 / 定位失败 / 搜索无结果

- 确认 `AMAP_API_KEY` 已配置且 PackageName、SHA1 与高德控制台一致。
- 模拟器需在 **Extended Controls → Location** 中设置模拟坐标（默认北京附近）。
- 真机需授予 **定位权限**，并开启系统 GPS。

### App 连不上后端

- 确认 `.\gradlew :server:run` 已在运行。
- 模拟器用 `10.0.2.2`；真机用电脑局域网 IP，并检查 `network_security_config.xml`。
- Windows 防火墙放行 8081 端口。

### 端口 8081 被占用

```powershell
# 换端口启动后端
$env:SERVER_PORT=8082; .\gradlew :server:run
```

同时把 App 中 `BASE_URL` 改为对应端口。

### 构建很慢

- 首次构建需下载依赖，属正常现象。
- 后续可在 Android Studio 中只 Run `app`，不必每次 `:server:run` 都重启。

---

## 九、后续开发指引

### 9.1 代码入口

| 关注点 | 路径 |
|--------|------|
| 启动页 / 登录 | `ui/splash/SplashActivity.kt`、`ui/login/LoginActivity.kt` |
| 主页与 Tab | `ui/main/MainActivity.kt`、`ui/fragment/` |
| 业务数据层 | `data/GanfanRepository.kt` |
| 网络 API | `data/remote/ApiClient.kt` |
| 本地数据库 | `data/local/` |
| 后端路由 | `server/src/main/kotlin/.../plugins/Routing.kt` |

### 9.2 常用构建命令

```powershell
# 编译 Debug APK
.\gradlew :app:assembleDebug

# 仅编译 Kotlin（快速检查语法）
.\gradlew :app:compileDebugKotlin

# 运行后端
.\gradlew :server:run

# 清理构建产物
.\gradlew clean
```

### 9.3 协作建议

- 每人维护自己的 `local.properties`，**不要提交**。
- 修改 `BASE_URL`、高德 Key、本机 IP 等本地调试配置时，**不要误提交**到公共仓库。
- 功能开发建议按模块分工（如：地图 / 社交 / 消息 / 后端 API）。
- 提交前在本地完整 Run 一遍，确认 Gradle Sync 与基本流程无报错。

### 9.4 打包压缩给他人时（维护者参考）

建议压缩前**排除**以下目录以减小体积：

- `app/build/`、`server/build/`、`build/`、`.gradle/`
- `.idea/workspace.xml`（可选，个人 IDE 状态）

**必须保留：**

- `gradlew`、`gradlew.bat`、`gradle/wrapper/`
- 全部 `src/` 源码与 `*.gradle.kts` 配置

可附带一份不含真实 Key 的 `local.properties` 模板，提醒对方自行填写。

---

## 十、课程知识点覆盖（参考）

| 知识点 | 实现 |
|--------|------|
| Activity | Splash、Login、Main、餐厅详情、聊天等 |
| Fragment | 地图、觅食圈、军师、消息、我的 |
| BroadcastReceiver | 网络变化、饭点提醒、拼桌成功 |
| Service | 拼桌前台服务、定位上报、AI 回复 |
| ContentProvider | 收藏餐厅、口味档案 |
| 持久化 | Room + SharedPreferences + ContentProvider |
| 网络 | Retrofit + OkHttp |
| 多媒体 | CameraX、通知 |

---

## 联系与问题反馈

本地环境差异较大，遇到问题请先对照 [第八节常见问题](#八常见问题)。仍无法解决时，在组内说明：

1. 操作系统与 Android Studio 版本  
2. 完整报错日志（Gradle Sync 或 Run 窗口）  
3. 已执行到哪一步  

祝开发顺利，干饭愉快 🍜
