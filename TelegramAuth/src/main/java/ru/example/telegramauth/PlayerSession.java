package ru.example.telegramauth;

import java.util.UUID;

/**
 * Represents a player's authentication session.
 */
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

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public String getPlayerName() {
        return playerName;
    }

    public long getLoginRequestTime() {
        return loginRequestTime;
    }

    public void setLoginRequestTime(long loginRequestTime) {
        this.loginRequestTime = loginRequestTime;
    }

    public long getConfirmMessageId() {
        return confirmMessageId;
    }

    public void setConfirmMessageId(long confirmMessageId) {
        this.confirmMessageId = confirmMessageId;
    }

    public long getChatId() {
        return chatId;
    }

    public void setChatId(long chatId) {
        this.chatId = chatId;
    }

    public boolean isAwaitingConfirmation() {
        return awaitingConfirmation;
    }

    public void setAwaitingConfirmation(boolean awaitingConfirmation) {
        this.awaitingConfirmation = awaitingConfirmation;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    public boolean isDenied() {
        return denied;
    }

    public void setDenied(boolean denied) {
        this.denied = denied;
    }

    /**
     * Check if the confirmation has timed out.
     */
    public boolean isTimedOut(int timeoutSeconds) {
        if (!awaitingConfirmation) return false;
        long elapsed = (System.currentTimeMillis() - loginRequestTime) / 1000;
        return elapsed >= timeoutSeconds;
    }
}
