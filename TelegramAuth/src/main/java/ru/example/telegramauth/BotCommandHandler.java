package ru.example.telegramauth;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Handles commands received from the Telegram bot.
 */
public class BotCommandHandler {

    private final TelegramAuthPlugin plugin;
    private final TelegramBot bot;

    public BotCommandHandler(TelegramAuthPlugin plugin, TelegramBot bot) {
        this.plugin = plugin;
        this.bot = bot;
    }

    /**
     * Handle /start command.
     */
    public void handleStart(long chatId) {
        String welcomeMsg = plugin.getRawMessage("tg-welcome");
        String formatted = ChatColor.stripColor(
                ChatColor.translateAlternateColorCodes('&', welcomeMsg));
        bot.sendMessage(chatId, formatted);
    }

    /**
     * Handle /link <code> command from Telegram.
     */
    public void handleLinkCommand(long chatId, String code) {
        if (code.isEmpty()) {
            String helpMsg = plugin.getRawMessage("tg-welcome");
            bot.sendMessage(chatId, ChatColor.stripColor(
                    ChatColor.translateAlternateColorCodes('&', helpMsg)));
            return;
        }

        // Check if this Telegram account is already linked
        UUID existingUUID = plugin.getStorage().getUUID(chatId);
        if (existingUUID != null) {
            String msg = plugin.getRawMessage("tg-already-linked");
            bot.sendMessage(chatId, ChatColor.stripColor(
                    ChatColor.translateAlternateColorCodes('&', msg)));
            return;
        }

        // Validate the code
        if (!plugin.getStorage().isCodeValid(code)) {
            String msg = plugin.getRawMessage("tg-code-invalid");
            bot.sendMessage(chatId, ChatColor.stripColor(
                    ChatColor.translateAlternateColorCodes('&', msg)));
            return;
        }

        // Get the UUID associated with the code
        UUID playerUUID = plugin.getStorage().getCodeUUID(code);
        if (playerUUID == null) {
            String msg = plugin.getRawMessage("tg-code-invalid");
            bot.sendMessage(chatId, ChatColor.stripColor(
                    ChatColor.translateAlternateColorCodes('&', msg)));
            return;
        }

        // Save the link
        plugin.getStorage().saveLink(playerUUID, chatId);
        plugin.getStorage().removeCode(code);

        // Notify success
        String successMsg = plugin.getRawMessage("tg-linked-success");
        bot.sendMessage(chatId, ChatColor.stripColor(
                ChatColor.translateAlternateColorCodes('&', successMsg)));

        // Notify the player in-game if online
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null && player.isOnline()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.sendMessage(plugin.getMessage("link-code-generated")
                        .replace("{code}", "✓")
                        .replace("{bot}", bot.getBotUsername())
                        .replace("{time}", "0"));
                player.sendMessage(plugin.getMessage("tg-login-confirmed")
                        .replace("{player}", player.getName()));
            });
        }

        plugin.debug("Linked UUID " + playerUUID + " to Telegram chat " + chatId);
    }

    /**
     * Handle "Confirm" button callback.
     */
    public void handleConfirm(long chatId, int messageId, String fromName) {
        UUID playerUUID = plugin.getStorage().getUUID(chatId);
        if (playerUUID == null) {
            String msg = "❌ Аккаунт не привязан!";
            bot.editMessage(chatId, messageId, msg, null);
            return;
        }

        PlayerSession session = plugin.getSessionManager().getSession(playerUUID);
        if (session == null || !session.isAwaitingConfirmation()) {
            String msg = "⏰ Запрос входа неактуален.";
            bot.editMessage(chatId, messageId, msg, null);
            return;
        }

        // Confirm the login
        session.setConfirmed(true);
        session.setAwaitingConfirmation(false);

        // Edit the message to show it was confirmed
        String confirmedMsg = "✅ <b>Вход подтверждён!</b>\nИгрок: " + session.getPlayerName();
        bot.editMessage(chatId, messageId, confirmedMsg, null);

        // Notify player in-game
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null && player.isOnline()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                // Remove freeze
                FreezeListener.unfreezePlayer(plugin, player);

                // Remove session
                plugin.getSessionManager().removeSession(playerUUID);

                player.sendMessage(plugin.getMessage("tg-login-confirmed")
                        .replace("{player}", player.getName()));
            });
        }

        plugin.debug("Login confirmed for " + playerUUID + " by Telegram user " + fromName);
    }

    /**
     * Handle "Deny" button callback.
     */
    public void handleDeny(long chatId, int messageId, String fromName) {
        UUID playerUUID = plugin.getStorage().getUUID(chatId);
        if (playerUUID == null) {
            String msg = "❌ Аккаунт не привязан!";
            bot.editMessage(chatId, messageId, msg, null);
            return;
        }

        PlayerSession session = plugin.getSessionManager().getSession(playerUUID);
        if (session == null) {
            String msg = "⏰ Запрос входа неактуален.";
            bot.editMessage(chatId, messageId, msg, null);
            return;
        }

        // Deny the login
        session.setDenied(true);
        session.setAwaitingConfirmation(false);

        // Edit the message
        String deniedMsg = "❌ <b>Вход отклонён!</b>\nИгрок: " + session.getPlayerName();
        bot.editMessage(chatId, messageId, deniedMsg, null);

        // Kick the player
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null && player.isOnline()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                String kickMsg = plugin.getRawMessage("kick-denied");
                player.kickPlayer(ChatColor.translateAlternateColorCodes('&', kickMsg));
                plugin.getSessionManager().removeSession(playerUUID);
            });
        }

        plugin.debug("Login denied for " + playerUUID + " by Telegram user " + fromName);
    }

    /**
     * Handle /help command.
     */
    public void handleHelp(long chatId) {
        String helpText = "<b>📋 Доступные команды:</b>\n\n" +
                "/start - Приветствие и инструкция\n" +
                "/link &lt;код&gt; - Привязать аккаунт\n" +
                "/help - Эта справка\n\n" +
                "<i>Для получения кода используйте /link на сервере Minecraft.</i>";
        bot.sendMessage(chatId, helpText);
    }

    /**
     * Handle unknown command.
     */
    public void handleUnknown(long chatId) {
        String msg = "❓ Неизвестная команда. Используйте /help для списка команд.";
        bot.sendMessage(chatId, msg);
    }

    /**
     * Send login confirmation request to the player's Telegram.
     */
    public void sendLoginConfirmation(PlayerSession session) {
        long chatId = session.getChatId();
        String playerName = session.getPlayerName();

        String confirmMsg = plugin.getRawMessage("tg-confirm-login")
                .replace("{player}", playerName);
        String formatted = ChatColor.stripColor(
                ChatColor.translateAlternateColorCodes('&', confirmMsg));

        int msgId = bot.sendMessageWithId(chatId, formatted, bot.createConfirmKeyboard());
        session.setConfirmMessageId(msgId);
        session.setAwaitingConfirmation(true);
        session.setLoginRequestTime(System.currentTimeMillis());

        plugin.debug("Sent login confirmation to chat " + chatId + " for player " + playerName);
    }
}
