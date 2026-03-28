package io.Sriptirc_wp_1258.fakeman;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Bukkit;
import io.Sriptirc_wp_1258.fakeman.database.DatabaseManager;
import io.Sriptirc_wp_1258.fakeman.manager.DummyManager;
import io.Sriptirc_wp_1258.fakeman.commands.CommandManager;
import io.Sriptirc_wp_1258.fakeman.listeners.EventListener;
import io.Sriptirc_wp_1258.fakeman.economy.EconomyManager;
import io.Sriptirc_wp_1258.fakeman.entity.EntityManager;
import io.Sriptirc_wp_1258.fakeman.entity.ArmorStandManager;
import io.Sriptirc_wp_1258.fakeman.citizens.CitizensManager;
import io.Sriptirc_wp_1258.fakeman.manager.PlayerBehaviorTracker;

public final class Fakeman extends JavaPlugin {
    
    private static Fakeman instance;
    private DatabaseManager databaseManager;
    private DummyManager dummyManager;
    private EconomyManager economyManager;
    private EntityManager entityManager;
    private FileConfiguration config;
    private PlayerBehaviorTracker playerBehaviorTracker;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // 保存默认配置文件
        saveDefaultConfig();
        config = getConfig();
        
        // 初始化经济管理器
        economyManager = new EconomyManager(this);
        
        // 初始化数据库管理器
        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();
        
        // 显示插件启动信息
        getLogger().info("正在启动 FakeMan 假人插件...");
        
        // 初始化假人管理器
        dummyManager = new DummyManager(this);
        
        // 初始化实体管理器
        entityManager = createEntityManager();
        if (entityManager != null) {
            boolean initialized = entityManager.initialize();
            if (!initialized) {
                getLogger().warning("实体管理器初始化失败，尝试使用备用管理器");
                // 如果Citizens管理器失败，回退到盔甲架管理器
                entityManager = new ArmorStandManager(this);
                entityManager.initialize();
                getLogger().info("已切换到盔甲架实体管理器");
                
                // 检查配置和Citizens插件状态，给出详细提示
                boolean configUseCitizens = config.getBoolean("dummy.use-citizens", false);
                boolean citizensPluginExists = Bukkit.getPluginManager().getPlugin("Citizens") != null;
                
                if (configUseCitizens && !citizensPluginExists) {
                    getLogger().warning("═══════════════════════════════════════════════════");
                    getLogger().warning("⚠ 配置要求使用Citizens，但Citizens插件未安装！");
                    getLogger().warning("⚠ 当前使用盔甲架假人（功能受限）");
                    getLogger().warning("⚠ 解决方案：");
                    getLogger().warning("⚠ 1. 安装Citizens插件以获得完整功能");
                    getLogger().warning("⚠ 2. 或修改config.yml: dummy.use-citizens: false");
                    getLogger().warning("═══════════════════════════════════════════════════");
                } else if (configUseCitizens && citizensPluginExists) {
                    getLogger().warning("═══════════════════════════════════════════════════");
                    getLogger().warning("⚠ Citizens插件已安装，但连接失败！");
                    getLogger().warning("⚠ 当前使用盔甲架假人（功能受限）");
                    getLogger().warning("⚠ 可能原因：");
                    getLogger().warning("⚠ 1. Citizens插件版本不兼容");
                    getLogger().warning("⚠ 2. Citizens插件未正确加载");
                    getLogger().warning("⚠ 3. API版本不匹配");
                    getLogger().warning("═══════════════════════════════════════════════════");
                } else if (!configUseCitizens) {
                    getLogger().info("配置设置为使用盔甲架假人");
                }
            } else {
                getLogger().info("实体管理器初始化成功: " + entityManager.getClass().getSimpleName());
                
                // 显示Citizens连接状态
                if (entityManager.getClass().getSimpleName().equals("CitizensManager")) {
                    getLogger().info("✅ 成功连接到Citizens插件！");
                    getLogger().info("✅ 假人将显示为真实玩家NPC");
                    getLogger().info("✅ 支持玩家皮肤、完整动画和服务器列表显示");
                } else {
                    getLogger().info("使用盔甲架假人系统");
                }
            }
        } else {
            getLogger().warning("无法创建实体管理器");
        }
        
