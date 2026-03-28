package io.Sriptirc_wp_1258.fakeman.database;

import io.Sriptirc_wp_1258.fakeman.Fakeman;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.Base64;

public class DatabaseManager {
    
    private final Fakeman plugin;
    private Connection connection;
    
    public DatabaseManager(Fakeman plugin) {
        this.plugin = plugin;
    }
    
    public void initialize() {
        try {
            Class.forName("org.sqlite.JDBC");
            String dbPath = plugin.getDataFolder().getAbsolutePath() + "/" + 
                          plugin.getPluginConfig().getString("storage.sqlite-file", "fakeman.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            
            createTables();
            plugin.getLogger().info("数据库连接已建立");
        } catch (ClassNotFoundException | SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "数据库初始化失败", e);
        }
    }
    
    private void createTables() {
        String createDummiesTable = "CREATE TABLE IF NOT EXISTS dummies (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "owner_uuid TEXT NOT NULL," +
                "dummy_name TEXT NOT NULL," +
                "world TEXT NOT NULL," +
                "x REAL NOT NULL," +
                "y REAL NOT NULL," +
                "z REAL NOT NULL," +
                "yaw REAL NOT NULL," +
                "pitch REAL NOT NULL," +
                "health REAL NOT NULL," +
                "max_health REAL NOT NULL," +
                "mode TEXT NOT NULL," +
                "online_time INTEGER NOT NULL," +
                "max_online_time INTEGER NOT NULL," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "last_active TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "inventory TEXT," +
                "armor TEXT," +
                "extra_data TEXT," +
                "UNIQUE(owner_uuid, dummy_name)" +
                ")";
        
        String createPlayerDataTable = "CREATE TABLE IF NOT EXISTS player_data (" +
                "player_uuid TEXT PRIMARY KEY," +
                "player_name TEXT NOT NULL," +
                "max_dummies INTEGER NOT NULL," +
                "total_spent REAL NOT NULL," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createDummiesTable);
            stmt.execute(createPlayerDataTable);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "创建数据库表失败", e);
        }
    }
    
    public void saveDummy(UUID ownerUuid, String dummyName, Location location, 
                         double health, double maxHealth, String mode, 
                         long onlineTime, long maxOnlineTime, 
                         ItemStack[] inventory, ItemStack[] armor, 
                         Map<String, Object> extraData) {
        
        String sql = "INSERT OR REPLACE INTO dummies " +
                    "(owner_uuid, dummy_name, world, x, y, z, yaw, pitch, " +
                    "health, max_health, mode, online_time, max_online_time, " +
                    "inventory, armor, extra_data) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, ownerUuid.toString());
            pstmt.setString(2, dummyName);
            pstmt.setString(3, location.getWorld().getName());
            pstmt.setDouble(4, location.getX());
            pstmt.setDouble(5, location.getY());
            pstmt.setDouble(6, location.getZ());
            pstmt.setFloat(7, location.getYaw());
            pstmt.setFloat(8, location.getPitch());
            pstmt.setDouble(9, health);
            pstmt.setDouble(10, maxHealth);
            pstmt.setString(11, mode);
            pstmt.setLong(12, onlineTime);
            pstmt.setLong(13, maxOnlineTime);
            pstmt.setString(14, itemStackArrayToBase64(inventory));
            pstmt.setString(15, itemStackArrayToBase64(armor));
            pstmt.setString(16, mapToJson(extraData));
            
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "保存假人数据失败", e);
        }
    }
    
    public Map<String, Object> loadDummy(UUID ownerUuid, String dummyName) {
        String sql = "SELECT * FROM dummies WHERE owner_uuid = ? AND dummy_name = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, ownerUuid.toString());
            pstmt.setString(2, dummyName);
            
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                Map<String, Object> data = new HashMap<>();
                
                // 基础信息
                data.put("id", rs.getInt("id"));
                data.put("owner_uuid", UUID.fromString(rs.getString("owner_uuid")));
                data.put("dummy_name", rs.getString("dummy_name"));
                
                // 位置信息
                String worldName = rs.getString("world");
                Location location = new Location(
                    Bukkit.getWorld(worldName),
                    rs.getDouble("x"),
                    rs.getDouble("y"),
                    rs.getDouble("z"),
                    rs.getFloat("yaw"),
                    rs.getFloat("pitch")
                );
                data.put("location", location);
                
                // 属性信息
                data.put("health", rs.getDouble("health"));
                data.put("max_health", rs.getDouble("max_health"));
                data.put("mode", rs.getString("mode"));
                data.put("online_time", rs.getLong("online_time"));
                data.put("max_online_time", rs.getLong("max_online_time"));
                
                // 物品信息
                data.put("inventory", base64ToItemStackArray(rs.getString("inventory")));
                data.put("armor", base64ToItemStackArray(rs.getString("armor")));
                
                // 额外数据
                data.put("extra_data", jsonToMap(rs.getString("extra_data")));
                data.put("created_at", rs.getTimestamp("created_at"));
                data.put("last_active", rs.getTimestamp("last_active"));
                
                return data;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "加载假人数据失败", e);
        }
        
        return null;
    }
    
    public List<Map<String, Object>> loadPlayerDummies(UUID playerUuid) {
        List<Map<String, Object>> dummies = new ArrayList<>();
        String sql = "SELECT * FROM dummies WHERE owner_uuid = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> data = new HashMap<>();
                
                // 基础信息
                data.put("id", rs.getInt("id"));
                data.put("owner_uuid", UUID.fromString(rs.getString("owner_uuid")));
                data.put("dummy_name", rs.getString("dummy_name"));
                
                // 位置信息
                String worldName = rs.getString("world");
                Location location = new Location(
                    Bukkit.getWorld(worldName),
                    rs.getDouble("x"),
                    rs.getDouble("y"),
                    rs.getDouble("z"),
                    rs.getFloat("yaw"),
                    rs.getFloat("pitch")
                );
                data.put("location", location);
                
                // 属性信息
                data.put("health", rs.getDouble("health"));
                data.put("max_health", rs.getDouble("max_health"));
                data.put("mode", rs.getString("mode"));
                data.put("online_time", rs.getLong("online_time"));
                data.put("max_online_time", rs.getLong("max_online_time"));
                
                // 物品信息
                data.put("inventory", base64ToItemStackArray(rs.getString("inventory")));
                data.put("armor", base64ToItemStackArray(rs.getString("armor")));
                
                // 额外数据
                data.put("extra_data", jsonToMap(rs.getString("extra_data")));
                data.put("created_at", rs.getTimestamp("created_at"));
                data.put("last_active", rs.getTimestamp("last_active"));
                
                dummies.add(data);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "加载玩家假人列表失败", e);
        }
        
        return dummies;
    }
    
    public void deleteDummy(UUID ownerUuid, String dummyName) {
        String sql = "DELETE FROM dummies WHERE owner_uuid = ? AND dummy_name = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, ownerUuid.toString());
            pstmt.setString(2, dummyName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "删除假人数据失败", e);
        }
    }
    
    public void updatePlayerData(UUID playerUuid, String playerName, int maxDummies, double totalSpent) {
        String sql = "INSERT OR REPLACE INTO player_data " +
                    "(player_uuid, player_name, max_dummies, total_spent, last_seen) " +
                    "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            pstmt.setString(2, playerName);
            pstmt.setInt(3, maxDummies);
            pstmt.setDouble(4, totalSpent);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "更新玩家数据失败", e);
        }
    }
    
    public Map<String, Object> getPlayerData(UUID playerUuid) {
        String sql = "SELECT * FROM player_data WHERE player_uuid = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                Map<String, Object> data = new HashMap<>();
                data.put("player_uuid", UUID.fromString(rs.getString("player_uuid")));
                data.put("player_name", rs.getString("player_name"));
                data.put("max_dummies", rs.getInt("max_dummies"));
                data.put("total_spent", rs.getDouble("total_spent"));
                data.put("created_at", rs.getTimestamp("created_at"));
                data.put("last_seen", rs.getTimestamp("last_seen"));
                return data;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "获取玩家数据失败", e);
        }
        
        return null;
    }
    
    public int getPlayerDummyCount(UUID playerUuid) {
        String sql = "SELECT COUNT(*) as count FROM dummies WHERE owner_uuid = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "获取玩家假人数量失败", e);
        }
        
        return 0;
    }
    
    public int getTotalDummyCount() {
        String sql = "SELECT COUNT(*) as count FROM dummies";
        
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "获取总假人数量失败", e);
        }
        
        return 0;
    }
    
    // 物品序列化工具方法
    private String itemStackArrayToBase64(ItemStack[] items) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            
            dataOutput.writeInt(items.length);
            
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }
            
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "物品序列化失败", e);
            return "";
        }
    }
    
    private ItemStack[] base64ToItemStackArray(String data) {
        if (data == null || data.isEmpty()) {
            return new ItemStack[0];
        }
        
        try {
            byte[] bytes = Base64.getDecoder().decode(data);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            
            int size = dataInput.readInt();
            ItemStack[] items = new ItemStack[size];
            
            for (int i = 0; i < size; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }
            
            dataInput.close();
            return items;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "物品反序列化失败", e);
            return new ItemStack[0];
        }
    }
    
    // JSON工具方法
    private String mapToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                json.append(",");
            }
            json.append("\"").append(entry.getKey()).append("\":");
            
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(value.toString().replace("\"", "\\\"")).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                json.append(value);
            } else {
                json.append("\"").append(value.toString().replace("\"", "\\\"")).append("\"");
            }
            
            first = false;
        }
        
        json.append("}");
        return json.toString();
    }
    
    private Map<String, Object> jsonToMap(String json) {
        Map<String, Object> map = new HashMap<>();
        
        if (json == null || json.isEmpty() || json.equals("{}")) {
            return map;
        }
        
        try {
            // 简单的JSON解析
            json = json.trim();
            if (json.startsWith("{") && json.endsWith("}")) {
                json = json.substring(1, json.length() - 1);
                String[] pairs = json.split(",");
                
                for (String pair : pairs) {
                    String[] keyValue = pair.split(":", 2);
                    if (keyValue.length == 2) {
                        String key = keyValue[0].trim().replace("\"", "");
                        String value = keyValue[1].trim();
                        
                        if (value.startsWith("\"") && value.endsWith("\"")) {
                            value = value.substring(1, value.length() - 1);
                            map.put(key, value);
                        } else if (value.equals("true") || value.equals("false")) {
                            map.put(key, Boolean.parseBoolean(value));
                        } else {
                            try {
                                map.put(key, Double.parseDouble(value));
                            } catch (NumberFormatException e) {
                                map.put(key, value);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "JSON解析失败: " + json, e);
        }
        
        return map;
    }
    
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("数据库连接已关闭");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "关闭数据库连接失败", e);
        }
    }
}