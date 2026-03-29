package io.Sriptirc_wp_1258.fakeman.objects;

import io.Sriptirc_wp_1258.fakeman.Fakeman;
import io.Sriptirc_wp_1258.fakeman.entity.EntityManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class Dummy {
    
    private final Fakeman plugin;
    private final UUID ownerUuid;
    private final String name;
    
    private Location location;
    private ArmorStand armorStand; // 盔甲架实体
    private double health;
    private double maxHealth;
    private String mode; // idle, follow
    private long onlineTime;
    private long maxOnlineTime;
    private ItemStack[] inventory;
    private ItemStack[] armor;
    private Map<String, Object> extraData;
    
    // AI相关
    private Player targetPlayer;
    private Material lastTargetMaterial;
    private long lastActionTime;
    private long lastFoodTime;
    private Location safeLocation;
    
    public Dummy(Fakeman plugin, UUID ownerUuid, String name, Location location) {
        this.plugin = plugin;
        this.ownerUuid = ownerUuid;
        this.name = name;
        this.location = location.clone();
        this.health = 100.0;
        this.maxHealth = 100.0;
        this.mode = "idle";
        this.onlineTime = 0;
        this.maxOnlineTime = 3600L;
        // 完整的玩家背包结构：36格主背包 + 4格护甲栏 + 1格副手栏
        this.inventory = new ItemStack[41]; // 36格主背包 + 4格护甲栏 + 1格副手栏
        this.armor = new ItemStack[4]; // 护甲栏（头盔、胸甲、护腿、靴子）
        this.extraData = new HashMap<>();
        
        // AI初始化
        this.targetPlayer = null;
        this.lastTargetMaterial = null;
        this.lastActionTime = System.currentTimeMillis();
        this.lastFoodTime = 0L;
        this.safeLocation = location.clone();
    }
    
    public boolean spawn(Player owner) {
        if (armorStand != null && !armorStand.isDead()) {
            return false;
        }
        
        try {
            // 通过实体管理器创建实体
            EntityManager entityManager = plugin.getEntityManager();
            if (entityManager == null || !entityManager.isEnabled()) {
                plugin.getLogger().warning("实体管理器未启用");
                return false;
            }
            
            // 创建实体
            boolean success = entityManager.createDummyEntity(this, owner);
            if (!success) {
                plugin.getLogger().warning("创建假人实体失败");
                return false;
            }
            
            // 更新装备
            updateEquipment();
            
            // 更新显示名称
            updateDisplayName();
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("生成假人失败: " + e.getMessage());
            return false;
        }
    }
    
    public void despawn() {
        EntityManager entityManager = plugin.getEntityManager();
        if (entityManager != null) {
            entityManager.despawnEntity(this);
        }
    }
    
    public void delete() {
        EntityManager entityManager = plugin.getEntityManager();
        if (entityManager != null) {
            entityManager.deleteEntity(this);
        }
    }
    
    public void update() {
        if (!isSpawned()) {
            return;
        }
        
        // 更新在线时间
        onlineTime++;
        
        // 检查安全位置
        checkSafety();
        
        // 检查饥饿度（每60秒检查一次）
        if (onlineTime % 1200 == 0) { // 1200 ticks = 60秒
            checkHunger();
        }
        
        // 根据模式执行行为
        switch (mode) {
            case "idle":
                updateIdleMode();
                break;
            case "follow":
                updateFollowMode();
                break;
        }
        
        // 更新显示名称
        updateDisplayName();
        
        // 更新装备
        updateEquipment();
    }
    
    public void tick() {
        // 每tick执行的操作
        if (isSpawned()) {
            // 保持假人不动（idle模式）
            if (mode.equals("idle")) {
                EntityManager entityManager = plugin.getEntityManager();
                if (entityManager != null) {
                    entityManager.teleportEntity(this, location);
                }
            }
        }
    }
    
    private void updateIdleMode() {
        // 挂机模式，保持原地不动
        EntityManager entityManager = plugin.getEntityManager();
        if (entityManager != null) {
            entityManager.teleportEntity(this, location);
        }
    }
    
    private void updateFollowMode() {
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            // 寻找主人
            Player owner = Bukkit.getPlayer(ownerUuid);
            if (owner != null && owner.isOnline()) {
                targetPlayer = owner;
            } else {
                // 没有目标，切换回挂机模式
                mode = "idle";
                return;
            }
        }
        
        // 跟随玩家
        followPlayer(targetPlayer);
        
        // 模仿玩家行为
        imitatePlayerBehavior(targetPlayer);
    }
    
    private void followPlayer(Player player) {
        if (!isSpawned()) {
            return;
        }
        
        Location playerLoc = player.getLocation();
        Location dummyLoc = getLocation();
        
        // 计算距离
        double distance = dummyLoc.distance(playerLoc);
        int followDistance = plugin.getPluginConfig().getInt("ai.follow-distance", 10);
        int maxDistance = plugin.getPluginConfig().getInt("ai.max-follow-distance", 30);
        
        if (distance > maxDistance) {
            // 距离太远，传送到玩家附近
            Location teleportLoc = playerLoc.clone().add(
                (Math.random() * 5) - 2.5,
                0,
                (Math.random() * 5) - 2.5
            );
            EntityManager entityManager = plugin.getEntityManager();
            if (entityManager != null) {
                entityManager.teleportEntity(this, findSafeLocation(teleportLoc));
            }
            return;
        }
        
        if (distance > followDistance) {
            // 向玩家移动
            moveTowards(playerLoc);
        }
        
        // 看向玩家
        EntityManager entityManager = plugin.getEntityManager();
        if (entityManager != null) {
            entityManager.lookAt(this, playerLoc);
        }
    }
    
    private void imitatePlayerBehavior(Player player) {
        // 获取玩家最近的行为
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastActionTime < 1000) { // 1秒冷却
            return;
        }
        
        // 检查玩家手中的物品
        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem != null && handItem.getType() != Material.AIR) {
            Material material = handItem.getType();
            
            // 判断物品类型并采取相应行动
            if (isTool(material)) {
                // 玩家在使用工具，检查假人是否有对应工具
                if (hasToolForAction(material)) {
                    // 假人有对应工具，使用工具
                    useTool(material, player.getLocation());
                } else {
                    // 假人没有对应工具，尝试寻找或通知主人
                    handleMissingTool(material);
                }
            } else if (isWeapon(material)) {
                // 玩家在战斗，检查假人是否有武器
                if (hasWeaponInHand()) {
                    // 假人有武器，准备战斗
                    prepareCombat(player);
                } else {
                    // 假人没有武器，尝试寻找或通知主人
                    handleMissingWeapon();
                }
            }
        }
        
        lastActionTime = currentTime;
    }
    
    private void useTool(Material tool, Location targetLocation) {
        // 检查假人手中的工具耐久度
        ItemStack currentTool = getCurrentTool();
        if (currentTool != null) {
            // 检查工具是否即将损坏
            if (isToolAboutToBreak(currentTool)) {
                sendNotification("§c假人的" + getToolDisplayName(tool) + "即将损坏！");
                
                // 尝试从背包中寻找替代工具
                ItemStack replacement = findToolInInventory(tool);
                if (replacement != null && !isToolAboutToBreak(replacement)) {
                    switchToTool(replacement);
                    sendNotification("§a已自动更换工具");
                } else {
                    sendNotification("§c请给假人提供新的" + getToolDisplayName(tool));
                    return; // 没有可用工具，停止行动
                }
            }
            
            // 消耗工具耐久度
            damageTool(currentTool);
        }
        
        // 根据工具类型执行相应动作
        switch (tool) {
            case DIAMOND_PICKAXE:
            case IRON_PICKAXE:
            case STONE_PICKAXE:
            case WOODEN_PICKAXE:
            case GOLDEN_PICKAXE:
                // 挖矿行为
                mineBlock(targetLocation);
                break;
                
            case DIAMOND_AXE:
            case IRON_AXE:
            case STONE_AXE:
            case WOODEN_AXE:
            case GOLDEN_AXE:
                // 砍树行为
                chopTree(targetLocation);
                break;
                
            case FISHING_ROD:
                // 钓鱼行为
                goFishing(targetLocation);
                break;
        }
    }
    
    public void mineBlock(Location location) {
        // 简单实现：播放挖矿粒子效果
        playMiningParticles();
    }
    
    public void chopTree(Location location) {
        // 简单实现：播放砍树粒子效果
        playChoppingParticles();
    }
    
    private void goFishing(Location location) {
        // 简单实现：移动到水边
        if (isSpawned()) {
            Location waterLocation = findWaterLocation(location);
            if (waterLocation != null) {
                moveTowards(waterLocation);
                // 播放钓鱼粒子效果
                playFishingParticles();
            }
        }
    }
    
    private void prepareCombat(Player player) {
        // 检查玩家是否在战斗
        // 简单实现：看向玩家看的方向
        if (isSpawned()) {
            EntityManager entityManager = plugin.getEntityManager();
            if (entityManager != null) {
                entityManager.lookAt(this, player.getLocation());
            }
        }
    }
    
    private void moveTowards(Location target) {
        if (!isSpawned()) {
            return;
        }
        
        // 简单移动：传送到目标附近
        Location current = getLocation();
        Location direction = target.clone().subtract(current);
        
        // 设置朝向
        current.setDirection(direction.toVector());
        EntityManager entityManager = plugin.getEntityManager();
        if (entityManager != null) {
            entityManager.teleportEntity(this, current);
        }
    }
    
    private void checkSafety() {
        if (!isSpawned()) {
            return;
        }
        
        Location currentLoc = getLocation();
        
        // 防止掉入虚空
        if (currentLoc.getY() < 0 && plugin.getPluginConfig().getBoolean("safety.prevent-void", true)) {
            EntityManager entityManager = plugin.getEntityManager();
            if (entityManager != null) {
                entityManager.teleportEntity(this, safeLocation);
            }
            sendNotification("§c假人 " + name + " 即将掉入虚空，已传送到安全位置");
            return;
        }
        
        // 检查危险方块
        Material blockType = currentLoc.getBlock().getType();
        if (isDangerousBlock(blockType) && plugin.getPluginConfig().getBoolean("safety.prevent-dangerous-areas", true)) {
            EntityManager entityManager = plugin.getEntityManager();
            if (entityManager != null) {
                entityManager.teleportEntity(this, safeLocation);
            }
            sendNotification("§c假人 " + name + " 处于危险区域，已传送到安全位置");
            return;
        }
        
        // 更新安全位置
        if (isSafeLocation(currentLoc)) {
            safeLocation = currentLoc.clone();
        }
    }
    
    // 工具检查相关方法
    private boolean hasToolForAction(Material requiredTool) {
        // 检查假人手中是否有工具
        if (inventory != null && inventory.length > 0) {
            ItemStack mainHand = inventory[0];
            if (mainHand != null && mainHand.getType() != Material.AIR) {
                Material handTool = mainHand.getType();
                // 检查是否是同类工具（比如都是镐子）
                return isSameToolType(handTool, requiredTool);
            }
        }
        
        // 检查背包中是否有对应工具
        return findToolInInventory(requiredTool) != null;
    }
    
    private boolean hasWeaponInHand() {
        // 检查假人手中是否有武器
        if (inventory != null && inventory.length > 0) {
            ItemStack mainHand = inventory[0];
            if (mainHand != null && mainHand.getType() != Material.AIR) {
                return isWeapon(mainHand.getType());
            }
        }
        
        // 检查背包中是否有武器
        return findWeaponInInventory() != null;
    }
    
    private boolean isSameToolType(Material tool1, Material tool2) {
        // 判断两个工具是否是同一类型
        String name1 = tool1.name();
        String name2 = tool2.name();
        
        if (name1.endsWith("_PICKAXE") && name2.endsWith("_PICKAXE")) return true;
        if (name1.endsWith("_AXE") && name2.endsWith("_AXE")) return true;
        if (name1.endsWith("_SHOVEL") && name2.endsWith("_SHOVEL")) return true;
        if (name1.endsWith("_HOE") && name2.endsWith("_HOE")) return true;
        if (name1.equals("FISHING_ROD") && name2.equals("FISHING_ROD")) return true;
        
        return false;
    }
    
    private ItemStack findToolInInventory(Material requiredTool) {
        if (inventory == null) return null;
        
        for (ItemStack item : inventory) {
            if (item != null && item.getType() != Material.AIR) {
                Material itemType = item.getType();
                if (isSameToolType(itemType, requiredTool)) {
                    return item;
                }
            }
        }
        
        return null;
    }
    
    private ItemStack findWeaponInInventory() {
        if (inventory == null) return null;
        
        for (ItemStack item : inventory) {
            if (item != null && item.getType() != Material.AIR) {
                if (isWeapon(item.getType())) {
                    return item;
                }
            }
        }
        
        return null;
    }
    
    private void handleMissingTool(Material requiredTool) {
        // 假人没有对应工具的处理逻辑
        String toolName = getToolDisplayName(requiredTool);
        sendNotification("§e假人需要 " + toolName + " 来模仿你的行为！");
        sendNotification("§7请给假人装备相应的工具或放入背包");
        
        // 可以在这里添加自动切换到背包中工具的逻辑
        ItemStack foundTool = findToolInInventory(requiredTool);
        if (foundTool != null) {
            // 自动切换到手中
            switchToTool(foundTool);
            sendNotification("§a已自动切换到 " + toolName);
        }
    }
    
    private void handleMissingWeapon() {
        // 假人没有武器的处理逻辑
        sendNotification("§e假人需要武器来保护你！");
        sendNotification("§7请给假人装备武器或放入背包");
        
        // 可以在这里添加自动切换到背包中武器的逻辑
        ItemStack foundWeapon = findWeaponInInventory();
        if (foundWeapon != null) {
            // 自动切换到手中
            switchToTool(foundWeapon);
            sendNotification("§a已自动切换到武器");
        }
    }
    
    private void switchToTool(ItemStack tool) {
        // 将工具切换到假人手中（快捷栏0-8格）
        if (inventory != null && inventory.length > 0) {
            // 优先使用快捷栏第一格（0）
            ItemStack currentHand = inventory[0];
            
            // 找到工具在背包中的位置
            for (int i = 0; i < inventory.length; i++) {
                if (inventory[i] != null && inventory[i].equals(tool)) {
                    // 如果工具已经在快捷栏中，不需要交换
                    if (i < 9) {
                        // 工具已经在快捷栏，直接使用
                        return;
                    }
                    
                    // 交换位置：将工具放到快捷栏第一格
                    inventory[i] = currentHand;
                    inventory[0] = tool;
                    
                    // 更新装备
                    updateEquipment();
                    return;
                }
            }
        }
    }
    
    // 工具耐久度相关方法
    private ItemStack getCurrentTool() {
        // 检查快捷栏中的所有工具（0-8格）
        if (inventory != null && inventory.length > 0) {
            // 优先检查快捷栏第一格（0）
            if (inventory[0] != null && isTool(inventory[0])) {
                return inventory[0];
            }
            
            // 检查其他快捷栏位置
            for (int i = 1; i < Math.min(9, inventory.length); i++) {
                if (inventory[i] != null && isTool(inventory[i])) {
                    return inventory[i];
                }
            }
        }
        return null;
    }
    
    private boolean isTool(ItemStack item) {
        if (item == null) return false;
        Material material = item.getType();
        return material.name().endsWith("_PICKAXE") || 
               material.name().endsWith("_AXE") || 
               material.name().endsWith("_SHOVEL") || 
               material.name().endsWith("_HOE") || 
               material.name().endsWith("_SWORD") || 
               material == Material.FISHING_ROD;
    }
    
    private boolean isToolAboutToBreak(ItemStack tool) {
        if (tool == null || !tool.hasItemMeta()) {
            return false;
        }
        
        // 检查工具是否有耐久度
        if (tool.getType().getMaxDurability() > 0) {
            short durability = tool.getDurability();
            short maxDurability = tool.getType().getMaxDurability();
            
            // 如果耐久度只剩下10%或更少，认为即将损坏
            float remaining = 1.0f - ((float) durability / maxDurability);
            return remaining <= 0.1f;
        }
        
        return false;
    }
    
    private void damageTool(ItemStack tool) {
        if (tool == null || !tool.hasItemMeta()) {
            return;
        }
        
        // 检查工具是否有耐久度
        if (tool.getType().getMaxDurability() > 0) {
            short durability = tool.getDurability();
            short maxDurability = tool.getType().getMaxDurability();
            
            // 每次使用消耗1点耐久度
            if (durability < maxDurability) {
                tool.setDurability((short) (durability + 1));
                
                // 检查工具是否损坏
                if (durability + 1 >= maxDurability) {
                    // 工具损坏
                    tool.setAmount(0); // 清除物品
                    sendNotification("§c假人的工具已损坏！");
                    
                    // 尝试自动切换到背包中的其他工具
                    Material toolType = tool.getType();
                    ItemStack replacement = findToolInInventory(toolType);
                    if (replacement != null) {
                        switchToTool(replacement);
                        sendNotification("§a已自动更换工具");
                    }
                }
            }
        }
    }
    
    // 食物管理系统
    private boolean hasFood() {
        if (inventory == null) return false;
        
        for (ItemStack item : inventory) {
            if (item != null && item.getType() != Material.AIR) {
                if (isFood(item.getType())) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private void checkHunger() {
        // 模拟假人饥饿度
        // 每60秒有30%几率需要食物
        if (Math.random() < 0.3) {
            if (!hasFood()) {
                sendNotification("§e假人饿了，需要食物！");
                sendNotification("§7请给假人背包中放入食物");
            } else {
                // 假人有食物，自动消耗
                consumeFood();
                sendNotification("§a假人已自动进食");
            }
        }
    }
    
    private void consumeFood() {
        // 消耗一个食物
        if (inventory == null) return;
        
        for (int i = 0; i < inventory.length; i++) {
            ItemStack item = inventory[i];
            if (item != null && item.getType() != Material.AIR && isFood(item.getType())) {
                int amount = item.getAmount();
                if (amount > 1) {
                    item.setAmount(amount - 1);
                } else {
                    inventory[i] = null; // 清除食物
                }
                break;
            }
        }
    }
    
    // 工具方法
    private boolean isTool(Material material) {
        String name = material.name();
        return name.endsWith("_PICKAXE") || 
               name.endsWith("_AXE") || 
               name.endsWith("_SHOVEL") || 
               name.endsWith("_HOE") ||
               name.equals("FISHING_ROD");
    }
    
    private boolean isWeapon(Material material) {
        String name = material.name();
        return name.endsWith("_SWORD") || 
               name.endsWith("_BOW") || 
               name.equals("CROSSBOW") ||
               name.equals("TRIDENT");
    }
    
    private boolean isFood(Material material) {
        // 简单的食物判断
        String name = material.name();
        return name.contains("_APPLE") ||
               name.equals("BREAD") ||
               name.equals("COOKED_BEEF") ||
               name.equals("COOKED_CHICKEN") ||
               name.equals("COOKED_PORKCHOP") ||
               name.equals("COOKED_MUTTON") ||
               name.equals("COOKED_RABBIT") ||
               name.equals("COOKED_SALMON") ||
               name.equals("COOKED_COD") ||
               name.equals("CAKE") ||
               name.equals("PUMPKIN_PIE") ||
               name.equals("GOLDEN_APPLE") ||
               name.equals("ENCHANTED_GOLDEN_APPLE");
    }
    
    private boolean isDangerousBlock(Material material) {
        return material == Material.LAVA || 
               material == Material.FIRE || 
               material == Material.CAMPFIRE ||
               material == Material.SOUL_CAMPFIRE ||
               material == Material.CACTUS ||
               material == Material.SWEET_BERRY_BUSH;
    }
    
    private boolean isSafeLocation(Location location) {
        return location.getBlock().getType().isSolid() && 
               location.clone().add(0, 1, 0).getBlock().isEmpty() &&
               !isDangerousBlock(location.getBlock().getType()) &&
               !isDangerousBlock(location.clone().add(0, 1, 0).getBlock().getType());
    }
    
    private Location findSafeLocation(Location location) {
        // 寻找安全的位置
        for (int y = 0; y < 10; y++) {
            Location checkLoc = location.clone().add(0, y, 0);
            if (checkLoc.getBlock().getType().isSolid() && 
                checkLoc.clone().add(0, 1, 0).getBlock().isEmpty() &&
                checkLoc.clone().add(0, 2, 0).getBlock().isEmpty()) {
                return checkLoc.clone().add(0, 1, 0);
            }
        }
        return location;
    }
    
    private Location findWaterLocation(Location center) {
        // 在周围寻找水
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                Location checkLoc = center.clone().add(x, 0, z);
                if (checkLoc.getBlock().getType() == Material.WATER) {
                    return checkLoc;
                }
            }
        }
        return null;
    }
    
    private String getToolDisplayName(Material tool) {
        String name = tool.name();
        if (name.endsWith("_PICKAXE")) return "镐子";
        if (name.endsWith("_AXE")) return "斧头";
        if (name.endsWith("_SHOVEL")) return "铲子";
        if (name.endsWith("_HOE")) return "锄头";
        if (name.equals("FISHING_ROD")) return "钓鱼竿";
        if (name.endsWith("_SWORD")) return "剑";
        if (name.endsWith("_BOW")) return "弓";
        if (name.equals("CROSSBOW")) return "弩";
        if (name.equals("TRIDENT")) return "三叉戟";
        return "工具";
    }
    
    private String getModeDisplayName() {
        switch (mode) {
            case "idle":
                return "挂机";
            case "follow":
                return "跟随";
            default:
                return mode;
        }
    }
    
    private String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "秒";
        } else if (seconds < 3600) {
            return (seconds / 60) + "分";
        } else {
            return (seconds / 3600) + "时";
        }
    }
    
    private void updateDisplayName() {
        EntityManager entityManager = plugin.getEntityManager();
        if (entityManager == null) {
            return;
        }
        
        entityManager.updateEntityName(this);
    }
    
    private void updateEquipment() {
        EntityManager entityManager = plugin.getEntityManager();
        if (entityManager == null) {
            return;
        }
        
        entityManager.updateEntityEquipment(this);
    }
    
    private void playMiningParticles() {
        // 播放挖矿粒子效果
        if (isSpawned() && plugin.getPluginConfig().getBoolean("visual.particle-effects", true)) {
            Location loc = getLocation().add(0, 1.5, 0);
            getLocation().getWorld().spawnParticle(org.bukkit.Particle.BLOCK_CRACK, loc, 10, 
                org.bukkit.Material.STONE.createBlockData());
        }
    }
    
    private void playChoppingParticles() {
        // 播放砍树粒子效果
        if (isSpawned() && plugin.getPluginConfig().getBoolean("visual.particle-effects", true)) {
            Location loc = getLocation().add(0, 1.5, 0);
            getLocation().getWorld().spawnParticle(org.bukkit.Particle.BLOCK_CRACK, loc, 10, 
                org.bukkit.Material.OAK_LOG.createBlockData());
        }
    }
    
    private void playFishingParticles() {
        // 播放钓鱼粒子效果
        if (isSpawned() && plugin.getPluginConfig().getBoolean("visual.particle-effects", true)) {
            Location loc = getLocation().add(0, 1, 0);
            getLocation().getWorld().spawnParticle(org.bukkit.Particle.WATER_SPLASH, loc, 5);
        }
    }
    
    private void sendNotification(String message) {
        Player owner = Bukkit.getPlayer(ownerUuid);
        if (owner != null && owner.isOnline() && 
            plugin.getPluginConfig().getBoolean("notifications.chat-messages", true)) {
            owner.sendMessage(message);
        }
    }
    
    // Getter 和 Setter 方法
    public UUID getOwnerUuid() {
        return ownerUuid;
    }
    
    public String getName() {
        return name;
    }
    
    public Location getLocation() {
        EntityManager entityManager = plugin.getEntityManager();
        if (entityManager != null) {
            Location entityLoc = entityManager.getEntityLocation(this);
            if (entityLoc != null) {
                return entityLoc;
            }
        }
        return location;
    }
    
    public double getHealth() {
        return health;
    }
    
    public void setHealth(double health) {
        this.health = health;
    }
    
    public double getMaxHealth() {
        return maxHealth;
    }
    
    public void setMaxHealth(double maxHealth) {
        this.maxHealth = maxHealth;
    }
    
    public String getMode() {
        return mode;
    }
    
    public void setMode(String mode) {
        this.mode = mode;
    }
    
    public long getOnlineTime() {
        return onlineTime;
    }
    
    public void setOnlineTime(long onlineTime) {
        this.onlineTime = onlineTime;
    }
    
    public long getMaxOnlineTime() {
        return maxOnlineTime;
    }
    
    public void setMaxOnlineTime(long maxOnlineTime) {
        this.maxOnlineTime = maxOnlineTime;
    }
    
    public ItemStack[] getInventory() {
        return inventory;
    }
    
    public void setInventory(ItemStack[] inventory) {
        this.inventory = inventory;
        updateEquipment();
    }
    
    public ItemStack[] getArmor() {
        return armor;
    }
    
    public void setArmor(ItemStack[] armor) {
        this.armor = armor;
        updateEquipment();
    }
    
    public Map<String, Object> getExtraData() {
        return extraData;
    }
    
    public void setExtraData(Map<String, Object> extraData) {
        this.extraData = extraData;
    }
    
    public boolean shouldDespawn() {
        return onlineTime >= maxOnlineTime;
    }
    
    public String getDisplayName() {
        String format = plugin.getPluginConfig().getString("visual.name-format", "&e{owner}&7_&6假人");
        return format.replace("{owner}", Bukkit.getOfflinePlayer(ownerUuid).getName()).replace("&", "§");
    }
    
    public boolean isSpawned() {
        EntityManager entityManager = plugin.getEntityManager();
        if (entityManager != null) {
            return entityManager.isEntityValid(this);
        }
        return false;
    }
    
    // 盔甲架相关方法
    public ArmorStand getArmorStand() {
        return armorStand;
    }
    
    public void setArmorStand(ArmorStand armorStand) {
        this.armorStand = armorStand;
    }
    
    /**
     * 尝试收集物品到背包
     * @param material 要收集的物品类型
     */
    public void tryCollectItem(Material material) {
        if (inventory == null) {
            return;
        }
        
        // 检查背包是否有空位
        int emptySlot = -1;
        for (int i = 0; i < inventory.length; i++) {
            if (inventory[i] == null || inventory[i].getType() == Material.AIR) {
                emptySlot = i;
                break;
            }
        }
        
        if (emptySlot == -1) {
            // 背包已满，通知主人
            sendNotification("§c假人背包已满，无法收集 " + getMaterialDisplayName(material));
            return;
        }
        
        // 创建物品并添加到背包
        ItemStack item = new ItemStack(material, 1);
        inventory[emptySlot] = item;
        
        // 通知主人
        sendNotification("§a假人收集了 " + getMaterialDisplayName(material));
        
        // 更新装备显示
        updateEquipment();
    }
    
    /**
     * 获取物品的显示名称
     */
    private String getMaterialDisplayName(Material material) {
        String name = material.name().toLowerCase();
        // 简单的名称转换
        if (name.contains("log")) return "原木";
        if (name.contains("planks")) return "木板";
        if (name.contains("coal")) return "煤炭";
        if (name.contains("iron")) return "铁矿石";
        if (name.contains("gold")) return "金矿石";
        if (name.contains("diamond")) return "钻石";
        if (name.contains("emerald")) return "绿宝石";
        if (name.contains("redstone")) return "红石";
        if (name.contains("lapis")) return "青金石";
        return material.name();
    }
    
    // ============ 新的智能行为方法 ============
    
    /**
     * 开始挖矿行为
     */
    public void startMining(Material oreType, Location location) {
        if (!isSpawned() || !getMode().equals("follow")) {
            return;
        }
        
        sendNotification("§a假人正在帮你挖矿...");
        
        // 检查是否有镐子
        if (!hasToolForMining()) {
            sendNotification("§c假人没有镐子，无法挖矿！");
            return;
        }
        
        // 检查背包空间
        if (isInventoryFull()) {
            sendNotification("§c假人背包已满，无法收集矿石！");
            return;
        }
        
        // 移动到矿石位置附近
        moveTowards(location);
        
        // 播放挖矿粒子效果
        playMiningParticles();
        
        // 尝试收集矿石
        tryCollectItem(oreType);
    }
    
    /**
     * 开始砍树行为
     */
    public void startChopping(Material logType, Location location) {
        if (!isSpawned() || !getMode().equals("follow")) {
            return;
        }
        
        sendNotification("§a假人正在帮你砍树...");
        
        // 检查是否有斧头
        if (!hasToolForChopping()) {
            sendNotification("§c假人没有斧头，无法砍树！");
            return;
        }
        
        // 检查背包空间
        if (isInventoryFull()) {
            sendNotification("§c假人背包已满，无法收集原木！");
            return;
        }
        
        // 移动到树木位置附近
        moveTowards(location);
        
        // 播放砍树粒子效果
        playChoppingParticles();
        
        // 尝试收集原木
        tryCollectItem(logType);
    }
    
    /**
     * 开始钓鱼行为
     */
    public void startFishing(Location location) {
        if (!isSpawned() || !getMode().equals("follow")) {
            return;
        }
        
        sendNotification("§a假人正在陪你钓鱼...");
        
        // 检查是否有钓鱼竿
        if (!hasFishingRod()) {
            sendNotification("§c假人没有钓鱼竿，无法钓鱼！");
            return;
        }
        
        // 寻找水边位置
        Location waterLocation = findWaterLocation(location);
        if (waterLocation == null) {
            sendNotification("§c附近没有水，无法钓鱼！");
            return;
        }
        
        // 移动到水边
        moveTowards(waterLocation);
        
        // 播放钓鱼粒子效果
        playFishingParticles();
    }
    
    /**
     * 保护主人免受攻击
     */
    public void protectOwner(Entity attacker) {
        if (!isSpawned() || !getMode().equals("follow")) {
            return;
        }
        
        sendNotification("§c假人正在保护你！");
        
        // 检查是否有武器
        if (!hasWeaponNew()) {
            sendNotification("§c假人没有武器，无法战斗！");
            return;
        }
        
        // 冲向攻击者
        if (attacker != null) {
            Location attackerLoc = attacker.getLocation();
            moveTowards(attackerLoc);
            
            // 看向攻击者
            if (plugin.getEntityManager() != null) {
                plugin.getEntityManager().lookAt(this, attackerLoc);
            }
            
            // 播放战斗粒子效果
            playCombatParticles();
        }
    }
    
    /**
     * 吃东西
     */
    public void consumeFood(Material foodType) {
        if (!isSpawned() || !getMode().equals("follow")) {
            return;
        }
        
        // 检查假人是否需要吃东西（饥饿度管理）
        if (needsFood()) {
            // 检查背包中是否有食物
            ItemStack food = findFoodInInventory();
            if (food != null) {
                sendNotification("§a假人正在吃东西...");
                // 消耗食物
                consumeFoodItem(food);
            } else {
                sendNotification("§c假人饿了，但背包里没有食物！");
            }
        }
    }
    
    // ============ 工具检查方法 ============
    
    /**
     * 检查是否有挖矿工具
     */
    private boolean hasToolForMining() {
        // 检查假人手中的工具
        ItemStack handItem = getCurrentTool();
        if (handItem != null && isPickaxe(handItem.getType())) {
            return true;
        }
        
        // 检查背包中是否有镐子
        ItemStack pickaxe = findToolInInventory(Material.DIAMOND_PICKAXE);
        if (pickaxe == null) pickaxe = findToolInInventory(Material.IRON_PICKAXE);
        if (pickaxe == null) pickaxe = findToolInInventory(Material.STONE_PICKAXE);
        if (pickaxe == null) pickaxe = findToolInInventory(Material.WOODEN_PICKAXE);
        if (pickaxe == null) pickaxe = findToolInInventory(Material.GOLDEN_PICKAXE);
        
        if (pickaxe != null) {
            // 自动切换到镐子
            switchToTool(pickaxe);
            sendNotification("§a已自动切换到镐子");
            return true;
        }
        
        return false;
    }
    
    /**
     * 检查是否有砍树工具
     */
    private boolean hasToolForChopping() {
        // 检查假人手中的工具
        ItemStack handItem = getCurrentTool();
        if (handItem != null && isAxe(handItem.getType())) {
            return true;
        }
        
        // 检查背包中是否有斧头
        ItemStack axe = findToolInInventory(Material.DIAMOND_AXE);
        if (axe == null) axe = findToolInInventory(Material.IRON_AXE);
        if (axe == null) axe = findToolInInventory(Material.STONE_AXE);
        if (axe == null) axe = findToolInInventory(Material.WOODEN_AXE);
        if (axe == null) axe = findToolInInventory(Material.GOLDEN_AXE);
        
        if (axe != null) {
            // 自动切换到斧头
            switchToTool(axe);
            sendNotification("§a已自动切换到斧头");
            return true;
        }
        
        return false;
    }
    
    /**
     * 检查是否有钓鱼竿
     */
    private boolean hasFishingRod() {
        // 检查假人手中的工具
        ItemStack handItem = getCurrentTool();
        if (handItem != null && handItem.getType() == Material.FISHING_ROD) {
            return true;
        }
        
        // 检查背包中是否有钓鱼竿
        ItemStack fishingRod = findToolInInventory(Material.FISHING_ROD);
        if (fishingRod != null) {
            // 自动切换到钓鱼竿
            switchToTool(fishingRod);
            sendNotification("§a已自动切换到钓鱼竿");
            return true;
        }
        
        return false;
    }
    
    /**
     * 检查是否有武器（新版本）
     */
    private boolean hasWeaponNew() {
        // 检查假人手中的工具
        ItemStack handItem = getCurrentTool();
        if (handItem != null && isWeapon(handItem.getType())) {
            return true;
        }
        
        // 检查背包中是否有武器
        for (ItemStack item : inventory) {
            if (item != null && isWeapon(item.getType())) {
                // 自动切换到武器
                switchToTool(item);
                sendNotification("§a已自动切换到武器");
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查背包是否已满
     */
    private boolean isInventoryFull() {
        if (inventory == null) return true;
        
        for (ItemStack item : inventory) {
            if (item == null || item.getType() == Material.AIR) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 检查假人是否需要吃东西
     */
    private boolean needsFood() {
        // 简单的饥饿度管理：每10分钟需要吃一次东西
        long currentTime = System.currentTimeMillis();
        if (lastFoodTime == 0) {
            lastFoodTime = currentTime;
            return false;
        }
        
        // 10分钟 = 600000毫秒
        return currentTime - lastFoodTime > 600000;
    }
    
    /**
     * 在背包中寻找食物
     */
    private ItemStack findFoodInInventory() {
        if (inventory == null) return null;
        
        for (ItemStack item : inventory) {
            if (item != null && item.getType().isEdible()) {
                return item;
            }
        }
        return null;
    }
    
    /**
     * 消耗食物
     */
    private void consumeFoodItem(ItemStack food) {
        // 减少食物数量
        int amount = food.getAmount() - 1;
        if (amount <= 0) {
            // 从背包中移除空的食物
            for (int i = 0; i < inventory.length; i++) {
                if (inventory[i] == food) {
                    inventory[i] = null;
                    break;
                }
            }
        } else {
            food.setAmount(amount);
        }
        
        // 更新最后一次吃东西的时间
        lastFoodTime = System.currentTimeMillis();
        
        // 播放吃东西粒子效果
        playEatingParticles();
    }
    
    // ============ 粒子效果方法 ============
    
    private void playCombatParticles() {
        // 战斗粒子效果实现
        // 这里可以添加实际的粒子效果
    }
    
    private void playEatingParticles() {
        // 吃东西粒子效果实现
        // 这里可以添加实际的粒子效果
    }
    
    // 判断是否是镐子
    private boolean isPickaxe(Material material) {
        return material == Material.DIAMOND_PICKAXE ||
               material == Material.IRON_PICKAXE ||
               material == Material.STONE_PICKAXE ||
               material == Material.WOODEN_PICKAXE ||
               material == Material.GOLDEN_PICKAXE;
    }
    
    // 判断是否是斧头
    private boolean isAxe(Material material) {
        return material == Material.DIAMOND_AXE ||
               material == Material.IRON_AXE ||
               material == Material.STONE_AXE ||
               material == Material.WOODEN_AXE ||
               material == Material.GOLDEN_AXE;
    }
}