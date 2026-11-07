# MCP 0.2.3：Photon 2.2.11 / LDLib2 2.2.46

## 对照范围

2026-11-07，本机官方 Photon 仓库为 `3e274f0`（上一轮 `c37554c`），LDLib2 为 `27088488`（上一轮 2.2.44 的 `7a93a6ea`）。编译依据当前实例实际安装的 Photon 2.2.11、LDLib2 2.2.46 和 Photon 内嵌的 KilaGraph 21.1.0.15，不替换官方本体或把自定义补丁合并回官方仓库。

Photon 新增时间轴分段与 release 段、控制片段的 Drain 退出与 Seam 重启、Loop/PingPong 粒子生命周期，并修复停止后的排空和变速抖动。2.2.10 还修正了独立编辑器窗口的场景颜色／深度采样。LDLib2 更新主要涉及独立窗口与布局恢复、场景缓存取消、GUI 渲染和同步持久化。

## MCP 修改

- 编译版本、模组元数据、服务版本升为 0.2.3，依赖锁定当前已检验的版本范围。
- 时间轴下一次求值时间改用原生 `TimelinePlayer.localTime()`；新版时钟已移入 SectionClock，不能继续反射不存在的 `localTime` 字段。
- `photon_describe_timeline` 增加分段／release 样例、原生退出／接缝枚举和生命周期模式发现。`photon_update_timeline` 原生保存这些字段，仍支持撤销和重做。
- 新增 `photon_timeline_sections`（第 39 个工具）：`inspect`、`jump`、`queue`、`advance`、`release`；参数 `section` 供 jump/queue 使用。请求由下一个模拟 tick 或段尾执行，未生效／无目标时返回 `accepted=false`。工具不自动修改素材或把旧动画变成循环，播放／逐帧仍用 `photon_playback`。
- 编辑器发现优先使用已聚焦的独立窗口，再寻找游戏内编辑器或其他可见窗口。打开编辑器沿用 LDLib2 的 `EditorWindow.show`，避免独立窗口存在时又创建一份。
- 场景／编辑器截图读取所属 OS 窗口的离屏 target 和 GUI 缩放，不再误截游戏主 framebuffer。全屏截图仍读取游戏主窗口。
- 保留上轮碰撞原生数据与 MCP 的仅地形深度节点；没有把含实体 SceneDepth 当作地形深度。

## 素材是否需要改

本轮默认只审计，不执行旧版迁移程序，也不覆盖资源。正式目录下的工程、运行文件、模型、贴图、原生图和资源包的 SHA256 保存在工作区 `build/upgrade-2.2.11/asset-sha256.json`，重复审计必须与最初快照相同。

2.2.11 对未设置新字段的旧数据使用兼容默认值：生命周期 `Once`，控制片段退出 `CUT`，跨段 `AUTO`，无 sections 的时间轴按原先顺序播放。新字段只有作者明确启用时才改变表现；不能为了适配版本给所有火海或陨石随意添加循环／Drain。旧版 2.2.9 已迁移的碰撞配置未改，环斩仍只碰方块，四个指定发射物仍允许生物／玩家。

审计脚本：工作区 `tools/audit_photon_2211.py`。实时碰撞和打包加载测试可把报告写入本轮目录，避免覆盖上轮证据：

```text
node tools/verify_authority_runtime.mjs build/upgrade-2.2.11 2.2.11,2.2.46,0.2.3
node tools/verify_authority_packs.mjs build/upgrade-2.2.11
```

## 构建与安装

在 `PhotonMCP-1.21` 运行 `gradlew.bat test jar installMod --console=plain`。安装器会拒绝替换运行中的目标 Minecraft；旧 JAR 自动备份到 `build/install-backups/时间戳/`。Windows `.ps1` 脚本统一通过已安装的 PowerShell 7（`pwsh.exe`）调用。

## 验证状态

2026-11-07 实测结果：

- 31 项 JUnit 全部通过，包括新分段控制器／时钟 API、窗口 API 签名和 sections/release/exit/seam 数据保留。
- 50 次基础调用、25 次原生调用、42 次编辑器升级调用，以及 39 个工具的 stdio 发现／干净退出通过。
- 新分段测试通过 jump、queue、advance、release，缺失目标被拒绝；原生保存重开保留 section 重复次数、release 目标和 Drain/Restart 字段。
- Loop 和 PingPong 各在 8 tick 生命周期之后（发射器 age 15）仍保留一个粒子，未被误删。
- 编辑器弹出为独立 OS 窗口后仍可发现、读写、截图；场景与编辑器截图明确报告 `captureSource=editor_window`，已检查 PNG 内容，随后恢复到游戏窗口。
- 21 个碰撞用例通过：四个指定特效可碰方块、生物和玩家，环斩／剑气／火焰风压仍只碰方块。
- 环斩缓存与非缓存路径下，实体被着色像素均为 0，方块截面正常。测试期间冻结编辑器场景实体时钟，避免牛的头腿动画变化误报成染色；结束恢复原设置。闲置地形深度复制为 0。
- 24 个资源包内 39 个运行条目（38 个不同 ID）和 169 个 GPU Shader 变体加载／编译通过，包含 Ara 拖尾。
- 804 个素材／工程／包文件哈希与升级前一致；材质、贴图、网格、碰撞目标配置及动画数据未改。19400 次节点引用（含散装和包内重复）均能在当前库中解析。

证据分别位于工作区 `build/upgrade-2.2.11` 与本仓库 `build/qa/upgrade-2.2.11.json`。当前没有启用 Iris shader pack，不将原有兼容分支和方法签名检查等同于实际光影包验证。官方 Photon 与 LDLib2 仓库、JAR 均未修改。
