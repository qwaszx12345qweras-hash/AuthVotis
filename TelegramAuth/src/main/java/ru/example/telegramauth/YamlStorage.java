package ru.example.telegramauth;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * YAML-based storage implementation.
 */
public class YamlStorage implements Storage {

    private final TelegramAuthPlugin plugin;
    private File linksFile;
    private File codesFile;
    private YamlConfiguration linksConfig;
    private YamlConfiguration codesConfig;

    public YamlStorage(TelegramAuthPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        linksFile = new File(dataFolder, "links.yml");
        codesFile = new File(dataFolder, "codes.yml");

        if (!linksFile.exists()) {
            try {
                linksFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create links.yml: " + e.getMessage());
            }
        }

        if (!codesFile.exists()) {
            try {
                codesFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create codes.yml: " + e.getMessage());
            }
        }

        linksConfig = YamlConfiguration.loadConfiguration(linksFile);
        codesConfig = YamlConfiguration.loadConfiguration(codesFile);
    }

    @Override
    public void close() {
        saveLinks();
        saveCodes();
    }

    @Override
    public boolean isLinked(UUID uuid) {
        return linksConfig.contains("links." + uuid.toString());
    }

    @Override
    public long getTelegramId(UUID uuid) {
        return linksConfig.getLong("links." + uuid.toString(), 0L);
    }

    @Override
    public UUID getUUID(long telegramId) {
        if (linksConfig.getConfigurationSection("links") == null) {
            return null;
        }
        for (String key : linksConfig.getConfigurationSection("links").getKeys(false)) {
            if (linksConfig.getLong("links." + key) == telegramId) {
                return UUID.fromString(key);
            }
        }
        return null;
    }

    @Override
    public void saveLink(UUID uuid, long telegramId) {
        linksConfig.set("links." + uuid.toString(), telegramId);
        saveLinks();
    }

    @Override
    public void removeLink(UUID uuid) {
        linksConfig.set("links." + uuid.toString(), null);
        saveLinks();
    }

    @Override
    public void saveCode(String code, UUID uuid, long expiresAt) {
        codesConfig.set("codes." + code + ".uuid", uuid.toString());
        codesConfig.set("codes." + code + ".expires", expiresAt);
        saveCodes();
    }

    @Override
    public UUID getCodeUUID(String code) {
        if (!isCodeValid(code)) {
            return null;
        }
        String uuidStr = codesConfig.getString("codes." + code + ".uuid");
        if (uuidStr == null) {
            return null;
        }
        return UUID.fromString(uuidStr);
    }

    @Override
    public boolean isCodeValid(String code) {
        if (!codesConfig.contains("codes." + code)) {
            return false;
        }
        long expires = codesConfig.getLong("codes." + code + ".expires", 0);
        return System.currentTimeMillis() < expires;
    }

    @Override
    public void removeCode(String code) {
        codesConfig.set("codes." + code, null);
        saveCodes();
    }

    @Override
    public void cleanExpiredCodes() {
        if (codesConfig.getConfigurationSection("codes") == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (String code : codesConfig.getConfigurationSection("codes").getKeys(false)) {
            long expires = codesConfig.getLong("codes." + code + ".expires", 0);
            if (now >= expires) {
                codesConfig.set("codes." + code, null);
            }
        }
        saveCodes();
    }

    private void saveLinks() {
        try {
            linksConfig.save(linksFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save links.yml: " + e.getMessage());
        }
    }

    private void saveCodes() {
        try {
            codesConfig.save(codesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save codes.yml: " + e.getMessage());
        }
    }
}
