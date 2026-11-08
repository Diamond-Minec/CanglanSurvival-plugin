# 沧蓝工坊 · 整合生存服务端核心（CanglanSurvival）

> 作者：**沧蓝工坊**　适配：**Paper 1.21.11**　版本：**v1.0.0**
>
> 把生存服最常用的 **21 个功能** 整合进 **一个插件**，全部中文界面。
> 服主可以在游戏聊天框里直接输入指令，随时 **启用 / 禁用任意模块**，还能 **单独给某个玩家开权限或禁权限**。

**开箱即用：** 已编译好的插件在 [`dist/CanglanSurvival-1.0.0.jar`](dist/CanglanSurvival-1.0.0.jar)，丢进服务器 `plugins/` 就能跑。

| 文档 | 内容 |
| --- | --- |
| README.md（本文） | 功能总览、模块表、指令、权限、配置说明 |
| [docs/安装部署.md](docs/安装部署.md) | 从零开服的完整步骤、必改配置、性能建议、常见问题 |
| [docs/指令速查.md](docs/指令速查.md) | 全部指令一页查完（服主 + 玩家 + 领地规则 + 权限节点） |

### 项目结构

```
沧蓝工坊-整合生存包/
├── dist/
│   └── CanglanSurvival-1.0.0.jar    ← 编译好的插件（直接丢 plugins/）
├── docs/
│   ├── 安装部署.md
│   └── 指令速查.md
├── src/main/java/com/canglan/survival/
│   ├── CanglanSurvival.java         插件主类
│   ├── core/                        模块框架、指令路由、经济、权限、GUI、工具
│   └── module/                      21 个功能模块
├── src/main/resources/
│   ├── plugin.yml                  指令与权限声明
│   └── config.yml                  全部配置（中文注释）
├── pom.xml                           Maven 构建
├── LICENSE
└── README.md
```

自行编译：

```bash
mvn package
# 产物：target/CanglanSurvival-1.0.0.jar
```

---

## 一、为什么用它

| 传统做法 | 本插件 |
| --- | --- |
| 装 LuckPerms、TAB、公告、计分板、经济、商店、领地……十几个插件 | **只装一个 jar**，21 个功能全部内置 |
| 每个插件一份配置，汉化参差不齐 | **全中文**，所有提示语、界面、配置注释都是中文 |
| 想临时关掉某个功能要重启服务器或删插件 | 游戏内 **`/cl 禁用 商店`** 立即生效，无需重启 |
| 给某个玩家开权限要翻权限插件 | **`/cl 玩家 玩家名 允许 商店`** 一条指令搞定 |
| 十几个插件互相抢事件、报错难排查 | 单插件内部调度，模块独立启停，互不影响 |

---

## 二、21 个功能模块

| # | 模块 | ID | 主要功能 | 主要指令 |
| --- | --- | --- | --- | --- |
| 01 | 权限系统 | `perm` | LuckPerms 风格的组、前缀、继承、玩家权限节点 | `/perm` |
| 02 | TAB 列表 | `tab` | 自定义 TAB 页眉页脚、玩家列表名称、延迟/TPS 显示 | `/tab` |
| 03 | 信息公告栏 | `notice` | BOSS 栏轮播公告 + 定时全服广播，游戏内增删公告 | `/notice` |
| 04 | 计分板 | `scoreboard` | 右侧实时计分板：在线、金币、击杀、延迟、TPS | `/sb` |
| 05 | 经济系统 | `economy` | 金币余额、转账、财富榜、管理员发放/扣除 | `/money` `/pay` `/baltop` `/eco` |
| 06 | 系统商店 | `shop` | 图形化商店，用金币买卖物品，价格可配置 | `/shop` |
| 07 | 领地保护 | `claims` | 立体领地，最大 64×64×64：选两点圈地，护方块/容器/液体/爆炸，领地内禁 PvP，可信任好友 | `/claim` `/unclaim` |
| 08 | 地标传送 | `warps` | 全服公用传送点，管理员一键设点 | `/warp` `/warps` `/setwarp` `/delwarp` |
| 09 | 随机传送 | `rtp` | 随机传送到安全位置，避开岩浆/水/悬空 | `/rtp` |
| 10 | 家园 | `homes` | 玩家多个家，数量上限由权限决定 | `/sethome` `/home` `/delhome` `/homes` |
| 11 | 传送请求 | `tpa` | `/tpa` 请求传送、`/tpahere` 邀请、接受/拒绝 | `/tpa` `/tpahere` `/tpaccept` `/tpdeny` |
| 12 | 聊天管理 | `chat` | 聊天格式（带组前缀）、禁言、刷屏冷却、违禁词 | `/chat` |
| 13 | 玩家举报 | `report` | 举报玩家，提醒在线管理员并留档 | `/report` `/reports` |
| 14 | 处罚管理 | `punish` | 警告、踢出、封禁、解封、禁言 | `/warn` `/kick` `/ban` `/unban` `/mute` |
| 15 | 登录验证 | `auth` | 离线服注册/登录，未登录不能移动交互 | `/register` `/login` `/changepassword` |
| 16 | 欢迎引导 | `welcome` | 新玩家欢迎、新手礼包、服务器规则 | `/welcome` |
| 17 | 每日签到 | `signin` | 每日签到领金币，连续签到奖励更高 | `/signin` |
| 18 | 排行榜 | `top` | 金币/击杀/死亡/在线时长/挖掘/放置榜单 | `/top` |
| 19 | 玩家统计 | `stats` | 查看玩家详细数据 | `/stats` |
| 20 | 服务器监控 | `monitor` | TPS、内存、实体、区块，低 TPS 自动提醒 | `/monitor` |
| 21 | 钟表菜单 | `menu` | 进服发一个「功能菜单」钟表，右键打开图形菜单 | `/menu` |

