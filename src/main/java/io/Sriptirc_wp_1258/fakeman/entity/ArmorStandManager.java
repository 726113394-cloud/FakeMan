package io.Sriptirc_wp_1258.fakeman.entity;

import io.Sriptirc_wp_1258.fakeman.Fakeman;
import io.Sriptirc_wp_1258.fakeman.objects.Dummy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class ArmorStandManager implements EntityManager {
    
    private final Fakeman plugin;
    private final Map<String, ArmorStand> entityMap;
    private boolean enabled;
    
    public ArmorStandManager(Fakeman plugin) {
        this.plugin = plugin;
        this.entityMap = new HashMap<>();
        this.enabled = true; // 盔甲架管理器总是可用的
    }
    
    @Override
    public boolean initialize() {
        plugin.getLogger().info("盔甲架实体管理器已启用");
        return true;
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public boolean createDummyEntity(Dummy dummy, Player owner) {
        try {
            Location location = dummy.getLocation();
            
            // 创建盔甲架
            ArmorStand armorStand = location.getWorld().spawn(location, ArmorStand.class);
            
            // 配置盔甲架属性
            armorStand.setVisible(true);
            armorStand.setGravity(false);
            armorStand.setInvulnerable(true);
            armorStand.setCanPickupItems(false);
            armorStand.setCustomNameVisible(true);
            armorStand.setRemoveWhenFarAway(false);
            armorStand.setAI(false);
            armorStand.setCollidable(false);
            
            // 设置盔甲架为小型（看起来更像玩家）
            armorStand.setSmall(true);
            
            // 设置盔甲架有手臂
            armorStand.setArms(true);
            
            // 设置盔甲架为站立姿势（不是浮空）
            armorStand.setBasePlate(false);
            
            // 添加发光效果，让假人更显眼
            armorStand.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
            
            // 设置假人名称
            updateEntityName(dummy);
            
            // 设置假人装备
            updateEntityEquipment(dummy);
            
            // 存储实体引用
            String entityKey = getEntityKey(dummy.getOwnerUuid(), dummy.getName());
            entityMap.put(entityKey, armorStand);
            
            // 调试信息
            if (plugin.getPluginConfig().getBoolean("debug.enabled", false)) {
                plugin.getLogger().info("创建盔甲架假人成功: " + dummy.getName() + 
                    ", 实体Key: " + entityKey + ", UUID: " + armorStand.getUniqueId());
            }
            
            // 将实体存储到假人对象中
            dummy.setArmorStand(armorStand);
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "创建盔甲架假人失败", e);
            return false;
        }
    }
    
    @Override
    public void updateEntityEquipment(Dummy dummy) {
        ArmorStand armorStand = dummy.getArmorStand();
        if (armorStand == null || armorStand.isDead()) {
            return;
        }
        
        try {
            // 设置装备
            ItemStack[] armor = dummy.getArmor();
            if (armor != null && armor.length >= 4) {
                armorStand.getEquipment().setHelmet(armor[0]);
                armorStand.getEquipment().setChestplate(armor[1]);
                armorStand.getEquipment().setLeggings(armor[2]);
                armorStand.getEquipment().setBoots(armor[3]);
            }
            
            // 设置手持物品
            ItemStack[] inventory = dummy.getInventory();
            if (inventory != null && inventory.length > 0) {
                armorStand.getEquipment().setItemInMainHand(inventory[0]);
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "更新盔甲架装备失败", e);
        }
    }
    
    @Override
    public void updateEntityName(Dummy dummy) {
        ArmorStand armorStand = dummy.getArmorStand();
        if (armorStand == null || armorStand.isDead()) {
            return;
        }
        
        try {
            String displayName = dummy.getDisplayName();
            armorStand.setCustomName(displayName);
            armorStand.setCustomNameVisible(true);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "更新盔甲架名称失败", e);
        }
    }
    
    @Override
    public void teleportEntity(Dummy dummy, Location location) {
        ArmorStand armorStand = dummy.getArmorStand();
        if (armorStand == null || armorStand.isDead()) {
            return;
        }
        
        try {
            armorStand.teleport(location);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "传送盔甲架失败", e);
        }
    }
    
    @Override
    public void lookAt(Dummy dummy, Location target) {
        ArmorStand armorStand = dummy.getArmorStand();
        if (armorStand == null || armorStand.isDead()) {
            return;
        }
        
        try {
            Location armorStandLoc = armorStand.getLocation();
            armorStandLoc.setDirection(target.toVector().subtract(armorStandLoc.toVector()));
            armorStand.teleport(armorStandLoc);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "设置盔甲架朝向失败", e);
        }
    }
    
    @Override
    public void despawnEntity(Dummy dummy) {
        ArmorStand armorStand = dummy.getArmorStand();
        if (armorStand == null || armorStand.isDead()) {
            return;
        }
        
        try {
            // 从地图中移除
            String entityKey = getEntityKey(dummy.getOwnerUuid(), dummy.getName());
            entityMap.remove(entityKey);
            
            // 移除实体
            armorStand.remove();
            dummy.setArmorStand(null);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "移除盔甲架失败", e);
        }
    }
    
    @Override
    public void deleteEntity(Dummy dummy) {
        despawnEntity(dummy);
    }
    
    @Override
    public Dummy findDummyByEntity(Entity entity) {
        if (!(entity instanceof ArmorStand)) {
            // 调试信息
            if (plugin.getPluginConfig().getBoolean("debug.enabled", false)) {
                plugin.getLogger().info("实体不是盔甲架: " + entity.getType());
            }
            return null;
        }
        
        ArmorStand armorStand = (ArmorStand) entity;
        
        // 调试信息
        if (plugin.getPluginConfig().getBoolean("debug.enabled", false)) {
            plugin.getLogger().info("查找盔甲架假人: " + armorStand.getUniqueId() + 
                ", 名称: " + armorStand.getCustomName());
        }
        
        // 遍历所有假人，查找匹配的盔甲架
        for (Map.Entry<String, ArmorStand> entry : entityMap.entrySet()) {
            if (entry.getValue().equals(armorStand)) {
                // 从key中解析UUID和名称
                String[] parts = entry.getKey().split(":");
                if (parts.length == 2) {
                    UUID ownerUuid = UUID.fromString(parts[0]);
                    String dummyName = parts[1];
                    
                    // 调试信息
                    if (plugin.getPluginConfig().getBoolean("debug.enabled", false)) {
                        plugin.getLogger().info("找到假人: " + dummyName + ", 主人: " + ownerUuid);
                    }
                    
                    return plugin.getDummyManager().getDummy(ownerUuid, dummyName);
                }
            }
        }
        
        // 调试信息：未找到匹配的假人
        if (plugin.getPluginConfig().getBoolean("debug.enabled", false)) {
            plugin.getLogger().info("未在entityMap中找到匹配的盔甲架，entityMap大小: " + entityMap.size());
        }
        
        return null;
    }
    
    @Override
    public boolean isEntityValid(Dummy dummy) {
        ArmorStand armorStand = dummy.getArmorStand();
        return armorStand != null && !armorStand.isDead();
    }
    
    @Override
    public Location getEntityLocation(Dummy dummy) {
        ArmorStand armorStand = dummy.getArmorStand();
        if (armorStand == null || armorStand.isDead()) {
            return null;
        }
        return armorStand.getLocation();
    }
    
    @Override
    public void onDisable() {
        // 保存所有实体数据
        plugin.getLogger().info("盔甲架实体管理器已禁用");
        
        // 清理地图
        entityMap.clear();
    }
    
    private String getEntityKey(UUID ownerUuid, String dummyName) {
        return ownerUuid.toString() + ":" + dummyName;
    }
    
    /**
     * 获取所有活跃的盔甲架实体
     */
    public Map<String, ArmorStand> getEntityMap() {
        return new HashMap<>(entityMap);
    }
}