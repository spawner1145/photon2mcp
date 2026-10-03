# Photon MCP：26.2 → 1.21.1

迁移日期：2026-10-03。

## 目标与隔离

- 源项目：`D:\code\photon mcp\PhotonMCP-26.2`，未修改。
- 新项目：`D:\code\photon mcp\PhotonMCP-1.21`，复制源码、bridge、测试与案例；不复制 Git 历史、Gradle 缓存、构建结果或原测试实例。
- 目标实例：`D:\game\.minecraft\versions\1.21.1-NeoForge_21.1.252`。
- 保留目标实例原有 Photon 2.2.7、LDLib2 2.2.41 和它们自带的 KilaGraph。
- 输出：`build/libs/photon-mcp-neoforge-1.21.1-0.2.0.jar`。

## 适配内容

- Java 字节码目标由 25 改为 21；NeoForge 改为 21.1.252，FML 与模组依赖范围对应 1.21.1。
- 编译使用 ModDevGradle 生成的 1.21.1 开发映射和目标实例的依赖模组 JAR。
- 编辑器查找使用 `ModularUI.of(Minecraft.getInstance().screen)`；打开屏幕使用 `setScreen`。
- NBT 读取恢复为 `getCompound`、`getList(key, elementType)`、`getString`、`getAllKeys` 和 `ListTag.get(0)`；SNBT 使用 `TagParser.parseTag`。
- `Identifier` 改为 `ResourceLocation`；纹理创建使用 1.21.1 的 `DynamicTexture(NativeImage)`。
- 截图在渲染线程用 `Screenshot.takeScreenshot(client.getMainRenderTarget())` 读取 framebuffer，再在虚拟线程处理裁切、缩放和 PNG。使用 1.21.1 原生 ARGB 转换，避免红蓝通道反转。
- 保留三个原编辑器 Mixin、37 个 MCP 工具、Groovy 4.0.28 Jar-in-Jar 和 host/port 配置。
- 更新 stdio 路径、启动脚本、构建安装脚本和示例中的版本相关 API。

## 实测结果

- `gradlew.bat build`：成功；21 个 JUnit 测试，0 失败、0 错误。
- 正式游戏库启动独立测试客户端，日志确认 Minecraft 1.21.1、NeoForge 21.1.252、Photon 2.2.7、LDLib2 2.2.41、KilaGraph 21.1.0.15 和 Photon MCP 0.2.0 完成加载。
- `tests/integration.mjs http://127.0.0.1:8766/mcp`：50 次成功工具调用，覆盖层级、属性、批量回滚、历史、时间线、保存读取、纹理导入和 PNG 截图。
- `tests/native-integration.mjs http://127.0.0.1:8766/mcp`：25 次成功调用，覆盖 Groovy、反射、跨调用状态、异常回滚、撤销重做、关键帧和 FXPack 生命周期。
- `tests/bridge-smoke.mjs 8766`：initialize、37 个工具发现、EOF 正常关闭，退出码 0。
- 已查看真实编辑器截图，确认 MCP 菜单、场景和 UI 可见，PNG 色彩正常。

验证使用本项目的 `run-integration/`，端口 8766；存档来自原 1.20.1 原版实例的复制副本。未打开或修改原存档，未向目标游戏目录写入测试世界或测试特效。

日志：`build.log`、`build/qa/game-stdout.log`、`build/qa/integration.log`、`build/qa/native-integration.log`；单元测试报告：`build/reports/tests/test/index.html`；截图：`run-integration/mcp-smoke/editor.png`。

## 边界

仅验证上述具体版本组合，不承诺 1.21.0 或其他 Photon / LDLib2 版本。原 26.2 案例中的传送门动画和像素级扭曲统计未重新执行，不将其作为 1.21.1 的实测结果。两个游戏实例若同时启动，需要不同的 MCP 端口；正式实例默认端口仍是 8765。