> 另外内置 **中文指令**：`/公告`、`/商店`、`/签到`、`/金币`、`/菜单` 等 90 多个中文写法都能直接用，自动转成对应英文指令。

---

## 三、安装

1. 服务器必须是 **Paper 1.21.11**（或同版本系列），Java 21 及以上。
2. 把 `CanglanSurvival-1.0.0.jar` 放进服务器 `plugins/` 文件夹。
3. 启动服务器。首次启动会自动生成：
   ```
   plugins/CanglanSurvival/
   ├── config.yml         # 全部可调参数（中文注释）
   ├── modules.yml        # 21 个模块的开关状态
   ├── players.yml        # 玩家的单独授权记录
   ├── permissions.yml    # 权限组与玩家权限节点
   ├── economy.yml        # 金币数据
   ├── stats.yml          # 玩家统计数据
   ├── mutes.yml          # 禁言数据
   ├── claims.yml         # 领地数据
   ├── warps.yml          # 地标数据
   ├── homes.yml          # 家园数据
   ├── auth.yml           # 登录账号（密码加盐哈希，不存明文）
   ├── shop.yml           # 商店商品与价格
   ├── notices.yml        # 公告内容
   ├── reports.yml        # 举报记录
   ├── punish.yml         # 警告记录
   ├── signin.yml         # 签到数据
   ├── tab.yml / scoreboard.yml
   └── logs/reports.log   # 举报日志
   ```
4. 给服主自己（或管理组）`canglan.admin` 权限。**OP 默认拥有**该权限。

> 数据库都是 YAML 明文文件，方便备份、迁移和手工修改。

---

## 四、服主指令（核心）

主指令：**`/cl`**（别名 `/canglan`、`/cyan`），所有子指令都支持中文和英文两种写法。

### 4.1 查看

| 指令 | 说明 |
| --- | --- |
| `/cl` 或 `/cl 帮助` | 显示帮助 |
| `/cl 列表` | 查看 21 个模块的启用状态，以及你自己能不能用 |
| `/cl 信息 <模块>` | 查看某模块的介绍、指令、权限节点 |
| `/cl 状态` | 服务器 TPS、内存、模块统计 |
| `/cl 我的` | 查看你自己可用的模块 |

### 4.2 启用 / 禁用模块（服主）

| 指令 | 说明 |
| --- | --- |
| `/cl 启用 <模块>` | 立即启用某模块，无需重启 |
| `/cl 禁用 <模块>` | 立即禁用某模块，监听器与定时任务一并卸载 |
| `/cl 重载 <模块>` | 重载某模块配置 |
| `/cl 重载 all` | 重载全部配置与数据 |

示例：
```
/cl 禁用 商店          # 模块可以写中文名
/cl 禁用 shop          # 也可以写模块 ID
/cl 启用 tab
```

