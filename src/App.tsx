import { useState } from 'react'

const files: Record<string, string> = {
  'build.gradle': `plugins {
    id 'java'
    id 'com.github.johnrengelman.shadow' version '8.1.1'
}

group = 'ru.example'
version = '1.0.0'

repositories {
    mavenCentral()
    maven { url = 'https://repo.papermc.io/repository/maven-public/' }
}

dependencies {
    compileOnly 'com.destroystokyo.paper:paper-api:1.16.5-R0.1-SNAPSHOT'
    implementation 'org.telegram:telegrambots:6.9.7.1'
    implementation 'org.xerial:sqlite-jdbc:3.42.0.0'
    implementation 'com.zaxxer:HikariCP:4.0.3'
    implementation 'mysql:mysql-connector-java:8.0.33'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
    options.release = 8
}

shadowJar {
    archiveBaseName.set('TelegramAuth')
    archiveClassifier.set('')
    archiveVersion.set(project.version)
}

build.dependsOn shadowJar`,

  'settings.gradle': `rootProject.name = 'TelegramAuth'`,

  '.github/workflows/build.yml': `name: Build TelegramAuth
on:
  push:
    branches: [ main ]
  workflow_dispatch:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - uses: gradle/actions/setup-gradle@v4
      - run: gradle shadowJar
      - uses: actions/upload-artifact@v4
        with:
          name: TelegramAuth-Jar
          path: build/libs/TelegramAuth-*.jar`,

  'src/main/resources/plugin.yml': `name: TelegramAuth
version: 1.0.0
main: ru.example.telegramauth.TelegramAuthPlugin
api-version: 1.16
author: YourName
description: Telegram authentication with freeze system

commands:
  link:
    description: Get link code for Telegram binding
    usage: /link
    aliases: [tg, telegram]
  telegramauth:
    description: Admin command for TelegramAuth
    usage: /telegramauth reload
    permission: telegramauth.admin

permissions:
  telegramauth.admin:
    description: Allows using admin commands
    default: op
  telegramauth.bypass:
    description: Bypass Telegram authentication
    default: false`,

  'src/main/resources/config.yml': `# ============================================================
# TelegramAuth Configuration
# ============================================================

telegram:
  bot-token: "YOUR_BOT_TOKEN_HERE"
  bot-username: "your_bot_username"
  admin-chat-id: 0
  debug: false
  delay: 50

storage:
  type: SQLITE
  mysql:
    host: localhost
    port: 3306
    database: telegramauth
    username: root
    password: password
    useSSL: false
    max-pool-size: 10
    connection-timeout: 30000

security:
  code-length: 6
  code-lifetime: 300
  max-attempts: 3
  strict-mode: true
  session-timeout: 86400
  bind-to-ip: false
  kick-timeout: 120

freeze:
  movement: true
  chat: true
  commands: true
  interact: true
  item-use: true
  drop: true
  pickup: true
  inventory: true
  damage: true
  deal-damage: true
  mob-target: true
  portals: true
  sleep: true
  vehicle: true
  block-break: true
  block-place: true
  blindness-effect: true
  invisibility-effect: true

allowed-commands:
  - "/link"
  - "/tg"
  - "/telegram"
  - "/msg"
  - "/tell"
  - "/whisper"
  - "/w"
  - "/r"

messages:
  prefix: "&8[&bTelegramAuth&8] &r"
  join-frozen: "&cВы не авторизованы! Используйте &e/link &cдля привязки Telegram."
  join-frozen-title: "&c&lЗАМОРОЖЕН"
  join-frozen-subtitle: "&7Используйте /link для авторизации"
  link-code-generated: "&aВаш код для привязки: &e&l{code}\\n&aОтправьте боту &e@{bot} &aкоманду: &e/link {code}\\n&7Код действителен {time} секунд."
  link-already-bound: "&aВаш аккаунт уже привязан к Telegram!"
  link-code-expired: "&cВремя действия кода истекло."
  link-cooldown: "&cПодождите &e{time} &cсекунд."
  tg-welcome: "&aДобро пожаловать в TelegramAuth!\\n\\nДля привязки: &b/link <код>"
  tg-linked-success: "&a✅ Аккаунт успешно привязан!"
  tg-already-linked: "&cЭтот аккаунт уже привязан."
  tg-code-invalid: "&cНеверный или истёкший код."
  tg-confirm-login: "&e⚠️ Запрос входа\\nИгрок: &b{player}"
  tg-login-confirmed: "&a✅ Вход подтверждён!"
  tg-login-denied: "&c❌ Вход отклонён."
  tg-login-timeout: "&c⏰ Время подтверждения истекло."
  tg-button-confirm: "✅ Подтвердить"
  tg-button-deny: "❌ Отклонить"
  kick-not-confirmed: "&cВы не подтвердили вход!"
  kick-not-linked: "&cВы не привязали Telegram!"
  kick-denied: "&cВход был отклонён."
  admin-reload: "&aКонфигурация перезагружена!"
  admin-no-permission: "&cНет прав!"
  admin-unlinked: "&aПривязка &e{player} &aудалена."
  admin-player-not-found: "&cИгрок не найден!"

integrations:
  placeholderapi: true
  vault: true
  update-checker: true`,

  'TelegramAuthPlugin.java': `package ru.example.telegramauth;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class TelegramAuthPlugin extends JavaPlugin {

    private static TelegramAuthPlugin instance;
    private Storage storage;
    private TelegramBot telegramBot;
    private PlayerSessionManager sessionManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        sessionManager = new PlayerSessionManager(this);
        initStorage();
        initBot();
        getServer().getPluginManager().registerEvents(new FreezeListener(this), this);
        LinkCommand linkCommand = new LinkCommand(this);
        getCommand("link").setExecutor(linkCommand);
        getCommand("link").setTabCompleter(linkCommand);
        AdminCommand adminCommand = new AdminCommand(this);
        getCommand("telegramauth").setExecutor(adminCommand);
        getLogger().info("TelegramAuth has been enabled!");
    }

    @Override
    public void onDisable() {
        if (telegramBot != null) telegramBot.stopBot();
        if (storage != null) storage.close();
        if (sessionManager != null) sessionManager.clearAll();
        getLogger().info("TelegramAuth has been disabled!");
    }

    private void initStorage() {
        String type = getConfig().getString("storage.type", "SQLITE").toUpperCase();
        switch (type) {
            case "YAML": storage = new YamlStorage(this); break;
            case "MYSQL": storage = new MysqlStorage(this); break;
            default: storage = new SqliteStorage(this); break;
        }
        storage.init();
    }

    private void initBot() {
        String token = getConfig().getString("telegram.bot-token", "");
        String username = getConfig().getString("telegram.bot-username", "");
        if (token.isEmpty() || token.equals("YOUR_BOT_TOKEN_HERE")) {
            getLogger().warning("Telegram bot token is not set!");
            return;
        }
        try {
            telegramBot = new TelegramBot(this, token, username);
            telegramBot.startBot();
            getLogger().info("Telegram bot started!");
        } catch (Exception e) {
            getLogger().severe("Failed to start bot: " + e.getMessage());
        }
    }

    public void reload() {
        reloadConfig();
        if (storage != null) storage.close();
        initStorage();
        if (telegramBot != null) telegramBot.stopBot();
        initBot();
    }

    public static TelegramAuthPlugin getInstance() { return instance; }
    public Storage getStorage() { return storage; }
    public TelegramBot getTelegramBot() { return telegramBot; }
    public PlayerSessionManager getSessionManager() { return sessionManager; }

    public String getMessage(String path) {
        String prefix = getConfig().getString("messages.prefix", "&8[&bTelegramAuth&8] &r");
        String message = getConfig().getString("messages." + path, "&cNot found: " + path);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', prefix + message);
    }

    public String getRawMessage(String path) {
        String message = getConfig().getString("messages." + path, "&cNot found: " + path);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', message);
    }

    public boolean isDebugEnabled() { return getConfig().getBoolean("telegram.debug", false); }
    public void debug(String msg) { if (isDebugEnabled()) getLogger().info("[DEBUG] " + msg); }
}`,

  'PlayerSession.java': `package ru.example.telegramauth;

import java.util.UUID;

public class PlayerSession {
    private final UUID playerUUID;
    private final String playerName;
    private long loginRequestTime;
    private long confirmMessageId;
    private long chatId;
    private boolean awaitingConfirmation;
    private boolean confirmed;
    private boolean denied;

    public PlayerSession(UUID playerUUID, String playerName) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.loginRequestTime = System.currentTimeMillis();
        this.awaitingConfirmation = false;
        this.confirmed = false;
        this.denied = false;
    }

    public UUID getPlayerUUID() { return playerUUID; }
    public String getPlayerName() { return playerName; }
    public long getLoginRequestTime() { return loginRequestTime; }
    public void setLoginRequestTime(long t) { this.loginRequestTime = t; }
    public long getConfirmMessageId() { return confirmMessageId; }
    public void setConfirmMessageId(long id) { this.confirmMessageId = id; }
    public long getChatId() { return chatId; }
    public void setChatId(long chatId) { this.chatId = chatId; }
    public boolean isAwaitingConfirmation() { return awaitingConfirmation; }
    public void setAwaitingConfirmation(boolean b) { this.awaitingConfirmation = b; }
    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean b) { this.confirmed = b; }
    public boolean isDenied() { return denied; }
    public void setDenied(boolean b) { this.denied = b; }

    public boolean isTimedOut(int timeoutSeconds) {
        if (!awaitingConfirmation) return false;
        return (System.currentTimeMillis() - loginRequestTime) / 1000 >= timeoutSeconds;
    }
}`,

  'PlayerSessionManager.java': `package ru.example.telegramauth;

import org.bukkit.scheduler.BukkitRunnable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerSessionManager {
    private final TelegramAuthPlugin plugin;
    private final Map<UUID, PlayerSession> sessions;

    public PlayerSessionManager(TelegramAuthPlugin plugin) {
        this.plugin = plugin;
        this.sessions = new ConcurrentHashMap<>();
        startTimeoutChecker();
    }

    public PlayerSession getOrCreateSession(UUID uuid, String name) {
        return sessions.computeIfAbsent(uuid, k -> new PlayerSession(uuid, name));
    }

    public PlayerSession getSession(UUID uuid) { return sessions.get(uuid); }
    public void removeSession(UUID uuid) { sessions.remove(uuid); }
    public boolean hasSession(UUID uuid) { return sessions.containsKey(uuid); }
    public void clearAll() { sessions.clear(); }
    public Map<UUID, PlayerSession> getAllSessions() { return sessions; }

    private void startTimeoutChecker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                int timeout = plugin.getConfig().getInt("security.kick-timeout", 120);
                for (Map.Entry<UUID, PlayerSession> entry : sessions.entrySet()) {
                    PlayerSession session = entry.getValue();
                    if (session.isAwaitingConfirmation() && session.isTimedOut(timeout)) {
                        org.bukkit.entity.Player player = plugin.getServer().getPlayer(entry.getKey());
                        if (player != null && player.isOnline()) {
                            player.kickPlayer(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                plugin.getRawMessage("kick-not-confirmed")));
                        }
                        TelegramBot bot = plugin.getTelegramBot();
                        if (bot != null && session.getChatId() != 0) {
                            bot.sendMessage(session.getChatId(),
                                org.bukkit.ChatColor.stripColor(
                                    org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                        plugin.getRawMessage("tg-login-timeout"))));
                        }
                        sessions.remove(entry.getKey());
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }
}`,

  'Storage.java': `package ru.example.telegramauth;

import java.util.UUID;

public interface Storage {
    void init();
    void close();
    boolean isLinked(UUID uuid);
    long getTelegramId(UUID uuid);
    UUID getUUID(long telegramId);
    void saveLink(UUID uuid, long telegramId);
    void removeLink(UUID uuid);
    void saveCode(String code, UUID uuid, long expiresAt);
    UUID getCodeUUID(String code);
    boolean isCodeValid(String code);
    void removeCode(String code);
    void cleanExpiredCodes();
}`,

  'YamlStorage.java': `package ru.example.telegramauth;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class YamlStorage implements Storage {
    private final TelegramAuthPlugin plugin;
    private File linksFile, codesFile;
    private YamlConfiguration linksConfig, codesConfig;

    public YamlStorage(TelegramAuthPlugin plugin) { this.plugin = plugin; }

    @Override
    public void init() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();
        linksFile = new File(dataFolder, "links.yml");
        codesFile = new File(dataFolder, "codes.yml");
        try {
            if (!linksFile.exists()) linksFile.createNewFile();
            if (!codesFile.exists()) codesFile.createNewFile();
        } catch (IOException e) { plugin.getLogger().severe("Failed to create files: " + e.getMessage()); }
        linksConfig = YamlConfiguration.loadConfiguration(linksFile);
        codesConfig = YamlConfiguration.loadConfiguration(codesFile);
    }

    @Override public void close() { saveLinks(); saveCodes(); }

    @Override public boolean isLinked(UUID uuid) { return linksConfig.contains("links." + uuid.toString()); }

    @Override public long getTelegramId(UUID uuid) { return linksConfig.getLong("links." + uuid.toString(), 0L); }

    @Override public UUID getUUID(long telegramId) {
        if (linksConfig.getConfigurationSection("links") == null) return null;
        for (String key : linksConfig.getConfigurationSection("links").getKeys(false)) {
            if (linksConfig.getLong("links." + key) == telegramId) return UUID.fromString(key);
        }
        return null;
    }

    @Override public void saveLink(UUID uuid, long telegramId) {
        linksConfig.set("links." + uuid.toString(), telegramId); saveLinks();
    }

    @Override public void removeLink(UUID uuid) { linksConfig.set("links." + uuid.toString(), null); saveLinks(); }

    @Override public void saveCode(String code, UUID uuid, long expiresAt) {
        codesConfig.set("codes." + code + ".uuid", uuid.toString());
        codesConfig.set("codes." + code + ".expires", expiresAt); saveCodes();
    }

    @Override public UUID getCodeUUID(String code) {
        if (!isCodeValid(code)) return null;
        String s = codesConfig.getString("codes." + code + ".uuid");
        return s != null ? UUID.fromString(s) : null;
    }

    @Override public boolean isCodeValid(String code) {
        if (!codesConfig.contains("codes." + code)) return false;
        return System.currentTimeMillis() < codesConfig.getLong("codes." + code + ".expires", 0);
    }

    @Override public void removeCode(String code) { codesConfig.set("codes." + code, null); saveCodes(); }

    @Override public void cleanExpiredCodes() {
        if (codesConfig.getConfigurationSection("codes") == null) return;
        long now = System.currentTimeMillis();
        for (String code : codesConfig.getConfigurationSection("codes").getKeys(false)) {
            if (now >= codesConfig.getLong("codes." + code + ".expires", 0))
                codesConfig.set("codes." + code, null);
        }
        saveCodes();
    }

    private void saveLinks() { try { linksConfig.save(linksFile); } catch (IOException e) { plugin.getLogger().severe(e.getMessage()); } }
    private void saveCodes() { try { codesConfig.save(codesFile); } catch (IOException e) { plugin.getLogger().severe(e.getMessage()); } }
}`,

  'SqliteStorage.java': `package ru.example.telegramauth;

import java.io.File;
import java.sql.*;
import java.util.UUID;

public class SqliteStorage implements Storage {
    private final TelegramAuthPlugin plugin;
    private Connection connection;

    public SqliteStorage(TelegramAuthPlugin plugin) { this.plugin = plugin; }

    @Override public void init() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + new File(dataFolder, "data.db").getAbsolutePath());
            createTables();
        } catch (Exception e) { plugin.getLogger().severe("SQLite init failed: " + e.getMessage()); }
    }

    private void createTables() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS links (uuid TEXT PRIMARY KEY, telegram_id INTEGER NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS codes (code TEXT PRIMARY KEY, uuid TEXT NOT NULL, expires INTEGER NOT NULL)");
        }
    }

    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + new File(plugin.getDataFolder(), "data.db").getAbsolutePath());
        }
        return connection;
    }

    @Override public void close() { try { if (connection != null && !connection.isClosed()) connection.close(); } catch (SQLException e) {} }

    @Override public boolean isLinked(UUID uuid) {
        try { PreparedStatement ps = getConnection().prepareStatement("SELECT telegram_id FROM links WHERE uuid = ?");
            ps.setString(1, uuid.toString()); ResultSet rs = ps.executeQuery(); boolean f = rs.next(); rs.close(); ps.close(); return f;
        } catch (SQLException e) { return false; }
    }

    @Override public long getTelegramId(UUID uuid) {
        try { PreparedStatement ps = getConnection().prepareStatement("SELECT telegram_id FROM links WHERE uuid = ?");
            ps.setString(1, uuid.toString()); ResultSet rs = ps.executeQuery(); long id = rs.next() ? rs.getLong("telegram_id") : 0; rs.close(); ps.close(); return id;
        } catch (SQLException e) { return 0; }
    }

    @Override public UUID getUUID(long telegramId) {
        try { PreparedStatement ps = getConnection().prepareStatement("SELECT uuid FROM links WHERE telegram_id = ?");
            ps.setLong(1, telegramId); ResultSet rs = ps.executeQuery(); UUID u = rs.next() ? UUID.fromString(rs.getString("uuid")) : null; rs.close(); ps.close(); return u;
        } catch (SQLException e) { return null; }
    }

    @Override public void saveLink(UUID uuid, long telegramId) {
        try { PreparedStatement ps = getConnection().prepareStatement("INSERT OR REPLACE INTO links (uuid, telegram_id) VALUES (?, ?)");
            ps.setString(1, uuid.toString()); ps.setLong(2, telegramId); ps.executeUpdate(); ps.close();
        } catch (SQLException e) {}
    }

    @Override public void removeLink(UUID uuid) {
        try { PreparedStatement ps = getConnection().prepareStatement("DELETE FROM links WHERE uuid = ?");
            ps.setString(1, uuid.toString()); ps.executeUpdate(); ps.close();
        } catch (SQLException e) {}
    }

    @Override public void saveCode(String code, UUID uuid, long expiresAt) {
        try { PreparedStatement ps = getConnection().prepareStatement("INSERT OR REPLACE INTO codes (code, uuid, expires) VALUES (?, ?, ?)");
            ps.setString(1, code); ps.setString(2, uuid.toString()); ps.setLong(3, expiresAt); ps.executeUpdate(); ps.close();
        } catch (SQLException e) {}
    }

    @Override public UUID getCodeUUID(String code) {
        if (!isCodeValid(code)) return null;
        try { PreparedStatement ps = getConnection().prepareStatement("SELECT uuid FROM codes WHERE code = ?");
            ps.setString(1, code); ResultSet rs = ps.executeQuery(); UUID u = rs.next() ? UUID.fromString(rs.getString("uuid")) : null; rs.close(); ps.close(); return u;
        } catch (SQLException e) { return null; }
    }

    @Override public boolean isCodeValid(String code) {
        try { PreparedStatement ps = getConnection().prepareStatement("SELECT expires FROM codes WHERE code = ?");
            ps.setString(1, code); ResultSet rs = ps.executeQuery(); boolean v = rs.next() && System.currentTimeMillis() < rs.getLong("expires"); rs.close(); ps.close(); return v;
        } catch (SQLException e) { return false; }
    }

    @Override public void removeCode(String code) {
        try { PreparedStatement ps = getConnection().prepareStatement("DELETE FROM codes WHERE code = ?");
            ps.setString(1, code); ps.executeUpdate(); ps.close();
        } catch (SQLException e) {}
    }

    @Override public void cleanExpiredCodes() {
        try { PreparedStatement ps = getConnection().prepareStatement("DELETE FROM codes WHERE expires < ?");
            ps.setLong(1, System.currentTimeMillis()); ps.executeUpdate(); ps.close();
        } catch (SQLException e) {}
    }
}`,

  'MysqlStorage.java': `package ru.example.telegramauth;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.*;
import java.util.UUID;

public class MysqlStorage implements Storage {
    private final TelegramAuthPlugin plugin;
    private HikariDataSource dataSource;

    public MysqlStorage(TelegramAuthPlugin plugin) { this.plugin = plugin; }

    @Override public void init() {
        try {
            String host = plugin.getConfig().getString("storage.mysql.host", "localhost");
            int port = plugin.getConfig().getInt("storage.mysql.port", 3306);
            String db = plugin.getConfig().getString("storage.mysql.database", "telegramauth");
            String user = plugin.getConfig().getString("storage.mysql.username", "root");
            String pass = plugin.getConfig().getString("storage.mysql.password", "password");
            boolean ssl = plugin.getConfig().getBoolean("storage.mysql.useSSL", false);

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=" + ssl + "&autoReconnect=true");
            config.setUsername(user); config.setPassword(pass);
            config.setMaximumPoolSize(plugin.getConfig().getInt("storage.mysql.max-pool-size", 10));
            config.setConnectionTimeout(plugin.getConfig().getLong("storage.mysql.connection-timeout", 30000));
            config.setPoolName("TelegramAuth-HikariPool");
            dataSource = new HikariDataSource(config);
            createTables();
        } catch (Exception e) { plugin.getLogger().severe("MySQL init failed: " + e.getMessage()); }
    }

    private void createTables() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS ta_links (uuid VARCHAR(36) PRIMARY KEY, telegram_id BIGINT NOT NULL, INDEX idx_tid (telegram_id))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS ta_codes (code VARCHAR(10) PRIMARY KEY, uuid VARCHAR(36) NOT NULL, expires BIGINT NOT NULL, INDEX idx_exp (expires))");
        }
    }

    @Override public void close() { if (dataSource != null && !dataSource.isClosed()) dataSource.close(); }

    @Override public boolean isLinked(UUID uuid) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT telegram_id FROM ta_links WHERE uuid = ?")) {
            ps.setString(1, uuid.toString()); ResultSet rs = ps.executeQuery(); boolean f = rs.next(); rs.close(); return f;
        } catch (SQLException e) { return false; }
    }

    @Override public long getTelegramId(UUID uuid) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT telegram_id FROM ta_links WHERE uuid = ?")) {
            ps.setString(1, uuid.toString()); ResultSet rs = ps.executeQuery(); long id = rs.next() ? rs.getLong("telegram_id") : 0; rs.close(); return id;
        } catch (SQLException e) { return 0; }
    }

    @Override public UUID getUUID(long telegramId) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT uuid FROM ta_links WHERE telegram_id = ?")) {
            ps.setLong(1, telegramId); ResultSet rs = ps.executeQuery(); UUID u = rs.next() ? UUID.fromString(rs.getString("uuid")) : null; rs.close(); return u;
        } catch (SQLException e) { return null; }
    }

    @Override public void saveLink(UUID uuid, long telegramId) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("INSERT INTO ta_links (uuid, telegram_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE telegram_id = ?")) {
            ps.setString(1, uuid.toString()); ps.setLong(2, telegramId); ps.setLong(3, telegramId); ps.executeUpdate();
        } catch (SQLException e) {}
    }

    @Override public void removeLink(UUID uuid) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM ta_links WHERE uuid = ?")) {
            ps.setString(1, uuid.toString()); ps.executeUpdate();
        } catch (SQLException e) {}
    }

    @Override public void saveCode(String code, UUID uuid, long expiresAt) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("INSERT INTO ta_codes (code, uuid, expires) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE uuid = ?, expires = ?")) {
            ps.setString(1, code); ps.setString(2, uuid.toString()); ps.setLong(3, expiresAt);
            ps.setString(4, uuid.toString()); ps.setLong(5, expiresAt); ps.executeUpdate();
        } catch (SQLException e) {}
    }

    @Override public UUID getCodeUUID(String code) {
        if (!isCodeValid(code)) return null;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT uuid FROM ta_codes WHERE code = ?")) {
            ps.setString(1, code); ResultSet rs = ps.executeQuery(); UUID u = rs.next() ? UUID.fromString(rs.getString("uuid")) : null; rs.close(); return u;
        } catch (SQLException e) { return null; }
    }

    @Override public boolean isCodeValid(String code) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT expires FROM ta_codes WHERE code = ?")) {
            ps.setString(1, code); ResultSet rs = ps.executeQuery(); boolean v = rs.next() && System.currentTimeMillis() < rs.getLong("expires"); rs.close(); return v;
        } catch (SQLException e) { return false; }
    }

    @Override public void removeCode(String code) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM ta_codes WHERE code = ?")) {
            ps.setString(1, code); ps.executeUpdate();
        } catch (SQLException e) {}
    }

    @Override public void cleanExpiredCodes() {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM ta_codes WHERE expires < ?")) {
            ps.setLong(1, System.currentTimeMillis()); ps.executeUpdate();
        } catch (SQLException e) {}
    }
}`,

  'TelegramBot.java': `package ru.example.telegramauth;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.bots.DefaultBotSession;
import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.meta.TelegramApiException;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import java.util.ArrayList;
import java.util.List;

public class TelegramBot extends TelegramLongPollingBot {
    private final TelegramAuthPlugin plugin;
    private final String botToken;
    private final String botUsername;
    private BotCommandHandler commandHandler;
    private TelegramBotsApi botsApi;

    public TelegramBot(TelegramAuthPlugin plugin, String botToken, String botUsername) {
        super();
        this.plugin = plugin;
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.commandHandler = new BotCommandHandler(plugin, this);
    }

    @Override public String getBotToken() { return botToken; }
    @Override public String getBotUsername() { return botUsername; }

    public void startBot() {
        try {
            botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(this);
        } catch (TelegramApiException e) {
            plugin.getLogger().severe("Failed to start bot: " + e.getMessage());
        }
    }

    public void stopBot() { try { this.onClosing(); } catch (Exception e) {} }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                String text = update.getMessage().getText();
                long chatId = update.getMessage().getChatId();
                if (text.startsWith("/start")) commandHandler.handleStart(chatId);
                else if (text.startsWith("/link")) {
                    String code = text.length() > 6 ? text.substring(6).trim() : "";
                    commandHandler.handleLinkCommand(chatId, code);
                } else if (text.startsWith("/help")) commandHandler.handleHelp(chatId);
                else commandHandler.handleUnknown(chatId);
            } else if (update.hasCallbackQuery()) {
                String data = update.getCallbackQuery().getData();
                long chatId = update.getCallbackQuery().getMessage().getChatId();
                int msgId = update.getCallbackQuery().getMessage().getMessageId();
                String from = update.getCallbackQuery().getFrom().getFirstName();
                if ("confirm_login".equals(data)) commandHandler.handleConfirm(chatId, msgId, from);
                else if ("deny_login".equals(data)) commandHandler.handleDeny(chatId, msgId, from);
            }
        } catch (Exception e) { plugin.getLogger().severe("Error: " + e.getMessage()); }
    }

    public void sendMessage(long chatId, String text) { sendMessage(chatId, text, null); }

    public void sendMessage(long chatId, String text, InlineKeyboardMarkup keyboard) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                SendMessage msg = new SendMessage();
                msg.setChatId(String.valueOf(chatId));
                msg.setText(text);
                msg.setParseMode("HTML");
                if (keyboard != null) msg.setReplyMarkup(keyboard);
                execute(msg);
            } catch (TelegramApiException e) { plugin.getLogger().severe("Send failed: " + e.getMessage()); }
        });
    }

    public int sendMessageWithId(long chatId, String text, InlineKeyboardMarkup keyboard) {
        try {
            SendMessage msg = new SendMessage();
            msg.setChatId(String.valueOf(chatId));
            msg.setText(text);
            msg.setParseMode("HTML");
            if (keyboard != null) msg.setReplyMarkup(keyboard);
            return execute(msg).getMessageId();
        } catch (TelegramApiException e) { return 0; }
    }

    public void editMessage(long chatId, int messageId, String newText, InlineKeyboardMarkup keyboard) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                EditMessageText em = new EditMessageText();
                em.setChatId(String.valueOf(chatId));
                em.setMessageId(messageId);
                em.setText(newText);
                em.setParseMode("HTML");
                if (keyboard != null) em.setReplyMarkup(keyboard);
                execute(em);
            } catch (TelegramApiException e) {}
        });
    }

    public InlineKeyboardMarkup createConfirmKeyboard() {
        InlineKeyboardButton confirm = new InlineKeyboardButton();
        confirm.setText(plugin.getRawMessage("tg-button-confirm"));
        confirm.setCallbackData("confirm_login");
        InlineKeyboardButton deny = new InlineKeyboardButton();
        deny.setText(plugin.getRawMessage("tg-button-deny"));
        deny.setCallbackData("deny_login");
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(confirm); row.add(deny);
        List<List<InlineKeyboardButton>> kb = new ArrayList<>();
        kb.add(row);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(kb);
        return markup;
    }

    public BotCommandHandler getCommandHandler() { return commandHandler; }
}`,

  'BotCommandHandler.java': `package ru.example.telegramauth;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import java.util.UUID;

public class BotCommandHandler {
    private final TelegramAuthPlugin plugin;
    private final TelegramBot bot;

    public BotCommandHandler(TelegramAuthPlugin plugin, TelegramBot bot) {
        this.plugin = plugin;
        this.bot = bot;
    }

    public void handleStart(long chatId) {
        bot.sendMessage(chatId, ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', plugin.getRawMessage("tg-welcome"))));
    }

    public void handleLinkCommand(long chatId, String code) {
        if (code.isEmpty()) { handleStart(chatId); return; }
        if (plugin.getStorage().getUUID(chatId) != null) {
            bot.sendMessage(chatId, ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', plugin.getRawMessage("tg-already-linked"))));
            return;
        }
        if (!plugin.getStorage().isCodeValid(code)) {
            bot.sendMessage(chatId, ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', plugin.getRawMessage("tg-code-invalid"))));
            return;
        }
        UUID playerUUID = plugin.getStorage().getCodeUUID(code);
        if (playerUUID == null) {
            bot.sendMessage(chatId, ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', plugin.getRawMessage("tg-code-invalid"))));
            return;
        }
        plugin.getStorage().saveLink(playerUUID, chatId);
        plugin.getStorage().removeCode(code);
        bot.sendMessage(chatId, ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', plugin.getRawMessage("tg-linked-success"))));
    }

    public void handleConfirm(long chatId, int messageId, String fromName) {
        UUID playerUUID = plugin.getStorage().getUUID(chatId);
        if (playerUUID == null) { bot.editMessage(chatId, messageId, "❌ Аккаунт не привязан!", null); return; }
        PlayerSession session = plugin.getSessionManager().getSession(playerUUID);
        if (session == null || !session.isAwaitingConfirmation()) { bot.editMessage(chatId, messageId, "⏰ Запрос неактуален.", null); return; }
        session.setConfirmed(true);
        session.setAwaitingConfirmation(false);
        bot.editMessage(chatId, messageId, "✅ <b>Вход подтверждён!</b>\\nИгрок: " + session.getPlayerName(), null);
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null && player.isOnline()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                FreezeListener.unfreezePlayer(plugin, player);
                plugin.getSessionManager().removeSession(playerUUID);
            });
        }
    }

    public void handleDeny(long chatId, int messageId, String fromName) {
        UUID playerUUID = plugin.getStorage().getUUID(chatId);
        if (playerUUID == null) { bot.editMessage(chatId, messageId, "❌ Аккаунт не привязан!", null); return; }
        PlayerSession session = plugin.getSessionManager().getSession(playerUUID);
        if (session == null) { bot.editMessage(chatId, messageId, "⏰ Запрос неактуален.", null); return; }
        session.setDenied(true);
        session.setAwaitingConfirmation(false);
        bot.editMessage(chatId, messageId, "❌ <b>Вход отклонён!</b>\\nИгрок: " + session.getPlayerName(), null);
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null && player.isOnline()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.kickPlayer(ChatColor.translateAlternateColorCodes('&', plugin.getRawMessage("kick-denied")));
                plugin.getSessionManager().removeSession(playerUUID);
            });
        }
    }

    public void handleHelp(long chatId) {
        bot.sendMessage(chatId, "<b>📋 Команды:</b>\\n/start - Приветствие\\n/link <код> - Привязка\\n/help - Справка");
    }

    public void handleUnknown(long chatId) {
        bot.sendMessage(chatId, "❓ Неизвестная команда. /help");
    }

    public void sendLoginConfirmation(PlayerSession session) {
        String msg = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',
            plugin.getRawMessage("tg-confirm-login").replace("{player}", session.getPlayerName())));
        int msgId = bot.sendMessageWithId(session.getChatId(), msg, bot.createConfirmKeyboard());
        session.setConfirmMessageId(msgId);
        session.setAwaitingConfirmation(true);
        session.setLoginRequestTime(System.currentTimeMillis());
    }
}`,

  'LinkCommand.java': `package ru.example.telegramauth;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.*;

public class LinkCommand implements CommandExecutor, TabCompleter {
    private final TelegramAuthPlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Random random = new Random();

    public LinkCommand(TelegramAuthPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage(ChatColor.RED + "Players only!"); return true; }
        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();
        if (plugin.getStorage().isLinked(uuid)) { player.sendMessage(plugin.getMessage("link-already-bound")); return true; }
        if (cooldowns.containsKey(uuid)) {
            long elapsed = (System.currentTimeMillis() - cooldowns.get(uuid)) / 1000;
            if (elapsed < 10) {
                player.sendMessage(plugin.getMessage("link-cooldown").replace("{time}", String.valueOf(10 - elapsed)));
                return true;
            }
        }
        int len = plugin.getConfig().getInt("security.code-length", 6);
        String code = generateCode(len);
        int lifetime = plugin.getConfig().getInt("security.code-lifetime", 300);
        plugin.getStorage().saveCode(code, uuid, System.currentTimeMillis() + (lifetime * 1000L));
        cooldowns.put(uuid, System.currentTimeMillis());
        String botUsername = plugin.getConfig().getString("telegram.bot-username", "bot");
        player.sendMessage(plugin.getMessage("link-code-generated").replace("{code}", code).replace("{bot}", botUsername).replace("{time}", String.valueOf(lifetime)));
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> plugin.getStorage().removeCode(code), lifetime * 20L);
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) { return Collections.emptyList(); }

    private String generateCode(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) sb.append(random.nextInt(10));
        return sb.toString();
    }
}`,

  'FreezeListener.java': `package ru.example.telegramauth;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.UUID;

public class FreezeListener implements Listener {
    private final TelegramAuthPlugin plugin;

    public FreezeListener(TelegramAuthPlugin plugin) { this.plugin = plugin; }

    private boolean shouldFreeze(Player player) {
        if (player.hasPermission("telegramauth.bypass")) return false;
        UUID uuid = player.getUniqueId();
        if (!plugin.getStorage().isLinked(uuid)) return true;
        PlayerSession session = plugin.getSessionManager().getSession(uuid);
        return session != null && session.isAwaitingConfirmation() && !session.isConfirmed();
    }

    public static void freezePlayer(TelegramAuthPlugin plugin, Player player) {
        player.sendMessage(plugin.getMessage("join-frozen"));
        player.sendTitle(
            ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.join-frozen-title", "&c&lЗАМОРОЖЕН")),
            ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.join-frozen-subtitle", "&7/link")),
            10, 70, 20);
        if (plugin.getConfig().getBoolean("freeze.blindness-effect", true))
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false));
        if (plugin.getConfig().getBoolean("freeze.invisibility-effect", true))
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
        if (plugin.getStorage().isLinked(player.getUniqueId())) {
            long chatId = plugin.getStorage().getTelegramId(player.getUniqueId());
            if (chatId != 0 && plugin.getTelegramBot() != null) {
                PlayerSession session = plugin.getSessionManager().getOrCreateSession(player.getUniqueId(), player.getName());
                session.setChatId(chatId);
                plugin.getTelegramBot().getCommandHandler().sendLoginConfirmation(session);
            }
        }
    }

    public static void unfreezePlayer(TelegramAuthPlugin plugin, Player player) {
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.setAllowFlight(false);
        player.setFlying(false);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && shouldFreeze(p)) freezePlayer(plugin, p);
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent event) {
        if (!shouldFreeze(event.getPlayer()) || !plugin.getConfig().getBoolean("freeze.movement", true)) return;
        Location to = event.getTo();
        if (to != null && event.getFrom().distance(to) > 0.1) event.getPlayer().teleport(event.getFrom());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!shouldFreeze(event.getPlayer()) || !plugin.getConfig().getBoolean("freeze.chat", true)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!shouldFreeze(event.getPlayer()) || !plugin.getConfig().getBoolean("freeze.commands", true)) return;
        String msg = event.getMessage().toLowerCase();
        for (String cmd : plugin.getConfig().getStringList("allowed-commands")) {
            if (msg.startsWith(cmd.toLowerCase())) return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (!shouldFreeze(event.getPlayer()) || !plugin.getConfig().getBoolean("freeze.interact", true)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        if (!shouldFreeze(event.getPlayer()) || !plugin.getConfig().getBoolean("freeze.block-break", true)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        if (!shouldFreeze(event.getPlayer()) || !plugin.getConfig().getBoolean("freeze.block-place", true)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent event) {
        if (!shouldFreeze(event.getPlayer()) || !plugin.getConfig().getBoolean("freeze.drop", true)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player) || !shouldFreeze((Player) event.getEntity())) return;
        if (!plugin.getConfig().getBoolean("freeze.pickup", true)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventory(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player) || !shouldFreeze((Player) event.getPlayer())) return;
        if (!plugin.getConfig().getBoolean("freeze.inventory", true)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player) || !shouldFreeze((Player) event.getEntity())) return;
        if (!plugin.getConfig().getBoolean("freeze.damage", true)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !shouldFreeze((Player) event.getDamager())) return;
        if (!plugin.getConfig().getBoolean("freeze.deal-damage", true)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMobTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player) || !shouldFreeze((Player) event.getTarget())) return;
        if (!plugin.getConfig().getBoolean("freeze.mob-target", true)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPortal(PlayerPortalEvent event) {
        if (!shouldFreeze(event.getPlayer()) || !plugin.getConfig().getBoolean("freeze.portals", true)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBed(PlayerBedEnterEvent event) {
        if (!shouldFreeze(event.getPlayer()) || !plugin.getConfig().getBoolean("freeze.sleep", true)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onVehicle(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player) || !shouldFreeze((Player) event.getEntered())) return;
        if (!plugin.getConfig().getBoolean("freeze.vehicle", true)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!shouldFreeze(event.getPlayer()) || !plugin.getConfig().getBoolean("freeze.item-use", true)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { plugin.getSessionManager().removeSession(event.getPlayer().getUniqueId()); }
}`,

  'AdminCommand.java': `package ru.example.telegramauth;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AdminCommand implements CommandExecutor {
    private final TelegramAuthPlugin plugin;

    public AdminCommand(TelegramAuthPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("telegramauth.admin")) { sender.sendMessage(plugin.getMessage("admin-no-permission")); return true; }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "=== TelegramAuth ===");
            sender.sendMessage(ChatColor.YELLOW + "/telegramauth reload");
            sender.sendMessage(ChatColor.YELLOW + "/telegramauth unlink <player>");
            sender.sendMessage(ChatColor.YELLOW + "/telegramauth status");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload": plugin.reload(); sender.sendMessage(plugin.getMessage("admin-reload")); break;
            case "unlink":
                if (args.length < 2) { sender.sendMessage(ChatColor.RED + "Usage: /telegramauth unlink <player>"); return true; }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) { sender.sendMessage(plugin.getMessage("admin-player-not-found")); return true; }
                plugin.getStorage().removeLink(target.getUniqueId());
                plugin.getSessionManager().removeSession(target.getUniqueId());
                sender.sendMessage(plugin.getMessage("admin-unlinked").replace("{player}", args[1]));
                FreezeListener.freezePlayer(plugin, target);
                break;
            case "status":
                sender.sendMessage(ChatColor.GOLD + "=== Status ===");
                sender.sendMessage(ChatColor.YELLOW + "Storage: " + plugin.getConfig().getString("storage.type"));
                sender.sendMessage(ChatColor.YELLOW + "Bot: " + (plugin.getTelegramBot() != null ? "Running" : "Stopped"));
                sender.sendMessage(ChatColor.YELLOW + "Sessions: " + plugin.getSessionManager().getAllSessions().size());
                break;
            default: sender.sendMessage(ChatColor.RED + "Unknown subcommand."); break;
        }
        return true;
    }
}`,

  'README.md': `# TelegramAuth - Minecraft Plugin

Плагин аутентификации через Telegram для Paper 1.16.5.

## Сборка
\`\`\`bash
gradle shadowJar
\`\`\`

## Установка
1. Поместите JAR в plugins/
2. Настройте config.yml (bot-token, bot-username)
3. Перезапустите сервер

## Команды
- \`/link\` — получить код привязки
- \`/telegramauth reload\` — перезагрузить конфиг
- \`/telegramauth unlink <player>\` — отвязать игрока
`
}

