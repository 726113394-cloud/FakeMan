package io.Sriptirc_wp_1258.fakeman.manager;

import io.Sriptirc_wp_1258.fakeman.Fakeman;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家行为追踪器
 * 用于记录玩家最近的活动，以便假人智能模仿
 */
public class PlayerBehaviorTracker implements Listener {
    
    private final Fakeman plugin;
    private final DummyManager dummyManager;
    
    // 玩家行为记录，保存最近的行为
    private final Map<UUID, PlayerBehavior> playerBehaviors;
    
    public PlayerBehaviorTracker(Fakeman plugin) {
        this.plugin = plugin;
        this.dummyManager = plugin.getDummyManager();
        this.playerBehaviors = new HashMap<>();
        
        // 注册事件监听器
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    
    /**
     * 玩家行为记录类
     */
    public static class PlayerBehavior {
        private final UUID playerUuid;
        private BehaviorType lastBehavior;
        private Material lastMaterial;
        private Location lastLocation;
        private long lastActionTime;
        private Entity lastAttacker;
        
        public PlayerBehavior(UUID playerUuid) {
            this.playerUuid = playerUuid;
            this.lastBehavior = BehaviorType.IDLE;
            this.lastActionTime = System.currentTimeMillis();
        }
        
        public void updateBehavior(BehaviorType behavior, Material material, Location location) {
            this.lastBehavior = behavior;
            this.lastMaterial = material;
            this.lastLocation = location;
            this.lastActionTime = System.currentTimeMillis();
        }
        
        public void updateCombat(Entity attacker) {
            this.lastBehavior = BehaviorType.COMBAT;
            this.lastAttacker = attacker;
            this.lastActionTime = System.currentTimeMillis();
        }
        
        public BehaviorType getLastBehavior() {
            return lastBehavior;
        }
        
        public Material getLastMaterial() {
            return lastMaterial;
        }
        
        public Location getLastLocation() {
            return lastLocation;
        }
        
        public long getLastActionTime() {
            return lastActionTime;
        }
        
        public Entity getLastAttacker() {
            return lastAttacker;
        }
        
        public boolean isRecent(long thresholdMillis) {
            return System.currentTimeMillis() - lastActionTime <= thresholdMillis;
        }
    }
    
    /**
     * 行为类型枚举
     */
    public enum BehaviorType {
        IDLE,           // 空闲
        MINING,         // 挖矿
        CHOPPING,       // 砍树
        FISHING,        // 钓鱼
        EATING,         // 吃东西
        COMBAT,         // 战斗
        FARMING         // 农业（耕种、收割等）
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material blockType = event.getBlock().getType();
        
        PlayerBehavior behavior = getOrCreateBehavior(player.getUniqueId());
        
        // 判断行为类型
        if (isLogBlock(blockType)) {
            behavior.updateBehavior(BehaviorType.CHOPPING, blockType, event.getBlock().getLocation());
        } else if (isMiningBlock(blockType)) {
            behavior.updateBehavior(BehaviorType.MINING, blockType, event.getBlock().getLocation());
        } else if (isCropBlock(blockType)) {
            behavior.updateBehavior(BehaviorType.FARMING, blockType, event.getBlock().getLocation());
        }
        
        // 通知跟随模式的假人
        notifyDummiesToFollow(player, behavior);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            Player player = event.getPlayer();
            PlayerBehavior behavior = getOrCreateBehavior(player.getUniqueId());
            behavior.updateBehavior(BehaviorType.FISHING, Material.FISHING_ROD, player.getLocation());
            
            notifyDummiesToFollow(player, behavior);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item != null && isFood(item.getType())) {
            PlayerBehavior behavior = getOrCreateBehavior(player.getUniqueId());
            behavior.updateBehavior(BehaviorType.EATING, item.getType(), player.getLocation());
            
            notifyDummiesToFollow(player, behavior);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            
            // 获取攻击者
            Entity damager = event.getDamager();
            if (damager instanceof Projectile) {
                Projectile projectile = (Projectile) damager;
                ProjectileSource shooter = projectile.getShooter();
                if (shooter instanceof Entity) {
                    damager = (Entity) shooter;
                }
            }
            
            PlayerBehavior behavior = getOrCreateBehavior(player.getUniqueId());
            behavior.updateCombat(damager);
            
            notifyDummiesToFollow(player, behavior);
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerBehaviors.remove(event.getPlayer().getUniqueId());
    }
    
    /**
     * 通知跟随模式的假人模仿玩家行为
     */
    private void notifyDummiesToFollow(Player player, PlayerBehavior behavior) {
        // 只在启用智能识别时工作
        if (!plugin.getPluginConfig().getBoolean("ai.smart-recognition", true)) {
            return;
        }
        
        UUID playerUuid = player.getUniqueId();
        
        for (io.Sriptirc_wp_1258.fakeman.objects.Dummy dummy : dummyManager.getPlayerDummies(playerUuid)) {
            if (dummy.getMode().equals("follow") && dummy.isSpawned()) {
                // 根据行为类型执行相应动作
                switch (behavior.getLastBehavior()) {
                    case MINING:
                        dummy.startMining(behavior.getLastMaterial(), behavior.getLastLocation());
                        break;
                    case CHOPPING:
                        dummy.startChopping(behavior.getLastMaterial(), behavior.getLastLocation());
                        break;
                    case FISHING:
                        dummy.startFishing(behavior.getLastLocation());
                        break;
                    case COMBAT:
                        dummy.protectOwner(behavior.getLastAttacker());
                        break;
                    case EATING:
                        dummy.consumeFood(behavior.getLastMaterial());
                        break;
                }
            }
        }
    }
    
    /**
     * 获取或创建玩家行为记录
     */
    public PlayerBehavior getOrCreateBehavior(UUID playerUuid) {
        return playerBehaviors.computeIfAbsent(playerUuid, PlayerBehavior::new);
    }
    
    /**
     * 获取玩家最近行为
     */
    public PlayerBehavior getPlayerBehavior(UUID playerUuid) {
        return playerBehaviors.get(playerUuid);
    }
    
    /**
     * 清除玩家行为记录
     */
    public void clearPlayerBehavior(UUID playerUuid) {
        playerBehaviors.remove(playerUuid);
    }
    
    // 工具方法
    private boolean isLogBlock(Material material) {
        String name = material.name().toLowerCase();
        return name.contains("_log") || name.contains("_wood") || 
               material == Material.OAK_LOG || material == Material.BIRCH_LOG ||
               material == Material.SPRUCE_LOG || material == Material.JUNGLE_LOG ||
               material == Material.ACACIA_LOG || material == Material.DARK_OAK_LOG ||
               material == Material.MANGROVE_LOG || material == Material.CHERRY_LOG;
    }
    
    private boolean isMiningBlock(Material material) {
        String name = material.name().toLowerCase();
        return name.contains("ore") || name.contains("coal") || 
               material == Material.STONE || material == Material.DEEPSLATE ||
               material == Material.ANDESITE || material == Material.DIORITE ||
               material == Material.GRANITE || material == Material.NETHERRACK ||
               material == Material.END_STONE || material == Material.BASALT;
    }
    
    private boolean isCropBlock(Material material) {
        String name = material.name().toLowerCase();
        return name.contains("wheat") || name.contains("carrot") || name.contains("potato") ||
               name.contains("beetroot") || name.contains("melon") || name.contains("pumpkin") ||
               material == Material.SUGAR_CANE || material == Material.CACTUS ||
               material == Material.BAMBOO || material == Material.KELP;
    }
    
    private boolean isFood(Material material) {
        return material.isEdible();
    }
}