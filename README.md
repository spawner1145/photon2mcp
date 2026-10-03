# Photon MCP 0.2.0 — NeoForge 1.21.1

独立的客户端附加模组，让外部 MCP Agent 在游戏内 Photon Editor 中实时创建和修改特效。没有修改 Photon / LDLib2 原始源码，也不需要替换原模组。

0.2 增加了通用 Groovy/JVM 代码执行、真实 API 反射查询、安装类目录、原生编辑器动作和 FXPack 管理。**37 个专用工具不是能力白名单**：其余 Photon / LDLib / KilaGraph 能力可以通过代码入口访问。完整覆盖说明和未逐项验证的边界见 `COVERAGE.md`，不要把“API 可调用”理解为“每一个 GUI 操作都已经独立测试过”。

## 支持的环境

- Minecraft **1.21.1** / NeoForge **21.1.252** / Java **21**。
- Photon **2.2.7**，使用 `photon-neoforge-1.21.1-2.2.7-all.jar`。
- LDLib2 **2.2.41**，使用 `ldlib2-neoforge-1.21.1-2.2.41-all.jar`。
- 这版依据工作区源码和游戏中实际安装的 JAR 实现；元数据锁定了 Photon / LDLib2 的兼容区间，升级后需要重新验证 API / Mixin。

本目录从 `PhotonMCP-26.2` 独立复制并迁移，原版本保持不变。目录名 `PhotonMCP-1.21` 对应的实际游戏版本是 **1.21.1**，不包含对 1.21.0 的兼容承诺。迁移内容和验证记录见 `MIGRATION.md`。

## 安装和游戏内使用

编译好的文件：`build/libs/photon-mcp-neoforge-1.21.1-0.2.0.jar`。升级时移走旧版 Photon MCP JAR，不能同时安装两个版本。

安装位置：`D:\game\.minecraft\versions\1.21.1-NeoForge_21.1.252\mods`。保留原来的 Photon 和 LDLib2 JAR。

重启游戏，进入单人世界，用 `/photon_editor` 打开编辑器。顶部会变为：

```text
文件   视图   MCP
```

MCP 菜单提供：连接状态、Host / Port 配置、启动/停止服务、复制地址。服务在客户端启动时自动启动；菜单停止只影响 MCP，不关闭编辑器。

配置文件：游戏实例的 `config/photon-mcp.json`，**只有两个配置项**：

```json
{
  "host": "127.0.0.1",
  "port": 8765
}
```

默认 MCP 地址：`http://127.0.0.1:8765/mcp`。

没有 token、权限弹窗、Origin 检查或地址白名单。Host 支持 `0.0.0.0` 监听网络接口；远端客户端使用运行游戏电脑的 IP 连接。监听网络接口时，能访问该端口的客户端都能调用工具。

状态页面显示服务状态、地址、最近 5 分钟有活动的 MCP 客户端、请求数、最近工具、最近请求时间和错误。HTTP 没有持续 socket 在线状态，所以「最近客户端」不是永久在线连接数；客户端 DELETE 会话后立即移除。

## 外部 Agent 接入

### 支持 HTTP MCP 的客户端

使用 `examples/mcp-http.json` 的配置格式，配置名可自定：

```json
{"mcpServers":{"photon":{"url":"http://127.0.0.1:8765/mcp"}}}
```

传输是 **Streamable HTTP**，JSON-RPC 响应为 `application/json`；支持 initialize、initialized notification、ping、tools/list、tools/call 和会话 DELETE。可协商 `2025-03-26`、`2025-06-18`、`2025-11-25`。不提供可选 GET SSE 流，GET 返回 405；不支持旧式 `/sse` 端点。

### ccswitch内

```json
{
  "type": "http",
  "url": "http://127.0.0.1:8765/mcp"
}
```

### 仅支持 stdio 的客户端

使用 `examples/mcp-stdio.json`。需安装 Node.js（使用内置 fetch，不用 npm 安装依赖）：

```json
{
  "mcpServers": {
    "photon": {
      "command": "node",
      "args": ["D:/code/photon mcp/PhotonMCP-1.21/bridge/photon-mcp.mjs", "--host", "127.0.0.1", "--port", "8765"]
    }
  }
}
```

stdio bridge 不是第二个编辑器服务器，只把 MCP 消息和 PNG image 内容转发给游戏内 HTTP 服务；stdout 只输出协议 JSON。

## 工具能力

**37 个工具**，运行时可以通过 `tools/list` 获取准确输入 schema。

