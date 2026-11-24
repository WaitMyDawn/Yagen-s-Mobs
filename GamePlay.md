# 游戏机制 / Gameplay

本页记录当前仓库中已实现的玩法，数值以 **NeoForge 1.21.1** 为基准。沙漠鳐鱼、秃鹫、吊岩兽、泡囊鲵、迅敏节虫、蓄辉蝶、拾遗鳐和雪藏鼬均已接入五个构建目标。版本和前置依赖见 [README](README.md)，版本变更见 [更新日志](docs/changelog/)。

This guide documents implemented gameplay in the current source tree, using **NeoForge 1.21.1** for its values. Desert Skates, Condrocs, Rock Hangers, Bubble Salamanders, Velocipods, Lumen Butterflies, Salvage Rays, and Snowcache Weasels are available on all five build targets. See the [README](README.md) for versions and dependencies and the [changelog](docs/changelog/) for release history.

## 目录 / Contents

- [生成与配置 / Spawning and configuration](#spawning)
- [沙漠鳐鱼 / Desert Skate](#desert-skate)
- [秃鹫 / Condroc](#condroc)
- [吊岩兽与抛索器 / Rock Hanger and Silk Launcher](#rock-hanger)
- [泡囊鲵 / Bubble Salamander](#bubble-salamander)
- [迅敏节虫 / Velocipod](#velocipod)
- [蓄辉蝶 / Lumen Butterfly](#lumen-butterfly)
- [拾遗鳐 / Salvage Ray](#salvage-ray)
- [雪藏鼬 / Snowcache Weasel](#snowcache-weasel)
- [Xaero 生物头像 / Xaero entity icons](#xaero-entity-icons)
- [合成与获取 / Crafting and acquisition](#recipes)

<a id="spawning"></a>
## 生成与配置 / Spawning and configuration

服务器配置为 `config/yagens_mobs/yagens_mobs-server.toml`。需要单存档覆盖时，把配置放在该存档的 `serverconfig/yagens_mobs/` 下。修改生成选项后重启服务器或重新打开单人存档。所有生物默认允许自然生成。创造模式下，模组物品位于 **Yagen's Mobs** 标签页，刷怪蛋排在末尾。

The server config is `config/yagens_mobs/yagens_mobs-server.toml`. For a per-world override, place it in that world's `serverconfig/yagens_mobs/` directory. Restart the server or reopen the singleplayer world after changing spawn options. Natural spawning is enabled by default. Mod items are in the **Yagen's Mobs** creative tab, with spawn eggs at the end.

| `[spawns.<id>]` | 生成池与地形 / Pool and habitat | 默认权重 / Default weight | 默认组大小 / Default group size |
| --- | --- | --- | --- |
| `desert_skate` | 怪物池。沙漠、恶地类群系的沙地，白天也可生成。 / Monster pool. Sand in deserts and badlands biomes, including in daylight. | 2 | 3–4 |
| `condroc` | 动物池。稀树草原、沙漠、恶地及有足够落差的露天峡谷。 / Creature pool. Savannas, deserts, badlands, and qualifying open ravines. | 8 / 3 / 1 | 1–3 |
| `rock_hanger` | 怪物池。普通地下洞穴、溶洞和繁茂洞穴，跳过深暗之域和和平难度。 / Monster pool. Ordinary underground caves, dripstone caves, and lush caves, excluding the Deep Dark and Peaceful difficulty. | 普通、溶洞 10，繁茂 3 / 10 ordinary and dripstone, 3 lush | 1 |
| `bubble_salamander` | 水生动物池。河流、温水海洋和暖水海洋。 / Water creature pool. Rivers, lukewarm oceans, and warm oceans. | 河流 5，海洋 3 / 5 river, 3 ocean | 1–2 |
| `velocipod` | 动物池。沼泽、红树林沼泽的露天干燥地面。 / Creature pool. Dry ground under open sky in swamps and mangrove swamps. | 6 | 1–2 |
| `lumen_butterfly` | 动物池。繁花森林、平原、向日葵平原和草甸。 / Creature pool. Flower forests, plains, sunflower plains, and meadows. | 8 | 2–4 |
| `salvage_ray` | 末地外岛巢穴结构。 / Outer End nest structure. | `nestChance = 0.75` | 2–3 |
| `snowcache_weasel` | 动物池。雪原、雪针林、冰刺之地和雪林。 / Creature pool. Snowy plains, snowy taiga, ice spikes, and groves. | 6 | 1–2 |

常规生物条目使用 `enabled`、`weight`、`minGroupSize`、`maxGroupSize`。权重为 0 时关闭该生成条目。权重决定同一个群系、同一个池选中该条目时的相对占比。生成尝试还要经过生物上限和地点检查。组大小是尝试数量，实刷数量可能更少。最大值小于最小值时使用最小值。秃鹫的旱地和峡谷权重分别按基础值的 0.4 和 0.13 倍计算，泡囊鲵海洋为 0.5 倍，繁茂洞穴的吊岩兽为 0.3 倍。缩放四舍五入，非零权重至少保留 1。拾遗鳐的 `nestChance` 走结构候选点判定。

Regular creature entries use `enabled`, `weight`, `minGroupSize`, and `maxGroupSize`. A zero weight disables an entry. Weight determines an entry's relative share of selection in the same biome and pool. Attempts also pass mob cap and placement checks. Group sizes count attempts, so fewer mobs may spawn. A maximum below the minimum is raised to the minimum. Condroc weights are scaled by 0.4 in arid biomes and 0.13 in other biomes with qualifying ravines. Ocean salamanders use 0.5 and lush-cave Rock Hangers use 0.3. Scaling rounds to the nearest integer and keeps nonzero entries at a minimum of 1. Salvage Rays use `nestChance` for structure candidates.

<a id="desert-skate"></a>
## 沙漠鳐鱼 / Desert Skate

**中文**

- **属性与生成。** 成年体生命 20、护甲 4、韧性 2。幼年体生命 10、护甲和韧性均为 0。自然群第一只是成年体，其余成员分别有 25% 概率为幼年体，可用 `juvenileChance` 调整。需要沙子或红沙，和平难度关闭自然生成。
- **潜沙。** 在沙层内移动，贴近沙面时留下有方向的沙迹。挖掉覆盖它的沙块可暴露真实位置。出沙后闲置 4 秒，在可潜入的沙地上每秒有 10% 概率回沙。所在位置下雨时会出沙，雨停后恢复潜沙。客户端选项 `sandTrails` 默认开启，可关闭沙迹显示。
- **诱饵。** 检索 32 格内落在沙地上的生肉和生鱼。可用生牛肉、鸡肉、羊肉、猪肉、兔肉、鳕鱼、鲑鱼、热带鱼、河豚和生沙漠鳐鱼肉。它会锁定目标最多 12 秒，游到诱饵下方再破沙进食。也可以直接右键喂已经出沙的鳐鱼。进食获得 30 秒生命恢复 II。
- **狩猎。** 潜沙时，发现头顶 4 格内的玩家、兔子或村民后会先扬起沙粒预警。12 tick 后复查原目标的位置和视线，再破沙伏击。地面战斗中会追踪目标，目标逼近约 4 格时后退，并在有效射程内翘起尾部蓄力，随后从尾尖发射尖刺。蓄力 10 tick，尖刺伤害 4，射程 8 格。
- **背部减伤。** 两个年龄阶段都把来自上方的命中乘以 0.1，再进行护甲计算。弹射物取飞行方向的反向，近战取攻击者眼睛相对鳐鱼碰撞箱中心的方向。归一化后竖直分量达到 0.6 才算命中背部。无来源位置或绕过护甲的伤害不触发背甲。
- **成长与繁殖。** 幼体不主动攻击，跟随成年体，领头成年体战斗时会潜入可用沙层并躲在其下方。成长需要 20 分钟活跃实体时间，每吃一份生肉或生鱼减少 2 分钟。成年体进食后有 60 秒可繁殖，寻找 16 格内同样就绪的成年体，配对后产 1 只幼体，双方冷却 20 分钟。
- **掉落与食物。** 成年体必掉 1–3 块生鳐鱼肉，抢夺每级独立有 50% 概率加 1。幼体掉 1–2 块，无抢夺加成。生肉恢复 4 饱食度、2 饱和度，熟肉恢复 12 饱食度、12 饱和度。熔炉、烟熏炉、营火均可烹饪，获得熟肉解锁 **多汁的肉**，说明是“里面的水似乎烤不完！”。进食、潜沙、破沙和射击分别使用原版的吃食、沙步、挖沙和羊驼吐息。
- **饮水兼容。** Thirst Was Taken 2 和 Homeostatic 的生肉水量与水分饱和度为 20 / 10，熟肉为 16 / 8。Homeostatic 的玩家水分饱和度上限为 5。Tough As Nails 采用生 20、熟 16 的水量和 20% 水合档，水分饱和度最高分别为 8 和 6.4。

**English**

- **Stats and spawning.** Adults have 20 health, 4 armor, and 2 armor toughness. Juveniles have 10 health and zero armor or toughness. The first skate in a natural group is an adult. Each additional member has a 25% juvenile chance by default, controlled by `juvenileChance`. Spawning requires sand or red sand and a difficulty above Peaceful.
- **Burrowing.** Skates move through sand, leaving a directional wake just below the surface. Digging away the sand exposes the skate at its actual position. After four idle seconds on suitable sand, a surfaced skate has a 10% chance per second to dive. Local rain drives skates to the surface and prevents them from burrowing until it clears. Client option `sandTrails` toggles the wake and defaults to on.
- **Bait.** Skates detect raw meat and fish that have landed on sand within 32 blocks. They accept raw beef, chicken, mutton, porkchop, rabbit, cod, salmon, tropical fish, pufferfish, and raw skate meat. A skate commits to bait for up to 12 seconds and surfaces directly below it to eat. A surfaced skate also accepts hand-feeding. A meal grants Regeneration II for 30 seconds.
- **Hunting.** A buried adult emits a sand warning when a player, rabbit, or villager passes within four blocks above it. After 12 ticks it checks that the original prey remains close and visible before ambushing. On the surface it backs away as prey closes to about four blocks. It curls its tail for a ten-tick windup and fires a spine from the tip. The spine deals 4 base damage and travels up to eight blocks.
- **Dorsal armor.** Both ages take 10% of incoming damage from above before normal armor calculations. A projectile uses its reverse velocity. Melee uses the attacker's eye relative to the center of the skate's hitbox. The normalized direction needs an upward component of at least 0.6. Damage with no source position or that bypasses armor skips this reduction.
- **Growth and breeding.** Juveniles follow an adult and burrow beneath it in dry sand when it fights. Juveniles do not initiate attacks. They mature after 20 minutes of active entity time. Each raw meat or fish meal takes two minutes off that time. Fed adults remain ready for 60 seconds and seek another ready adult within 16 blocks. A pair produces one juvenile and both enter a 20-minute cooldown.
- **Loot and food.** Adults always roll 1–3 raw skate meat, with each Looting level independently having a 50% chance to add one. Juveniles drop 1–2 with no Looting bonus. Raw meat restores 4 hunger and 2 saturation. Cooked meat restores 12 hunger and 12 saturation. A furnace, smoker, or campfire cooks it. Obtaining cooked meat grants **Juicy Meat**. Eating, diving, surfacing, and firing use vanilla sound sources.
- **Thirst integration.** Thirst Was Taken 2 and Homeostatic assign 20 water and 10 water saturation to raw meat, and 16 water and 8 water saturation to cooked meat. Homeostatic caps player water saturation at 5. Tough As Nails uses the same water amounts and its 20% hydration tier, yielding up to 8 and 6.4 water saturation respectively.

[实机 / In game](docs/assets-for-readme/desert-skate-intro.gif)

<a id="condroc"></a>
## 秃鹫 / Condroc

### 野外、食物与繁殖 / Wildlife, food and breeding

- **中文。** 秃鹫最常见于稀树草原，也会出现在沙漠、恶地和露天峡谷。峡谷按地形判定，在某一水平轴的两个方向各相隔 9 格的位置，都要比生成点高至少 8 格。自然生成个体有 20% 概率为幼鸟，可用 `juvenileChance` 调整。幼鸟 20 分钟长大。秃鹫生命值 16。成年秃鹫主动俯冲抓挠僵尸类，基础伤害 8，攻击后重新拉升，对玩家保持中立。
- **English.** Condrocs are most common in savannas and also appear in deserts, badlands, and exposed ravines. In a ravine, terrain must rise at least eight blocks nine blocks out on both sides of one horizontal axis. Each natural spawn has a 20% chance to be a chick, controlled by `juvenileChance`. Chicks mature after 20 minutes. Condrocs have 16 health. Adults dive at zombie-family mobs for 8 base damage, then pull up again. They do not hunt players.

- **中文。** 两个年龄阶段都搜寻约 24 格内掉落的腐肉和生肉、生鱼，吃一份获得生命恢复 I 15 秒。用腐肉手喂或让成年体自己捡食腐肉可进入 60 秒繁殖状态。两只就绪成年体在 16 格内配对，找有支撑的空位筑巢。巢内有 1–3 颗蛋，约 5 分钟后孵化，巢破碎并掉 2–4 根木棍。亲本冷却 5 分钟。
- **English.** Both ages seek dropped rotten flesh and raw meat or fish within about 24 blocks. A meal gives 15 seconds of Regeneration I. Adult Condrocs become ready to breed for 60 seconds after eating rotten flesh from hand-feeding or the ground. Two ready adults within 16 blocks can build a supported nest containing 1–3 eggs. After about five minutes it hatches the chicks, breaks, and drops 2–4 sticks. The parents have a five-minute cooldown.

- **中文。** 成年体空闲时优先去有腐肉的驿站。找不到驿站时可以落在裸露的金合欢原木或原木正上方的树冠上。正常巡飞以地表上方 8–12 格为目标，幼鸟飞得更低。幼鸟使用两种专用啾鸣变奏，开启原版字幕后可看到对应文字。成年体掉落 1–2 根秃鹫长羽，每级抢夺独立有 50% 概率增 1 根。幼鸟无长羽掉落。
- **English.** Idle adults prefer stocked Vulture Posts. Without one, they can rest on exposed acacia logs or on the canopy directly above a log. Adults aim to patrol 8–12 blocks above the ground, while chicks fly closer to the surface. Chicks have two dedicated chirp variants with text in the vanilla subtitle display. Adults drop 1–2 Flight Feathers with an independent 50% chance of one more per Looting level. Chicks drop no Flight Feathers.

### 驿站、货运与邮袋 / Posts, freight and Mailbags

1. **建站 / Build a station.** 秃鹫驿站高两格，同向、同所有者的相邻柱段沿横梁相连，只有两端保留支柱。每列有一个栖位和一个下方货位。手持腐肉右键投放，也可用界面批量存取。点击界面顶部的驿站名称开始编辑，按 Enter 保存或按 Esc 取消。连起来的一座驿站共储存至多 **65,536** 个腐肉。驻站成鸟每 10 分钟消耗一个腐肉，缺粮会离开。 / A Vulture Post stands two blocks tall. Adjacent columns with the same owner and orientation connect along the beam, leaving supports at the two ends. Each column provides one perch and one cargo slot. Right-click with rotten flesh or use the bulk inventory controls. Click the station name to edit it, then press Enter to save or Esc to cancel. A connected station holds up to **65,536** flesh in total. Each resting adult consumes one every ten minutes and leaves if supplies run out.
2. **装货 / Stage cargo.** 普通方块直接右键放入下格，潜行右键取回。用空摄物钳右键采集功能方块及其方块实体数据。也可潜行用空钳在两对角点选体积至多 64 格的区域。用已装载的钳子右键下格装货，潜行使用空钳收回功能方块或多格货物。空地上右键可原样放回，须有足够空间。大箱子的两半占两个相邻货位，运输时保留连体外观。依赖货物外部的模组网络可能要在目的地重新连接。 / Right-click a lower slot with an ordinary block to stage it, and sneak-right-click to take it back. Empty Cargo Tongs capture a functional block and its saved block entity data. Sneak-use empty tongs on two opposite corners to select up to 64 cells. Use loaded tongs on a lower slot to stage the load. Sneak-use empty tongs to retrieve functional or multi-block cargo, then use them in the world where it fits. A double chest takes two adjoining slots and keeps its joined appearance in flight. Modded networks connected to blocks outside the selection may need reconnection.
3. **派送 / Dispatch.** 右键横梁打开同维度的站点列表，查看谁放置了驿站、距离、方位、空闲柱数与腐肉成本。悬停可以查看预约来自哪座驿站。所有者可改名、设置列表可见性和是否返程。派送时立即预留目的站的货位，一格货物要一只就位成年秃鹫，费用为 `ceil(距离 / 100) × 秃鹫数量` 个腐肉，距离按站点中心之间的三维直线计算。目的站需有足够货位，满栖仍能卸货。落地后成鸟优先就空栖位。 / Right-click the beam to see stations in the same dimension, ownership markers, distance, bearing, available cargo slots, and fuel cost. Hover to see which stations reserved slots. The owner can change the name, listing visibility, and whether the birds return. Dispatch reserves destination cargo space immediately and assigns one perched adult per loaded block. It costs `ceil(distance / 100) × number of birds` rotten flesh, using straight-line 3D distance between station centers. A station with free cargo space can receive a shipment even if all perches are occupied. Arriving birds use available perches when they can.
4. **飞行与回收 / Flight and recovery.** 秃鹫并排挂着真实货物飞，边向前边爬升，巡航约 4 格/秒，地形净空目标为至少 24 格。开阔处约每分钟滑降一格，约两分钟振翅回升两格。每秒测一次真实位移，连续三次不超过两格时，尝试局部绕行。等区块加载的时间不计入卡住判断。运输、可选返程、泊站和坠货恢复期间临时加载有关区块。任何一只承运鸟死亡会让整件货物坠落，系统寻找可完整放置的位置并通知发送者坐标。货运存档可恢复，地形、承运鸟或目的站失效会转入恢复流程。 / Birds fly in formation with the visible cargo, climbing as they move forward. Cruise speed is about four blocks per second, with a target terrain clearance of at least 24 blocks. In open air they sink about one block per minute, then flap to regain two blocks after roughly two minutes. Three consecutive one-second samples of two blocks or less trigger a local detour search. Waiting for chunks does not count as a stall. Freight, optional return trips, docking, and cargo recovery temporarily keep relevant chunks loaded. If a carrier dies, the entire load falls. The server looks for room to place it intact and reports the position to the sender. Jobs are saved for resumption and can enter recovery if terrain, carriers, or the destination become invalid.
5. **邮袋 / Mailbag.** 用秃鹫长羽和皮革合成，可随身打开 27 格空间，禁止直接或间接套邮袋。右键自有驿站绑定并尝试装入最近的空闲成鸟。绑定显示会跟随驿站名称更新。袋里有成鸟且目的站有效时，用 **返站** 发出同维度的邮袋，无腐肉开销。洞穴里先搜索可通行的出口或合适的短途直达路线，找不到路线时保留原袋及成鸟。飞行中遇到障碍会尝试重算局部路线。返站后用驿站界面的 **领取邮袋** 拿回。送信失败或承运鸟死亡时优先把邮袋安置成可取回的包裹方块。若找不到放置位置，恢复任务会保留邮袋并继续寻找。拆除驿站时，站内已送达、尚未领取的邮袋会作为不会自然消失的物品掉出。 / A Flight Feather and leather craft a Mailbag with 27 slots. Mailbags cannot be nested directly or indirectly. Use one on your own post to bind it and attempt to capture the nearest available perched adult. The bound name follows station renames. With a bird inside and a valid destination, choose **Send Home** to dispatch within the same dimension for no rotten flesh. Underground, it first finds a passable exit or a viable short direct route. If neither exists, the bag and bird stay with you. Obstacles encountered in flight can trigger local replanning. On arrival, use the station's **Collect Mail** button. On delivery failure or carrier death, recovery places a retrievable parcel block. If no suitable spot exists, it keeps the bag and retries. Breaking a post drops its uncollected delivered bags as items with unlimited lifetime.

**寻路与存档 / Routing and saves**

- **中文。** 长途巡航会随着航程加载前方区块，根据前方地形调整高度。局部货运绕行会检查飞行编队和悬挂货物的空间，搜索半径 16 格，节点预算 2,400。邮袋的洞穴寻路使用 A*，按水平曼哈顿距离在 80 格内搜索，节点预算 12,000，经过的每一段都检查秃鹫和袋子能否一起通过。出发、遇到受阻路线和洞穴到站时需要局部搜索。运输任务保存路线、货物和承运鸟，发送者离线不影响服务器上的任务推进。
- **English.** Long trips load chunks ahead as the flight progresses and adjust altitude to the terrain. Local freight detours check clearance for both the formation and suspended load, with a 16-block search radius and a 2,400-node budget. Cave mail uses A* with a horizontal Manhattan radius of 80 blocks and a 12,000-node budget. Each edge checks space for the bird and bag together. Local searches handle departure, new obstructions, and enclosed destinations. Jobs save the route, cargo, and carriers, and can progress while the sender is offline.

[实机 / In game](docs/assets-for-readme/condroc-intro.gif)

<a id="rock-hanger"></a>
## 吊岩兽与抛索器 / Rock Hanger and Silk Launcher

**中文**

- **地点与属性。** 生命值 40，护甲 12。自然生成需要阴暗洞穴、可承重的洞顶和连续无碰撞、无液体的吊挂空间。会优先检查头顶，必要时检查同一洞室邻近列。默认从洞顶向下要求 16 格净空，由 `minimumAirBelow` 控制，可设 16–256。对着合格方块底面使用刷怪蛋可直接吊顶，此方式要求净空内是空气。普通方式放出的个体从落地状态开始。
- **洞顶循环。** 平时倒吊，岩丝休息长度随机为 1–6 格，隔一段时间伸缩，回升时偶尔下滑 1–2 格。捕食覆盖吊点下方约 3×3 列，以实际视线和通路为准，竖直距离受世界上下边界及阻挡限制。捕食对象是玩家、蜘蛛、洞穴蜘蛛。攻击前预警 16 tick，悬丝伏击伤害在简单、普通、困难下分别为 **12 / 18 / 24**。受伤后缩丝贴顶约 10 秒，再重新放丝。回收防御期间抵御弹射物。
- **进食与剪丝。** 吊点下方放蜘蛛眼可引它下来，吃一个回满血并进食约 3 秒。击毙蜘蛛类也可进入进食动作。进食时用剪刀右键本体可收 2 个岩丝。对可见吊丝用剪刀可剪断，收 1 个岩丝并使它坠落。同一次进食已经剪过毛束时，再剪吊丝仍会让它掉落，岩丝只结算一次。贴顶且线缩短到无法裁剪时等它重新下放。
- **落地与回吊。** 落地后主动近战玩家，简单、普通、困难伤害 **4 / 6 / 8**。它在地面会放弃久追不到的目标，也不主动追逐蜘蛛。没有可攻击目标时优先找头顶满足净空的洞顶，并搜索能通过的墙面、转角和顶面路线。到达后重新放丝，可反复诱食、采丝和回吊。身姿根据所贴表面和移动方向旋转。死亡掉落 2–4 个岩丝，抢夺每级独立有 50% 概率增加一个。
- **架索。** 潜行右键铁制抛索器打开五格弹仓，每格最多 64 卷。最左专用格为空时，一次只用一卷。专用格装入索卷时可从弹仓按所需长度消耗多卷，每卷提供 32 格。手持时预览落点，射击是直线。普通两锚架设受 64 格瞄准和锚间距上限约束。先打第一枚岩钉，然后向可通行的第二锚面射击。向空气使用或切换物品可让岩丝从单钉垂下。能找到原抛索器时按弹仓规则放垂。抛索器离手且无法匹配时只用已支付的一卷。绳子遇到阻挡或世界下界会缩短。栅栏、树叶和玻璃等面不能锚定，下半砖只取底面、上半砖只取顶面，双层半砖按整块处理。
- **攀爬。** 右键完整岩丝抓握，用方向键沿其移动。水平或较平缓的岩丝用双手吊挂，人体自然向下，接近竖直时过渡为抱索攀爬姿势。下行速度乘 1.6，疾跑键乘 1.5，两者可叠加。按潜行或跳跃脱离。用右键与岩钉互动可回收已花费的索卷。跨岛协助架索见下方拾遗鳐章节。

**English**

- **Habitat and stats.** Rock Hangers have 40 health and 12 armor. Natural spawning requires a dark cave, a sturdy ceiling, and an unobstructed, fluid-free hanging column. It checks overhead first and may check nearby columns in the same chamber. The default ceiling clearance is 16 blocks, controlled by `minimumAirBelow` from 16 to 256. A spawn egg can attach one directly under a suitable block if the required column is air. Other egg placements start it on the ground.
- **Ceiling behavior.** Hangers idle upside down on a strand 1–6 blocks long. They vary its length over time and occasionally slip 1–2 blocks when reeling back up. They detect players, spiders, and cave spiders in roughly a 3×3 column below the anchor, subject to line of sight, passage, and world height. A strike gives a 16-tick warning. Ambush damage is **12 / 18 / 24** on Easy, Normal, and Hard. An injured tethered Hanger reels itself against the ceiling for about ten seconds, then hangs again. It resists projectiles while in this defensive state.
- **Feeding and shearing.** A dropped spider eye below the anchor draws it down. It eats one, heals to full, and feeds for about three seconds. Killing a spider can also trigger feeding. Shear the body while feeding for two Rock Silk. Cutting an exposed tether gives one Rock Silk and drops the Hanger. Cutting after shearing the tuft during the same meal still drops the creature without a second yield. Wait for a braced Hanger to extend a visible tether before cutting it.
- **Ground and recovery.** On the ground it fights players for **4 / 6 / 8** damage on Easy, Normal, and Hard and gives up on unreachable targets. It does not seek spiders from the ground. Without a target, it looks for a valid ceiling overhead and searches for a route along walls, corners, and the ceiling. Once it reaches the roof, it can hang again, letting the feeding and shearing loop repeat. Its model rotates with the surface and direction of travel. Death yields 2–4 Rock Silk with an independent 50% chance of an extra silk per Looting level.
- **Rigging.** Sneak-right-click the reusable Iron Silk Launcher to open its five chamber slots. Each holds up to 64 spools. With the designated first slot empty, a shot uses a single spool. Loading that slot enables multi-spool lengths across the chamber, at 32 blocks per spool. The held launcher previews the straight shot and indicates invalid, obstructed, or out-of-reach aims. Ordinary rigging limits targeting and pin separation to 64 blocks. Fire a first pin, then aim at a clear second anchor, or use in the air to hang a line. Switching items can also hang the line. Hanging follows the chamber rules when the matching launcher is available and uses the paid spool when it is unavailable. A hanging line stops short of obstructions and the lower world limit. Fences, leaves, and glass cannot anchor a pin. Lower slabs accept the underside, upper slabs the top, and double slabs act as full blocks.
- **Climbing.** Right-click a completed silk line to grip it. Move along the strand with the movement keys. On horizontal or shallow silk, both hands grip overhead and the body hangs below. Steeper silk blends into the wraparound climbing pose. Descent multiplies speed by 1.6 and sprint multiplies it by 1.5. These multipliers stack. Sneak or jump to release. Right-click a pin to recover the paid spools. See Salvage Ray below for assisted long spans.

[实机 / In game](docs/assets-for-readme/rock-hanger-intro.gif)

<a id="bubble-salamander"></a>
## 泡囊鲵 / Bubble Salamander

**中文**

- **栖息与生长。** 生命值 16，护甲和韧性为 0，捕食伤害 3。自然生成列需要从生成方块起连续 3–9 格水，水面上方是见天的空气。幼体会游动，通常 20 分钟长大，有与成体相同的体色系统，不能被佩戴。该生物没有专属物品掉落。
- **野外 AI。** 空闲时挑选可到达的水中目标，游一段再停顿。搁浅时尝试返回水中。野生个体会捕食鳕鱼、鲑鱼、热带鱼和河豚，优先找附近可见目标，跳过带名字和从桶里放出的鱼。捕杀一条鱼回复 2 点生命。
- **驯服与等待。** 同一个玩家给野生个体喂 3 条生鳕鱼或生鲑鱼后驯服，进度按玩家记，不显示信任度文本。驯服后可用四种生鱼手喂，回复 4 点生命。喂幼体可加快成长。空手潜行右键切换等待和随主人在水中游动。等待时偶尔会离开原位，去吃等待点 7 格内的掉落生鱼，吃后回复 4 点生命并返回原位。允许生物拾取环境食物时才会捡食。驯服后不猎杀活鱼。
- **佩戴。** 在水下卸掉头盔，用主手空手右键自己的成年泡囊鲵。它套在头上，每 tick 补满空气，水下挖掘速度免受浸水与悬浮两项惩罚，清除并阻止挖掘疲劳。佩戴时提供持续的夜视画面，离开即结束且不覆盖已有药水效果。玩家眼睛离开水面自动摘下，可用 **B** 手动摘下。死亡、退出和切换维度也会解除佩戴。佩戴状态下，模型会随玩家从直立调整到水平游泳姿势，尾巴、鳍鳃和肢体随游动变化。
- **水桶。** 用水桶右键未佩戴个体可装桶，已驯服个体仅主人可装。重新放出后保留年龄、体色、混色种子、性别和驯服状态等实体数据。
- **繁殖与遗传。** 两只异性、已驯服、未佩戴且在水中的成年体，用生鱼进入繁殖。暖水海洋、温水海洋及深温水海洋禁繁殖。双方同主人时，后代自动驯服给该玩家。雌性有额外小鳍。体色及其花纹 **90% 母本、9% 父本、1% 重抽**。绿色、粉色、黄色、黑色、褐色、红色、蓝色权重各 2，白化与混色各 1。体色也作用于泡囊。新混色用单个 64 位种子确定斑块位置，客户端按需生成贴图，同时最多缓存 32 种动态花纹。较旧的 32 种预设花纹仍可读入。

**English**

- **Habitat and growth.** Salamanders have 16 health, zero armor and toughness, and a 3-damage hunting bite. Natural placement needs 3–9 consecutive water blocks starting at the spawn cell, with sky-exposed air above. Babies swim, normally mature in 20 minutes, and use the same color system as adults. Only adults can be worn. There is no species-specific item drop.
- **Wild behavior.** A salamander chooses a reachable point in the water, swims there, and pauses. A stranded salamander tries to return to the water. Wild individuals hunt nearby visible cod, salmon, tropical fish, and pufferfish. They skip named and bucket-released fish. A kill restores 2 health.
- **Taming and waiting.** Feed a wild salamander three raw cod or salmon from the same player. Progress is tracked per player without a visible trust counter. Tamed salamanders accept all four raw fish by hand and heal 4 health. Feeding a baby speeds up growth. Sneak-right-click with an empty main hand to toggle waiting and underwater following. A waiting pet can pick up raw fish within seven blocks of its waiting point when mob pickup rules allow, heal 4 health, and return to that point. Tamed salamanders leave live fish alone.
- **Wearing.** Submerge, take off your helmet, and right-click your tamed adult with an empty main hand. The bubble refills air every tick, removes the submersion and floating mining penalties, and clears and prevents Mining Fatigue. It provides a continuous Night Vision view while fitted and leaves existing potion effects intact. It detaches when your eyes leave the water. Press **B** to remove it manually. Death, logout, and dimension changes also detach it. The model follows the wearer's upright or horizontal swimming pose, with animated limbs, gills, and tail.
- **Buckets.** Use a water bucket on an unworn salamander. Only its owner can bucket a tamed one. Releasing it preserves its age, coat, mixed seed, sex, and taming data.
- **Breeding and inheritance.** Feed raw fish to two tamed adults of opposite sexes while both are in water and neither is worn. Warm, lukewarm, and deep lukewarm oceans are excluded from breeding. Offspring of two pets with the same owner inherit that owner. Females have extra frills. Offspring inherit the mother's coat and pattern 90% of the time, the father's 9%, or roll a new coat 1%. Green, pink, yellow, black, brown, red, and blue each have weight 2. Albino and mixed each have weight 1. Coat color also affects the bubble. New mixed coats use a single 64-bit seed to generate patches on the client, which caches up to 32 generated patterns. The 32 older preset patterns remain readable.

[实机 / In game](docs/assets-for-readme/bubble-salamander-intro.gif)

<a id="velocipod"></a>
## 迅敏节虫 / Velocipod

**中文**

- **属性与取食。** 成年体 80 生命、8 护甲，幼体 40 生命、0 护甲。对玩家是中立生物，受攻击会反击，简单、普通、困难难度咬击 **8 / 10 / 12**。主动捕猎史莱姆，成年体可打所有体型，幼体只主动打最大生命 4 或以下的小、中史莱姆。每次有效咬击史莱姆给节虫 **生命恢复 II 4 秒**，幼体咬中合格史莱姆额外缩短 30 秒成长时间。会被它捕猎的史莱姆在 16 格内尝试向外逃离。
- **甘蔗。** 在环境交互规则允许时，会从甘蔗顶部吃掉一格，保留根部，并寻找符合原版种植条件的空位补种。手喂每份甘蔗有 **20%** 概率驯服，甘蔗也可恢复体力。
- **育幼。** 成年体击杀最大型史莱姆可触发育幼。它先找干燥、有空间、附近没有敌对生物的安全地面位置，抵达后育幼 **60 秒**，产一只幼体。完成后繁殖冷却 20 分钟。幼体自然成长 20 分钟，有 90% 继承亲本体色、10% 随机抽色。绿、紫、蓝、粉、黄是两年龄阶段共有的五种配色。没有专属死亡物品掉落，死亡时背包内容会掉出。
- **骑乘。** 主人空手右键已驯服成年体可站上背部。战斗或育幼中的个体不能骑。方向键移动，地面约 **5 格/秒**。跳跃键起飞和抬升，空中前后移动结合视角俯仰控制升降，普通飞行 **12 格/秒**，疾跑 **36 格/秒**。降低高度接近地面会着陆。潜行下坐骑。骑手和节虫一起侧倾，最大 30°，冲刺时更快振翅。碰撞会检查骑手头顶空间。起飞、悬停、前飞、转向、降落和待机均有动画表现。
- **体力与背包。** 体力为 0–100，骑乘时以无数字进度条占用经验条位置。低于 30 点不能冲刺。着地每 tick 回 1 点，空中速度不超过 2 格/秒每 tick 回 0.2 点，普通飞行每 tick 耗 0.1 点，冲刺每 tick 耗 0.25 点。一次甘蔗回复 50 点。主人潜行右键或骑乘时按 **E** 打开 27 格背包。有甘蔗且低于 50 点时会自己消耗一份。
- **视角。** 原版第三人称背面、Ninjas Free Play 的自由和固定视角会随速度拉远。在 `config/yagens_mobs/yagens_mobs-client.toml` 中，`velocipodMaxCameraPullback` 默认最多增加 4 格，可设 0–16，设 0 关闭。

**English**

- **Stats and hunting.** Adults have 80 health and 8 armor. Juveniles have 40 health and no armor. Velocipods are neutral to players and retaliate when attacked. The bite deals **8 / 10 / 12** on Easy, Normal, and Hard. Adults hunt all sizes of regular slime. Juveniles only hunt small and medium slimes with a maximum of 4 health. A successful slime bite grants **Regeneration II for four seconds**. A juvenile also takes 30 seconds off its growth time for each successful bite on eligible prey. Slimes the Velocipod can hunt try to flee out of its 16-block radius.
- **Sugar cane.** When mob interactions are allowed, Velocipods eat a block off the top of a cane stalk, leave the root, and look for an empty slot where vanilla cane can grow. Each hand-fed cane has a **20%** chance to tame a wild Velocipod and can restore stamina.
- **Brooding.** Killing a largest-size slime can make an adult brood. It first seeks dry, clear ground away from hostile mobs. After **60 seconds** at a safe site it produces one juvenile, then enters a 20-minute cooldown. Juveniles mature in 20 minutes. A juvenile inherits the parent's color 90% of the time and rolls a random color 10% of the time. Both ages come in green, purple, blue, pink, and yellow. The species has no dedicated death loot and drops its backpack contents on death.
- **Riding.** The owner can empty-hand right-click a tamed adult to stand on its back. A fighting or brooding adult cannot be mounted. Move at about **5 blocks/s** on the ground. Jump to take off or climb. In flight, forward and backward motion follow the camera pitch. Cruise at **12 blocks/s** or sprint at **36 blocks/s**. Descend to land and sneak to dismount. Mount and rider bank together up to 30° and the wings beat faster during sprints. Movement checks rider headroom. Takeoff, hovering, forward flight, turns, landing, and idle movement have distinct animation behavior.
- **Stamina and backpack.** Stamina ranges from 0 to 100 and replaces the experience bar with an unnumbered bar while riding. Sprint requires 30 stamina. Recover 1 per tick on the ground or 0.2 per tick in the air at up to 2 blocks/s. Ordinary flight consumes 0.1 per tick and sprinting consumes 0.25. A cane restores 50 points. The owner can sneak-right-click or press **E** while mounted to open a 27-slot backpack. The Velocipod eats a stored cane when below 50 stamina.
- **Camera.** The rear third-person camera and Ninjas Free Play free and fixed cameras pull back with speed. In `config/yagens_mobs/yagens_mobs-client.toml`, `velocipodMaxCameraPullback` defaults to four extra blocks and accepts 0–16. Set it to 0 to disable the pullback.

[实机 / In game](docs/assets-for-readme/velocipod-intro.gif)

<a id="lumen-butterfly"></a>
## 蓄辉蝶 / Lumen Butterfly

### 捕获、跟随与经验 / Taming, following and experience

- **中文。** 自然群是成年蝶，生命值 8，九种基础色等概率。幼体是繁殖产生的毛毛虫，通常在地面爬行，20 分钟后长大并参与蝶群经验分配和基础色能力。用对应植物右键一次即可驯服。主人潜行右键切换跟随和等待，最多 **7 只**跟随，满员后的新宠先等待。跟随中的蝶免疫主人伤害，所有蝶免疫仙人掌和凋零玫瑰。成蝶沿各自倾斜的椭圆轨道慢速围绕主人，面朝实际飞行方向。离开主人 32 格或穿越维度时，登记跟随的个体会尝试返回主人附近的安全位置。
- **English.** Natural groups consist of adults with 8 health and equally likely base colors. Caterpillars are born through breeding, normally crawl, and mature after 20 minutes. Experience sharing and base-color powers start at adulthood. One use of the matching plant tames a butterfly. Owners can sneak-right-click to switch between following and waiting. Up to **seven** butterflies can follow at a time, and newly tamed pets wait when the slots are full. Followers are immune to damage caused by their owner. All butterflies are immune to cactus and wither roses. Flying adults trace individual tilted elliptical orbits and face their actual direction of flight. Registered followers can rejoin their owner at a safe point from more than 32 blocks away or from another dimension.

- **中文。** 野生成蝶会飞向约 12 格内的经验球并吸收。玩家击杀生物时，把原本掉落的经验均分给同维度、32 格内的成年跟随蝶。绿色加成先算再均分，专用经验粒子从死亡生物飞到每只蝶。经验以小数保存，主人普通右键取回，整点进入经验条，余数保留在账本里。有主蝶死亡会把正余额返给主人，包括离线主人上线后的结算。无主蝶死亡时一般掉 2–3 个较大经验球，经验不足时更少，小数余量保留。
- **English.** Wild adults fly toward experience orbs in about a 12-block search area and absorb them. When a player kills a mob, its normal experience is split among adult followers within 32 blocks in the same dimension. The green multiplier is applied before division, and custom experience particles fly from the defeated mob to each butterfly. Balances preserve fractions. Right-click as owner to withdraw. Whole points reach the bar, with remainders kept in the ledger. A pet's positive balance returns to its owner on death, with offline payouts held until the owner is available. Wild butterflies normally drop two or three larger orbs on death. Low totals may produce fewer, and fractions are retained.

### 颜色与能力 / Colors and powers

| 颜色 / Color | 驯服植物 / Taming plant | 效果、开销和叠加 / Effect, cost and stacking |
| --- | --- | --- |
| 金 / Gold | 蒲公英 / Dandelion | 金蝶自己或其跟随玩家濒死时可触发图腾。每只共享一个 10 分钟冷却，蝶群轮流触发。触发后回到 1 点生命，并获得生命恢复 II 45 秒、伤害吸收 II 5 秒和抗火 40 秒。 / Saves itself or its followed player from fatal damage. Its two uses share one ten-minute cooldown per butterfly. Ready gold followers take turns. The saved target returns to 1 health with Regeneration II for 45 seconds, Absorption II for 5 seconds, and Fire Resistance for 40 seconds. |
| 银灰 / Silver | 茜草花 / Azure Bluet | 蝶群吸收击杀经验时给主人 30 秒速度，每只加一级。 / When the swarm absorbs kill XP, grants Speed for 30 seconds at one level per silver follower. |
| 红 / Red | 虞美人 / Poppy | 蝶群吸收击杀经验时给主人 30 秒力量，每只加一级。 / When the swarm absorbs kill XP, grants Strength for 30 seconds at one level per red follower. |
| 黑 / Black | 凋零玫瑰 / Wither Rose | 主人没有夜视时可购买 30 秒夜视。同色中能付款的蝶均摊 1 经验。 / Buys 30 seconds of Night Vision when the owner lacks it. Eligible black butterflies split the total cost of 1 XP. |
| 粉 / Pink | 粉红色郁金香 / Pink Tulip | 主人未满血时维持生命恢复，每只参与者每秒消耗 1 经验并增加一级。已有其他生命恢复来源时让其先发挥。 / While the owner is injured, each contributing pink butterfly spends 1 XP per second and adds one level of Regeneration. Existing Regeneration from another source takes precedence. |
| 绿 / Green | 仙人掌 / Cactus | 击杀经验总量按 `1 + 跟随绿蝶数` 放大再均分。野生绿蝶吸收经验球时翻倍。 / Multiplies kill XP by `1 + number of green followers` before splitting. Wild green butterflies double the value of absorbed orbs. |
| 蓝 / Blue | 矢车菊 / Cornflower | 主人没有水下呼吸时可购买 30 秒水下呼吸。同色能付款的蝶均摊 1 经验。有蓝色成蝶跟随时，蝶群可以被气泡包裹着下水跟随。 / Buys 30 seconds of Water Breathing when the owner lacks it. Eligible blue butterflies split the total cost of 1 XP. An adult blue follower lets the swarm follow underwater inside bubbles. |
| 褐 / Brown | 棕色蘑菇 / Brown Mushroom | 帮助 3 格内正在工作的熔炉、高炉、烟熏炉和酿造台。每只成蝶为每台正在加速的方块每 4 秒花 1 经验，速度为 `1 + 参与褐蝶数` 倍。可在工作区放置等待的褐蝶。 / Helps active furnaces, blast furnaces, smokers, and brewing stands within three blocks. Each adult helper pays 1 XP per four seconds of extra work per active block. Speed is `1 + number of brown helpers` times normal. A waiting brown pet can be stationed near the machines. |
| 浅蓝 / Light Blue | 兰花 / Blue Orchid | 原版掉落流程使用时运或抢夺时，每只有余量的浅蓝蝶消费 1 经验，共同提高对应附魔的有效等级。时运 III 加两只可达时运 V，工具自身附魔保持原样。 / When mining or killing resolves applicable Fortune or Looting loot, each funded light-blue follower spends 1 XP to add one effective level. Fortune III plus two followers acts as Fortune V while leaving the tool's enchantments intact. |

### 繁殖与彩色 / Breeding and rainbow lineage

- **中文。** 成年蝶不靠喂食繁殖。附近 8 格有各自对应的植物时，不在冷却的成年蝶每秒有 1% 概率进入 60 秒可繁殖状态。支持对应的盆栽。两只已就绪异性接近后生一只幼体，亲本冷却五分钟。后代基础色 49% 随母、49% 随父、2% 随机。双亲同主则后代继承主人，跟随槽满时等待。
- **English.** Adult butterflies breed around plants. With its own matching plant within eight blocks and its cooldown over, each adult has a 1% roll per second for a 60-second readiness window. Matching potted plants count. Ready adults of opposite sexes produce one caterpillar when they meet and both enter a five-minute cooldown. The base color follows the mother 49%, the father 49%, or a random roll 2% of the time. A child with two parents owned by the same player inherits that owner and waits if the swarm is full.

- **中文。** 九个基础色按上表固定为九个位。自然生成只占自己的位。后代合并双亲血脉，并加入自己基础色的位。九个位齐全时变成有附魔光效的彩色变种，能力替换为群体护盾。彩蝶跟随时可保护自己和该主人的其他跟随蝶，每抵消 1 点经过护甲等结算后的伤害，消耗 1 经验。余额可低至 **−1,000**，有多只彩蝶时依次承接剩余伤害。可普通右键从主人经验偿债。自然生成没有彩蝶。
- **English.** The nine table rows define nine fixed lineage bits. A wild spawn begins with its own bit. A child combines both parents' bits and adds its own base color. Completing all nine produces a rainbow variant with an enchantment glint and a swarm shield in place of the base power. A following rainbow butterfly shields itself and its owner's other followers, spending 1 XP per point of final damage prevented. Each balance can reach **−1,000**. Multiple rainbows cover remaining damage in turn. The owner can right-click to repay the debt with their XP. Rainbow butterflies come from breeding rather than natural spawn rolls.

[环绕实机 / Orbit footage](docs/assets/lumen-butterfly-orbit.gif) · [经验吸收实机 / XP footage](docs/assets/lumen-butterfly-xp.gif)

<a id="salvage-ray"></a>
## 拾遗鳐 / Salvage Ray

**中文**

- **巢穴与变种。** 在末地外岛的末地高原、中地和荒地生成崖边巢穴 `yagens_mobs:salvage_ray_nest`。结构集合的候选间距参数为 19 区块、最小间隔参数为 7 区块。候选地表高度为 Y 41–106，朝前 7 格的地面至少低 16 格，后方 3 格仍要有接近候选高度的地面。默认合格候选点经过 75% 判定。巢穴是依地形造出的不规则末地石岩台，外缘点缀紫颂植物和花，中央留出降落和进出空间。巢匣首次就绪时生成 2–3 只常驻拾遗鳐。普通玩家放置的巢匣不创建新鳐群。可使用刷怪蛋。拾遗鳐生命 24、护甲 2，拥有蓝、紫、粉、黑、白化五种配色。单尾、双尾、三尾权重为 3∶2∶1，每尾六关节，末端动作随升降与转向逐节滞后。死亡会掉出仍带着的货物，没有专属肉类。
- **拾遗。** 巡航中约每 4 tick 搜索 64 格球形范围内的下坠物品。确认下方通向虚空后，根据目标速度和剩余下坠距离加速飞往预判的拦截点，并检查双方在相邻 tick 之间的接触，避免高速目标从身旁穿过。也会拾取地面的掉落物，一次顺手收集拾取点 2.5 格内可见的其他物品，最多携带 **64 件**，可以混装。运送地面物品途中发现虚空坠落物或待救援玩家时，会放下当前货物并优先拦截。优先返回已认定的常驻巢匣，必要时在约 128 格内择近找巢匣。箱子只剩部分容量时先存能存的，剩下的掉在巢匣上。货物随实体存档。
- **繁殖与幼体。** 成鳐把捡来的物品交给自己认定的巢匣时，若巢匣内有紫颂果，会根据实际存进去的数量判定。成功概率为 `(5 + 入箱数量 / 8) %`，例如 8 件为 6%，64 件为 13%。成功时消耗一颗紫颂果，繁殖就绪状态存档并持续到配对成功。两只就绪成鳐在 16 格内会尝试靠近，生出一只幼体后双方清除状态。幼体按成年模型的 55% 缩放，12 点生命、0 护甲，随机继承一位亲本的体色和尾数，跟随亲本，20 分钟后长大。幼体长大后才参与拾遗、救援、载人和架索。
- **巢匣与线路。** 右键拾遗巢匣打开 27 格库存，支持漏斗和比较器。点击库存界面的名称编辑，按回车保存。普通玩家可以给自己放置或自然生成的无主巢匣改名。服务器区域保护仍然生效，管理员也可管理他人的巢匣。用同一枚指南针依次右键两个巢匣可创建双向线路，一座巢匣可连到多个站点。手持指南针显示可视范围内的虚拟线路，潜行右键线路，或潜行右键巢匣后点击条目，可取消连接。右键红石切换该站的潜影弹拦截。拾遗鳐空闲时在当前巢匣附近 64 格内自由游动。
- **载人。** 手持紫颂果能吸引约 20 格内的空闲拾遗鳐。把紫颂果扔到地上才是投喂。它会捡起并带回巢匣，吃掉一颗，认定进食位置 8 格内的巢匣。拿紫颂果右键鳐鱼打开当前巢匣的直连航线，界面显示当前站名、距离和方位。拾遗鳐在选择窗口内等候，选站后玩家双手抓住腹部，由它飞往站点并在附近安全位置放下。到达后认定目的地并改用该处作为当前巢匣。潜行取消载人。
- **坠落救援。** 空手潜行右键巢匣登记，再操作一次取消。负责该巢的拾遗鳐会使用 64 格预判拦截逻辑救援已登记的玩家，包括向虚空坠落和下方有地面的高空急坠，并送回巢匣附近的安全落点。滑翔、创造飞行或已乘坐其他实体时不会触发。每只拾遗鳐成功接住玩家后冷却 30 秒。
- **长岩丝接力。** 用抛索器打好第一枚岩钉，保持牵线，右键空闲拾遗鳐，再瞄准第二锚面使用抛索器。岩丝延伸到拾遗鳐身上，抵达目标后它安装第二枚锚钉。这个流程跳过普通的 64 格限制，瞄准长度取玩家渲染距离和视线到维度高度边界距离的较小值，同时要求线路处于已加载区块、锚面有效且索卷足够。点空处会取消接力选择。每卷仍提供 32 格，第一格的装弹规则沿用普通抛索器。成功后岩丝可抓握和回收。失败时已付索卷会尝试从第一锚点返还。

**English**

- **Nests and variants.** The structure `yagens_mobs:salvage_ray_nest` generates on cliffs in the End highlands, midlands, and barrens. Its structure set uses a spacing of 19 chunks and a separation of 7. A site needs terrain at Y 41–106, a drop of at least 16 blocks seven blocks ahead, and ground near the site height three blocks behind. Eligible candidates have a default 75% roll. The nest is an irregular end-stone ledge with chorus plants and flowers on its edges and a clear flight approach to the box. The central box seeds 2–3 resident rays when it first becomes ready. Player-placed boxes do not seed a group. A spawn egg is available. Rays have 24 health and 2 armor and come in blue, purple, pink, black, and albino. One, two, and three tails have weights of 3:2:1. Each tail has six joints that lag in succession during turns, climbs, and dives. A ray drops any carried stack on death and has no dedicated meat drop.
- **Salvage.** While patrolling, a ray scans for falling items in a 64-block radius about every four ticks. It predicts where targets over the void will fall and adjusts its diving speed to intercept them. Contact checks account for motion between ticks so a fast target can be caught as it passes. A ray also gathers grounded drops, including other visible drops within 2.5 blocks of a pickup, up to **64 items** across mixed stacks. It lets go of ground cargo to intercept a void drop or rescue a falling player. It prefers its home box for delivery and can search for a nearby box within about 128 blocks. It stores whatever fits and drops the overflow on top of the box. Carried stacks are saved with the entity.
- **Breeding and juveniles.** When an adult deposits recovered items in a known Nest Box with chorus fruit, the chance is `(5 + items actually deposited / 8) %`. Eight items give a 6% chance and 64 give a 13% chance. On success it eats one fruit and remains ready until breeding succeeds, including through a save and reload. Two ready adults within 16 blocks can approach and produce one juvenile, then both reset their readiness. A juvenile is 55% of adult size, has 12 health and no armor, inherits one random parent’s color and tail count, and follows that parent. It matures after 20 minutes and gains the ability to salvage, rescue, carry passengers, and rig lines.
- **Boxes and routes.** Right-click a Salvage Nest Box to open its 27-slot inventory. It works with hoppers and comparators. Click the inventory title to edit it and press Enter to save. Players can rename their own boxes and unowned boxes generated in nests. Server area protection still applies, and administrators can manage other players’ boxes. Use the same compass on two boxes to link them in both directions. Each box can have multiple connections. Holding a compass shows the virtual routes in view. Sneak-use a line or sneak-use the box and select a route to remove it. Right-click with redstone to toggle shulker bullet interception. Idle rays roam within 64 blocks of their current home.
- **Passenger routes.** Holding chorus fruit attracts idle rays within about 20 blocks. Drop the fruit to feed them. A ray brings dropped fruit back to a box, eats one, and recognizes boxes within eight blocks of the meal. Right-click a ray with fruit in hand to choose a direct route from its current home box. Entries show the box name, distance, and bearing. The ray waits while its passenger selects a route. On departure the player hangs beneath it with both hands, then the ray flies automatically and releases them at a safe point near the destination. It recognizes the arrival box and uses it as its new home. Sneak to cancel the ride.
- **Void rescue.** Empty-hand sneak-right-click a Nest Box to sign up or cancel. A resident ray uses 64-block predictive interception to catch registered players falling into the void or falling rapidly toward solid ground, then returns them safely to the box. Creative flight, elytra flight, and existing passengers do not trigger rescue. A ray has a 30-second cooldown after a successful pickup.
- **Long silk relay.** Fire the first pin, keep the line attached to the launcher, and right-click an available ray. Aim at the second anchor and use the launcher again. The silk follows the ray until it flies to the target and sets the second pin. The relay bypasses the ordinary 64-block limit. Aim distance is limited by the shorter of render distance and the height boundary along the view ray. It also requires loaded chunks, a valid anchor, and enough spools. Using the launcher while aiming at empty sky cancels the relay selection. Each spool gives 32 blocks and the designated chamber slot follows the normal launcher rules. The completed line can be climbed and recovered. A failed relay attempts to return the paid spools at the first anchor.

[实机 / In game](docs/assets/salvage-ray/salvage-ray-ingame.gif) · [实机变种 / In-game variants](docs/assets/salvage-ray/salvage-ray-ingame-variants.png) · [新巢穴 / Redesigned nest](docs/assets/salvage-ray/salvage-ray-ingame-nest.png) · [幼体 / Juvenile](docs/assets/salvage-ray/salvage-ray-ingame-juvenile.png)

<a id="snowcache-weasel"></a>
## 雪藏鼬 / Snowcache Weasel

### 驯服、乘坐与整理 / Taming, perching, and sorting

- 雪藏鼬有 10 点生命值，成年和幼年各有蓝、浅蓝、浅粉、白色四种配色。用生兔肉必定驯服，生鸡肉有三分之一概率。给两只靠近的已驯服成年雪藏鼬各喂一块生兔肉或生鸡肉即可繁殖，繁殖后冷却 5 分钟。求偶期间会暂停整理。幼体可以驯服和上肩，长大后开始工作。
- 主人空手右键后，成年雪藏鼬坐在头顶，幼年雪藏鼬坐在肩上。乘坐期间，潜行右键箱子可循环切换这只鼬对该箱子的**存取、只存、只取**模式，大箱子视为一个箱子。空手潜行右键其他方块让它下来，并从新地点开始找箱子。
- 成年驯服后会搜索工作点半径 16 格内的箱子，距离同时计算水平和高度。派发配方图沿用已设定的工作点。它按箱子里第一个非空格的创造模式标签分类，物品属于多个标签时采用标签列表中的首个分类标签。未列入任何分类标签的物品归为一类。空箱收到第一件物品后建立自己的分类。
- 它优先搬同标签的一批，一次最多搬九格。已装上的货物找不到同类位置时，优先放进最近且能容下整批的箱子，放置时先用箱尾。普通箱里的异类货物有合适去处才会启动搬运。箱位、模式和权限在物品实际转移时再次校验，未放下的货物会留在雪藏鼬身上，等待箱子变化。
- The weasel has 10 health. Adults and juveniles each come in blue, light blue, light pink, and white. Raw rabbit always tames it. Raw chicken succeeds one third of the time. Feed two nearby tamed adults one raw rabbit or raw chicken each to breed them. They pause work while courting and have a five-minute breeding cooldown. Juveniles can be tamed and ride on a shoulder, and they start work as adults.
- Right-click the weasel with an empty hand to pick it up. Adults perch on your head and juveniles perch on your shoulder. While carrying a weasel, sneak-right-click a chest to cycle **deposit and withdraw**, **deposit only**, and **withdraw only** for that weasel. Both halves of a large chest share one mode. Sneak-right-click another block with an empty hand to set it down and assign a new work area.
- A tamed adult searches for chests within a three-dimensional radius of 16 blocks from its assigned work point. Blueprint jobs use the same work point. The first occupied slot defines the chest’s creative tab. Items on multiple tabs use the first category tab in tab order. Items absent from category tabs share an uncategorized group. The first item placed in an empty chest establishes its category.
- The weasel carries up to nine stacks of the same category per trip. If a matching chest cannot receive the remaining cargo, it first looks for the nearest chest that can hold the entire batch and starts by appending to occupied slots. Items in ordinary mixed chests are only picked up for a matching or empty destination. Permission, capacity, and interaction mode are rechecked during transfers.

### 配方图 / Recipe Blueprint

- 同时安装 JEI 后，模组标签页和工作台配方会提供配方图。多人游戏中，客户端与服务端均需安装 JEI 才能编辑并派发配方图。配方用五张纸、一个墨囊和一个淡蓝色染料。
- 右键配方图，在搜索框输入物品名或注册名。也可以在玩家物品栏里把 JEI 的物品拖到配方图上。点击配方中的材料可以展开或折叠支线，点左右箭头翻配方，滚轮缩放，拖动画布平移，点「适应画布」可以重新取景。右侧合计区分别列出直接材料和按已展开子配方折算的材料，这两组数字无需相加。悬停物品图标可查看提示。相同的材料格共享已选子配方和合成余量。
- 工作台配方节点有**任意合成、仅此合成、散件**三个模式。任意合成在可用工作台配方中优先选库存覆盖较好的配方。仅此合成使用选定配方。散件保留该节点所需材料供玩家自行处理。
- 拿已保存的配方图右键驯服的成年雪藏鼬即可派单。它先卸下已有货物，再按任务取料。每一层先取箱中现成的直接材料，缺少的数量再按子配方收集。任务库存可超过普通整理的九格；缺料时仍会带回能拿到的材料和已完成的产物。有可达工作台时，它会按选定规则合成，在交互距离内可以接着做后续批次。存有计划材料的箱子或工作台暂时走不到时，会保留进度并重试约 30 秒。工作结束后，有物品才会提示领取，空手潜行右键取回成品、余料和配方产生的容器。
- 安装农夫乐事时可选烹饪锅配方。雪藏鼬会同时收集烹饪材料和出餐容器，玩家在烹饪锅完成这一步。
- Installing JEI makes the Recipe Blueprint available in the mod tab and at the crafting table. Editing and assigning jobs in multiplayer requires JEI on the server and the client. The recipe uses five paper, one ink sac, and one light blue dye.
- Right-click the blueprint to search JEI by item name or registry ID. From the player inventory, a JEI ingredient can also be dragged onto the item. Click an ingredient to expand or collapse its subrecipe, page through recipes with the arrows, scroll to zoom, and drag to pan. Fit Canvas resets the view. The materials panel lists direct ingredients and the totals after expanding subrecipes. These are alternative summaries. Hover an item to see its tooltip. Equivalent slots share the chosen subrecipe and surplus crafting output.
- Workbench nodes offer **Any recipe**, **Exact recipe**, and **Ingredients only**. Any recipe prefers the supported workbench recipe with the best stock coverage. Exact recipe uses the selected recipe. Ingredients only gathers the materials at that node for the player.
- Use a saved blueprint on your tamed adult weasel to assign the job. It unloads existing cargo first. At each recipe level it takes available direct ingredients from chests and uses subrecipes for the shortfall. A separate job inventory can hold more than nine stacks. It crafts at a reachable crafting table and continues subsequent batches while it stays within interaction range. If the weasel cannot reach a chest with required stock or a workbench, it retains progress and retries for about 30 seconds. A pickup notice appears only when items are available. After it finishes, sneak-right-click with an empty hand to retrieve the output, surplus materials, and remaining containers.
- Farmer's Delight cooking pot recipes include both the ingredients and the serving container. The player cooks the meal in the pot.

### 存储模组兼容 / Storage mod compatibility

下表列出 0.3.1 已验证的模组版本。存储模组均为可选依赖。 / The table lists the versions verified for 0.3.1. All storage mods are optional.

| 平台 / Platform | 更多箱子 / Iron Chests | 精妙背包 / Sophisticated Backpacks | Refined Storage | 机械动力 / Create |
| --- | --- | --- | --- | --- |
| NeoForge 1.21.1 | 16.0.7 | 3.26.23.2263 | 2.0.9 | 6.0.10 |
| Forge 1.20.1 | 14.4.4 | 3.26.22.2264 | 1.12.4 | 6.0.8 |
| Fabric 1.21.1 | Fabric 2.0.4 | Fabric Port 3.23.4.3.106 | 2.0.9 | — |
| Fabric 1.21.11 | Fabric 2.0.5 | — | — | Create Fly 6.0.9-5 |
| Fabric 26.3 | Fabric 3.0.0 | Fabric Continuation 3.24.1.3 | — | Create Fly 6.0.9-2 |

Fabric 26.3 同时安装精妙背包移植版和 JEI 时，背包前置要求 JEI 31.16.0.87 或更新版本。 / On Fabric 26.3, the backpack port’s dependency requires JEI 31.16.0.87 or newer when both are installed.

- **更多箱子与精妙背包**按第一个非空格分类，优先级固定为 100。把背包放在地上后才能让雪藏鼬访问。携带自己的雪藏鼬潜行右键，可以为这只鼬独立设置该箱子或背包的存取模式。背包移动后保留设置。
- **RS 终端与合成终端**默认只取，作为配方图任务的材料来源。携带自己的雪藏鼬打开终端可以切换其存取模式。**输入器**只存、**输出器**只取，通过端口面向的箱子、木桶或支持的背包交接。在它们原有的界面里可以编辑优先级，默认 98。
- **机械动力**通过装在固定物品库上的安山或黄铜漏斗存取，方向随漏斗模式。黄铜漏斗遵守自身筛选条件，红石停机时暂停搬运。携带自己的雪藏鼬空手潜行右键漏斗，可编辑优先级，默认 99。
- 数字越小越优先。容器满了、筛选不匹配或端口暂时不可用时，会尝试其他可用的容器。取自输出口的物品会去同分类或空的箱子，也能交给另一个优先级更高且库存独立的端口。已被 RS 端口用作交接箱的箱子会通过端口操作。

- **Iron Chests and placed Sophisticated Backpacks** use the first occupied slot for sorting and have a fixed priority of 100. While carrying your own weasel, sneak-right-click to assign a mode for that weasel. Its backpack setting follows the backpack when moved.
- **Refined Storage Grid and Crafting Grid** default to take-only and supply materials for Blueprint jobs. Open their menus while carrying your own weasel to change the mode. **Importers** are deposit-only and **Exporters** are take-only. They use the chest, barrel, or supported backpack they face as a handoff. Edit their priority in the native menu. The default is 98.
- **Create** uses an Andesite or Brass Funnel attached to a fixed Item Vault. Its mode follows the funnel direction. Brass filters and redstone pauses apply. Carry your own weasel and sneak-right-click the funnel with an empty hand to edit the priority. The default is 99.
- Lower numbers are considered first. Full, filtered, or inactive endpoints can be skipped. Items taken from an outlet go to a matching or empty box, or to a higher-priority inlet with independent storage. The weasel accesses RS handoff containers through their ports.

<a id="xaero-entity-icons"></a>
## Xaero 生物头像 / Xaero entity icons

安装 Xaero 的小地图后，八种生物在实体雷达中各有一张头像。在「实体雷达设置」中可将「图标」设为「总是显示」。同时安装 Xaero 的世界地图时，可在大地图里开启小地图的实体雷达叠层。

With Xaero’s Minimap installed, all eight creatures have face icons in its Entity Radar. Set Icons to Always in the radar settings. With Xaero’s World Map also installed, you can enable the minimap radar overlay on the full map.

<a id="recipes"></a>
## 合成与获取 / Crafting and acquisition

配方按工作台自上而下书写，`·` 表示空格。 / Recipe rows read from top to bottom on a crafting grid. `·` marks an empty slot.

| 产物 / Result | 排列 / Pattern | 材料 / Ingredients |
| --- | --- | --- |
| 秃鹫驿站 / Vulture Post | `PPP` / `P·P` / `L·L` | P 金合欢木板 / Acacia Planks, L 金合欢原木 / Acacia Log |
| 摄物钳 / Cargo Tongs | `P·P` / `·P·` / `·P·` | P 金合欢木板 / Acacia Planks |
| 邮袋 / Mailbag | `··F` / `P·P` / `PPP` | F 秃鹫长羽 / Flight Feather, P 皮革 / Leather |
| 岩丝索卷 / Rock Silk Spool | `RRR` / `·S·` | R 岩丝 / Rock Silk, S 木棍 / Stick |
| 铁制抛索器 / Iron Silk Launcher | `II·` / `IXI` / `·S·` | I 铁锭 / Iron Ingot, X 弩 / Crossbow, S 木棍 / Stick |
| 拾遗巢匣 / Salvage Nest Box | `PCP` / `PEP` / `PPP` | P 紫珀块 / Purpur Block, C 紫颂果 / Chorus Fruit, E 末地石砖 / End Stone Bricks |

## 维护索引 / Maintenance index

- **生成与配置 / Spawning and configuration** · [SpawnDefaults](common/src/main/java/yagen/waitmydawn/yagens_mobs/core/SpawnDefaults.java) · [ServerConfigs](platforms/neoforge-1.21.1/src/main/java/yagen/waitmydawn/yagens_mobs/config/ServerConfigs.java) · [Biome modifiers](platforms/neoforge-1.21.1/src/main/resources/data/yagens_mobs/neoforge/biome_modifier/)
- **实体与互动 / Entities and interactions** · [Entity behaviors](platforms/neoforge-1.21.1/src/main/java/yagen/waitmydawn/yagens_mobs/entity/) · [Condroc transport](platforms/neoforge-1.21.1/src/main/java/yagen/waitmydawn/yagens_mobs/transport/) · [Rock Silk](platforms/neoforge-1.21.1/src/main/java/yagen/waitmydawn/yagens_mobs/silk/) · [Lumen Butterfly](platforms/neoforge-1.21.1/src/main/java/yagen/waitmydawn/yagens_mobs/butterfly/) · [Salvage Ray](platforms/neoforge-1.21.1/src/main/java/yagen/waitmydawn/yagens_mobs/salvage/)
- **配方、掉落与成就 / Recipes, loot and advancement** · [Recipes](platforms/neoforge-1.21.1/src/main/resources/data/yagens_mobs/recipe/) · [Loot](platforms/neoforge-1.21.1/src/main/resources/data/yagens_mobs/loot_table/) · [Juicy Meat](platforms/neoforge-1.21.1/src/main/resources/data/yagens_mobs/advancement/husbandry/juicy_meat.json)