### 4.3 给玩家单独开关权限（服主）

| 指令 | 说明 |
| --- | --- |
| `/cl 玩家 <玩家> 列表` | 查看该玩家被单独设置的模块 |
| `/cl 玩家 <玩家> 允许 <模块>` | 单独允许该玩家使用（即使他没有权限节点） |
| `/cl 玩家 <玩家> 禁止 <模块>` | 单独禁止该玩家使用（优先级最高） |
| `/cl 玩家 <玩家> 重置 <模块>` | 恢复按默认权限判断 |

示例：
```
/cl 玩家 小明 禁止 商店        # 小明不能用商店
/cl 玩家 小明 允许 权限系统    # 小明可以进权限管理
/cl 玩家 小明 重置 商店        # 恢复默认
```

> 三种状态的关系：
> - **全局禁用** → 所有玩家都不能用（模块彻底停止工作）
> - **玩家禁止** → 只有这个玩家不能用
> - **玩家允许** → 这个玩家即使没有 `canglan.module.<模块>` 权限也能用（该模块内的管理指令也会一并放行）

### 4.4 修改服务器名称（服主）

| 指令 | 说明 |
| --- | --- |
| `/servername "新名字"` | 修改服务器名称，支持 `&` 颜色代码，名字可以带空格 |
| `/servername` | 查看当前名称与用法 |
| `/servername reset` | 恢复默认名称 |

名字会自动同步到 **TAB 列表页眉、计分板标题、功能菜单标题、新玩家欢迎标题**。
配置里对应 `config.yml` 的 `server-name`，TAB 和计分板用 `{server}` 占位符引用。

> 服务器列表里显示的 MOTD 属于 `server.properties` 的 `motd`，需要改文件后重启才能生效；
> `/servername` 改的是插件内部显示的服务器名。

---

## 4.5 中文指令（玩家可直接用）

不用记英文，输入中文会自动转成对应指令：

| 中文 | 英文 | 中文 | 英文 |
| --- | --- | --- | --- |
| `/公告` | `/notice` | `/商店` | `/shop` |
| `/金币` `/余额` | `/money` | `/转账 玩家 金额` | `/pay` |
| `/签到` `/打卡` | `/signin` | `/排行榜` `/榜单` | `/top` |
| `/地标` `/传送点` | `/warps` | `/随机传送` | `/rtp` |
| `/家园` `/回家` | `/homes` `/home` | `/设置家` | `/sethome` |
| `/领地` `/圈地` | `/claim` | `/取消领地` | `/unclaim` |
| `/传送请求 玩家` | `/tpa` | `/接受传送` | `/tpaccept` |
| `/菜单` `/钟表菜单` | `/menu` | `/统计` | `/stats` |
| `/举报 玩家 原因` | `/report` | `/金币榜` | `/baltop` |

输入错误的中文指令也会有中文提示，例如 `/瞎写` → “没有这个中文指令，你可能想输入 …”。

### 4.6 钟表菜单

- 玩家进服自动获得一个 **「功能菜单」时钟**（快捷栏最后一格），右键打开
- 也可以直接输入 `/menu`
- 菜单包含：系统商店、地标传送、随机传送、我的家园、我的领地、玩家传送、每日签到、排行榜、我的统计、我的金币、服务器公告、服务器信息、计分板开关、服务器规则
- 钟表不能丢弃，死亡重生后自动补发；`/menu 获取` 可重新领取
- 不想要这个钟表：`/cl 禁用 菜单`

---

## 五、常用玩法指令（玩家）

| 功能 | 指令 |
| --- | --- |
| 查看能用什么 | `/cl 我的` |
| 商店 | `/shop`（左键买 1 个、Shift+左键买 16 个、右键卖出） |
| 金币 | `/money`、`/pay <玩家> <金额>`、`/baltop` |
| 圈地 | `/claim pos1` → `/claim pos2` → `/claim create <名字>`；`/claim auto` 一键圈脚下区块；`/unclaim` 删除；`/claim trust <玩家>` 信任好友 |
| 地标 | `/warps` 列表、`/warp <名称>` 传送 |
| 随机传送 | `/rtp` |
| 家园 | `/sethome`、`/home`、`/homes` |
| 传送请求 | `/tpa <玩家>`、`/tpaccept`、`/tpdeny` |
| 签到 | `/signin` |
| 排行榜 | `/top`、`/top 击杀`、`/top 在线` |
| 统计 | `/stats`、`/stats <玩家>` |
| 计分板 | `/sb`、`/sb 关闭` |
| 举报 | `/report <玩家> <原因>` |
| 登录 | `/register <密码> <确认密码>`、`/login <密码>` |

