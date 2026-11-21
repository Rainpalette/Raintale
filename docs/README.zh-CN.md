<h1 align="center">雨中逸事 · Rain Tales</h1>

<p align="center"><i>在漫长的雨中截下属于你的故事。</i></p>

<p align="center">
  一个隐私优先的虚拟角色陪伴相机 App（Android · Kotlin + Compose）<br/>
  角色站进你的取景框 → 摆好动作 → 拍下合影 → 她对这一刻说一句话
</p>

<p align="center">
  <a href="../LICENSE"><img src="https://img.shields.io/badge/code-MIT-green" alt="Code: MIT"></a>
  <a href="../ASSETS_LICENSE.md"><img src="https://img.shields.io/badge/assets-All%20Rights%20Reserved-orange" alt="Assets: All Rights Reserved"></a>
  <a href="https://github.com/Rainpalette/raintale/actions"><img src="https://github.com/Rainpalette/raintale/actions/workflows/build.yml/badge.svg" alt="build"></a>
  <img src="https://img.shields.io/badge/Android-10%2B-blue" alt="Android 10+">
</p>

> ⚠️ **实验性项目（v0.x）**：个人业余开源项目，尽力维护、不承诺响应时限。招牌角色米娜的正式立绘与专属语音尚未就绪（当前为占位剪影 + 系统语音），不影响功能使用。

---

## 这是什么
`雨中逸事`是一款 Android 相机 App，融合了虚拟角色与 AI 评论功能。你可以选择心仪的虚拟角色，与其合照，并让他/她对照片中的瞬间发表一句话。

技术上说：**虚拟角色叠加在相机实时画面上**（可拖动、缩放、换动作、换角色），按下快门合成一张全分辨率合照存进相册，然后由 AI（Google Gemini，用你自己的 key）以角色的口吻对照片说一句话——可以朗读出来，可以留言让她回应，可以存进「影集批注」变成你们的共同回忆。

`雨中逸事`目前没有服务器和账号管理，也不收集任何数据。

<details>
<summary><b>关于这个项目的设定</b></summary>

<br/>

在世界的某处，有一辆公交车载走了人们放弃的东西。没人知道终点站会有什么，但那些坐到最后的人再也没有回来。
一个叫米娜的女孩在半路下了车。她周围的空间变成了一个小小的公交站、透明的雨伞、安静的树列和雨。她已经在这里等了一段时间，看着别人下车停留，目睹他们记忆的碎片，最后再次乘车离开，消失在雨中。
而你作为这个空间的临时访客，在这里与米娜相遇。在你踏入这片空间后，这个空间扭曲成了你记忆中的场景。她会在你离开这个神秘空间之前，与你分享这些瞬间。


</details>

## 为什么特别

- **零收集，可验证**。没有服务器、没有账号、没有统计埋点、关闭了系统云备份。AI 用你自己的 API key 直连，数据只在你的设备与你启用的第三方服务之间传输——代码全部公开，你可以自己抓包验证。

- **人像深度**。ML Kit 端上人像分割，让真人可以站在角色**前面**——不再是"贴纸永远盖在最上层"。分割完全在本机运行，不上传。

- **知情同意做到位**。每个数据外发面（AI 上传 / 维基查询 / 位置）在首次触达前都有独立告知，同意带版本号——文案更新会重新征求同意。


## 功能

