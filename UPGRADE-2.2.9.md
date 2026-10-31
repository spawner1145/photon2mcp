# Photon 2.2.9 / LDLib2 2.2.44 迁移

## MCP 0.2.2

- 编译依赖改为当前实例的 Photon 2.2.9、LDLib2 2.2.44，依赖版本范围相应收窄；包含 KilaGraph 21.1.0.15 的嵌套编译库。
- 安装包允许 Photon 文件名前有显示名称前缀，但必须恰好找到一个指定版本，避免误用旧 JAR。安装前拒绝替换仍在运行的目标 Minecraft 实例，并备份旧 MCP。
- 原有编辑、时间轴、资源、Shader Graph、FXPack、Groovy 工具保留。新增 `photon_get_compatibility`，能直接发现运行版本、碰撞配置结构和地形深度兼容节点。
- Groovy 原生快捷导入只在存在游戏上下文时启用；普通脚本单元测试不初始化游戏注册表。
- JSON 写入时间轴时，将数字数组还原为原生 Float Bezier 列表；空数值通道保留常量基础值，避免新版速度轨道绘制空关键帧崩溃。UUID 整数数组不参与转换。

## 官方与本地分支的差别

官方仓库 `Photon-1.21-authority` 已有新的碰撞实现，并不是完全没有实体碰撞：`client.collision` 中提供共享区段快照、扫掠、接触求解、实体、液体、碰撞体、触发器与事件去重。它没有合并本地 `CollisionSettings` / `collision_emitter` 的数据格式，也没有本地 `TerrainDepthNode`。

官方还增加了 FX 实例、预加载、针对性缓存失效及编辑器修复。MCP 继续调用官方对象注册表，不复制本地旧碰撞运行时代码，不改写官方 Photon 或 LDLib2 JAR。

## 特效数据迁移

运行工作区 `tools/migrate_photon_229.py` 默认只审计，`--apply` 才备份并修改正式特效。范围为 `output` 顶层带 `.fxpack` 的效果目录，仅修改根工程／运行文件、`assets` 中的 NBT 与资源包内 NBT；历史备份、实验目录和旧预览不覆盖。

- `collision_emitter` → 官方 `particle_emitter`。
- 旧 `physics.collision` → 官方 `CollisionSetting` 的 `filters`、`events`、`response`、`advanced`，不再保存一个不存在的后端选择。
- 生物／玩家使用 `entities=true`、`filters.entityCategory=Living`；保留旧方块、实体、液体开关，不把其他效果统一改成实体碰撞。
- 旧扫掠碰撞子特效显式使用 `spawnAtContact`。方块专用子特效使用 `surface=Blocks`；环斩保留共享组去重。
- UUID、变换、Timeline、贴图、网格、颜色与普通 Shader 不改动。
- 螺旋丸旧工程中空的 `customSpace:{}` 改为新版 TransformRef 使用的空字符串；不替换非空引用或整个 Transform。
- 不支持精确映射的参数会报错，不静默吞掉（例如旧 `PIERCE` 反应、强制运行时 owner ID）。

修改前逐文件 SHA256 和备份位置写入 `output/backups/Photon229官方迁移前_时间戳/migration-record.json`，审计结果在工作区 `build/authority-migration`。迁移可重复执行，已迁移的 NBT 不再写入。重跑历史构建脚本后，应再次执行该迁移；不能直接拿旧脚本的产物覆盖已迁移的安装包。

## 仅地形深度

迁移将旧节点类替换为 `dev.photonmcp.compat.terrain.TerrainDepthNode`。该节点由 MCP 注册，保留 `raw`、`eye`、`visible` 三个输出；不使用 SceneDepth 假装地形深度。

- 原版世界在 cutout 地形结束、实体开始之前按需复制深度；编辑器在实体绘制前复制自己的场景深度。
- 拷贝按材质需求启停，世界／编辑器缓存分离，检查帧、视口、目标尺寸与世界身份，保存并恢复 GL 绑定。
- Iris 路径复用官方 Photon 已有的 Iris 管线访问接口，反射只在使用 shader pack 时解析，不把 Iris 类放进无 Iris 环境的必需签名。
- 采样无法确认有效时输出不可见，不回退到含实体的深度，避免环斩给玩家和生物染色。
- 此项不是官方 Photon 的原生能力：迁移后的环斩需要 MCP 0.2.2 兼容节点。可在编辑器节点库与 MCP 工具中发现。该节点不执行伤害或世界修改。

## 验证边界

构建与单元测试、迁移幂等与 NBT 往返、已安装依赖的 Mixin 方法签名可离线验证。编辑器、实体碰撞、实际 shader pack、地形与实体遮挡的画面对照必须启动游戏后验证；编译通过不代表这些视觉项目已经验证。

2026-10-31 已验证：29 项 JUnit、7 项迁移测试；7 套碰撞配置各测方块／生物／玩家共 21 个真实粒子用例；缓存／非缓存编辑器的环斩投影中实体均未染色，方块正常着色，关闭投影后的闲置深度复制为 0。39 个打包运行条目（38 个不同 ID）完整反序列化，169 个实际 GPU Shader 变体编译通过（含 Ara 拖尾变体）。50 次基础工具调用、25 次原生能力调用、42 次升级编辑器调用，以及 38 个工具的 stdio 发现／干净退出均通过。当前实例未安装／启用 Iris，不能将兼容代码和方法签名检查说成 shader pack 实测。

`output/astral-ara-slash` 是旧实验版本，与 `output/星辰液化刀光_独立版` 共用 `astral:razor_crescent`；本轮分别验证两个包内容，但没有覆盖当前安装的正式刀光来统一实验包。`rasengan.fxpack` 原先未安装，本轮修复其旧引用格式并验证可读，没有擅自安装这个额外效果。其他 22 个正式工作区包与实例中的同名包逐字节一致。