### 领地系统（最大 64×64×64）

| 指令 | 说明 |
| --- | --- |
| `/claim` | 打开领地帮助（含当前上限） |
| `/claim pos1` | 把脚下位置设为第一个角点 |
| `/claim pos2` | 把脚下位置设为第二个角点 |
| `/claim create <名字>` | 把两个角点之间的立体范围圈成领地 |
| `/claim auto` | 一键圈脚下区块（16×16、高 64 格） |
| `/claim info` | 查看脚下领地信息 |
| `/claim list` | 查看自己的全部领地 |
| `/claim trust <玩家>` | 信任好友，让 TA 在你的领地里自由活动 |
| `/claim untrust <玩家>` | 取消信任 |
| `/claim rename <新名字>` | 给脚下领地改名 |
| `/unclaim` | 删除脚下的领地 |

规则：
- **单边最大 64 格、高度最大 64 格、体积最大 262144 格**（可在 `config.yml` 的 `claims` 里改）
- 领地内别人不能：破坏方块、放置方块、开箱子/熔炉、拉门/按按钮、倒液体、取液体、炸掉方块、破坏画与展示框
- 领地内默认禁止 PvP（`claims.pvp-in-claims: true` 可打开）
- 领地范围会跟别人的领地做**重叠检测**，重叠时不允许圈
- 每人领地数量上限默认 1 块（`claims.default-limit`），可用权限 `canglan.claim.limit.N` 提高到 N 块
- 老版本的按区块领地数据会在启动时**自动转换**成立体领地，不会丢

---

## 六、权限节点

| 节点 | 默认 | 说明 |
| --- | --- | --- |
| `canglan.admin` | OP | 全部管理权限（模块开关、玩家授权） |
| `canglan.module.<模块ID>` | 见下 | 使用某个模块 |
| `canglan.economy.admin` | OP | `/eco` 管理员经济操作 |
| `canglan.chat.admin` | OP | 禁言、清屏 |
| `canglan.punish.admin` | OP | 警告、踢出、封禁 |
| `canglan.report.admin` | OP | 查看/清空举报 |
| `canglan.report.notify` | OP | 接收举报提醒 |
| `canglan.stats.others` | OP | 查看他人统计 |
| `canglan.monitor.notify` | OP | 接收低 TPS 提醒 |
| `canglan.auth.bypass` | OP | 跳过登录验证 |
| `canglan.claim.bypass` | OP | 无视领地保护 |
| `canglan.claim.limit.<N>` | 无 | 领地数量上限 N 块 |
| `canglan.home.limit.<N>` | 无 | 家园数量上限 N 个 |
| `canglan.home.unlimited` | OP | 家园无上限 |
| `canglan.chat.bypass` | OP | 跳过聊天冷却与违禁词 |
| `canglan.rtp.bypass` | OP | 跳过随机传送冷却 |

`canglan.module.*` 默认值：`perm`、`punish`、`monitor` 默认 **OP**，其余 18 个模块（含 `menu` 钟表菜单）默认 **所有玩家可用**。

在游戏内用权限系统加权限：
```
/perm 组 vip 添加权限 canglan.module.shop
/perm 玩家 小明 添加权限 canglan.home.limit.5
```

> 解封相关：Bukkit 的 IP 解封指令是 **`/pardon-ip <IP>`**（没有 `/unban-ip` 这个指令，
> 想解封 IP 却提示未知指令就是这个原因）；玩家解封用 `/unban <玩家>`。

---

## 七、配置文件说明（`config.yml`）

全部参数都带中文注释，常用几项：