function App() {
  const [selectedFile, setSelectedFile] = useState<string>('build.gradle')
  const [copied, setCopied] = useState(false)

  const fileTree = [
    { name: 'build.gradle', path: 'build.gradle' },
    { name: 'settings.gradle', path: 'settings.gradle' },
    { name: '.github/workflows/build.yml', path: '.github/workflows/build.yml' },
    { name: 'plugin.yml', path: 'src/main/resources/plugin.yml' },
    { name: 'config.yml', path: 'src/main/resources/config.yml' },
    { name: 'TelegramAuthPlugin.java', path: 'TelegramAuthPlugin.java' },
    { name: 'PlayerSession.java', path: 'PlayerSession.java' },
    { name: 'PlayerSessionManager.java', path: 'PlayerSessionManager.java' },
    { name: 'Storage.java', path: 'Storage.java' },
    { name: 'YamlStorage.java', path: 'YamlStorage.java' },
    { name: 'SqliteStorage.java', path: 'SqliteStorage.java' },
    { name: 'MysqlStorage.java', path: 'MysqlStorage.java' },
    { name: 'TelegramBot.java', path: 'TelegramBot.java' },
    { name: 'BotCommandHandler.java', path: 'BotCommandHandler.java' },
    { name: 'LinkCommand.java', path: 'LinkCommand.java' },
    { name: 'FreezeListener.java', path: 'FreezeListener.java' },
    { name: 'AdminCommand.java', path: 'AdminCommand.java' },
    { name: 'README.md', path: 'README.md' },
  ]

  const handleCopy = () => {
    navigator.clipboard.writeText(files[selectedFile] || '')
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  const getLanguage = (file: string) => {
    if (file.endsWith('.java')) return 'java'
    if (file.endsWith('.yml')) return 'yaml'
    if (file.endsWith('.gradle')) return 'gradle'
    if (file.endsWith('.md')) return 'markdown'
    return 'text'
  }

  return (
    <div className="min-h-screen bg-gray-900 text-gray-100">
      {/* Header */}
      <header className="bg-gradient-to-r from-blue-900 via-indigo-900 to-purple-900 border-b border-gray-700">
        <div className="max-w-7xl mx-auto px-4 py-6">
          <div className="flex items-center gap-4">
            <div className="text-4xl">🔐</div>
            <div>
              <h1 className="text-3xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-blue-400 to-purple-400">
                TelegramAuth
              </h1>
              <p className="text-gray-400 text-sm mt-1">
                Плагин аутентификации через Telegram для Paper 1.16.5
              </p>
            </div>
          </div>
          <div className="flex gap-3 mt-4 flex-wrap">
            <span className="px-3 py-1 bg-green-900/50 text-green-300 rounded-full text-xs font-medium border border-green-700">
              Paper 1.16.5
            </span>
            <span className="px-3 py-1 bg-blue-900/50 text-blue-300 rounded-full text-xs font-medium border border-blue-700">
              Java 8 bytecode
            </span>
            <span className="px-3 py-1 bg-purple-900/50 text-purple-300 rounded-full text-xs font-medium border border-purple-700">
              Telegram Bot API
            </span>
            <span className="px-3 py-1 bg-orange-900/50 text-orange-300 rounded-full text-xs font-medium border border-orange-700">
              SQLite / MySQL / YAML
            </span>
          </div>
        </div>
      </header>

      <div className="max-w-7xl mx-auto px-4 py-6 flex flex-col lg:flex-row gap-6">
        {/* File tree */}
        <aside className="lg:w-72 shrink-0">
          <div className="bg-gray-800 rounded-xl border border-gray-700 overflow-hidden sticky top-6">
            <div className="px-4 py-3 border-b border-gray-700 bg-gray-800/50">
              <h2 className="text-sm font-semibold text-gray-300 flex items-center gap-2">
                📁 Структура проекта
              </h2>
            </div>
            <nav className="p-2 max-h-[70vh] overflow-y-auto">
              {fileTree.map((file) => (
                <button
                  key={file.path}
                  onClick={() => setSelectedFile(file.path)}
                  className={`w-full text-left px-3 py-2 rounded-lg text-sm transition-all ${
                    selectedFile === file.path
                      ? 'bg-blue-600/20 text-blue-300 border border-blue-600/30'
                      : 'text-gray-400 hover:bg-gray-700/50 hover:text-gray-200'
                  }`}
                >
                  <span className="flex items-center gap-2">
                    {file.name.endsWith('.java') ? '☕' :
                     file.name.endsWith('.yml') ? '📋' :
                     file.name.endsWith('.gradle') ? '⚙️' :
                     file.name.endsWith('.md') ? '📖' : '📄'}
                    <span className="truncate">{file.name}</span>
                  </span>
                </button>
              ))}
            </nav>
          </div>
        </aside>

        {/* Code viewer */}
        <main className="flex-1 min-w-0">
          <div className="bg-gray-800 rounded-xl border border-gray-700 overflow-hidden">
            {/* File header */}
            <div className="flex items-center justify-between px-4 py-3 border-b border-gray-700 bg-gray-800/50">
              <div className="flex items-center gap-2">
                <span className="text-sm font-mono text-gray-300">
                  TelegramAuth/{selectedFile}
                </span>
                <span className="px-2 py-0.5 bg-gray-700 rounded text-xs text-gray-400">
                  {getLanguage(selectedFile)}
                </span>
              </div>
              <button
                onClick={handleCopy}
                className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
                  copied
                    ? 'bg-green-600 text-white'
                    : 'bg-gray-700 text-gray-300 hover:bg-gray-600'
                }`}
              >
                {copied ? '✓ Скопировано!' : '📋 Копировать'}
              </button>
            </div>
            {/* Code content */}
            <div className="overflow-x-auto">
              <pre className="p-4 text-sm leading-relaxed">
                <code className="text-gray-300 font-mono whitespace-pre">
                  {files[selectedFile]}
                </code>
              </pre>
            </div>
          </div>

          {/* Info cards */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mt-6">
            <div className="bg-gray-800 rounded-xl border border-gray-700 p-4">
              <h3 className="text-sm font-semibold text-blue-300 mb-2">🔒 Заморозка</h3>
              <p className="text-xs text-gray-400">
                Полная блокировка: движение, чат, команды, блоки, предметы, урон, порталы, транспорт
              </p>
            </div>
            <div className="bg-gray-800 rounded-xl border border-gray-700 p-4">
              <h3 className="text-sm font-semibold text-purple-300 mb-2">📱 Telegram</h3>
              <p className="text-xs text-gray-400">
                Long-polling бот с inline-кнопками подтверждения входа
              </p>
            </div>
            <div className="bg-gray-800 rounded-xl border border-gray-700 p-4">
              <h3 className="text-sm font-semibold text-green-300 mb-2">💾 Хранилище</h3>
              <p className="text-xs text-gray-400">
                YAML, SQLite или MySQL — переключение через config.yml
              </p>
            </div>
          </div>
        </main>
      </div>

      {/* Footer */}
      <footer className="border-t border-gray-700 mt-12">
        <div className="max-w-7xl mx-auto px-4 py-6 text-center text-sm text-gray-500">
          TelegramAuth Plugin • Paper 1.16.5 • Java 8 Bytecode • Gradle + ShadowJar
        </div>
      </footer>
    </div>
  )
}

export default App
