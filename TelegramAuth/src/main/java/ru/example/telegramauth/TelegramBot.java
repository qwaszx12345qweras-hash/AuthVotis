package ru.example.telegramauth;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramApiException;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.bots.DefaultBotSession;
import org.telegram.telegrambots.TelegramBotsApi;

import java.util.ArrayList;
import java.util.List;

/**
 * Telegram bot using long-polling.
 */
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

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    /**
     * Start the bot with long-polling.
     */
    public void startBot() {
        try {
            botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(this);
            plugin.getLogger().info("Telegram bot long-polling session started.");
        } catch (TelegramApiException e) {
            plugin.getLogger().severe("Failed to start bot session: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Stop the bot.
     */
    public void stopBot() {
        try {
            this.onClosing();
            plugin.getLogger().info("Telegram bot session stopped.");
        } catch (Exception e) {
            plugin.getLogger().severe("Error stopping bot: " + e.getMessage());
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                String text = update.getMessage().getText();
                long chatId = update.getMessage().getChatId();

                plugin.debug("Received message from chat " + chatId + ": " + text);

                if (text.startsWith("/start")) {
                    commandHandler.handleStart(chatId);
                } else if (text.startsWith("/link")) {
                    String code = text.length() > 6 ? text.substring(6).trim() : "";
                    commandHandler.handleLinkCommand(chatId, code);
                } else if (text.startsWith("/help")) {
                    commandHandler.handleHelp(chatId);
                } else {
                    commandHandler.handleUnknown(chatId);
                }
            } else if (update.hasCallbackQuery()) {
                String callbackData = update.getCallbackQuery().getData();
                long chatId = update.getCallbackQuery().getMessage().getChatId();
                int messageId = update.getCallbackQuery().getMessage().getMessageId();
                String fromName = update.getCallbackQuery().getFrom().getFirstName();

                plugin.debug("Received callback from chat " + chatId + ": " + callbackData);

                if ("confirm_login".equals(callbackData)) {
                    commandHandler.handleConfirm(chatId, messageId, fromName);
                } else if ("deny_login".equals(callbackData)) {
                    commandHandler.handleDeny(chatId, messageId, fromName);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error processing update: " + e.getMessage());
            if (plugin.isDebugEnabled()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Send a simple text message.
     */
    public void sendMessage(long chatId, String text) {
        sendMessage(chatId, text, null);
    }

    /**
     * Send a message with inline keyboard.
     */
    public void sendMessage(long chatId, String text, InlineKeyboardMarkup keyboard) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                SendMessage message = new SendMessage();
                message.setChatId(String.valueOf(chatId));
                message.setText(text);
                message.setParseMode("HTML");
                if (keyboard != null) {
                    message.setReplyMarkup(keyboard);
                }
                execute(message);
            } catch (TelegramApiException e) {
                plugin.getLogger().severe("Failed to send message to " + chatId + ": " + e.getMessage());
                if (plugin.isDebugEnabled()) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Send a message synchronously and return the message ID.
     */
    public int sendMessageWithId(long chatId, String text, InlineKeyboardMarkup keyboard) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText(text);
            message.setParseMode("HTML");
            if (keyboard != null) {
                message.setReplyMarkup(keyboard);
            }
            org.telegram.telegrambots.meta.api.objects.Message sentMessage = execute(message);
            return sentMessage.getMessageId();
        } catch (TelegramApiException e) {
            plugin.getLogger().severe("Failed to send message to " + chatId + ": " + e.getMessage());
            return 0;
        }
    }

    /**
     * Edit an existing message.
     */
    public void editMessage(long chatId, int messageId, String newText, InlineKeyboardMarkup keyboard) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                EditMessageText editMessage = new EditMessageText();
                editMessage.setChatId(String.valueOf(chatId));
                editMessage.setMessageId(messageId);
                editMessage.setText(newText);
                editMessage.setParseMode("HTML");
                if (keyboard != null) {
                    editMessage.setReplyMarkup(keyboard);
                }
                execute(editMessage);
            } catch (TelegramApiException e) {
                plugin.getLogger().severe("Failed to edit message: " + e.getMessage());
                if (plugin.isDebugEnabled()) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Create confirmation keyboard.
     */
    public InlineKeyboardMarkup createConfirmKeyboard() {
        InlineKeyboardButton confirmButton = new InlineKeyboardButton();
        confirmButton.setText(plugin.getRawMessage("tg-button-confirm"));
        confirmButton.setCallbackData("confirm_login");

        InlineKeyboardButton denyButton = new InlineKeyboardButton();
        denyButton.setText(plugin.getRawMessage("tg-button-deny"));
        denyButton.setCallbackData("deny_login");

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(confirmButton);
        row.add(denyButton);

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(row);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        return markup;
    }

    /**
     * Get the command handler.
     */
    public BotCommandHandler getCommandHandler() {
        return commandHandler;
    }
}