```yaml
prefix: "&b&l沧蓝工坊 &7» &f"    # 全局消息前缀
server-name: "&b&l沧蓝工坊 &f生存服"   # 服务器名称（也可以用 /servername "名字" 改）
economy:
  starting-balance: 100.0        # 新玩家初始金币
claims:
  default-limit: 1               # 默认能圈几块地
  max-size: 64                   # 单块领地单边最大格数
  max-height: 64                 # 单块领地最大高度
  max-blocks: 262144             # 单块领地最大体积（64×64×64）
  pvp-in-claims: false           # 领地内是否允许 PvP
  protection:
    buckets: true                # 防止在别人领地倒液体
    explosions: true             # 爆炸不破坏领地内方块
    containers: true             # 保护箱子等容器
rtp:
  cooldown: 30                   # 随机传送冷却（秒）
chat:
  format: "&7[{group}] &f{player} &8» &f{message}"
signin:
  base-reward: 50.0              # 签到基础金币
  streak-bonus: 10.0             # 每多连续一天多给的金币
monitor:
  tps-alert-threshold: 15.0      # TPS 低于多少提醒管理员
```

商店商品在 `shop.yml`，公告在 `notices.yml`，都可以直接改文件后 `/cl 重载 all`。

---

## 八、与 LuckPerms / TAB 等插件的关系

- **LuckPerms**：本插件内置一套轻量权限系统（组、前缀、节点），小服可以完全不装 LuckPerms。
  如果服务器 **已经装了 LuckPerms**，`canglan.module.*` 等节点请直接在 LuckPerms 里分配，
  本插件的模块判断会走 Bukkit 权限（LuckPerms 生效）；内置的组前缀仍可用于聊天与 TAB 显示。
- **TAB / 计分板等原版插件**：功能已内置，装了会和本插件的 TAB / 计分板冲突，建议择一使用。
- **Essentials / CMI 等**：`/home`、`/warp`、`/tpa` 等指令会重名，Bukkit 会让其中一个生效，
  建议只保留一套，或把不用的模块 `/cl 禁用`。

---

## 九、常见问题

**Q：改了配置怎么生效？**
A：`/cl 重载 all`，或重启服务器。模块开关即时生效，无需重启。

**Q：玩家说“某个功能用不了”？**
A：先 `/cl 列表` 看模块是否全局启用，再看 `/cl 玩家 <玩家> 列表` 是否有单独禁止，
最后确认玩家有 `canglan.module.<模块>` 权限。

**Q：怎么让某个功能只给 VIP 用？**
A：`/cl 禁用 shop` 会所有人禁用；正确做法是给模块权限只发给 VIP 组：
`/perm 组 vip 添加权限 canglan.module.shop`，然后其它组不给他们该节点（可在 plugin.yml 里把默认值改为 false，或用 `/perm 玩家 <玩家> 禁止 shop` 单独禁止普通玩家）。

**Q：数据会丢吗？**
A：每 5 分钟自动保存 + 关服保存 + `/cl 重载` 前保存。数据都是 `plugins/CanglanSurvival/*.yml`，直接备份该文件夹即可。

**Q：能不能只用其中几个功能？**
A：可以，`/cl 禁用 <模块>` 就行，被禁用的模块不占监听器、不跑定时任务、不消耗性能。

---

## 十、开发者 / 二次开发

- 源码：`src/main/java/com/canglan/survival/**`
- 构建：`mvn package`（需要 JDK 21+、Maven 3.8+）
- 新增模块：继承 `com.canglan.survival.core.Module`，实现 `onEnable` / `onDisable` / `onCommand`，
  然后在 `CanglanSurvival#registerModules()` 里注册，并在 `plugin.yml` 加指令即可。
- 模块自带监听器注册、定时任务管理、权限判断、中文消息工具（`Msg`）、YAML 存储（`YamlStore`）、
  图形界面基类（`Gui`）等基础设施。

---

## 十一、版本记录

### v1.0.0
- 首个版本，整合 21 个生存服常用模块
- 支持游戏内 `/cl` 启用/禁用模块、给玩家单独授权
- **立体领地系统**：两点圈地，单块最大 64×64×64，保护方块 / 容器 / 液体 / 爆炸 / PvP
- 钟表菜单：进服自动发放「功能菜单」时钟，右键打开图形菜单
- `/servername "名字"` 游戏内修改服务器名称，同步 TAB、计分板、菜单标题
- 全部提示统一使用英文指令名（`/notice`、`/shop`、`/claim`…），中文写法仍可用
- 全中文提示、全中文配置注释
- 适配 Paper 1.21.11 / Java 21

---

**沧蓝工坊开发 · 未经授权请勿倒卖**
