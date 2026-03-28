package io.Sriptirc_wp_1258.fakeman.entity;

import io.Sriptirc_wp_1258.fakeman.objects.Dummy;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface EntityManager {
    
    /**
     * 初始化实体管理器
     * @return 是否初始化成功
     */
    boolean initialize();
    
    /**
     * 是否为启用状态
     */
    boolean isEnabled();
    
    /**
     * 创建假人实体
     * @param dummy 假人对象
     * @param owner 假人主人
     * @return 是否创建成功
     */
    boolean createDummyEntity(Dummy dummy, Player owner);
    
    /**
     * 更新实体装备
     * @param dummy 假人对象
     */
    void updateEntityEquipment(Dummy dummy);
    
    /**
     * 更新实体名称
     * @param dummy 假人对象
     */
    void updateEntityName(Dummy dummy);
    
    /**
     * 传送实体
     * @param dummy 假人对象
     * @param location 目标位置
     */
    void teleportEntity(Dummy dummy, Location location);
    
    /**
     * 让实体看向某个位置
     * @param dummy 假人对象
     * @param target 目标位置
     */
    void lookAt(Dummy dummy, Location target);
    
    /**
     * 移除实体（不删除数据）
     * @param dummy 假人对象
     */
    void despawnEntity(Dummy dummy);
    
    /**
     * 删除实体（包括数据）
     * @param dummy 假人对象
     */
    void deleteEntity(Dummy dummy);
    
    /**
     * 通过实体查找假人
     * @param entity Bukkit实体
     * @return 对应的假人对象，如果没有则返回null
     */
    Dummy findDummyByEntity(org.bukkit.entity.Entity entity);
    
    /**
     * 检查实体是否有效
     * @param dummy 假人对象
     * @return 实体是否有效（已生成且未死亡）
     */
    boolean isEntityValid(Dummy dummy);
    
    /**
     * 获取实体位置
     * @param dummy 假人对象
     * @return 实体位置，如果实体无效则返回null
     */
    Location getEntityLocation(Dummy dummy);
    
    /**
     * 插件禁用时的清理工作
     */
    void onDisable();
}