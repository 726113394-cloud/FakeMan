package io.Sriptirc_wp_1258.fakeman.listeners;

import io.Sriptirc_wp_1258.fakeman.Fakeman;
import io.Sriptirc_wp_1258.fakeman.entity.EntityManager;
import io.Sriptirc_wp_1258.fakeman.manager.DummyManager;
import io.Sriptirc_wp_1258.fakeman.objects.Dummy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class EventListener implements Listener {
    
    private final Fakeman plugin;
    private final DummyManager dummyManager;
    private final EntityManager entityManager;
    
    public EventListener(Fakeman plugin) {
        this.plugin = plugin;
        this.dummyManager = plugin.getDummyManager();
        this.entityManager = plugin.getEntityManager();
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();
        
        // 调试信息：打印点击的实体类型
        if (plugin.getPluginConfig().getBoolean("debug.enabled", false)) {
            plugin.getLogger().info("玩家 " + player.getName() + " 右键点击了实体: " + 
                entity.getType() + " (UUID: " + entity.getUniqueId() + ")");
        }
        
        // 通过实体管理器检查是否是假人实体
        Dummy dummy = entityManager.findDummyByEntity(entity);
        if (dummy == null) {
            // 调试信息：实体不是假人
            if (plugin.getPluginConfig().getBoolean("debug.enabled", false)) {
                plugin.getLogger().info("实体不是假人或未找到对应假人");
            }
            return;
        }
        
        event.setCancelled(true);
        
        // 检查是否是假人的主人
        if (!dummy.getOwnerUuid().equals(player.getUniqueId())) {
            player.sendMessage("§c这不是你的假人！");
            return;
        }
        
        // 右键打开假人背包
        openDummyInventory(player, dummy);
        
        // 调试信息：成功打开背包
        if (plugin.getPluginConfig().getBoolean("debug.enabled", false)) {
            plugin.getLogger().info("成功为玩家 " + player.getName() + " 打开假人 " + 
                dummy.getName() + " 的背包");
        }
    }
    
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        
        // 通过实体管理器检查是否是假人实体
        Dummy dummy = entityManager.findDummyByEntity(entity);
        if (dummy == null) {
            return;
        }
        
        // 假人受到伤害
        event.setCancelled(true); // 防止假人受到伤害
        
        // 通知主人
        if (plugin.getPluginConfig().getBoolean("safety.notify-on-attack", true)) {
            Player owner = Bukkit.getPlayer(dummy.getOwnerUuid());
            if (owner != null && owner.isOnline()) {
                owner.sendMessage("§c你的假人 " + dummy.getName() + " 受到了攻击！");
                
                // 播放声音
                if (plugin.getPluginConfig().getBoolean("notifications.sound-effects", true)) {
                    owner.playSound(owner.getLocation(), 
                        org.bukkit.Sound.valueOf(plugin.getPluginConfig().getString("notifications.sound-type", "ENTITY_EXPERIENCE_ORB_PICKUP")),
                        (float) plugin.getPluginConfig().getDouble("notifications.sound-volume", 1.0),
                        (float) plugin.getPluginConfig().getDouble("notifications.sound-pitch", 1.0)
                    );
                }
            }
        }
        
        // 如果是跟随模式且主人被攻击，假人保护主人
        if (dummy.getMode().equals("follow") && event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent damageEvent = (EntityDamageByEntityEvent) event;
            Entity damager = damageEvent.getDamager();
            
            if (damager instanceof Player) {
                Player attacker = (Player) damager;
                Player owner = Bukkit.getPlayer(dummy.getOwnerUuid());
                
                if (owner != null && owner.isOnline() && attacker.getUniqueId().equals(owner.getUniqueId())) {
                    // 假人看向攻击者
                    entityManager.lookAt(dummy, attacker.getLocation());
                    
                    // 发送消息
                    attacker.sendMessage("§c你在攻击自己的假人！");
                }
            }
        }
    }
    
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        
        // 通过实体管理器检查是否是假人实体
        Dummy dummy = entityManager.findDummyByEntity(entity);
        if (dummy == null) {
            return;
        }
        
        event.setCancelled(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        
        // 假人死亡处理
        if (plugin.getPluginConfig().getBoolean("safety.remove-on-death", true)) {
            dummyManager.despawnDummy(dummy.getOwnerUuid(), dummy.getName());
            
            Player owner = Bukkit.getPlayer(dummy.getOwnerUuid());
            if (owner != null && owner.isOnline()) {
                owner.sendMessage("§c你的假人 " + dummy.getName() + " 已死亡！");
            }
        } else {
            // 重新生成假人
            Player owner = Bukkit.getPlayer(dummy.getOwnerUuid());
            if (owner != null && owner.isOnline()) {
                dummy.spawn(owner);
            }
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        
        // 玩家下线时，假人继续在线（根据配置）
        // 这里可以添加逻辑来处理玩家下线时的假人行为
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        
        // 玩家上线时，检查假人状态
        // 这里可以添加欢迎消息或状态检查
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material blockType = event.getBlock().getType();
        
        // 如果玩家在砍树或挖矿，通知跟随模式的假人
        if (isMiningBlock(blockType) || isLogBlock(blockType)) {
            notifyDummiesToCollect(player, blockType);
        }
    }
    
    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            // 玩家钓鱼成功，通知假人
            notifyDummiesToFish(event.getPlayer());
        }
    }
    
    @EventHandler
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        // 玩家吃东西，假人也可能需要吃东西
        // 这里可以添加假人饥饿度管理
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        // 防止假人掉入虚空
        Player player = event.getPlayer();
        
        // 检查玩家是否有假人
        UUID playerUuid = player.getUniqueId();
        
        for (Dummy dummy : dummyManager.getPlayerDummies(playerUuid)) {
            if (dummy.isSpawned()) {
                // 获取假人当前位置
                Location dummyLocation = dummy.getLocation();
                if (dummyLocation == null) {
                    continue;
                }
                
                // 检查假人是否在危险位置（虚空边缘）
                if (isDangerousLocation(dummyLocation)) {
                    // 将假人传送到安全位置
                    Location safeLocation = findSafeLocation(player.getLocation(), dummyLocation);
                    if (safeLocation != null) {
                        entityManager.teleportEntity(dummy, safeLocation);
                        // 通知主人
                        player.sendMessage("§c你的假人处于危险位置，已自动传送至安全区域！");
                    }
                }
            }
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // 检查是否是假人背包
        String title = event.getView().getTitle();
        if (title.contains("的背包")) {
            Player player = (Player) event.getPlayer();
            Inventory inventory = event.getInventory();
            
            // 从标题中提取假人名
            String dummyName = title.replace(ChatColor.GOLD.toString(), "")
                                   .replace(ChatColor.GRAY.toString(), "")
                                   .replace("的背包", "")
                                   .trim();
            
            // 查找对应的假人
            Dummy dummy = plugin.getDummyManager().getDummyByName(dummyName);
            if (dummy != null) {
                // 保存背包数据到新的结构
                ItemStack[] mainInventory = new ItemStack[36];
                ItemStack[] armorInventory = new ItemStack[4];
                ItemStack offhandItem = null;
                
                // 保存主背包内容（0-35格）
                for (int i = 0; i < 36; i++) {
                    mainInventory[i] = inventory.getItem(i);
                }
                
                // 保存护甲栏（36-39格）
                for (int i = 0; i < 4; i++) {
                    armorInventory[i] = inventory.getItem(36 + i);
                }
                
                // 保存副手栏（40格）
                offhandItem = inventory.getItem(40);
                
                // 设置假人的背包数据
                dummy.setMainInventory(mainInventory);
                dummy.setArmorInventory(armorInventory);
                dummy.setOffhandItem(offhandItem);
                
                // 保存到数据库
                plugin.getDummyManager().saveDummy(dummy);
                
                player.sendMessage("§a假人背包已保存");
            } else {
                player.sendMessage("§c未找到对应的假人，背包修改可能未保存");
            }
        }
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // 检查是否是假人背包
        String title = event.getView().getTitle();
        if (title.contains("的背包")) {
            int slot = event.getRawSlot();
            
            // 检查是否点击了护甲栏位置（36-39是护甲栏，40是副手栏）
            if (slot >= 36 && slot <= 39) {
                ItemStack currentItem = event.getCurrentItem();
                ItemStack cursorItem = event.getCursor();
                
                // 如果是放置物品到护甲栏
                if (cursorItem != null && cursorItem.getType() != Material.AIR) {
                    boolean isValidArmor = false;
                    
                    // 检查物品是否是合适的护甲类型
                    switch (slot) {
                        case 36: // 头盔位置
                            isValidArmor = isValidHelmet(cursorItem);
                            break;
                        case 37: // 胸甲位置
                            isValidArmor = isValidChestplate(cursorItem);
                            break;
                        case 38: // 护腿位置
                            isValidArmor = isValidLeggings(cursorItem);
                            break;
                        case 39: // 靴子位置
                            isValidArmor = isValidBoots(cursorItem);
                            break;
                    }
                    
                    // 如果不是有效的护甲，取消事件
                    if (!isValidArmor) {
                        event.setCancelled(true);
                        Player player = (Player) event.getWhoClicked();
                        player.sendMessage("§c这个位置只能放置护甲！");
                    }
                }
            }
            
            // 检查是否点击了副手栏位置（40）
            if (slot == 40) {
                // 副手栏可以放置任何物品，不需要限制
                // 但我们可以添加一些限制，比如不能放置某些特殊物品
            }
        }
    }
    
    // 工具方法
    private void openDummyInventory(Player player, Dummy dummy) {
        // 创建一个临时的玩家背包副本（41格：36主背包 + 4护甲栏 + 1副手栏）
        Inventory dummyInventory = Bukkit.createInventory(
            null, 
            41, // 玩家背包大小
            ChatColor.GOLD + dummy.getName() + ChatColor.GRAY + "的背包"
        );
        
        // 获取假人的背包数据
        ItemStack[] mainInventory = dummy.getMainInventory();
        ItemStack[] armorInventory = dummy.getArmorInventory();
        ItemStack offhandItem = dummy.getOffhandItem();
        
        // 设置主背包内容（0-35格）
        if (mainInventory != null) {
            for (int i = 0; i < Math.min(mainInventory.length, 36); i++) {
                if (mainInventory[i] != null) {
                    dummyInventory.setItem(i, mainInventory[i]);
                }
            }
        }
        
        // 设置护甲栏（36-39格）
        if (armorInventory != null) {
            for (int i = 0; i < Math.min(armorInventory.length, 4); i++) {
                if (armorInventory[i] != null) {
                    dummyInventory.setItem(36 + i, armorInventory[i]);
                }
            }
        }
        
        // 设置副手栏（40格）
        if (offhandItem != null) {
            dummyInventory.setItem(40, offhandItem);
        }
        
        // 打开背包
        player.openInventory(dummyInventory);
        
        // 记录当前打开的假人
        openDummies.put(player.getUniqueId(), dummy.getName());
        
        // 发送提示
        player.sendMessage("§a已打开假人背包");
        player.sendMessage("§7假人: §e" + dummy.getName());
        player.sendMessage("§7模式: §e" + getModeDisplayName(dummy.getMode()));
        player.sendMessage("§7血量: §e" + String.format("%.1f", dummy.getHealth()) + "/" + 
            String.format("%.1f", dummy.getMaxHealth()));
    }
    
    private void notifyDummiesToCollect(Player player, Material material) {
        UUID playerUuid = player.getUniqueId();
        
        for (Dummy dummy : dummyManager.getPlayerDummies(playerUuid)) {
            if (dummy.getMode().equals("follow") && dummy.isSpawned()) {
                // 通知假人收集相同类型的物品
                // 让假人移动到玩家位置附近
                Location playerLoc = player.getLocation();
                Location collectLoc = playerLoc.clone().add(
                    (Math.random() * 3) - 1.5,
                    0,
                    (Math.random() * 3) - 1.5
                );
                
                // 移动到收集位置
                entityManager.teleportEntity(dummy, collectLoc);
                
                // 看向玩家
                entityManager.lookAt(dummy, playerLoc);
                
                // 根据方块类型执行相应收集动作
                if (isLogBlock(material)) {
                    // 如果是原木，执行砍树动作
                    dummy.chopTree(playerLoc);
                    // 尝试收集原木到背包
                    dummy.tryCollectItem(material);
                } else if (isMiningBlock(material)) {
                    // 如果是矿石，执行挖矿动作
                    dummy.mineBlock(playerLoc);
                    // 尝试收集矿石到背包
                    dummy.tryCollectItem(material);
                }
            }
        }
    }
    
    private void notifyDummiesToFish(Player player) {
        UUID playerUuid = player.getUniqueId();
        
        for (Dummy dummy : dummyManager.getPlayerDummies(playerUuid)) {
            if (dummy.getMode().equals("follow") && dummy.isSpawned()) {
                // 通知假人去钓鱼
                // 这里可以添加钓鱼逻辑
                entityManager.lookAt(dummy, player.getLocation());
            }
        }
    }
    
    /**
     * 检查位置是否危险（虚空、岩浆等）
     */
    private boolean isDangerousLocation(Location location) {
        if (location == null) {
            return false;
        }
        
        // 检查是否在虚空中（Y坐标过低）
        if (location.getY() <= 0) {
            return true;
        }
        
        // 检查下方是否是虚空
        Location below = location.clone().subtract(0, 1, 0);
        if (below.getBlock().getType() == Material.AIR) {
            // 检查下方更多方块
            for (int i = 2; i <= 10; i++) {
                Location check = location.clone().subtract(0, i, 0);
                if (check.getBlock().getType() == Material.AIR) {
                    if (check.getY() <= 0) {
                        return true; // 接近虚空
                    }
                } else {
                    break; // 遇到实体方块，安全
                }
            }
        }
        
        // 检查周围是否有危险方块（岩浆、仙人掌等）
        Material blockType = location.getBlock().getType();
        if (blockType == Material.LAVA || blockType == Material.LAVA_CAULDRON ||
            blockType == Material.FIRE || blockType == Material.SOUL_FIRE ||
            blockType == Material.CACTUS || blockType == Material.SWEET_BERRY_BUSH) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 寻找安全位置
     */
    private Location findSafeLocation(Location playerLocation, Location currentLocation) {
        // 首先尝试玩家当前位置
        Location safeLocation = playerLocation.clone().add(1, 0, 1);
        safeLocation.setY(safeLocation.getWorld().getHighestBlockYAt(safeLocation) + 1);
        
        if (!isDangerousLocation(safeLocation)) {
            return safeLocation;
        }
        
        // 尝试当前位置周围的位置
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (x == 0 && z == 0) continue;
                
                Location testLocation = currentLocation.clone().add(x, 0, z);
                testLocation.setY(testLocation.getWorld().getHighestBlockYAt(testLocation) + 1);
                
                if (!isDangerousLocation(testLocation)) {
                    return testLocation;
                }
            }
        }
        
        // 如果都找不到安全位置，返回玩家位置
        return playerLocation.clone().add(0, 1, 0);
    }
    
    private boolean isMiningBlock(Material material) {
        String name = material.name();
        return name.contains("_ORE") || 
               name.equals("COAL_ORE") ||
               name.equals("DIAMOND_ORE") ||
               name.equals("EMERALD_ORE") ||
               name.equals("GOLD_ORE") ||
               name.equals("IRON_ORE") ||
               name.equals("LAPIS_ORE") ||
               name.equals("REDSTONE_ORE") ||
               name.equals("NETHER_QUARTZ_ORE") ||
               name.equals("ANCIENT_DEBRIS");
    }
    
    private boolean isLogBlock(Material material) {
        String name = material.name();
        return name.endsWith("_LOG") || 
               name.endsWith("_WOOD") ||
               name.equals("BAMBOO") ||
               name.equals("MUSHROOM_STEM") ||
               name.equals("CRIMSON_STEM") ||
               name.equals("WARPED_STEM");
    }
    
    private String getModeDisplayName(String mode) {
        switch (mode) {
            case "idle":
                return "挂机";
            case "follow":
                return "跟随";
            default:
                return mode;
        }
    }
    
    // 护甲验证方法
    private boolean isValidHelmet(ItemStack item) {
        if (item == null) return false;
        String name = item.getType().name();
        return name.endsWith("_HELMET") || 
               name.equals("CARVED_PUMPKIN") || 
               name.equals("TURTLE_HELMET") ||
               name.equals("SKULL") ||
               name.equals("PLAYER_HEAD") ||
               name.equals("ZOMBIE_HEAD") ||
               name.equals("CREEPER_HEAD") ||
               name.equals("DRAGON_HEAD");
    }
    
    private boolean isValidChestplate(ItemStack item) {
        if (item == null) return false;
        String name = item.getType().name();
        return name.endsWith("_CHESTPLATE") || 
               name.equals("ELYTRA");
    }
    
    private boolean isValidLeggings(ItemStack item) {
        if (item == null) return false;
        String name = item.getType().name();
        return name.endsWith("_LEGGINGS");
    }
    
    private boolean isValidBoots(ItemStack item) {
        if (item == null) return false;
        String name = item.getType().name();
        return name.endsWith("_BOOTS");
    }
}