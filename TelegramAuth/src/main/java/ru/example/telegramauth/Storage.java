package ru.example.telegramauth;

import java.util.UUID;

/**
 * Interface for storage implementations.
 */
public interface Storage {

    /**
     * Initialize the storage (create tables, load data, etc.)
     */
    void init();

    /**
     * Close the storage connection.
     */
    void close();

    /**
     * Check if a player UUID is linked to a Telegram account.
     */
    boolean isLinked(UUID uuid);

    /**
     * Get the Telegram chat ID for a player UUID.
     * Returns 0 if not linked.
     */
    long getTelegramId(UUID uuid);

    /**
     * Get the player UUID for a Telegram chat ID.
     * Returns null if not found.
     */
    UUID getUUID(long telegramId);

    /**
     * Save a link between UUID and Telegram chat ID.
     */
    void saveLink(UUID uuid, long telegramId);

    /**
     * Remove a link for a player UUID.
     */
    void removeLink(UUID uuid);

    /**
     * Save a temporary link code.
     */
    void saveCode(String code, UUID uuid, long expiresAt);

    /**
     * Get the UUID associated with a code.
     * Returns null if code is invalid or expired.
     */
    UUID getCodeUUID(String code);

    /**
     * Check if a code exists and is not expired.
     */
    boolean isCodeValid(String code);

    /**
     * Remove a code (after use or expiration).
     */
    void removeCode(String code);

    /**
     * Remove all expired codes.
     */
    void cleanExpiredCodes();
}
