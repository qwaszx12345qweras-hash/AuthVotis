package ru.example.telegramauth;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.UUID;

/**
 * MySQL-based storage implementation using HikariCP.
 */
public class MysqlStorage implements Storage {

    private final TelegramAuthPlugin plugin;
    private HikariDataSource dataSource;

    public MysqlStorage(TelegramAuthPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        try {
            String host = plugin.getConfig().getString("storage.mysql.host", "localhost");
            int port = plugin.getConfig().getInt("storage.mysql.port", 3306);
            String database = plugin.getConfig().getString("storage.mysql.database", "telegramauth");
            String username = plugin.getConfig().getString("storage.mysql.username", "root");
            String password = plugin.getConfig().getString("storage.mysql.password", "password");
            boolean useSSL = plugin.getConfig().getBoolean("storage.mysql.useSSL", false);
            int maxPoolSize = plugin.getConfig().getInt("storage.mysql.max-pool-size", 10);
            long connectionTimeout = plugin.getConfig().getLong("storage.mysql.connection-timeout", 30000);

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database +
                    "?useSSL=" + useSSL + "&autoReconnect=true&useUnicode=true&characterEncoding=UTF-8");
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(maxPoolSize);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(connectionTimeout);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            config.setPoolName("TelegramAuth-HikariPool");

            dataSource = new HikariDataSource(config);

            createTables();
            plugin.getLogger().info("MySQL storage initialized.");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize MySQL: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createTables() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS ta_links (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "telegram_id BIGINT NOT NULL, " +
                "INDEX idx_telegram_id (telegram_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS ta_codes (" +
                "code VARCHAR(10) PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "expires BIGINT NOT NULL, " +
                "INDEX idx_expires (expires)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Override
    public boolean isLinked(UUID uuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT telegram_id FROM ta_links WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            boolean found = rs.next();
            rs.close();
            return found;
        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL error (isLinked): " + e.getMessage());
            return false;
        }
    }

    @Override
    public long getTelegramId(UUID uuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT telegram_id FROM ta_links WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            long id = 0;
            if (rs.next()) {
                id = rs.getLong("telegram_id");
            }
            rs.close();
            return id;
        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL error (getTelegramId): " + e.getMessage());
            return 0;
        }
    }

    @Override
    public UUID getUUID(long telegramId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT uuid FROM ta_links WHERE telegram_id = ?")) {
            ps.setLong(1, telegramId);
            ResultSet rs = ps.executeQuery();
            UUID uuid = null;
            if (rs.next()) {
                uuid = UUID.fromString(rs.getString("uuid"));
            }
            rs.close();
            return uuid;
        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL error (getUUID): " + e.getMessage());
            return null;
        }
    }

    @Override
    public void saveLink(UUID uuid, long telegramId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO ta_links (uuid, telegram_id) VALUES (?, ?) " +
                     "ON DUPLICATE KEY UPDATE telegram_id = ?")) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, telegramId);
            ps.setLong(3, telegramId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL error (saveLink): " + e.getMessage());
        }
    }

    @Override
    public void removeLink(UUID uuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM ta_links WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL error (removeLink): " + e.getMessage());
        }
    }

    @Override
    public void saveCode(String code, UUID uuid, long expiresAt) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO ta_codes (code, uuid, expires) VALUES (?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE uuid = ?, expires = ?")) {
            ps.setString(1, code);
            ps.setString(2, uuid.toString());
            ps.setLong(3, expiresAt);
            ps.setString(4, uuid.toString());
            ps.setLong(5, expiresAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL error (saveCode): " + e.getMessage());
        }
    }

    @Override
    public UUID getCodeUUID(String code) {
        if (!isCodeValid(code)) {
            return null;
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT uuid FROM ta_codes WHERE code = ?")) {
            ps.setString(1, code);
            ResultSet rs = ps.executeQuery();
            UUID uuid = null;
            if (rs.next()) {
                uuid = UUID.fromString(rs.getString("uuid"));
            }
            rs.close();
            return uuid;
        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL error (getCodeUUID): " + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean isCodeValid(String code) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT expires FROM ta_codes WHERE code = ?")) {
            ps.setString(1, code);
            ResultSet rs = ps.executeQuery();
            boolean valid = false;
            if (rs.next()) {
                long expires = rs.getLong("expires");
                valid = System.currentTimeMillis() < expires;
            }
            rs.close();
            return valid;
        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL error (isCodeValid): " + e.getMessage());
            return false;
        }
    }

    @Override
    public void removeCode(String code) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM ta_codes WHERE code = ?")) {
            ps.setString(1, code);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL error (removeCode): " + e.getMessage());
        }
    }

    @Override
    public void cleanExpiredCodes() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM ta_codes WHERE expires < ?")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL error (cleanExpiredCodes): " + e.getMessage());
        }
    }
}