- [x] 角色合影：拖动 / 双指缩放 / 切动作 / 切角色，全分辨率合成存相册
- [x] AI 评论：拍照后角色说一句应景的话（结构化输出 + 安全护栏 + 心理安全指令）
- [x] 朗读：系统 TTS（设备自带、离线、不联网）
- [x] 留言拍照：拍完写一句话（打字或语音），她针对性回应
- [x] 人像模式：人在角色前 / 后即时切换（端上分割）
- [x] 实时点评：她陪你走一段，画面变了才说话（本地变化检测省额度）
- [x] 陪伴模式 + 自定义角色：纯文字 persona，唤起一个"没有形的人"
- [x] 图库 + 影集批注：她说过的话自动存档，你也可以写下注脚；支持导出 / 导入（JSON）
- [x] 知识链接：认出地标 / 品牌时，可挂维基百科简介（默认关）
- [x] VN 开场教程：以视觉小说形式的初遇与引导
- [x] 离线台词：没网、没 key 时她也不会沉默
- [ ] 米娜正式立绘与专属音色（进行中）
- [ ] 英文界面（i18n 地基已就绪）
- [ ] 演示模式（无 key 试玩）

## 开始使用

### 你需要

- Android 10+（minSdk 29）的手机
- 一个 **Google Gemini API key**（[aistudio.google.com/apikey](https://aistudio.google.com/apikey) 免费申请）
  - 依 Google 条款，使用者须年满 18 岁或所在地法定年龄
  - ⚠️ **免费层 API 的内容可能被 Google 用于训练模型**（付费层默认不会）——app 内首次使用会再次提醒
- 不配 key 也能用：拍照合成、图库、教程全部可用，只是角色只说预写的离线台词

### 安装

```bash
git clone https://github.com/Rainpalette/raintale.git
# 用 Android Studio 打开运行，或：
./gradlew installDebug   # 安装到已连接的设备
```

> 开发期可在项目根目录 `local.properties` 里加 `GEMINI_API_KEY=你的key` 作为调试回退（该文件不入库；release 构建永远不含任何 key）。

### 配置

装好后：**设置（右上齿轮）→ 账户 / BYOK → 粘贴 key → 保存**。key 只存在你的手机上。
不知道某个设定怎么调？设置页底部「☂️ 询问米娜」，直接问她。

需要注意的是 **「☂️ 询问米娜」** 功能需要先设置 API key，离线模式下无法使用。

## 隐私

一句话：**开发者没有服务器，不收集、也无法收集你的任何数据。**

| 数据 | 去向 |
|---|---|
| 照片、影集批注、设置、API key | 只存你的手机（系统云备份已关闭） |
| AI 评论开启时 | 照片经你自己的 key 直连 Google Gemini |
| 知识链接开启时（默认关） | 识别出的**名称文字**发给维基百科（不发照片） |
| 位置开启时（默认关） | 坐标经系统地理编码换成城市级地名，Gemini 只收地名 |
| 崩溃报告 | 只存本机，仅在你手动分享时离开设备（key 自动打码） |

完整政策见 [privacy-policy.md](privacy-policy.md)（app 内 设置→关于 也可直达）。


## 角色与授权（重要）

本项目对角色分三档，认证边界是"**是否是项目提供的功能**"：

| 档 | 怎么来 | 项目立场 |
|---|---|---|
| 官方角色库 | 项目自带（米娜等） | ✅ 项目认证、支持 |
| 自定义角色 | app 内写文字 persona（无图、无声） | ✅ 项目认证、支持 |
| 非官方角色 | 下载源码**改代码**塞入 | ❌ 与本项目无关，使用者自负一切后果 |

为此，本仓库的授权是**分层**的：

- **代码**：[MIT](../LICENSE)——自由使用、修改、分发。
- **官方美术 / 语音资产**：[All Rights Reserved](../ASSETS_LICENSE.md)——**不适用 MIT**，仅限官方未修改构建使用，禁止在修改版 / 衍生构建中分发，禁止用作其它角色、产品或 AI 训练素材。
- **第三方组件**：见 [THIRD_PARTY_LICENSES.md](../THIRD_PARTY_LICENSES.md)（AndroidX / Compose / CameraX / Room / Coil、ML Kit 等）。

fork 并分发修改版时，请**更换应用名称并移除官方资产**，避免与官方版本混淆。


---

<p align="center"><sub>灰色的雨里，站台那盏灯是唯一的暖光。</sub></p>