| 类别 | 工具 |
| --- | --- |
| 连接 / 编辑器 | `photon_get_server_status`, `photon_get_editor_state`, `photon_open_editor` |
| 通用原生能力 | `photon_execute_code`, `photon_api_lookup`, `photon_list_api_classes`, `photon_editor_action`, `photon_fxpack` |
| 实际 API / 数据发现 | `photon_list_object_types`, `photon_describe_object_type`, `photon_describe_component`, `photon_describe_timeline` |
| 读取场景 | `photon_get_scene_info`, `photon_get_object`, `photon_get_project` |
| 特效对象编辑 | `photon_create_object`, `photon_update_object`, `photon_set_transform`, `photon_reparent_object`, `photon_duplicate_object`, `photon_delete_object`, `photon_select_object` |
| 预览 / 视觉闭环 | `photon_playback`, `photon_set_view`, `photon_screenshot` |
| 项目 / 导出 | `photon_new_project`, `photon_open_project`, `photon_save_project`, `photon_export_effect` |
| 资源 | `photon_list_resources`, `photon_import_texture`, `photon_read_resource`, `photon_write_resource` |
| 批量 / 历史 / 时间线 | `photon_batch`, `photon_checkpoint`, `photon_undo_redo`, `photon_update_timeline` |

支持粒子、Beam、Trail、AraTrail、Force Field 和空分组，以及 Photon 序列化系统暴露的粒子配置、材质、曲线、梯度、形状和轨道。对象的默认 JSON、原始 SNBT、递归 NBT 类型 schema 均可查询。

资源工具也能读取和复制 Photon / KilaGraph 的 Shader Graph、Render Graph、Fullscreen Graph 等 NBT 资源；图内部操作使用 Photon 原生数据，不是 Blender 的 Python API。没有任意 Java/Python 代码执行接口，也没有第三方模型生成 / 资产网站下载集成。

这个设计借鉴 Blender MCP 的「场景信息 → 对象操作 → 属性检查 → 截图反馈」闭环，但采用嵌入式 MCP 服务，不需要额外运行 Python 服务。

## 典型工作流

1. 进入世界，`photon_open_editor`，然后 `photon_new_project` 或 `photon_open_project`。
2. `photon_describe_object_type({"type":"particle_emitter"})` 查询真实字段。
3. 创建对象：

```json
{
  "type": "particle_emitter",
  "name": "Sparks",
  "patch": {
    "config": {
      "maxParticles": 512,
      "emission": {"emissionRate": {"data": {"number": 3}}},
      "startLifetime": {"data": {"number": 30}}
    }
  }
}
```

4. 用返回的 UUID 调用 `photon_set_transform`。旋转是度，位置/缩放是父级局部坐标。
5. `photon_playback({"action":"seek","tick":30})`，再 `photon_screenshot({"region":"scene","max_size":1280})`。
6. 根据截图迭代，用 `photon_save_project` 保存 `.fxproj`。

完整 Agent 工作约定见 `examples/agent-instructions.md`。

### 原生代码执行

`photon_execute_code` 在 Minecraft 主线程运行 **Groovy/JVM**，不是 Python，也不是只能调用专用工具的字符串命令解释器。可直接创建 Photon 原生类、调用方法、读取/修改属性，并访问 Shader Graph / Render Graph / 时间线等完整对象模型。Groovy 4.0.28 运行库以 Jar-in-Jar 随模组提供；客户端不用额外安装脚本引擎。

绑定变量：`client`、`editor`、`project`、`fx`、`runtime`、`scene`、`timeline`、`mcp`、`api`、`args`。编辑器未打开时相关变量为 null。脚本变量跨调用保留，`reset_context:true` 清空它们；每次重新绑定当前编辑器对象。

```json
{
  "code": "import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction\ndef emitter = mcp.object('Sparks')\nprintln emitter.config.startSpeed\nemitter.config.startSpeed = NumberFunction.constant(0.4)\nreturn emitter.id().toString()"
}
```

先用 `photon_list_api_classes` 找类，再用 `photon_api_lookup` 查实际字段、重载、构造器和枚举。`expression` 可定位活对象，例如 `mcp.object('Sparks').config.renderer`。私有字段可通过 `api.read(object, 'field')` / `api.write(object, 'field', value)` 访问；其他反射能力仍可直接使用 JVM API。

默认 `record_history:true` 将脚本直接修改的 FX 数据提交为一个撤销步骤；异常时恢复这部分 FX 数据。**不恢复文件、世界、GPU、UI 或其他外部副作用，也不回滚脚本变量**。脚本自己调用 `mcp.call(...)` 推历史时用 `record_history:false` 避免重复记录。关闭历史后自己负责原生缓存/运行时刷新，或传 `reload:true`。`mcp.call` 返回 JSON，`mcp.object` 返回真实 IFXObject。

