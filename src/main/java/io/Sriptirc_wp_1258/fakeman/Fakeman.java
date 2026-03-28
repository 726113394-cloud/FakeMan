package io.Sriptirc_wp_1258.fakeman;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import io.Sriptirc_wp_1258.fakeman.database.DatabaseManager;
import io.Sriptirc_wp_1258.fakeman.manager.DummyManager;
import io.Sriptirc_wp_1258.fakeman.commands.CommandManager;
import io.Sriptirc_wp_1258.fakeman.listeners.EventListener;
import io.Sriptirc_wp_1258.fakeman.economy.EconomyManager;

public final class Fakeman extends JavaPlugin {
    
    private static Fakeman instance;
    private DatabaseManager databaseManager;
    private DummyManager dummyManager;
    private EconomyManager economyManager;
    private FileConfiguration config;
    
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
        
        // 初始化假人管理器
        dummyManager = new DummyManager(this);
        
        // 注册命令
        new CommandManager(this).registerCommands();
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new EventListener(this), this);
        
        // 加载所有假人数据
        dummyManager.loadAllDummies();
        
        getLogger().info("FakeMan 插件已启用！");
    }
    
    @Override
    public void onDisable() {
        // 保存所有假人数据
        if (dummyManager != null) {
            dummyManager.saveAllDummies();
        }
        
        // 关闭数据库连接
        if (databaseManager != null) {
            databaseManager.close();
        }
        
        getLogger().info("FakeMan 插件已禁用！");
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
    
    public FileConfiguration getPluginConfig() {
        return config;
    }
}
