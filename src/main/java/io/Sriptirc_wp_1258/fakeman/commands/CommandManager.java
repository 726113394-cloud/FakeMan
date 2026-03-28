package io.Sriptirc_wp_1258.fakeman.commands;

import io.Sriptirc_wp_1258.fakeman.Fakeman;
import io.Sriptirc_wp_1258.fakeman.manager.DummyManager;
import io.Sriptirc_wp_1258.fakeman.objects.Dummy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import java.util.*;

public class CommandManager implements CommandExecutor, TabCompleter {
    
    private final Fakeman plugin;
    private final DummyManager dummyManager;
    
    public CommandManager(Fakeman plugin) {
        this.plugin = plugin;
        this.dummyManager = plugin.getDummyManager();
    }
    
    public void registerCommands() {
        plugin.getCommand("fakeman").setExecutor(this);
        plugin.getCommand("fakeman").setTabCompleter(this);
        plugin.getCommand("fm").setExecutor(this);
        plugin.getCommand("fm").setTabCompleter(this);
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家才能使用此命令！");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            showHelp(player);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "help":
                showHelp(player);
                break;
                
            case "summon":
                handleSummon(player, args);
                break;
                
            case "list":
                handleList(player);
                break;
                
            case "info":
                handleInfo(player, args);
                break;
                
            case "mode":
                handleMode(player, args);
                break;
                
            case "time":
                handleTime(player, args);
                break;
                
            case "remove":
                handleRemove(player, args);
                break;
                
            case "tp":
                handleTeleport(player, args);
                break;
                
            case "reload":
                handleReload(player);
                break;
                
            case "admin":
                handleAdmin(player, args);
                break;
                
            default:
                player.sendMessage("§c未知命令！输入 /fakeman help 查看帮助");
                break;
        }
        
