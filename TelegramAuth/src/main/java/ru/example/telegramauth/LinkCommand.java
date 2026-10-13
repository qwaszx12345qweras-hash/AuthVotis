package ru.example.telegramauth;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Handles the /link command for generating link codes.
 */
public class LinkCommand implements CommandExecutor, TabCompleter {

    private final TelegramAuthPlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Random random = new Random();

    public LinkCommand(TelegramAuthPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        // Check if already linked
        if (plugin.getStorage().isLinked(uuid)) {
            player.sendMessage(plugin.getMessage("link-already-bound"));
            return true;
        }

        // Check cooldown
        int cooldownSeconds = 10;
        if (cooldowns.containsKey(uuid)) {
            long lastUsed = cooldowns.get(uuid);
            long elapsed = (System.currentTimeMillis() - lastUsed) / 1000;
            if (elapsed < cooldownSeconds) {
                long remaining = cooldownSeconds - elapsed;
                player.sendMessage(plugin.getMessage("link-cooldown")
                        .replace("{time}", String.valueOf(remaining)));
                return true;
            }
        }

        // Generate code
        int codeLength = plugin.getConfig().getInt("security.code-length", 6);
        String code = generateCode(codeLength);
        int lifetime = plugin.getConfig().getInt("security.code-lifetime", 300);
        long expiresAt = System.currentTimeMillis() + (lifetime * 1000L);

        // Save code to storage
        plugin.getStorage().saveCode(code, uuid, expiresAt);

        // Set cooldown
        cooldowns.put(uuid, System.currentTimeMillis());

        // Get bot username
        String botUsername = plugin.getConfig().getString("telegram.bot-username", "bot");

        // Send message to player
        String message = plugin.getMessage("link-code-generated")
                .replace("{code}", code)
                .replace("{bot}", botUsername)
                .replace("{time}", String.valueOf(lifetime));
        player.sendMessage(message);

        // Schedule code expiration cleanup
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            plugin.getStorage().removeCode(code);
            plugin.debug("Code " + code + " expired and removed.");
        }, lifetime * 20L);

        plugin.debug("Generated code " + code + " for player " + player.getName());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }

    /**
     * Generate a random numeric code of the specified length.
     */
    private String generateCode(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
