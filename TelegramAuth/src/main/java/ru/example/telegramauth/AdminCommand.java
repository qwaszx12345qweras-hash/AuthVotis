package ru.example.telegramauth;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Admin command handler for /telegramauth.
 */
public class AdminCommand implements CommandExecutor {

    private final TelegramAuthPlugin plugin;

    public AdminCommand(TelegramAuthPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("telegramauth.admin")) {
            sender.sendMessage(plugin.getMessage("admin-no-permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "=== TelegramAuth Admin ===");
            sender.sendMessage(ChatColor.YELLOW + "/telegramauth reload" + ChatColor.GRAY + " - Reload config");
            sender.sendMessage(ChatColor.YELLOW + "/telegramauth unlink <player>" + ChatColor.GRAY + " - Unlink player");
            sender.sendMessage(ChatColor.YELLOW + "/telegramauth status" + ChatColor.GRAY + " - Show plugin status");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.reload();
                sender.sendMessage(plugin.getMessage("admin-reload"));
                break;

            case "unlink":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /telegramauth unlink <player>");
                    return true;
                }
                handleUnlink(sender, args[1]);
                break;

            case "status":
                showStatus(sender);
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /telegramauth for help.");
                break;
        }

        return true;
    }

    private void handleUnlink(CommandSender sender, String playerName) {
        Player target = plugin.getServer().getPlayer(playerName);
        UUID targetUUID;

        if (target != null) {
            targetUUID = target.getUniqueId();
        } else {
            // Try to find UUID by name from storage (simplified - only works for online players)
            sender.sendMessage(plugin.getMessage("admin-player-not-found"));
            return;
        }

        if (!plugin.getStorage().isLinked(targetUUID)) {
            sender.sendMessage(ChatColor.RED + "Player " + playerName + " is not linked.");
            return;
        }

        plugin.getStorage().removeLink(targetUUID);
        plugin.getSessionManager().removeSession(targetUUID);

        sender.sendMessage(plugin.getMessage("admin-unlinked")
                .replace("{player}", playerName));

        // Freeze the player if online
        if (target != null && target.isOnline()) {
            FreezeListener.freezePlayer(plugin, target);
        }
    }

    private void showStatus(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== TelegramAuth Status ===");
        sender.sendMessage(ChatColor.YELLOW + "Storage: " + ChatColor.WHITE +
                plugin.getConfig().getString("storage.type", "SQLITE"));
        sender.sendMessage(ChatColor.YELLOW + "Bot: " + ChatColor.WHITE +
                (plugin.getTelegramBot() != null ? ChatColor.GREEN + "Running" : ChatColor.RED + "Not running"));
        sender.sendMessage(ChatColor.YELLOW + "Active sessions: " + ChatColor.WHITE +
                plugin.getSessionManager().getAllSessions().size());
        sender.sendMessage(ChatColor.YELLOW + "Debug: " + ChatColor.WHITE +
                (plugin.isDebugEnabled() ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
    }
}
