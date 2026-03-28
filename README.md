# FakeMan - 智能假人插件

一个功能强大的Minecraft假人插件，允许玩家召唤假人帮助挂机、收集资源、保护玩家。

## 功能特性

### 核心功能
- **智能假人召唤**：召唤假人作为你的替身在线
- **两种模式**：挂机模式（原地不动）和跟随模式（智能模仿玩家行为）
- **经济系统**：召唤假人需要游戏币（支持Vault经济插件）
- **时间限制**：默认1小时在线时间，有权限可延长至24小时
- **背包系统**：36格大背包，右键假人即可打开
- **安全保护**：防止假人掉入虚空，死亡/受攻击时通知主人

### 智能AI
- **跟随模式**：假人会智能识别并模仿玩家行为
  - 玩家砍树 → 假人收集同类型木材
  - 玩家挖矿 → 假人收集同类型矿石
  - 玩家钓鱼 → 假人跟着钓鱼
  - 玩家被攻击 → 假人保护玩家

### 权限系统
- `FakeMan.use` - 基础使用权限
- `FakeMan.expend` - 扩展权限（召唤多个假人 + 延长在线时间）
- `FakeMan.admin` - 管理员权限

## 安装要求

### 必需插件
- **Vault** - 经济系统支持
- **Minecraft版本**：1.20.x

### 推荐配置
- 至少2GB内存
- 支持SQLite数据库

## 快速开始

### 1. 召唤假人
```bash
/fakeman summon
```
在当前位置召唤一个假人，需要30000游戏币

### 2. 查看假人列表
```bash
/fakeman list
```

### 3. 控制假人
```bash
# 切换模式
/fakeman mode <假人名> idle      # 挂机模式
/fakeman mode <假人名> follow    # 跟随模式

# 查看/设置时间
/fakeman time <假人名>           # 查看剩余时间
/fakeman time <假人名> 6h        # 设置6小时（需要权限）

# 传送到假人
/fakeman tp <假人名>

# 移除假人
/fakeman remove <假人名> confirm
```

### 4. 打开假人背包
- **右键点击假人**即可打开36格背包
- 假人背包内容会自动保存

## 详细命令

### 玩家命令
| 命令 | 描述 | 权限 |
|------|------|------|
| `/fakeman help` | 显示帮助信息 | FakeMan.use |
| `/fakeman summon` | 召唤假人 | FakeMan.use |
| `/fakeman list` | 查看假人列表 | FakeMan.use |
| `/fakeman info <假人名>` | 查看假人详细信息 | FakeMan.use |
| `/fakeman mode <假人名> <模式>` | 切换假人模式 | FakeMan.use |
| `/fakeman time <假人名> [时间]` | 查看/设置在线时间 | FakeMan.use（查看）<br>FakeMan.expend（设置） |
| `/fakeman remove <假人名>` | 移除假人 | FakeMan.use |
| `/fakeman tp <假人名>` | 传送到假人 | FakeMan.use |
| `/fakeman reload` | 重载配置 | FakeMan.admin |

### 管理员命令
| 命令 | 描述 | 权限 |
|------|------|------|
| `/fakeman admin list` | 查看所有假人 | FakeMan.admin |
| `/fakeman admin remove <玩家> <假人名>` | 移除玩家假人 | FakeMan.admin |
| `/fakeman admin reload` | 重载所有数据 | FakeMan.admin |

## 配置文件

配置文件位于 `plugins/FakeMan/config.yml`

### 主要配置项

```yaml
# 假人基础设置
dummy:
  default-health: 100          # 默认血量
  default-online-time: 3600    # 默认在线时间（秒）
  max-online-time: 86400       # 最大在线时间（秒）
  inventory-size: 36           # 背包大小
  default-mode: "idle"         # 默认模式

# 经济设置
economy:
  summon-cost: 30000           # 召唤费用
  enabled: true                # 启用经济系统

# 权限设置
permissions:
  use-permission: "FakeMan.use"
  expend-permission: "FakeMan.expend"
  admin-permission: "FakeMan.admin"

# 假人数量限制
limits:
  default-max-dummies: 1       # 默认最大假人数
  expend-max-dummies: 5        # 有权限时的最大假人数
  server-max-dummies: 0        # 服务器总限制（0=无限制）

# 假人AI设置
ai:
  follow-distance: 10          # 跟随距离
  max-follow-distance: 30      # 最大跟随距离
  smart-recognition: true      # 智能识别
  protect-distance: 15         # 保护距离

# 安全设置
safety:
  prevent-void: true           # 防止掉入虚空
  remove-on-death: true        # 死亡后消失
  notify-on-attack: true       # 受攻击时通知
```

## 数据存储

插件使用SQLite数据库存储数据：
- 假人位置、状态、背包数据
- 玩家数据、经济记录
- 所有数据自动保存，服务器重启后假人自动重新生成

数据库文件：`plugins/FakeMan/fakeman.db`

## 权限节点

### 玩家权限
- `FakeMan.use` - 基础使用权限
- `FakeMan.expend` - 扩展权限（可召唤最多5个假人，在线时间延长至24小时）

### 管理员权限
- `FakeMan.admin` - 完全控制权限

## 常见问题

### Q: 假人召唤失败？
A: 检查：
1. 是否有足够游戏币（30000）
2. 是否达到假人数量限制
3. 是否有使用权限（FakeMan.use）

### Q: 假人不跟随我？
A: 确保：
1. 假人模式设置为"follow"
2. 你在假人的跟随距离内（默认10格）
3. 假人AI已启用

### Q: 服务器重启后假人消失？
A: 假人数据会自动保存，重启后会重新生成。如果假人没有重新出现，请检查数据库文件。

### Q: 如何延长假人时间？
A: 需要 `FakeMan.expend` 权限，然后使用：
```bash
/fakeman time <假人名> 24h
```

## 更新日志

### v1.0.0
- 初始版本发布
- 基础假人召唤功能
- 智能跟随AI
- 经济系统支持
- SQLite数据存储
- 完整权限系统

## 技术支持

如有问题或建议，请：
1. 检查控制台错误日志
2. 确认配置文件正确
3. 确保依赖插件已安装
4. 查看本README文档

## 注意事项

1. **假人数量**：默认每个玩家1个，有权限最多5个
2. **在线时间**：默认1小时，有权限最多24小时
3. **背包安全**：假人背包内容会自动保存
4. **性能影响**：大量假人可能影响服务器性能
5. **数据备份**：定期备份数据库文件

---

**FakeMan - 让你的游戏生活更轻松！**