package ru.example.telegramauth;

import java.io.File;
import java.sql.*;
import java.util.UUID;

/**
 * SQLite-based storage implementation.
 */
public class SqliteStorage implements Storage {

    private final TelegramAuthPlugin plugin;
    private Connection connection;

    public SqliteStorage(TelegramAuthPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            String url = "jdbc:sqlite:" + new File(dataFolder, "data.db").getAbsolutePath();
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(url);

            createTables();
            plugin.getLogger().info("SQLite storage initialized.");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize SQLite: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS links (" +
                "uuid TEXT PRIMARY KEY, " +
                "telegram_id INTEGER NOT NULL" +
                ")"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS codes (" +
                "code TEXT PRIMARY KEY, " +
                "uuid TEXT NOT NULL, " +
                "expires INTEGER NOT NULL" +
                ")"
            );
        }
    }

    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            File dataFolder = plugin.getDataFolder();
            String url = "jdbc:sqlite:" + new File(dataFolder, "data.db").getAbsolutePath();
            connection = DriverManager.getConnection(url);
        }
        return connection;
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to close SQLite connection: " + e.getMessage());
        }
    }

    @Override
    public boolean isLinked(UUID uuid) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                "SELECT telegram_id FROM links WHERE uuid = ?"
            );
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            boolean found = rs.next();
            rs.close();
            ps.close();
            return found;
        } catch (SQLException e) {
            plugin.getLogger().severe("SQLite error (isLinked): " + e.getMessage());
            return false;
        }
    }

    @Override
    public long getTelegramId(UUID uuid) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                "SELECT telegram_id FROM links WHERE uuid = ?"
            );
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            long id = 0;
            if (rs.next()) {
                id = rs.getLong("telegram_id");
            }
            rs.close();
            ps.close();
            return id;
        } catch (SQLException e) {
            plugin.getLogger().severe("SQLite error (getTelegramId): " + e.getMessage());
            return 0;
        }
    }

    @Override
    public UUID getUUID(long telegramId) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                "SELECT uuid FROM links WHERE telegram_id = ?"
            );
            ps.setLong(1, telegramId);
            ResultSet rs = ps.executeQuery();
            UUID uuid = null;
            if (rs.next()) {
                uuid = UUID.fromString(rs.getString("uuid"));
            }
            rs.close();
            ps.close();
            return uuid;
        } catch (SQLException e) {
            plugin.getLogger().severe("SQLite error (getUUID): " + e.getMessage());
            return null;
        }
    }

    @Override
    public void saveLink(UUID uuid, long telegramId) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                "INSERT OR REPLACE INTO links (uuid, telegram_id) VALUES (?, ?)"
            );
            ps.setString(1, uuid.toString());
            ps.setLong(2, telegramId);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("SQLite error (saveLink): " + e.getMessage());
        }
    }

    @Override
    public void removeLink(UUID uuid) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                "DELETE FROM links WHERE uuid = ?"
            );
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("SQLite error (removeLink): " + e.getMessage());
        }
    }

    @Override
    public void saveCode(String code, UUID uuid, long expiresAt) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                "INSERT OR REPLACE INTO codes (code, uuid, expires) VALUES (?, ?, ?)"
            );
            ps.setString(1, code);
            ps.setString(2, uuid.toString());
            ps.setLong(3, expiresAt);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("SQLite error (saveCode): " + e.getMessage());
        }
    }

    @Override
    public UUID getCodeUUID(String code) {
        if (!isCodeValid(code)) {
            return null;
        }
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                "SELECT uuid FROM codes WHERE code = ?"
            );
            ps.setString(1, code);
            ResultSet rs = ps.executeQuery();
            UUID uuid = null;
            if (rs.next()) {
                uuid = UUID.fromString(rs.getString("uuid"));
            }
            rs.close();
            ps.close();
            return uuid;
        } catch (SQLException e) {
            plugin.getLogger().severe("SQLite error (getCodeUUID): " + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean isCodeValid(String code) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                "SELECT expires FROM codes WHERE code = ?"
            );
            ps.setString(1, code);
            ResultSet rs = ps.executeQuery();
            boolean valid = false;
            if (rs.next()) {
                long expires = rs.getLong("expires");
                valid = System.currentTimeMillis() < expires;
            }
            rs.close();
            ps.close();
            return valid;
        } catch (SQLException e) {
            plugin.getLogger().severe("SQLite error (isCodeValid): " + e.getMessage());
            return false;
        }
    }

    @Override
    public void removeCode(String code) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                "DELETE FROM codes WHERE code = ?"
            );
            ps.setString(1, code);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("SQLite error (removeCode): " + e.getMessage());
        }
    }

    @Override
    public void cleanExpiredCodes() {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                "DELETE FROM codes WHERE expires < ?"
            );
            ps.setLong(1, System.currentTimeMillis());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("SQLite error (cleanExpiredCodes): " + e.getMessage());
        }
    }
}