        return true;
    }
    
    private void showHelp(Player player) {
        player.sendMessage("§6§l=== FakeMan 假人插件帮助 ===");
        player.sendMessage("§e/fakeman help §7- 显示此帮助信息");
        player.sendMessage("§e/fakeman summon §7- 在当前位置召唤假人");
        player.sendMessage("§e/fakeman list §7- 查看你的所有假人");
        player.sendMessage("§e/fakeman info <假人名> §7- 查看假人详细信息");
        player.sendMessage("§e/fakeman mode <假人名> <idle/follow> §7- 切换假人模式");
        player.sendMessage("§e/fakeman time <假人名> [时间] §7- 查看/设置假人时间");
        player.sendMessage("§e/fakeman remove <假人名> §7- 移除假人");
        player.sendMessage("§e/fakeman tp <假人名> §7- 传送到假人位置");
        player.sendMessage("§e/fakeman reload §7- 重载插件配置");
        
        if (player.hasPermission("FakeMan.admin")) {
            player.sendMessage("§c§l管理员命令:");
            player.sendMessage("§c/fakeman admin list §7- 查看所有假人");
            player.sendMessage("§c/fakeman admin remove <玩家> <假人名> §7- 移除玩家假人");
            player.sendMessage("§c/fakeman admin reload §7- 重载所有数据");
        }
        
        player.sendMessage("§7§o提示: 右键假人可以打开假人背包");
    }
    
    private void handleSummon(Player player, String[] args) {
        if (!player.hasPermission("FakeMan.use")) {
            player.sendMessage("§c你没有使用假人插件的权限！");
            return;
        }
        
        // 检查经济
        double cost = plugin.getEconomyManager().getSummonCost();
        if (!plugin.getEconomyManager().hasEnoughMoney(player, cost)) {
            player.sendMessage("§c你的余额不足！召唤假人需要 " + 
                plugin.getEconomyManager().format(cost) + " " + 
                plugin.getEconomyManager().getCurrencyName());
            return;
        }
        
        Location location = player.getLocation();
        Dummy dummy = dummyManager.spawnDummy(player, location);
        
        if (dummy != null) {
            player.sendMessage("§a成功召唤假人！");
            player.sendMessage("§7假人名称: §e" + dummy.getName());
            player.sendMessage("§7当前位置: §e" + formatLocation(location));
            player.sendMessage("§7默认模式: §e" + getModeDisplayName(dummy.getMode()));
            player.sendMessage("§7在线时间: §e" + formatTime(dummy.getMaxOnlineTime()));
            player.sendMessage("§7§o右键假人可以打开背包，使用指令控制假人行为");
        }
    }
    
    private void handleList(Player player) {
        List<Dummy> dummies = dummyManager.getPlayerDummies(player.getUniqueId());
        
        if (dummies.isEmpty()) {
            player.sendMessage("§e你还没有召唤任何假人！");
            player.sendMessage("§7使用 §e/fakeman summon §7召唤一个假人");
            return;
        }
        
        player.sendMessage("§6§l=== 你的假人列表 ===");
        player.sendMessage("§7当前假人数量: §e" + dummies.size() + "/" + getMaxDummies(player));
        
        for (int i = 0; i < dummies.size(); i++) {
            Dummy dummy = dummies.get(i);
            String status = dummy.isSpawned() ? "§a在线" : "§c离线";
            String mode = getModeDisplayName(dummy.getMode());
            String timeLeft = formatTime(dummy.getMaxOnlineTime() - dummy.getOnlineTime());
            
            player.sendMessage("§e" + (i + 1) + ". §7" + dummy.getName() + 
                " §8| §7状态: " + status + 
                " §8| §7模式: " + mode + 
                " §8| §7剩余: " + timeLeft);
        }
        
        player.sendMessage("§7使用 §e/fakeman info <假人名> §7查看详细信息");
    }
    
    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /fakeman info <假人名>");
            return;
        }
        
        String dummyName = args[1];
        Dummy dummy = dummyManager.getDummy(player.getUniqueId(), dummyName);
        
        if (dummy == null) {
            player.sendMessage("§c未找到假人: " + dummyName);
            return;
        }
        
        player.sendMessage("§6§l=== 假人信息 ===");
        player.sendMessage("§7名称: §e" + dummy.getName());
        player.sendMessage("§7状态: " + (dummy.isSpawned() ? "§a在线" : "§c离线"));
        player.sendMessage("§7位置: §e" + formatLocation(dummy.getLocation()));
        player.sendMessage("§7血量: §e" + String.format("%.1f", dummy.getHealth()) + "/" + 
            String.format("%.1f", dummy.getMaxHealth()));
        player.sendMessage("§7模式: §e" + getModeDisplayName(dummy.getMode()));
        player.sendMessage("§7在线时间: §e" + formatTime(dummy.getOnlineTime()) + "/" + 
            formatTime(dummy.getMaxOnlineTime()));
        player.sendMessage("§7创建时间: §e" + formatTimestamp(System.currentTimeMillis() - dummy.getOnlineTime() * 1000));
        
        // 背包信息
        int inventoryItems = countItems(dummy.getInventory());
        int armorItems = countItems(dummy.getArmor());
        player.sendMessage("§7背包物品: §e" + inventoryItems + " 个");
        player.sendMessage("§7装备物品: §e" + armorItems + " 个");
        
        player.sendMessage("§7§o使用指令控制假人: /fakeman mode|time|remove|tp");
    }
    
    private void handleMode(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§c用法: /fakeman mode <假人名> <idle/follow>");
            player.sendMessage("§7模式说明:");
            player.sendMessage("§7  idle - 挂机模式，原地不动");
            player.sendMessage("§7  follow - 跟随模式，模仿你的行为");
            return;
        }
        
        String dummyName = args[1];
        String mode = args[2].toLowerCase();
        
        if (!mode.equals("idle") && !mode.equals("follow")) {
            player.sendMessage("§c无效的模式！只能使用 idle 或 follow");
            return;
        }
        
        Dummy dummy = dummyManager.getDummy(player.getUniqueId(), dummyName);
        if (dummy == null) {
            player.sendMessage("§c未找到假人: " + dummyName);
            return;
        }
        
        String oldMode = dummy.getMode();
        dummy.setMode(mode);
        
        player.sendMessage("§a已切换假人模式！");
        player.sendMessage("§7假人: §e" + dummyName);
        player.sendMessage("§7旧模式: §e" + getModeDisplayName(oldMode));
        player.sendMessage("§7新模式: §e" + getModeDisplayName(mode));
        
        if (mode.equals("follow")) {
            player.sendMessage("§7§o跟随模式下，假人会模仿你的行为并收集资源");
        }
    }
    
    private void handleTime(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /fakeman time <假人名> [时间]");
            player.sendMessage("§7时间格式: 1h, 30m, 3600s");
            return;
        }
        
        String dummyName = args[1];
        Dummy dummy = dummyManager.getDummy(player.getUniqueId(), dummyName);
        
        if (dummy == null) {
            player.sendMessage("§c未找到假人: " + dummyName);
            return;
        }
        
        if (args.length == 2) {
            // 查看时间
            long current = dummy.getOnlineTime();
            long max = dummy.getMaxOnlineTime();
            long left = max - current;
            
            player.sendMessage("§6§l假人时间信息");
            player.sendMessage("§7假人: §e" + dummyName);
            player.sendMessage("§7已在线: §e" + formatTime(current));
            player.sendMessage("§7最大时间: §e" + formatTime(max));
            player.sendMessage("§7剩余时间: §e" + formatTime(left));
            
            if (left < 3600) {
                player.sendMessage("§c警告: 假人即将消失！");
            }
            
            // 检查是否有延长权限
            if (player.hasPermission("FakeMan.expend")) {
                long maxAllowed = plugin.getPluginConfig().getLong("dummy.max-online-time", 86400L);
                player.sendMessage("§7你可以延长到: §e" + formatTime(maxAllowed));
            }
            
            return;
        }
        
        // 设置时间
        if (!player.hasPermission("FakeMan.expend")) {
            player.sendMessage("§c你没有延长假人时间的权限！");
            return;
        }
        
        String timeStr = args[2];
        long seconds = parseTime(timeStr);
        
        if (seconds <= 0) {
            player.sendMessage("§c无效的时间格式！使用如: 1h, 30m, 3600s");
            return;
        }
        
        long maxAllowed = plugin.getPluginConfig().getLong("dummy.max-online-time", 86400L);
        if (seconds > maxAllowed) {
            player.sendMessage("§c时间不能超过 " + formatTime(maxAllowed) + "！");
            return;
        }
        
        dummy.setMaxOnlineTime(seconds);
        player.sendMessage("§a已设置假人最大在线时间！");
        player.sendMessage("§7假人: §e" + dummyName);
        player.sendMessage("§7新时间: §e" + formatTime(seconds));
    }
    
    private void handleRemove(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /fakeman remove <假人名>");
            player.sendMessage("§c警告: 此操作不可逆！假人背包物品将丢失");
            return;
        }
        
        String dummyName = args[1];
        
        // 确认
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            player.sendMessage("§c你确定要移除假人 " + dummyName + " 吗？");
            player.sendMessage("§c假人背包中的所有物品将会丢失！");
            player.sendMessage("§c输入 §e/fakeman remove " + dummyName + " confirm §c确认移除");
            return;
        }
        
        boolean success = dummyManager.despawnDummy(player.getUniqueId(), dummyName);
        if (success) {
            player.sendMessage("§a已成功移除假人: " + dummyName);
        } else {
            player.sendMessage("§c移除假人失败！假人可能不存在");
        }
    }
    
    private void handleTeleport(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /fakeman tp <假人名>");
            return;
        }
        
        String dummyName = args[1];
        Dummy dummy = dummyManager.getDummy(player.getUniqueId(), dummyName);
        
        if (dummy == null) {
            player.sendMessage("§c未找到假人: " + dummyName);
            return;
        }
        
        if (!dummy.isSpawned()) {
            player.sendMessage("§c假人当前不在线！");
            return;
        }
        
        player.teleport(dummy.getLocation());
        player.sendMessage("§a已传送到假人位置: " + dummyName);
    }
    
    private void handleReload(Player player) {
        if (!player.hasPermission("FakeMan.admin")) {
            player.sendMessage("§c你没有权限重载插件！");
            return;
        }
        
        plugin.reloadConfig();
        player.sendMessage("§a插件配置已重载！");
    }
    
    private void handleAdmin(Player player, String[] args) {
        if (!player.hasPermission("FakeMan.admin")) {
            player.sendMessage("§c你没有管理员权限！");
            return;
        }
        
        if (args.length < 2) {
            player.sendMessage("§c用法: /fakeman admin <list|remove|reload>");
            return;
        }
        
        String adminCmd = args[1].toLowerCase();
        
        switch (adminCmd) {
            case "list":
                // 显示所有假人
                int total = dummyManager.getTotalDummyCount();
                player.sendMessage("§6§l服务器假人统计");
                player.sendMessage("§7总假人数: §e" + total);
                // 这里可以添加更多统计信息
                break;
                
            case "remove":
                if (args.length < 4) {
                    player.sendMessage("§c用法: /fakeman admin remove <玩家名> <假人名>");
                    return;
                }
                
                String targetPlayer = args[2];
                String targetDummy = args[3];
                
                Player target = Bukkit.getPlayer(targetPlayer);
                if (target == null) {
                    player.sendMessage("§c玩家不在线或不存在！");
                    return;
                }
                
                boolean success = dummyManager.despawnDummy(target.getUniqueId(), targetDummy);
                if (success) {
                    player.sendMessage("§a已移除玩家 " + targetPlayer + " 的假人: " + targetDummy);
                    target.sendMessage("§c管理员移除了你的假人: " + targetDummy);
                } else {
                    player.sendMessage("§c移除失败！假人可能不存在");
                }
                break;
                
            case "reload":
                plugin.reloadConfig();
                player.sendMessage("§a插件配置已重载！");
                break;
                
            default:
                player.sendMessage("§c未知的管理员命令！");
                break;
        }
    }
    
    // 工具方法
    private String getModeDisplayName(String mode) {
        switch (mode) {
            case "idle":
                return "§7挂机";
            case "follow":
                return "§a跟随";
            default:
                return mode;
        }
    }
    
    private String formatLocation(Location location) {
        return String.format("世界: %s, X: %.1f, Y: %.1f, Z: %.1f",
            location.getWorld().getName(),
            location.getX(),
            location.getY(),
            location.getZ());
    }
    
    private String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "秒";
        } else if (seconds < 3600) {
            return (seconds / 60) + "分钟" + (seconds % 60 > 0 ? " " + (seconds % 60) + "秒" : "");
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + "小时" + (minutes > 0 ? " " + minutes + "分钟" : "");
        }
    }
    
    private String formatTimestamp(long timestamp) {
        long seconds = timestamp / 1000;
        if (seconds < 60) {
            return seconds + "秒前";
        } else if (seconds < 3600) {
            return (seconds / 60) + "分钟前";
        } else if (seconds < 86400) {
            return (seconds / 3600) + "小时前";
        } else {
            return (seconds / 86400) + "天前";
        }
    }
    
    private long parseTime(String timeStr) {
        try {
            timeStr = timeStr.toLowerCase();
            if (timeStr.endsWith("h")) {
                return Long.parseLong(timeStr.substring(0, timeStr.length() - 1)) * 3600;
            } else if (timeStr.endsWith("m")) {
                return Long.parseLong(timeStr.substring(0, timeStr.length() - 1)) * 60;
            } else if (timeStr.endsWith("s")) {
                return Long.parseLong(timeStr.substring(0, timeStr.length() - 1));
            } else {
                return Long.parseLong(timeStr);
            }
        } catch (NumberFormatException e) {
            return -1;
        }
    }
    
    private int countItems(ItemStack[] items) {
        if (items == null) return 0;
        int count = 0;
        for (ItemStack item : items) {
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                count++;
            }
        }
        return count;
    }
    
    private int getMaxDummies(Player player) {
        int defaultMax = plugin.getPluginConfig().getInt("limits.default-max-dummies", 1);
        if (player.hasPermission("FakeMan.expend")) {
            return plugin.getPluginConfig().getInt("limits.expend-max-dummies", 5);
        }
        return defaultMax;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (!(sender instanceof Player)) {
            return completions;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 1) {
            // 主命令补全
            List<String> commands = Arrays.asList(
                "help", "summon", "list", "info", "mode", 
                "time", "remove", "tp", "reload"
            );
            
            if (player.hasPermission("FakeMan.admin")) {
                commands = new ArrayList<>(commands);
                commands.add("admin");
            }
            
            StringUtil.copyPartialMatches(args[0], commands, completions);
            
        } else if (args.length == 2) {
            // 子命令参数补全
            String subCommand = args[0].toLowerCase();
            
            switch (subCommand) {
                case "info":
                case "mode":
                case "time":
                case "remove":
                case "tp":
                    // 假人名补全
                    List<Dummy> dummies = dummyManager.getPlayerDummies(player.getUniqueId());
                    for (Dummy dummy : dummies) {
                        completions.add(dummy.getName());
                    }
                    break;
                    
                case "admin":
                    List<String> adminCommands = Arrays.asList("list", "remove", "reload");
                    StringUtil.copyPartialMatches(args[1], adminCommands, completions);
                    break;
            }
            
        } else if (args.length == 3) {
            // 第三参数补全
            String subCommand = args[0].toLowerCase();
            
            switch (subCommand) {
                case "mode":
                    List<String> modes = Arrays.asList("idle", "follow");
                    StringUtil.copyPartialMatches(args[2], modes, completions);
                    break;
                    
                case "time":
                    // 时间格式提示
                    completions.add("1h");
                    completions.add("6h");
                    completions.add("12h");
                    completions.add("24h");
                    break;
                    
                case "remove":
                    completions.add("confirm");
                    break;
                    
                case "admin":
                    if (args[1].equalsIgnoreCase("remove")) {
                        // 玩家名补全
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            completions.add(p.getName());
                        }
                    }
                    break;
            }
        }
        
        Collections.sort(completions);
        return completions;
    }
}