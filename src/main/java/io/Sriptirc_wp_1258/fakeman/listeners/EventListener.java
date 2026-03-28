package io.Sriptirc_wp_1258.fakeman.listeners;

import io.Sriptirc_wp_1258.fakeman.Fakeman;
import io.Sriptirc_wp_1258.fakeman.manager.DummyManager;
import io.Sriptirc_wp_1258.fakeman.objects.Dummy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class EventListener implements Listener {
    
    private final Fakeman plugin;
    private final DummyManager dummyManager;
    
    public EventListener(Fakeman plugin) {
        this.plugin = plugin;
        this.dummyManager = plugin.getDummyManager();
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Zombie)) {
            return;
        }
        
        Zombie zombie = (Zombie) event.getRightClicked();
        Player player = event.getPlayer();
        
        // 检查是否是假人
        Dummy dummy = findDummyByEntity(zombie);
        if (dummy == null) {
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
    }
    
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Zombie)) {
            return;
        }
        
        Zombie zombie = (Zombie) event.getEntity();
        Dummy dummy = findDummyByEntity(zombie);
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
                    dummy.getLocation().setDirection(attacker.getLocation().getDirection());
                    
                    // 发送消息
                    attacker.sendMessage("§c你在攻击自己的假人！");
                }
            }
        }
    }
    
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Zombie)) {
            return;
        }
        
        Zombie zombie = (Zombie) event.getEntity();
        Dummy dummy = findDummyByEntity(zombie);
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
            dummy.spawn();
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
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // 检查是否是假人背包
        if (event.getView().getTitle().contains("的背包")) {
            // 这里可以添加保存假人背包数据的逻辑
        }
    }
    
    // 工具方法
    private Dummy findDummyByEntity(Zombie zombie) {
        // 通过实体查找对应的假人
        // 这里需要优化，可以使用实体UUID映射
        for (Dummy dummy : dummyManager.getPlayerDummies(UUID.randomUUID())) {
            // 简化实现，实际应该使用更精确的匹配
            if (dummy.isSpawned() && dummy.getLocation().getWorld().equals(zombie.getWorld())) {
                // 检查距离
                if (dummy.getLocation().distance(zombie.getLocation()) < 1.0) {
                    return dummy;
                }
            }
        }
        return null;
    }
    
    private void openDummyInventory(Player player, Dummy dummy) {
        int size = plugin.getPluginConfig().getInt("dummy.inventory-size", 36);
        Inventory inventory = Bukkit.createInventory(
            null, 
            size, 
            ChatColor.GOLD + dummy.getName() + ChatColor.GRAY + "的背包"
        );
        
        // 设置背包内容
        ItemStack[] dummyInventory = dummy.getInventory();
        for (int i = 0; i < Math.min(dummyInventory.length, size); i++) {
            if (dummyInventory[i] != null) {
                inventory.setItem(i, dummyInventory[i]);
            }
        }
        
        player.openInventory(inventory);
        
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
                // 这里可以添加更复杂的AI逻辑
                dummy.getLocation().setDirection(player.getLocation().getDirection());
                
                // 简单实现：假人看向玩家破坏的方块位置
                // 实际应该让假人移动到该位置并收集物品
            }
        }
    }
    
    private void notifyDummiesToFish(Player player) {
        UUID playerUuid = player.getUniqueId();
        
        for (Dummy dummy : dummyManager.getPlayerDummies(playerUuid)) {
            if (dummy.getMode().equals("follow") && dummy.isSpawned()) {
                // 通知假人去钓鱼
                // 这里可以添加钓鱼逻辑
            }
        }
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
}