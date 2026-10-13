package ru.example.telegramauth;

import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player authentication sessions.
 */
public class PlayerSessionManager {

    private final TelegramAuthPlugin plugin;
    private final Map<UUID, PlayerSession> sessions;

    public PlayerSessionManager(TelegramAuthPlugin plugin) {
        this.plugin = plugin;
        this.sessions = new ConcurrentHashMap<>();
        startTimeoutChecker();
    }

    /**
     * Create or get a session for a player.
     */
    public PlayerSession getOrCreateSession(UUID uuid, String playerName) {
        return sessions.computeIfAbsent(uuid, k -> new PlayerSession(uuid, playerName));
    }

    /**
     * Get an existing session.
     */
    public PlayerSession getSession(UUID uuid) {
        return sessions.get(uuid);
    }

    /**
     * Remove a session.
     */
    public void removeSession(UUID uuid) {
        sessions.remove(uuid);
    }

    /**
     * Check if a player has an active session.
     */
    public boolean hasSession(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    /**
     * Clear all sessions.
     */
    public void clearAll() {
        sessions.clear();
    }

    /**
     * Get all sessions.
     */
    public Map<UUID, PlayerSession> getAllSessions() {
        return sessions;
    }

    /**
     * Periodically check for timed-out sessions and kick players.
     */
    private void startTimeoutChecker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                int timeout = plugin.getConfig().getInt("security.kick-timeout", 120);

                for (Map.Entry<UUID, PlayerSession> entry : sessions.entrySet()) {
                    PlayerSession session = entry.getValue();

                    if (session.isAwaitingConfirmation() && session.isTimedOut(timeout)) {
                        // Kick the player
                        org.bukkit.entity.Player player = plugin.getServer().getPlayer(entry.getKey());
                        if (player != null && player.isOnline()) {
                            String kickMsg = plugin.getRawMessage("kick-not-confirmed");
                            player.kickPlayer(org.bukkit.ChatColor.translateAlternateColorCodes('&', kickMsg));
                        }

                        // Notify via Telegram
                        TelegramBot bot = plugin.getTelegramBot();
                        if (bot != null && session.getChatId() != 0) {
                            String timeoutMsg = plugin.getRawMessage("tg-login-timeout");
                            bot.sendMessage(session.getChatId(),
                                    org.bukkit.ChatColor.stripColor(
                                            org.bukkit.ChatColor.translateAlternateColorCodes('&', timeoutMsg)));
                        }

                        sessions.remove(entry.getKey());
                        plugin.debug("Session timed out for " + entry.getKey());
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Check every second
    }
}
