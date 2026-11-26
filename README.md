# PureTone 工作区

本目录存放对话记录、笔记与开发代码。**按类别分二级/三级目录，不混放。**

## 目录约定

```
D:\PureTone\
├─ docs\                     文档与记录
│  ├─ conversations\         对话记录归档（按 日期-主题.md）
│  └─ notes\                 技术笔记、调研、踩坑记录
├─ projects\                 完整项目，一个项目一个二级目录
│  └─ <project-name>\
│     ├─ src\                源码
│     ├─ docs\               本项目文档
│     └─ assets\             本项目资源
├─ scripts\                  单文件小工具，按用途分三级
│  ├─ audio\                 音频相关（标签、歌词、转码）
│  ├─ media\                 图像/视频相关
│  ├─ net\                   抓取、接口调用
│  └─ system\                系统运维、文件批处理
├─ assets\                   跨项目复用的素材
│  ├─ images\
│  └─ samples\               测试用小样本
├─ data\                     数据、中间产物、临时导出
├─ archive\                  过期或废弃内容（不删先归档）
└─ .workbuddy\               会话与记忆数据（勿删）
```

## 工具链放哪

能跨项目复用的**重型工具链**不放在本目录，统一放 `D:\ProgramCode`：

```
D:\ProgramCode\
├─ toolchains\               JDK / Python 解释器 / Node / Rust / Go SDK
├─ portable\                 便携版二进制（ffmpeg、mediainfo、7z 等）
└─ cache\                    pip / npm / cargo 下载缓存
```

判断标准：

- **项目专用依赖** → 项目目录内的虚拟环境（`projects\<name>\.venv`）
- **多项目复用、几十 MB 以上的运行时/SDK** → `D:\ProgramCode\toolchains`
- **单个可执行文件、绿色免安装** → `D:\ProgramCode\portable`
- **纯脚本、零第三方依赖** → `PureTone\scripts` 直接放，不必装任何东西

## 命名习惯

- 脚本：小写下划线，`lrclib_fetch.py`
- 记录：`2026-11-26-歌词工具.md`
- 项目：小写连字符，`lyrics-toolkit`
