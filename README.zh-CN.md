# PodBox

<a href="https://github.com/w1lli4m666-droid/PodBox/blob/main/README.md">English <img src="https://flagcdn.com/gb.svg" alt="United Kingdom flag" height="14"></a> |
<a href="https://github.com/w1lli4m666-droid/PodBox/blob/main/README.zh-CN.md">简体中文 <img src="https://flagcdn.com/cn.svg" alt="China flag" height="14"></a>

PodBox 是一款面向低端电视盒子和小屏触屏音响的轻量播客播放器，目标设备包括小米电视盒子、小爱触屏音响，以及 Android 4.1 起的旧安卓设备。

## 功能

- 通过 Apple iTunes Search API 搜索播客。
- 支持订阅和取消订阅。
- 最近更新页面，应用启动时自动刷新一次。
- 在线流式播放播客音频，不下载整集节目。
- 支持 0.5x 到 2.0x 播放速度。
- 支持快退 15 秒、快进 15 秒，以及长按加速跳转。
- 支持下一个播放、全部播放、播放列表编辑。
- 播放模式支持顺序播放、列表循环、单集循环。
- 同时适配遥控器和小屏触屏操作。
- 自动清理缓存，适合存储空间有限的设备。

## 遥控器操作说明

- `上 / 下 / 左 / 右`：在顶栏、节目按钮、底部播放器、播放列表按钮之间移动光标。
- `确认 / OK / Enter`：点击当前焦点按钮。
- `设置键 / 菜单键`：光标跳至底部播放器栏。
- 光标位于节目列表按钮时按 `返回`：光标返回当前顶栏按钮。
- 位于订阅详情页时按 `返回`：返回订阅列表。
- 展开播放列表以后按 `返回`：收起播放列表。
- 光标位于顶栏按钮时按 `返回`：应用进入后台，当前播客继续播放。
- 点击播放列表 SVG 图标：展开播放列表；再次点击同一个图标：收起播放列表。
- 长按快退或快进：
  - 短按：跳转 15 秒
  - 长按超过 0.6 秒：每次跳转 30 秒
  - 长按超过 5 秒：每次跳转 60 秒

## 触屏操作说明

- 点击顶栏切换页面。
- 点击搜索输入框可调用系统中文输入法。
- 最近更新和订阅页面支持下拉刷新。
- 点击底部播放器按钮进行播放控制。
- 点击播放列表图标可展开并编辑当前播放队列。

## 页面逻辑

- 应用启动默认显示“最近更新”，并自动刷新一次。
- 每次应用会话首次进入“订阅”页面时，自动刷新一次订阅内容。
- 当前页面是“最近更新”时，再次点击顶栏“最近更新”会手动刷新。
- 当前页面是“订阅”时，再次点击顶栏“订阅”会手动刷新。
- 搜索支持中文、英文和拼音候选输入。

## 播放列表

播放列表图标位于底部播放器快退按钮左侧。

- 右上角播放模式图标会在顺序播放、列表循环、单集循环之间切换。
- 清空列表图标会移除当前播放列表中的所有节目。
- 每个列表节目右侧有上移、下移、删除按钮。
- 点击某个列表节目可从该节目开始播放。

## 构建

依赖：

- Android Studio 或 Android SDK
- JDK 17 或更新版本

Debug 构建：

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleDebug
```

Release APK：

```powershell
.\gradlew.bat :app:assembleRelease
```

Release 会输出：

- `armeabi-v7a`
- `arm64-v8a`
- `universal`

当前 release 构建使用 Android debug signing config 签名，方便测试安装。正式发布前应替换为私有 release keystore。

## 说明

PodBox 使用 ExoPlayer 2.x core 做流式播放和倍速控制，以保持较好的 Android 4 兼容性和较小的 APK 体积。播客搜索来自 Apple 公开 iTunes Search API，节目播放使用搜索结果返回的 RSS feed URL。
