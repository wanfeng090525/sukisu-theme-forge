# SukiSU Theme Forge

Android 端的 SukiSU Ultra 主题包开发工具。用来创建、编辑、校验和导出 `.sukisu` 主题包，支持明暗双套（adaptive）主题、图片槽位和高级 JSON 编辑。

> 非官方工具。主题包格式来自 [SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra)，本项目只做编辑与打包。

## 功能

- 新建空白 v5 自适应主题，或导入现有 `.sukisu` 继续编辑
- 浅色 / 深色两套独立编辑：keyColor、配色风格 / 规范、themeMode、玻璃参数、首页布局、状态区尺寸、交互开关
- 底栏 Dock：替换 4 个底栏图标（`bottom_bar_icon_*`），悬浮底栏、模糊、角标、预测性返回、滑动关闭、pager 拦截模式
- 图片槽位：壁纸、首页卡片、最多 5 个贴纸，每个贴纸可调位置 / 旋转 / 大小，自动处理 `hasBackground`、`hasCardBackground` 和 `homeCardStickerTransforms`
- 外观覆盖 `appearance`：shared / light / dark 三组色板令牌 + 图片锐化 / 饱和度 / 对比度 / 亮度
- 全局设置：应用图标、首页标题、模块列表排序、页面缩放、模块简介行数
- 自定义文案 `customTexts`：SukiSU 全量 50+ 文案键位，带搜索、分组和恢复默认
- `theme.json` 完整编辑：一键载入全字段模板，格式化、校验、复制 / 粘贴、应用
- 导出前自动把 `version` 提升到内容所需的最低版本（v3 / v4 / v5）
- AES-256-GCM 加密，生成管理器可直接导入的 `.sukisu`

## 构建

GitHub Actions 会自动构建 debug APK：

1. 打开仓库的 **Actions** → **Android CI**
2. 等最新一次运行完成
3. 在运行页面的 **Artifacts** 下载 `ThemeForge-debug`

也可以在本地构建（需要 JDK 17 + Android SDK 35）：

```bash
./gradlew assembleDebug
```

APK 输出在 `app/build/outputs/apk/debug/app-debug.apk`。

## 使用

1. 安装 APK，打开 ThemeForge
2. **新建主题**，或**导入 .sukisu**
3. 在「基础 / 外观 / 首页 / 底栏 / 图片 / 文案」中调整，图片通过系统文件选择器添加
4. 「JSON」可载入完整字段模板后直接改；「预览」检查卡片、贴纸和底栏
5. 点右上角「导出」或预览页的「导出 .sukisu」，选择保存位置

导出的文件可以直接在 SukiSU Ultra 管理器里通过「导入主题包」使用。

## 主题包格式（v3 - v5 adaptive）

`.sukisu` 文件：

```
"SKTH" + 12 字节 IV + AES-256-GCM 密文
```

解密后是 STORED ZIP：

```
theme.json
background_light
card_background_light
card_sticker_light_0..4
background_dark
card_background_dark
card_sticker_dark_0..4
bottom_bar_icon_home
bottom_bar_icon_superuser
bottom_bar_icon_module
bottom_bar_icon_setting
```

`theme.json` 顶层：`version`、`appIconId`、`customHomeTitle`、`adaptive`、`light`、`dark`；可选 `appearance`、`customTexts`、`bottomBarIcons`、`moduleSort*`。

版本能力：

| 内容 | 最低版本 |
| --- | --- |
| adaptive 明暗双套 | v3 |
| `appearance` 色板 / 图片效果 | v4 |
| `homeLayout=immersive`、`bottomBarIcons` | v5 |

工具只接受 v3 - v5 且 `adaptive=true` 的包；v1 / v2 旧格式不支持。

## 限制

- 主题包只能修改管理器已有的字段、颜色和图片，**不能新增 UI 组件**。例如实时走时时钟无法通过主题包实现，需要改管理器源码。
- 首页卡片的标题、标签、版本号仍由管理器绘制；工具只负责卡片背景图和文字颜色所在主题。
- 预览是简化还原，包含首页卡片、贴纸和底栏示意，不包含系统状态栏和统计卡。

## 安全说明

加密密钥与 SukiSU Ultra 客户端内置的密钥相同，作用是保证文件格式和防止随手改包，**不是版权保护**。任何人都可以用同样的方式解开 `.sukisu`。

请只导入来源可信的主题包。
