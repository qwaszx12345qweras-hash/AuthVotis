package ru.example.telegramauth;

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

        // Save default config
        saveDefaultConfig();

        // Initialize session manager
        sessionManager = new PlayerSessionManager(this);

        // Initialize storage
        initStorage();

        // Initialize and start Telegram bot
        initBot();

        // Register listeners
        getServer().getPluginManager().registerEvents(new FreezeListener(this), this);

        // Register commands
        LinkCommand linkCommand = new LinkCommand(this);
        getCommand("link").setExecutor(linkCommand);
        getCommand("link").setTabCompleter(linkCommand);

        AdminCommand adminCommand = new AdminCommand(this);
        getCommand("telegramauth").setExecutor(adminCommand);

        getLogger().info("TelegramAuth has been enabled!");
        getLogger().info("Storage type: " + getConfig().getString("storage.type", "SQLITE"));
    }

    @Override
    public void onDisable() {
        // Stop Telegram bot
        if (telegramBot != null) {
            telegramBot.stopBot();
        }

        // Close storage
        if (storage != null) {
            storage.close();
        }

        // Clear sessions
        if (sessionManager != null) {
            sessionManager.clearAll();
        }

        getLogger().info("TelegramAuth has been disabled!");
    }

    private void initStorage() {
        String type = getConfig().getString("storage.type", "SQLITE").toUpperCase();

        switch (type) {
            case "YAML":
                storage = new YamlStorage(this);
                break;
            case "MYSQL":
                storage = new MysqlStorage(this);
                break;
            case "SQLITE":
            default:
                storage = new SqliteStorage(this);
                break;
        }

        storage.init();
        getLogger().info("Storage initialized: " + type);
    }

    private void initBot() {
        String token = getConfig().getString("telegram.bot-token", "");
        String username = getConfig().getString("telegram.bot-username", "");

        if (token.isEmpty() || token.equals("YOUR_BOT_TOKEN_HERE")) {
            getLogger().warning("Telegram bot token is not set! Bot will not start.");
            getLogger().warning("Please set telegram.bot-token in config.yml");
            return;
        }

        try {
            telegramBot = new TelegramBot(this, token, username);
            telegramBot.startBot();
            getLogger().info("Telegram bot started successfully!");
        } catch (Exception e) {
            getLogger().severe("Failed to start Telegram bot: " + e.getMessage());
            if (getConfig().getBoolean("telegram.debug", false)) {
                e.printStackTrace();
            }
        }
    }

    public void reload() {
        reloadConfig();

        // Reinitialize storage
        if (storage != null) {
            storage.close();
        }
        initStorage();

        // Reinitialize bot
        if (telegramBot != null) {
            telegramBot.stopBot();
        }
        initBot();

        getLogger().info("Configuration reloaded!");
    }

    public static TelegramAuthPlugin getInstance() {
        return instance;
    }

    public Storage getStorage() {
        return storage;
    }

    public TelegramBot getTelegramBot() {
        return telegramBot;
    }

    public PlayerSessionManager getSessionManager() {
        return sessionManager;
    }

    public String getMessage(String path) {
        String prefix = getConfig().getString("messages.prefix", "&8[&bTelegramAuth&8] &r");
        String message = getConfig().getString("messages." + path, "&cMessage not found: " + path);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', prefix + message);
    }

    public String getRawMessage(String path) {
        String message = getConfig().getString("messages." + path, "&cMessage not found: " + path);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', message);
    }

    public boolean isDebugEnabled() {
        return getConfig().getBoolean("telegram.debug", false);
    }

    public void debug(String message) {
        if (isDebugEnabled()) {
            getLogger().info("[DEBUG] " + message);
        }
    }
}