代码执行没有认证、沙箱、能力白名单或代码许可弹窗。不要在主线程 sleep、无限循环或等待截图/资源重载 future；截图应在脚本返回后通过独立 MCP 调用采集。Java 构造器/方法的数字类型需要正确，Groovy 运算结果可能需要显式转为 float。

### 时间与打包

`photon_playback.tick` 是 Photon 编辑器的**预览计数器**，与原生 `simulateTo` 相同；粒子入队和 TimelinePlayer 的“先求值后前进”会使原生轨道求值时钟略有差异。`photon_get_editor_state` 同时返回 `timelineEvaluationTick` 和 `timelineNextTick`，不要假设三个时钟永远相等。长预览定位分段模拟，避免原生单次 10,000 tick 快速模拟上限截断结果。

动画轨道的配置属性、关键帧增删/移动、插入时间、曲线/表达式片段、Control / Activator / Audio / Signal / Speed / PostProcess 均可通过原生时间线 API 操作；已有 `photon_update_timeline` 也能提交完整序列化数据。真实原生关键帧示例见 `examples/vergil-cross-gate.groovy`。

`photon_fxpack` 支持导出、列举和删除包内效果。默认使用 Photon 原生依赖收集规则，跳过收件人通常已有的模组/原版资源；`include_assets` 可显式嵌入这些资源的 ID，例如自定义 Shader 的 JSON / VSH / FSH。

可运行的示例：`node examples/create-demo.mjs`，通过 MCP 生成带橙色/青色粒子环和散射核心的特效，并保存项目、导出 `.fx`、拍摄编辑器和场景 PNG。脚本会替换当前项目，运行前先保存自己的工作。示例使用工具查询默认数据后再组装配置，不依赖硬编码整份 Photon 对象。

已生成的示例项目 `examples/mcp-energy-core.fxproj` 和效果文件 `examples/mcp-energy-core.fx` 也包含在工作区内，可直接在 Photon 中打开。

### 原 26.2 案例：维吉尔风格十字空间门

本节的动画录制和像素统计来自原 26.2 版本，不是本次 1.21.1 迁移的新验证结果。案例脚本的 `ResourceLocation` 已适配 1.21.1，但本次没有重新执行完整动画视觉回归。

运行 `node examples/create-vergil-gate.mjs`，通过 MCP 代码入口创建门体、裂隙火花、原生动画轨道与标记，并保存到 `examples/vergil-cross-gate-reference/`。会替换当前项目，先保存自己的工作。此前 `examples/vergil-cross-gate/` 的第一版文件和原 Shader 保留，新版使用独立的 `photon_mcp:vergil_gate_reference`。

- 2–7 tick：先竖斩，从上到下推进；5 tick / 0.25 秒完成，不是从中心向两端长出。
- 12–17 tick：再横斩，从左到右推进；5 tick / 0.25 秒完成，带移动亮头和完成冲击闪光。
- 17–23 tick：完整十字短暂停顿；23–39 tick：沿完整横向切线，上下两侧使用同一开门进度同时撕开。
- 门体横向比纵向长，几何轴比约 1.76；银白核心、淡蓝紫电弧、深黑紫内部和细碎光屑。边缘具有持续流动的多尺度噪声、断裂支路与卷曲细丝。
- 39–174 tick：维持门体并持续折射周围场景；174–197 tick：闭合。总粒子寿命 200 tick / 10 秒。
- 使用 `SamplerSceneColor` 对真实场景做屏幕空间折射和色散，不只是发光贴图；不修改真实世界几何，也不包含传送逻辑。
- `.fxpack` 显式携带门 Shader 的三个资源文件；原 Photon / LDLib2 仍是依赖。

交付文件位于 `examples/vergil-cross-gate-reference/`：`vergil-cross-gate.fxproj`、`vergil-cross-gate.fx`、`vergil-cross-gate.fxpack`、`vergil-cross-gate.gif`、`stages.png`、`refraction-comparison.png`、`visual-verification.json`。通过 `node examples/capture-vergil.mjs` 录制 81 个真实 GPU 帧（前 40 tick 逐 tick 采集），再用安装了 Pillow 的 Python 运行 `examples/render-vergil-preview.py` 生成按实际 tick 间隔播放的 GIF 和对照图。

运行 `python tests/vergil-visual.py` 检查截图中的斩切方向、同时开门、宽高比、持续边框运动与折射 A/B。当前实测门体发光边界宽高比 1.764；WarpStrength 0 / 0.06 对照有 16,477 个像素显著变化，开门后两帧的发光边框掩码有 11,569 个像素变化。这是屏幕空间折射和视觉特效，不包含传送逻辑，也不是对游戏参考的逐像素复刻。

