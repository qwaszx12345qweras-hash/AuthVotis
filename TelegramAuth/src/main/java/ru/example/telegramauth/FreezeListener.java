package ru.example.telegramauth;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

/**
 * Handles all freeze events for unauthenticated players.
 */
public class FreezeListener implements Listener {

    private final TelegramAuthPlugin plugin;

    public FreezeListener(TelegramAuthPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Check if a player should be frozen.
     */
    private boolean shouldFreeze(Player player) {
        if (player.hasPermission("telegramauth.bypass")) {
            return false;
        }

        UUID uuid = player.getUniqueId();
        Storage storage = plugin.getStorage();

        // If not linked at all - freeze
        if (!storage.isLinked(uuid)) {
            return true;
        }

        // If linked but has an active session awaiting confirmation - freeze
        PlayerSession session = plugin.getSessionManager().getSession(uuid);
        if (session != null && session.isAwaitingConfirmation() && !session.isConfirmed()) {
            return true;
        }

        return false;
    }

    /**
     * Freeze a player (called on join).
     */
    public static void freezePlayer(TelegramAuthPlugin plugin, Player player) {
        // Send messages
        player.sendMessage(plugin.getMessage("join-frozen"));
        player.sendTitle(
                ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("messages.join-frozen-title", "&c&lЗАМОРОЖЕН")),
                ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("messages.join-frozen-subtitle", "&7Используйте /link для авторизации")),
                10, 70, 20
        );

        // Apply effects
        if (plugin.getConfig().getBoolean("freeze.blindness-effect", true)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false));
        }
        if (plugin.getConfig().getBoolean("freeze.invisibility-effect", true)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
        }

        // If player is linked, send login confirmation to Telegram
        if (plugin.getStorage().isLinked(player.getUniqueId())) {
            long chatId = plugin.getStorage().getTelegramId(player.getUniqueId());
            if (chatId != 0 && plugin.getTelegramBot() != null) {
                PlayerSession session = plugin.getSessionManager()
                        .getOrCreateSession(player.getUniqueId(), player.getName());
                session.setChatId(chatId);

                // Send confirmation request via Telegram
                plugin.getTelegramBot().getCommandHandler().sendLoginConfirmation(session);
            }
        }

        plugin.debug("Player " + player.getName() + " has been frozen.");
    }

    /**
     * Unfreeze a player (called after confirmation).
     */
    public static void unfreezePlayer(TelegramAuthPlugin plugin, Player player) {
        // Remove effects
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);

        // Reset flight
        player.setAllowFlight(false);
        player.setFlying(false);

        plugin.debug("Player " + player.getName() + " has been unfrozen.");
    }

    // ========== JOIN EVENT ==========
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Schedule check after 1 tick to ensure player is fully loaded
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && shouldFreeze(player)) {
                freezePlayer(plugin, player);
            }
        }, 1L);
    }

    // ========== MOVEMENT ==========
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!shouldFreeze(player)) return;
        if (!plugin.getConfig().getBoolean("freeze.movement", true)) return;

        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null) return;

        // Only block if actually moved (more than 0.1 blocks)
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            double distance = from.distance(to);
            if (distance > 0.1) {
                // Teleport back
                player.teleport(from);
            }
        }
    }

    // ========== CHAT ==========
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!shouldFreeze(player)) return;
        if (!plugin.getConfig().getBoolean("freeze.chat", true)) return;

        event.setCancelled(true);
        player.sendMessage(plugin.getMessage("join-frozen"));
    }

    // ========== COMMANDS ==========
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!shouldFreeze(player)) return;
        if (!plugin.getConfig().getBoolean("freeze.commands", true)) return;

        String message = event.getMessage().toLowerCase();
        java.util.List<String> allowedCommands = plugin.getConfig().getStringList("allowed-commands");

        boolean allowed = false;
        for (String cmd : allowedCommands) {
            String allowedCmd = cmd.toLowerCase().replace("/", "");
            if (message.startsWith("/" + allowedCmd) || message.startsWith(allowedCmd)) {
                allowed = true;
                break;
            }
        }

        if (!allowed) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessage("join-frozen"));
        }
    }

    // ========== BLOCK INTERACTION ==========
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!shouldFreeze(player)) return;
        if (!plugin.getConfig().getBoolean("freeze.interact", true)) return;

        event.setCancelled(true);
    }

    // ========== BLOCK BREAK ==========
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!shouldFreeze(player)) return;
        if (!plugin.getConfig().getBoolean("freeze.block-break", true)) return;

        event.setCancelled(true);
    }

    // ========== BLOCK PLACE ==========
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!shouldFreeze(player)) return;
        if (!plugin.getConfig().getBoolean("freeze.block-place", true)) return;

        event.setCancelled(true);
    }

    // ========== ITEM DROP ==========
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!shouldFreeze(player)) return;
        if (!plugin.getConfig().getBoolean("freeze.drop", true)) return;

        event.setCancelled(true);
    }

    // ========== ITEM PICKUP ==========
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!shouldFreeze(player)) return;
        if (!plugin.getConfig().getBoolean("freeze.pickup", true)) return;

        event.setCancelled(true);
    }

    // ========== INVENTORY OPEN ==========
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        if (!shouldFreeze(player)) return;
        if (!plugin.getConfig().getBoolean("freeze.inventory", true)) return;

        event.setCancelled(true);
    }

    // ========== DAMAGE RECEIVED ==========
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!shouldFreeze(player)) return;
        if (!plugin.getConfig().getBoolean("freeze.damage", true)) return;

        event.setCancelled(true);
    }

    // ========== DAMAGE DEALT ==========
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        Player player = (Player) event.getDamager();
        if (!shouldFreeze(player)) return;
        if (!plugin.getConfig().getBoolean("freeze.deal-damage", true)) return;

        event.setCancelled(true);
    }

    // ========== MOB TARGETING ==========
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player)) return;
        Player player = (Player) event.getTarget();
        if (!shouldFreeze(player)) return;
        if (!plugin.getConfig().getBoolean("freeze.mob-target", true)) return;

        event.setCancelled(true);
    }

    // ========== PORTALS ==========
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        if (!shouldFreeze(player)) return;
        if (!plugin.getConfig().getBoolean("freeze.portals", true)) return;

        event.setCancelled(true);
    }

    // ========== BED SLEEP ==========
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        Player player = event.getPlayer();
        if (!shouldFreeze(player)) return;
        if (!plugin.getConfig().getBoolean("freeze.sleep", true)) return;

        event.setCancelled(true);
    }

    // ========== VEHICLE ENTER ==========
    @EventHandler(priority = EventPriority.HIGH)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player)) return;
        Player player = (Player) event.getEntered();
        if (!shouldFreeze(player)) return;
        if (!plugin.getConfig().getBoolean("freeze.vehicle", true)) return;

        event.setCancelled(true);
    }

    // ========== ITEM CONSUME ==========
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!shouldFreeze(player)) return;
        if (!plugin.getConfig().getBoolean("freeze.item-use", true)) return;

        event.setCancelled(true);
    }

    // ========== QUIT EVENT ==========
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getSessionManager().removeSession(player.getUniqueId());
    }
}
