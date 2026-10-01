# Photon MCP 0.2 能力覆盖与边界

## 先纠正 0.1 的范围

0.1 提供了 32 个专用工具，**没有 execute code，不是所有 Photon 功能的完整封装**。不能把当时的交付说成“所有 Blender MCP 等价功能 / 所有 Photon 能力都已经完成并实测”。

0.2 提供两层入口：37 个专用工具，以及与 Blender `execute_blender_code` 思路对应的 **`photon_execute_code` 通用 JVM 入口**。后者可以访问当前安装版本的 Photon / LDLib2 / KilaGraph 原生 API，不限于专用工具的白名单。

因此：**原生 API 能力可达**，不代表**每个菜单按钮都有独立 MCP 工具**，也不代表**所有组合、GUI 事件路径和渲染配置都已逐项验证**。

## 对应关系

| 能力 | MCP 入口 | 验证范围 |
| --- | --- | --- |
| 读取场景、属性、层级、真实 API | scene/object/project、describe、api_lookup、list_api_classes | 已实测序列化属性与原生类/字段/方法查询 |
| 添加、删除、复制、变换、父子关系 | create/delete/duplicate/update/transform/reparent | 已实测，包括批量回滚与撤销 |
| 非序列化属性、内部对象、任意原生方法 | execute_code、api.read/write、JVM 反射 | 已实测原生配置、私有字段及编辑器渲染器操作；并非所有方法逐一测试 |
| 播放、暂停、重启、定位、步进 | playback | 已实测，定位保留原生预览计数语义；同时报告轨道求值时钟 |
| 关键帧、曲线、表达式、轨道、片段、标记 | update_timeline + execute_code 操作原生 Timeline/AnimatedProperty | 已实测关键帧添加/删除、ripple insert、求值、撤销；其余轨道通过原生 API 可访问 |
| Camera 旋转、缩放、中心、显示模式 | set_view；投影/FOV/更多相机能力通过 execute_code | 已实测专用相机控制和原生方法查询；未逐项实测每种投影设置 |
| 材质、纹理、曲线、梯度、网格、资源 | describe/read/write_resource/import_texture + execute_code | 已实测纹理导入、图资源复制/保存、原生 CustomShaderMaterial 与 GPU 数据配置 |
| Shader Graph / Function Graph / Fullscreen Graph / Render Graph 的节点和端口 | read/write_resource + execute_code 直接访问 Photon/KilaGraph 对象模型 | API 可达；没有另做完整节点 GUI 操作录制器，也没有逐个节点验证 |
| 物理、Force Field、模型粒子、VAT、拖尾、子发射器、自定义 GPU 数据 | 对象/组件数据 + execute_code | 可通过已安装 Photon 的原生配置/API 调用；没有逐种特效组合实测 |
| 音频、信号、速度、激活、控制、后处理轨道 | timeline + execute_code | 原生 API 可达；未把所有音频输出、事件回调与后处理图组合都实测一遍 |
| 保存/打开项目、导出效果 | new/open/save/export_effect | 已实测 `.fxproj` / `.fx` / SNBT |
| 打包依赖、列举/删除包内效果 | fxpack | 已实测原生导出、显式 Shader 资源嵌入、列举和删除 |
| 选择、检查器、操作历史、检查点 | select / undo_redo / checkpoint | 已实测 |
| 原生剪贴板、设置、面板、快捷键动作 | editor_action list/invoke + execute_code | 已实测列举与原生 undo；其他动作仍有原生 focus/GUI 上下文要求 |
| 截图和视觉闭环 | screenshot | 已实测真实 GPU PNG、区域裁切和 51 帧动画录制 |
| 原生菜单、窗口、UI 事件和其他未封装入口 | execute_code 操作 editor/client/UI 对象，或使用 editor_action | API 可访问；不能把“有通用入口”宣传成“所有 GUI 自动化均验证通过” |

## 与 Blender MCP 不等价的部分

- 执行语言是 Groovy/JVM，不是 Python/bpy；访问的是 Photon 和 Minecraft 的原生对象模型。
- Blender 的网格建模/雕刻/骨骼/Blender 文件格式不是 Photon 原生能力，不能直接同名照搬。
- Poly Haven / Sketchfab / Tripo / Rodin 等第三方搜索和生成服务不是 Photon 内置功能；本模组没有凭空加入这些服务。
- 通用代码执行不修复原 Photon/LDLib/KilaGraph 的自身缺陷或本来就不支持的序列化/渲染组合。
- 任意代码的文件、GPU、世界、音频、UI、全局状态副作用不能由 FX 撤销系统完整恢复。主线程代码不能阻塞等待需要主线程完成的 future。

## 原生 API 工作流

1. 查询安装版本与当前编辑器状态。
2. 用 `photon_list_api_classes` 找实际类；用 `photon_api_lookup` 获取字段、方法重载、构造器、枚举，不猜签名。
3. 普通对象编辑用专用工具；复杂时间线/图/渲染/原生 UI 操作使用 `photon_execute_code`。
4. 用 `.fxproj`、检查点或操作历史保存需要恢复的数据；代码的非 FX 副作用另行处理。
5. 定位和截图检查结果，再保存/导出。

直接创建原生发射器、GPU 通道、场景折射 Shader、动画轨道、关键帧和标记的完整案例：`examples/vergil-cross-gate.groovy`。它通过 MCP 在实际游戏内运行；不是外部伪造的项目截图。

## 已完成的实测

- 21 个单元测试。
- 基础端到端 50 次成功工具调用。
- 扩展原生能力回归 25 次成功工具调用。
- 37 个工具的 HTTP / stdio 发现。
- 初版“横线 → 竖线 → 开门”及参考修订版“自上向下竖斩 → 自左向右横斩 → 上下同步开门”特效与原生动画轨道。初版项目和 Shader 保留。
- 初版同帧扭曲关闭/开启 A/B 对照：12,599 个像素显著变化；51 帧真实 GPU 动画截图。参考修订版有 16,477 个像素显著变化、81 帧 GPU 截图。
- `tests/vergil-visual.py` 根据真实截图验证定向斩切、同步开门、横向门体（宽高比 1.764）、持续边框运动（开门后两帧发光掩码变化 11,569 像素）与场景折射。

配置依旧只有 host 和 port；没有 token、鉴权、代码沙箱或额外授权弹窗。
