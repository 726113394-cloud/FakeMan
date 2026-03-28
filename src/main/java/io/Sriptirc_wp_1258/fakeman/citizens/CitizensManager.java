package io.Sriptirc_wp_1258.fakeman.citizens;

import io.Sriptirc_wp_1258.fakeman.Fakeman;
import io.Sriptirc_wp_1258.fakeman.entity.EntityManager;
import io.Sriptirc_wp_1258.fakeman.objects.Dummy;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class CitizensManager implements EntityManager {
    
    private final Fakeman plugin;
    private NPCRegistry npcRegistry;
    private boolean enabled;
    private final Map<String, NPC> npcMap;
    
    public CitizensManager(Fakeman plugin) {
        this.plugin = plugin;
        this.npcMap = new HashMap<>();
    }
    
    @Override
    public boolean initialize() {
        if (Bukkit.getServer().getPluginManager().getPlugin("Citizens") == null) {
            plugin.getLogger().warning("未找到Citizens插件，Citizens管理器将禁用");
            this.enabled = false;
            return false;
        }
        
        try {
            // 使用Citizens的主要注册表
            npcRegistry = CitizensAPI.getNPCRegistry();
            if (npcRegistry == null) {
                plugin.getLogger().warning("无法获取Citizens注册表");
                this.enabled = false;
                return false;
            }
            
            plugin.getLogger().info("Citizens实体管理器已启用，使用版本: " + 
                Bukkit.getServer().getPluginManager().getPlugin("Citizens").getDescription().getVersion());
            this.enabled = true;
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "初始化Citizens管理器失败", e);
            this.enabled = false;
            return false;
        }
    }
    
    @Override
    public boolean isEnabled() {
        return enabled && npcRegistry != null;
    }
    
    @Override
    public boolean createDummyEntity(Dummy dummy, Player owner) {
        if (!isEnabled()) {
            plugin.getLogger().warning("Citizens管理器未启用，无法创建NPC");
            return false;
        }
        
        try {
            String npcName = dummy.getName();
            Location location = dummy.getLocation();
            
            // 创建NPC
            NPC npc = npcRegistry.createNPC(EntityType.PLAYER, npcName);
            
            // 设置NPC位置
            npc.spawn(location);
            
            // 设置NPC为玩家皮肤（使用主人皮肤）
            if (npc.hasTrait(SkinTrait.class)) {
                SkinTrait skinTrait = npc.getTrait(SkinTrait.class);
                skinTrait.setSkinName(owner.getName());
                skinTrait.setShouldUpdateSkins(true);
            }
            
            // 禁用自动看向玩家
            if (npc.hasTrait(LookClose.class)) {
                LookClose lookClose = npc.getTrait(LookClose.class);
                lookClose.lookClose(false);
            }
            
            // 设置NPC属性
            npc.setProtected(true); // 保护NPC不被攻击
            
            // 确保假人在服务器列表中显示为在线玩家
            if (npc.getEntity() instanceof org.bukkit.entity.Player) {
                org.bukkit.entity.Player playerEntity = (org.bukkit.entity.Player) npc.getEntity();
                // 设置游戏模式为生存模式，确保在玩家列表中显示
                playerEntity.setGameMode(org.bukkit.GameMode.SURVIVAL);
                // 设置玩家显示名称
                playerEntity.setDisplayName(dummy.getDisplayName());
                // 设置玩家列表名称
                playerEntity.setPlayerListName(dummy.getDisplayName());
            }
            
            // 存储假人UUID到NPC数据中
            npc.data().setPersistent("fakeman_dummy_uuid", dummy.getOwnerUuid().toString());
            npc.data().setPersistent("fakeman_dummy_name", dummy.getName());
            
            // 存储NPC引用
            String npcKey = getNPCKey(dummy.getOwnerUuid(), dummy.getName());
            npcMap.put(npcKey, npc);
            
            // 更新显示名称
            updateEntityName(dummy);
            
            // 更新装备
            updateEntityEquipment(dummy);
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "创建Citizens NPC失败", e);
            return false;
        }
    }
    
    @Override
    public void updateEntityEquipment(Dummy dummy) {
        NPC npc = getNPC(dummy);
        if (npc == null || !npc.isSpawned()) {
            return;
        }
        
        try {
            // 获取实体装备管理器
            if (npc.getEntity() instanceof org.bukkit.entity.LivingEntity) {
                org.bukkit.entity.LivingEntity livingEntity = (org.bukkit.entity.LivingEntity) npc.getEntity();
                org.bukkit.inventory.EntityEquipment equipment = livingEntity.getEquipment();
                
                // 设置装备
                ItemStack[] armor = dummy.getArmor();
                if (armor != null && armor.length >= 4) {
                    equipment.setHelmet(armor[0]);
                    equipment.setChestplate(armor[1]);
                    equipment.setLeggings(armor[2]);
                    equipment.setBoots(armor[3]);
                }
                
                // 设置手持物品
                ItemStack[] inventory = dummy.getInventory();
                if (inventory != null && inventory.length > 0) {
                    equipment.setItemInMainHand(inventory[0]);
                }
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "更新Citizens NPC装备失败", e);
        }
    }
    
    @Override
    public void updateEntityName(Dummy dummy) {
        NPC npc = getNPC(dummy);
        if (npc == null) {
            return;
        }
        
        try {
            String displayName = dummy.getDisplayName();
            npc.setName(displayName);
            
            // 确保名称标签可见
            if (npc.isSpawned()) {
                npc.getEntity().setCustomNameVisible(true);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "更新Citizens NPC名称失败", e);
        }
    }
    
    @Override
    public void teleportEntity(Dummy dummy, Location location) {
        NPC npc = getNPC(dummy);
        if (npc == null || !npc.isSpawned()) {
            return;
        }
        
        try {
            npc.teleport(location, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "传送Citizens NPC失败", e);
        }
    }
    
    @Override
    public void lookAt(Dummy dummy, Location target) {
        NPC npc = getNPC(dummy);
        if (npc == null || !npc.isSpawned()) {
            return;
        }
        
        try {
            Location npcLoc = npc.getEntity().getLocation();
            npcLoc.setDirection(target.toVector().subtract(npcLoc.toVector()));
            npc.teleport(npcLoc, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "设置Citizens NPC朝向失败", e);
        }
    }
    
    @Override
    public void despawnEntity(Dummy dummy) {
        NPC npc = getNPC(dummy);
        if (npc == null) {
            return;
        }
        
        try {
            if (npc.isSpawned()) {
                npc.despawn();
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "移除Citizens NPC失败", e);
        }
    }
    
    @Override
    public void deleteEntity(Dummy dummy) {
        NPC npc = getNPC(dummy);
        if (npc == null) {
            return;
        }
        
        try {
            despawnEntity(dummy);
            
            // 从地图中移除
            String npcKey = getNPCKey(dummy.getOwnerUuid(), dummy.getName());
            npcMap.remove(npcKey);
            
            // 销毁NPC
            npc.destroy();
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "删除Citizens NPC失败", e);
        }
    }
    
    @Override
    public Dummy findDummyByEntity(Entity entity) {
        if (!isEnabled()) {
            return null;
        }
        
        NPC npc = CitizensAPI.getNPCRegistry().getNPC(entity);
        if (npc == null) {
            return null;
        }
        
        // 检查是否是FakeMan的NPC
        String ownerUuidStr = npc.data().get("fakeman_dummy_uuid");
        String dummyName = npc.data().get("fakeman_dummy_name");
        
        if (ownerUuidStr == null || dummyName == null) {
            return null;
        }
        
        UUID ownerUuid = UUID.fromString(ownerUuidStr);
        return plugin.getDummyManager().getDummy(ownerUuid, dummyName);
    }
    
    @Override
    public boolean isEntityValid(Dummy dummy) {
        NPC npc = getNPC(dummy);
        return npc != null && npc.isSpawned();
    }
    
    @Override
    public Location getEntityLocation(Dummy dummy) {
        NPC npc = getNPC(dummy);
        if (npc == null || !npc.isSpawned()) {
            return null;
        }
        return npc.getEntity().getLocation();
    }
    
    @Override
    public void onDisable() {
        // 清理地图
        npcMap.clear();
        
        if (enabled) {
            plugin.getLogger().info("Citizens实体管理器已禁用");
        }
    }
    
    private NPC getNPC(Dummy dummy) {
        String npcKey = getNPCKey(dummy.getOwnerUuid(), dummy.getName());
        return npcMap.get(npcKey);
    }
    
    private String getNPCKey(UUID ownerUuid, String dummyName) {
        return ownerUuid.toString() + ":" + dummyName;
    }
}