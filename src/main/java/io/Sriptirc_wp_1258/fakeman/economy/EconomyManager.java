package io.Sriptirc_wp_1258.fakeman.economy;

import io.Sriptirc_wp_1258.fakeman.Fakeman;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Level;

public class EconomyManager {
    
    private final Fakeman plugin;
    private Economy economy;
    private boolean enabled;
    
    public EconomyManager(Fakeman plugin) {
        this.plugin = plugin;
        this.enabled = setupEconomy();
    }
    
    private boolean setupEconomy() {
        if (!plugin.getPluginConfig().getBoolean("economy.enabled", true)) {
            plugin.getLogger().info("经济系统已禁用");
            return false;
        }
        
        if (Bukkit.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("未找到Vault插件，经济系统将禁用");
            return false;
        }
        
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning("未找到经济服务提供者");
            return false;
        }
        
        economy = rsp.getProvider();
        if (economy == null) {
            plugin.getLogger().warning("无法获取经济服务");
            return false;
        }
        
        plugin.getLogger().info("经济系统已启用，使用: " + economy.getName());
        return true;
    }
    
    public boolean isEnabled() {
        return enabled && economy != null;
    }
    
    public boolean hasEnoughMoney(Player player, double amount) {
        if (!isEnabled()) {
            // 经济系统禁用时，默认有足够金钱
            return true;
        }
        
        try {
            return economy.has(player, amount);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "检查玩家余额失败: " + player.getName(), e);
            return false;
        }
    }
    
    public boolean withdrawMoney(Player player, double amount) {
        if (!isEnabled()) {
            // 经济系统禁用时，默认扣款成功
            return true;
        }
        
        if (amount <= 0) {
            return true;
        }
        
        try {
            EconomyResponse response = economy.withdrawPlayer(player, amount);
            if (response.transactionSuccess()) {
                plugin.getLogger().info("玩家 " + player.getName() + " 扣款 " + amount + " 成功，余额: " + response.balance);
                return true;
            } else {
                plugin.getLogger().warning("玩家 " + player.getName() + " 扣款失败: " + response.errorMessage);
                return false;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "扣款失败: " + player.getName(), e);
            return false;
        }
    }
    
    public boolean depositMoney(Player player, double amount) {
        if (!isEnabled()) {
            return true;
        }
        
        if (amount <= 0) {
            return true;
        }
        
        try {
            EconomyResponse response = economy.depositPlayer(player, amount);
            if (response.transactionSuccess()) {
                plugin.getLogger().info("玩家 " + player.getName() + " 存款 " + amount + " 成功，余额: " + response.balance);
                return true;
            } else {
                plugin.getLogger().warning("玩家 " + player.getName() + " 存款失败: " + response.errorMessage);
                return false;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "存款失败: " + player.getName(), e);
            return false;
        }
    }
    
    public double getBalance(Player player) {
        if (!isEnabled()) {
            return 0;
        }
        
        try {
            return economy.getBalance(player);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "获取玩家余额失败: " + player.getName(), e);
            return 0;
        }
    }
    
    public String format(double amount) {
        if (!isEnabled()) {
            return String.format("%.2f", amount);
        }
        
        try {
            return economy.format(amount);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "格式化金额失败", e);
            return String.format("%.2f", amount);
        }
    }
    
    public String getCurrencyName() {
        if (!isEnabled()) {
            return "游戏币";
        }
        
        try {
            return economy.currencyNamePlural();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "获取货币名称失败", e);
            return "游戏币";
        }
    }
    
    public String getCurrencyNameSingular() {
        if (!isEnabled()) {
            return "游戏币";
        }
        
        try {
            return economy.currencyNameSingular();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "获取货币单数名称失败", e);
            return "游戏币";
        }
    }
    
    public double getSummonCost() {
        return plugin.getPluginConfig().getDouble("economy.summon-cost", 30000.0);
    }
    
    public String getFormattedSummonCost() {
        return format(getSummonCost());
    }
}