录制用的棋盘墙是**预览世界测试夹具**，不写入 FX，也不修改真实存档。为避免原生缓存重编译等待影响这组对照，该录制脚本使用预览渲染器的即时渲染路径。

### 修改规则

- `patch` 深合并 NBT Compound，保留已有数字的 byte/int/float 等类型。
- 数组整体替换；JSON null 删除键。原始 `snbt_patch` 提供精确类型控制。
- 对象结构使用专用 transform / reparent 工具，不在普通数据 patch 中改 ID / 父子关系。
- 禁用的 ToggleGroup 可能只序列化 `_enable`。先启用再查询，可以获得其余字段。
- 批量操作通过 alias 引用新对象，一次提交、一次撤销、一次预览更新；失败不修改实际项目。
- 编辑请求在 Minecraft 主线程执行，GPU 截图在后续完整帧读回，不在网络线程操作游戏状态。
- 对象编辑不是 60Hz 连续输入流；大规模构建应使用 batch，避免反复重建预览。

## 构建

标准 NeoForge ModDevGradle 构建：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
.\gradlew.bat build
```

默认从 `gradle.properties` 中的游戏目录获取 Photon / LDLib2 JAR，也可传 `-PgameDir=...`。构建不会把依赖模组打包进本模组。

### 本机构建与安装脚本

`build-local.ps1` 使用同一个 ModDevGradle 构建，并从目标实例读取 Photon / LDLib2 依赖。首次构建需要联网下载 NeoForge 开发依赖；缓存准备好后可加 `-Offline`：

```powershell
.\build-local.ps1 -Test -Install
```

只构建用 `.\build-local.ps1`，离线构建、测试并安装用 `.\build-local.ps1 -Offline -Test -Install`。可以用 `-JavaHome` 和 `-GameDir` 指定其他本机路径。1.21.1 不再使用原 26.2 的直接游戏 classpath 构建文件，避免混淆游戏 JAR 与开发映射不匹配。

## 验证

- JUnit：配置仅 host/port、HTTP 协议与会话、工具 schema、工具错误、JSON/SNBT 数据合并和 PNG content 格式。
- 游戏内端到端：`node tests/integration.mjs`。**此脚本新建并替换当前 Photon 项目，测试前保存自己的项目。**
- 当前版本已通过 **21 个 JUnit 测试**；原端到端脚本验证 **50 次成功工具调用**，新增 `node tests/native-integration.mjs` 验证 **25 次原生能力调用**，包括代码执行、反射、断言错误回传、FX 回滚/撤销、关键帧增删/时间插入/定位、长预览定位和 FXPack 导出/列举/删除。
- stdio bridge 已验证 initialize、tools/list 和正常关闭（退出码 0）。0.2 工具目录为 37 个。
- 原 26.2 的空间扭曲 A/B 与动画视觉统计保留在案例章节；本次未重新执行 `tests/vergil-visual.py`，不将这些统计计入 1.21.1 迁移验证。
- 联调用的是 `run-integration/` 独立实例和复制的存档；没有在原游戏存档上进行特效测试。
- 测试覆盖创建、变换、修改、层级、复制/删除、选择/检查器、暂停定位、undo/redo、检查点、batch rollback、时间线、保存/读取/导出、导入纹理、资源复制和真实 GPU PNG 截图。

本次 1.21.1 回归在独立测试实例的端口 **8766** 上执行：

```powershell
.\tests\launch-test.ps1 -WorldDir '已有兼容存档目录'
node tests/integration.mjs http://127.0.0.1:8766/mcp
node tests/native-integration.mjs http://127.0.0.1:8766/mcp
node tests/bridge-smoke.mjs 8766
```

启动脚本把存档复制到 `run-integration/saves/Photon MCP Integration Test`，已有测试副本不会被覆盖。不要传入 26.2 等高于 1.21.1 的存档。测试日志在 `build/qa/`；编辑器截图在 `run-integration/mcp-smoke/editor.png`。正式安装仍默认监听 **8765**；若同时启动 26.2 与 1.21.1，请为其中一个实例设置不同端口。

## 边界

- 需要先进入世界，不能从标题页面构造依赖世界注册表的 Photon 场景。
- 新建/打开项目直接替换，不弹出保存确认；由 Agent 先保存必要工作。
- undo / 检查点保存 FX 对象和时间线，不会撤销文件保存、纹理导入或资源文件覆写。
- `.fx` 与 `.fxproj` 的外部资源引用不自动嵌入。跨机器分发可用 `photon_fxpack` 或原 Photon File 菜单的 `.fxpack` 功能。
- 外部原生浮动窗口不在游戏主 framebuffer 内；当前截图仅保证主游戏编辑器窗口。
- 游戏关闭/暂停渲染时不能采集新帧；网络调用超时返回工具错误。