        // 注册命令
        new CommandManager(this).registerCommands();
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new EventListener(this), this);
        
        // 初始化玩家行为追踪器
        playerBehaviorTracker = new PlayerBehaviorTracker(this);
        
        // 加载所有假人数据
        dummyManager.loadAllDummies();
        
        // 显示Citizens连接状态
        displayCitizensConnectionStatus();
        
        getLogger().info("FakeMan 插件已启用！");
    }
    
    @Override
    public void onDisable() {
        // 保存所有假人数据
        if (dummyManager != null) {
            dummyManager.saveAllDummies();
        }
        
        // 关闭实体管理器
        if (entityManager != null) {
            entityManager.onDisable();
        }
        
        // 关闭数据库连接
        if (databaseManager != null) {
            databaseManager.close();
        }
        
        getLogger().info("FakeMan 插件已禁用！");
    }
    
    /**
     * 显示Citizens连接状态
     */
    private void displayCitizensConnectionStatus() {
        getLogger().info("═══════════════════════════════════════════════════");
        getLogger().info("FakeMan 假人插件 v" + getDescription().getVersion());
        getLogger().info("作者: " + String.join(", ", getDescription().getAuthors()));
        
        if (entityManager != null) {
            String managerType = entityManager.getClass().getSimpleName();
            
            if (managerType.equals("CitizensManager")) {
                getLogger().info("✅ 状态: 已连接到Citizens插件");
                getLogger().info("✅ 假人类型: 真实玩家NPC");
                getLogger().info("✅ 功能: 玩家皮肤、完整动画、服务器列表显示");
            } else {
                getLogger().info("⚠ 状态: 使用盔甲架假人");
                
                boolean configUseCitizens = config.getBoolean("dummy.use-citizens", false);
                boolean citizensPluginExists = Bukkit.getPluginManager().getPlugin("Citizens") != null;
                
                if (configUseCitizens && !citizensPluginExists) {
                    getLogger().info("⚠ 原因: Citizens插件未安装");
                    getLogger().info("⚠ 建议: 安装Citizens插件以获得完整功能");
                } else if (configUseCitizens && citizensPluginExists) {
                    getLogger().info("⚠ 原因: Citizens连接失败");
                    getLogger().info("⚠ 建议: 检查Citizens插件版本和API兼容性");
                } else {
                    getLogger().info("✅ 配置: 设置为使用盔甲架");
                }
            }
        } else {
            getLogger().warning("❌ 状态: 实体管理器未初始化");
        }
        
        getLogger().info("═══════════════════════════════════════════════════");
        getLogger().info("使用 /fakeman check 查看详细状态");
        getLogger().info("═══════════════════════════════════════════════════");
    }
    
    public static Fakeman getInstance() {
        return instance;
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    public DummyManager getDummyManager() {
        return dummyManager;
    }
    
    public EconomyManager getEconomyManager() {
        return economyManager;
    }
    
    public EntityManager getEntityManager() {
        return entityManager;
    }
    
    public FileConfiguration getPluginConfig() {
        return config;
    }
    
    public PlayerBehaviorTracker getPlayerBehaviorTracker() {
        return playerBehaviorTracker;
    }
    
    /**
     * 根据配置创建实体管理器
     */
    private EntityManager createEntityManager() {
        boolean useCitizens = config.getBoolean("dummy.use-citizens", false);
        
        if (useCitizens) {
            getLogger().info("尝试创建Citizens实体管理器");
            return new CitizensManager(this);
        } else {
            getLogger().info("使用盔甲架实体管理器");
            return new ArmorStandManager(this);
        }
    }
}
