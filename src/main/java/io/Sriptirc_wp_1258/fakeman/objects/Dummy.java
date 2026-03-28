package io.Sriptirc_wp_1258.fakeman.objects;

import io.Sriptirc_wp_1258.fakeman.Fakeman;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class Dummy {
    
    private final Fakeman plugin;
    private final UUID ownerUuid;
    private final String name;
    
    private Location location;
    private Zombie entity;
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
        this.inventory = new ItemStack[36];
        this.armor = new ItemStack[4];
        this.extraData = new HashMap<>();
        
        // AI初始化
        this.targetPlayer = null;
        this.lastTargetMaterial = null;
        this.lastActionTime = System.currentTimeMillis();
        this.safeLocation = location.clone();
    }
    
    public boolean spawn() {
        if (entity != null && entity.isValid()) {
            return false;
        }
        
        try {
            // 创建僵尸实体作为假人
            Zombie zombie = location.getWorld().spawn(location, Zombie.class);
            
            // 设置假人属性
            zombie.setCustomName(getDisplayName());
            zombie.setCustomNameVisible(true);
            zombie.setAI(false); // 禁用原版AI
            zombie.setAdult();
            zombie.setCanPickupItems(false);
            zombie.setRemoveWhenFarAway(false);
            zombie.setSilent(true);
            
            // 设置血量
            Objects.requireNonNull(zombie.getAttribute(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(maxHealth);
            zombie.setHealth(health);
            
            // 添加效果使其看起来像假人
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 255, false, false));
            
            // 设置装备
            updateEquipment(zombie);
            
            this.entity = zombie;
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("生成假人实体失败: " + e.getMessage());
            return false;
        }
    }
    
    public void despawn() {
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
        entity = null;
    }
    
    public void update() {
        if (entity == null || !entity.isValid()) {
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
        updateEquipment(entity);
    }
    
    public void tick() {
        // 每tick执行的操作
        if (entity != null && entity.isValid()) {
            // 保持假人不动（idle模式）
            if (mode.equals("idle")) {
                entity.teleport(location);
            }
        }
    }
    
    private void updateIdleMode() {
        // 挂机模式，保持原地不动
        if (entity != null) {
            entity.teleport(location);
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
        if (entity == null || !entity.isValid()) {
            return;
        }
        
        Location playerLoc = player.getLocation();
        Location dummyLoc = entity.getLocation();
        
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
            entity.teleport(findSafeLocation(teleportLoc));
            return;
        }
        
        if (distance > followDistance) {
            // 向玩家移动
            moveTowards(playerLoc);
        }
    }
    
    private void imitatePlayerBehavior(Player player) {
        // 获取玩家最近的行为
        // 这里需要监听玩家事件，暂时简单实现
        
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
                if (hasWeapon()) {
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
    
    private void mineBlock(Location location) {
        // 简单实现：播放挖矿动画
        if (entity != null) {
            entity.swingMainHand();
            // 可以在这里添加粒子效果
        }
    }
    
    private void chopTree(Location location) {
        // 简单实现：播放砍树动画
        if (entity != null) {
            entity.swingMainHand();
            // 可以在这里添加粒子效果
        }
    }
    
    private void goFishing(Location location) {
        // 简单实现：移动到水边
        if (entity != null) {
            Location waterLocation = findWaterLocation(location);
            if (waterLocation != null) {
                moveTowards(waterLocation);
                entity.swingMainHand();
            }
        }
    }
    
    private void prepareCombat(Player player) {
        // 检查玩家是否在战斗
        // 简单实现：看向玩家看的方向
        if (entity != null) {
            entity.teleport(entity.getLocation().setDirection(player.getLocation().getDirection()));
        }
    }
    
    private void moveTowards(Location target) {
        if (entity == null || !entity.isValid()) {
            return;
        }
        
        Location current = entity.getLocation();
        Location direction = target.clone().subtract(current);
        
        // 设置朝向
        current.setDirection(direction.toVector());
        entity.teleport(current);
        
        // 简单移动（由于AI被禁用，这里只是转向）
        // 实际移动需要更复杂的路径查找
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
    
    private void checkSafety() {
        if (entity == null || !entity.isValid()) {
            return;
        }
        
        Location currentLoc = entity.getLocation();
        
        // 防止掉入虚空
        if (currentLoc.getY() < 0 && plugin.getPluginConfig().getBoolean("safety.prevent-void", true)) {
            entity.teleport(safeLocation);
            sendNotification("§c假人 " + name + " 即将掉入虚空，已传送到安全位置");
            return;
        }
        
        // 检查危险方块
        Material blockType = currentLoc.getBlock().getType();
        if (isDangerousBlock(blockType) && plugin.getPluginConfig().getBoolean("safety.prevent-dangerous-areas", true)) {
            entity.teleport(safeLocation);
            sendNotification("§c假人 " + name + " 处于危险区域，已传送到安全位置");
            return;
        }
        
        // 更新安全位置
        if (isSafeLocation(currentLoc)) {
            safeLocation = currentLoc.clone();
        }
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
    
    private boolean hasWeapon() {
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
        // 将工具切换到假人手中
        if (inventory != null && inventory.length > 0) {
            // 保存当前手中的物品
            ItemStack currentHand = inventory[0];
            
            // 找到工具在背包中的位置
            for (int i = 0; i < inventory.length; i++) {
                if (inventory[i] != null && inventory[i].equals(tool)) {
                    // 交换位置
                    inventory[i] = currentHand;
                    inventory[0] = tool;
                    
                    // 更新实体装备
                    if (entity != null) {
                        updateEquipment(entity);
                    }
                    break;
                }
            }
        }
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
    
    // 工具耐久度相关方法
    private ItemStack getCurrentTool() {
        if (inventory != null && inventory.length > 0) {
            return inventory[0];
        }
        return null;
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
    
    // 检查假人是否有食物（用于饥饿度管理）
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
    
    private void updateDisplayName() {
        if (entity == null || !entity.isValid()) {
            return;
        }
        
        String format = plugin.getPluginConfig().getString("visual.name-format", "&e{owner}&7_&6NPC");
        String displayName = format
            .replace("{owner}", Bukkit.getOfflinePlayer(ownerUuid).getName())
            .replace("{health}", String.format("%.1f", health))
            .replace("{maxHealth}", String.format("%.1f", maxHealth))
            .replace("{mode}", getModeDisplayName())
            .replace("{time}", formatTime(maxOnlineTime - onlineTime));
        
        // 添加颜色代码
        displayName = displayName.replace("&", "§");
        
        entity.setCustomName(displayName);
        
        // 是否显示血量
        boolean showHealth = plugin.getPluginConfig().getBoolean("visual.show-health", true);
        entity.setCustomNameVisible(showHealth);
    }
    
    private void updateEquipment(Zombie zombie) {
        EntityEquipment equipment = zombie.getEquipment();
        if (equipment == null) {
            return;
        }
        
        // 设置装备
        if (armor != null && armor.length >= 4) {
            equipment.setHelmet(armor[0]);
            equipment.setChestplate(armor[1]);
            equipment.setLeggings(armor[2]);
            equipment.setBoots(armor[3]);
        }
        
        // 设置主手物品
        if (inventory != null && inventory.length > 0) {
            equipment.setItemInMainHand(inventory[0]);
        }
        
        // 防止装备掉落
        equipment.setHelmetDropChance(0);
        equipment.setChestplateDropChance(0);
        equipment.setLeggingsDropChance(0);
        equipment.setBootsDropChance(0);
        equipment.setItemInMainHandDropChance(0);
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
        return entity != null ? entity.getLocation() : location;
    }
    
    public double getHealth() {
        return health;
    }
    
    public void setHealth(double health) {
        this.health = health;
        if (entity != null && entity.isValid()) {
            entity.setHealth(health);
        }
    }
    
    public double getMaxHealth() {
        return maxHealth;
    }
    
    public void setMaxHealth(double maxHealth) {
        this.maxHealth = maxHealth;
        if (entity != null && entity.isValid()) {
            Objects.requireNonNull(entity.getAttribute(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(maxHealth);
        }
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
    }
    
    public ItemStack[] getArmor() {
        return armor;
    }
    
    public void setArmor(ItemStack[] armor) {
        this.armor = armor;
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
        String format = plugin.getPluginConfig().getString("visual.name-format", "&e{owner}&7_&6NPC");
        return format.replace("{owner}", Bukkit.getOfflinePlayer(ownerUuid).getName()).replace("&", "§");
    }
    
    public boolean isSpawned() {
        return entity != null && entity.isValid();
    }
}