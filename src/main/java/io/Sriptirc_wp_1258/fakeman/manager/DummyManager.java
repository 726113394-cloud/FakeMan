package io.Sriptirc_wp_1258.fakeman.manager;

import io.Sriptirc_wp_1258.fakeman.Fakeman;
import io.Sriptirc_wp_1258.fakeman.database.DatabaseManager;
import io.Sriptirc_wp_1258.fakeman.objects.Dummy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class DummyManager {
    
    private final Fakeman plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, List<Dummy>> playerDummies;
    private final Map<String, Dummy> activeDummies;
    private BukkitRunnable updateTask;
    
    public DummyManager(Fakeman plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.playerDummies = new ConcurrentHashMap<>();
        this.activeDummies = new ConcurrentHashMap<>();
    }
    
    public void loadAllDummies() {
        plugin.getLogger().info("正在加载所有假人数据...");
        
        // 这里可以添加从数据库加载所有假人的逻辑
        // 由于假人数量可能很多，建议按需加载
        
        startUpdateTask();
        plugin.getLogger().info("假人管理器已初始化");
    }
    
    private void startUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        
        int interval = plugin.getPluginConfig().getInt("dummy.ai-update-interval", 20);
        
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateAllDummies();
            }
        };
        
        updateTask.runTaskTimer(plugin, 0L, interval);
    }
    
    private void updateAllDummies() {
        for (Dummy dummy : activeDummies.values()) {
            try {
                dummy.update();
                
                // 检查在线时间
                if (dummy.shouldDespawn()) {
                    despawnDummy(dummy.getOwnerUuid(), dummy.getName());
                    continue;
                }
                
                // 更新假人状态
                dummy.tick();
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "更新假人时出错: " + dummy.getName(), e);
            }
        }
    }
    
    public Dummy spawnDummy(Player owner, Location location) {
        // 检查权限
        if (!owner.hasPermission("FakeMan.use")) {
            owner.sendMessage("§c你没有使用假人插件的权限！");
            return null;
        }
        
        // 检查经济
        if (!plugin.getEconomyManager().hasEnoughMoney(owner, getSummonCost())) {
            owner.sendMessage("§c你的余额不足！召唤假人需要 " + getSummonCost() + " 游戏币");
            return null;
        }
        
        // 检查假人数量限制
        UUID ownerUuid = owner.getUniqueId();
        int currentCount = getPlayerDummyCount(ownerUuid);
        int maxCount = getMaxDummies(owner);
        
        if (currentCount >= maxCount) {
            owner.sendMessage("§c你已达到最大假人数量限制！当前: " + currentCount + "/" + maxCount);
            return null;
        }
        
        // 检查服务器总假人数量限制
        int serverMax = plugin.getPluginConfig().getInt("limits.server-max-dummies", 0);
        if (serverMax > 0 && getTotalDummyCount() >= serverMax) {
            owner.sendMessage("§c服务器假人数量已达上限！");
            return null;
        }
        
        // 扣除费用
        if (!plugin.getEconomyManager().withdrawMoney(owner, getSummonCost())) {
            owner.sendMessage("§c支付失败！");
            return null;
        }
        
        // 创建假人
        String dummyName = owner.getName() + "_NPC";
        Dummy dummy = new Dummy(plugin, ownerUuid, dummyName, location);
        
        // 设置假人属性
        dummy.setHealth(getDefaultHealth());
        dummy.setMaxHealth(getDefaultHealth());
        dummy.setOnlineTime(0);
        dummy.setMaxOnlineTime(getDefaultOnlineTime(owner));
        dummy.setMode(getDefaultMode());
        
        // 初始化背包
        int inventorySize = plugin.getPluginConfig().getInt("dummy.inventory-size", 36);
        dummy.setInventory(new ItemStack[inventorySize]);
        dummy.setArmor(new ItemStack[4]);
        
        // 生成假人实体
        if (!dummy.spawn(owner)) {
            owner.sendMessage("§c召唤假人失败！");
            // 退款
            plugin.getEconomyManager().depositMoney(owner, getSummonCost());
            return null;
        }
        
        // 保存到数据库
        saveDummyToDatabase(dummy);
        
        // 添加到内存管理
        addDummyToMemory(ownerUuid, dummy);
        
        // 发送通知
        owner.sendMessage("§a成功召唤假人！名称: " + dummyName);
        owner.sendMessage("§7假人默认在线时间: " + formatTime(getDefaultOnlineTime(owner)));
        owner.sendMessage("§7右键假人可以打开背包，使用 /fakeman 可以控制假人");
        
        // 播放声音
        if (plugin.getPluginConfig().getBoolean("notifications.sound-effects", true)) {
            owner.playSound(owner.getLocation(), 
                org.bukkit.Sound.valueOf(plugin.getPluginConfig().getString("notifications.sound-type", "ENTITY_EXPERIENCE_ORB_PICKUP")),
                (float) plugin.getPluginConfig().getDouble("notifications.sound-volume", 1.0),
                (float) plugin.getPluginConfig().getDouble("notifications.sound-pitch", 1.0)
            );
        }
        
        return dummy;
    }
    
    public boolean despawnDummy(UUID ownerUuid, String dummyName) {
        Dummy dummy = getDummy(ownerUuid, dummyName);
        if (dummy == null) {
            return false;
        }
        
        // 保存数据
        saveDummyToDatabase(dummy);
        
        // 移除实体
        dummy.despawn();
        
        // 从内存中移除
        removeDummyFromMemory(ownerUuid, dummyName);
        
        // 从数据库中删除（或者标记为离线）
        // databaseManager.deleteDummy(ownerUuid, dummyName);
        
        // 通知主人
        Player owner = Bukkit.getPlayer(ownerUuid);
        if (owner != null && owner.isOnline()) {
            owner.sendMessage("§e你的假人 " + dummyName + " 已消失");
        }
        
        return true;
    }
    
    public Dummy getDummy(UUID ownerUuid, String dummyName) {
        String key = ownerUuid.toString() + ":" + dummyName;
        return activeDummies.get(key);
    }
    
    public List<Dummy> getPlayerDummies(UUID playerUuid) {
        return playerDummies.getOrDefault(playerUuid, new ArrayList<>());
    }
    
    public int getPlayerDummyCount(UUID playerUuid) {
        return getPlayerDummies(playerUuid).size();
    }
    
    public int getTotalDummyCount() {
        return activeDummies.size();
    }
    
    private void addDummyToMemory(UUID ownerUuid, Dummy dummy) {
        String key = ownerUuid.toString() + ":" + dummy.getName();
        activeDummies.put(key, dummy);
        
        playerDummies.computeIfAbsent(ownerUuid, k -> new ArrayList<>()).add(dummy);
    }
    
    private void removeDummyFromMemory(UUID ownerUuid, String dummyName) {
        String key = ownerUuid.toString() + ":" + dummyName;
        activeDummies.remove(key);
        
        List<Dummy> dummies = playerDummies.get(ownerUuid);
        if (dummies != null) {
            dummies.removeIf(d -> d.getName().equals(dummyName));
            if (dummies.isEmpty()) {
                playerDummies.remove(ownerUuid);
            }
        }
    }
    
    private void saveDummyToDatabase(Dummy dummy) {
        databaseManager.saveDummy(
            dummy.getOwnerUuid(),
            dummy.getName(),
            dummy.getLocation(),
            dummy.getHealth(),
            dummy.getMaxHealth(),
            dummy.getMode(),
            dummy.getOnlineTime(),
            dummy.getMaxOnlineTime(),
            dummy.getInventory(),
            dummy.getArmor(),
            dummy.getExtraData()
        );
    }
    
    public void saveAllDummies() {
        plugin.getLogger().info("正在保存所有假人数据...");
        
        for (Dummy dummy : activeDummies.values()) {
            try {
                saveDummyToDatabase(dummy);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "保存假人数据失败: " + dummy.getName(), e);
            }
        }
        
        plugin.getLogger().info("已保存 " + activeDummies.size() + " 个假人数据");
    }
    
    // 配置相关方法
    private double getSummonCost() {
        return plugin.getPluginConfig().getDouble("economy.summon-cost", 30000.0);
    }
    
    private double getDefaultHealth() {
        return plugin.getPluginConfig().getDouble("dummy.default-health", 100.0);
    }
    
    private long getDefaultOnlineTime(Player player) {
        long defaultTime = plugin.getPluginConfig().getLong("dummy.default-online-time", 3600L);
        
        // 检查是否有延长权限
        if (player.hasPermission("FakeMan.expend")) {
            return plugin.getPluginConfig().getLong("dummy.max-online-time", 86400L);
        }
        
        return defaultTime;
    }
    
    private String getDefaultMode() {
        return plugin.getPluginConfig().getString("dummy.default-mode", "idle");
    }
    
    private int getMaxDummies(Player player) {
        int defaultMax = plugin.getPluginConfig().getInt("limits.default-max-dummies", 1);
        
        // 检查是否有扩展权限
        if (player.hasPermission("FakeMan.expend")) {
            return plugin.getPluginConfig().getInt("limits.expend-max-dummies", 5);
        }
        
        return defaultMax;
    }
    
    private String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "秒";
        } else if (seconds < 3600) {
            return (seconds / 60) + "分钟";
        } else {
            return (seconds / 3600) + "小时";
        }
    }
    
    public void onDisable() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        
        // 移除所有假人实体
        for (Dummy dummy : activeDummies.values()) {
            try {
                dummy.despawn();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "移除假人实体失败: " + dummy.getName(), e);
            }
        }
        
        activeDummies.clear();
        playerDummies.clear();
    }
}