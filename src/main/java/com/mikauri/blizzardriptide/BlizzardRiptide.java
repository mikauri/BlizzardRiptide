package com.mikauri.blizzardriptide;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.stream.Collectors;

public final class BlizzardRiptide extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    // --- Config ---
    private boolean enabled;
    private int chargeTicks;          // wind-up delay (ticks) before the launch fires
    private double velocityMultiplier;// scales the vanilla launch power
    private double snowTempThreshold; // biome temperature below which precipitation is snow
    private boolean requireSkyAccess; // must the player be exposed to the sky?
    private long cooldownTicks;       // per-player cooldown
    private boolean playSound, showParticles;
    private Set<String> worldsWhitelist;

    // --- Runtime state ---
    private final Map<UUID, Long> lastDashTick = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();
        Bukkit.getPluginManager().registerEvents(this, this);
        PluginCommand cmd = getCommand("blizzardriptide");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        getLogger().info("BlizzardRiptide enabled (v" + getDescription().getVersion() + ").");
    }

    @Override
    public void onDisable() {
        getLogger().info("BlizzardRiptide disabled.");
    }

    private void reloadSettings() {
        reloadConfig();
        FileConfiguration c = getConfig();
        enabled            = c.getBoolean("enabled", true);
        chargeTicks        = c.getInt("charge_ticks", 10);
        velocityMultiplier = c.getDouble("velocity_multiplier", 1.0);
        snowTempThreshold  = c.getDouble("snow_temperature_threshold", 0.15);
        requireSkyAccess   = c.getBoolean("require_sky_access", true);
        cooldownTicks      = c.getLong("cooldown_ticks", 10L);
        playSound          = c.getBoolean("play_sound", true);
        showParticles      = c.getBoolean("show_particles", true);
        worldsWhitelist    = new HashSet<>(c.getStringList("worlds_whitelist"));
    }

    // ---------------- Detection ----------------

    // A Riptide trident right-clicked out of water/rain never enters the "use"
    // (charge) state client-side, so we can't poll isHandRaised() to detect a
    // release. Instead we launch directly off the right-click, with an optional
    // short wind-up delay so it still feels like a charge.
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!enabled) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player p = event.getPlayer();
        if (!worldsWhitelist.isEmpty() && !worldsWhitelist.contains(p.getWorld().getName())) return;

        int level = riptideLevel(event.getItem());
        if (level <= 0) return;

        // If already in water, vanilla handles the dash. (Rain can't fall where it snows.)
        if (p.isInWater()) return;
        if (!isSnowingAt(p.getLocation())) return;
        if (onCooldown(p)) return;

        // Start the cooldown immediately so the repeated right-click packets a held
        // button sends don't queue multiple launches during the wind-up.
        lastDashTick.put(p.getUniqueId(), p.getWorld().getFullTime());

        if (chargeTicks <= 0) {
            dash(p, level);
            return;
        }

        if (playSound) p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_RETURN, 0.7f, 0.8f);
        final UUID id = p.getUniqueId();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Player pl = Bukkit.getPlayer(id);
            if (pl == null || !pl.isOnline() || !enabled) return;
            if (riptideLevel(pl.getInventory().getItemInMainHand()) <= 0
                    && riptideLevel(pl.getInventory().getItemInOffHand()) <= 0) return;
            if (pl.isInWater() || !isSnowingAt(pl.getLocation())) return;
            dash(pl, level);
        }, chargeTicks);
    }

    // ---------------- The launch ----------------

    private void dash(Player p, int level) {
        // Vanilla riptide speed factor: 3.0 * (1 + level) / 4.0  (L1=1.5, L2=2.25, L3=3.0)
        double speed = 3.0 * (1.0 + level) / 4.0 * velocityMultiplier;
        Vector dir = p.getLocation().getDirection().normalize().multiply(speed);
        p.setVelocity(dir);
        p.setFallDistance(0f);

        lastDashTick.put(p.getUniqueId(), p.getWorld().getFullTime());

        World w = p.getWorld();
        if (playSound) {
            Sound s = level >= 3 ? Sound.ITEM_TRIDENT_RIPTIDE_3
                    : level == 2 ? Sound.ITEM_TRIDENT_RIPTIDE_2
                    : Sound.ITEM_TRIDENT_RIPTIDE_1;
            w.playSound(p.getLocation(), s, 1f, 1f);
        }
        if (showParticles) {
            w.spawnParticle(Particle.SNOWFLAKE, p.getLocation(), 40, 0.4, 0.6, 0.4, 0.02);
        }
    }

    // ---------------- Helpers ----------------

    private int riptideLevel(ItemStack item) {
        if (item == null || item.getType() != Material.TRIDENT) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        return meta.getEnchantLevel(Enchantment.RIPTIDE);
    }

    private boolean isSnowingAt(Location loc) {
        World w = loc.getWorld();
        if (w == null || !w.hasStorm()) return false;

        if (requireSkyAccess) {
            Block highest = w.getHighestBlockAt(loc);
            if (highest.getY() > loc.getBlockY()) return false; // something overhead
        }
        double temp = w.getTemperature(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        return temp < snowTempThreshold;
    }

    private boolean onCooldown(Player p) {
        if (cooldownTicks <= 0) return false;
        Long last = lastDashTick.get(p.getUniqueId());
        if (last == null) return false;
        return p.getWorld().getFullTime() - last < cooldownTicks;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastDashTick.remove(event.getPlayer().getUniqueId());
    }

    // ---------------- Command ----------------

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.AQUA + "BlizzardRiptide " + ChatColor.GRAY + "v" + getDescription().getVersion());
            sender.sendMessage(ChatColor.YELLOW + "  /" + label + " status " + ChatColor.GRAY + "- show settings");
            sender.sendMessage(ChatColor.YELLOW + "  /" + label + " reload " + ChatColor.GRAY + "- reload config");
            sender.sendMessage(ChatColor.YELLOW + "  /" + label + " toggle " + ChatColor.GRAY + "- enable/disable");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> {
                sender.sendMessage(ChatColor.AQUA + "BlizzardRiptide: " + (enabled ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
                sender.sendMessage(ChatColor.GRAY + "chargeTicks=" + chargeTicks
                        + " velocityMult=" + velocityMultiplier
                        + " cooldown=" + cooldownTicks + "t"
                        + " snowTemp<" + snowTempThreshold
                        + " skyAccess=" + requireSkyAccess);
                if (sender instanceof Player p) {
                    sender.sendMessage(ChatColor.GRAY + "Snowing here: "
                            + (isSnowingAt(p.getLocation()) ? ChatColor.GREEN + "yes" : ChatColor.RED + "no"));
                }
            }
            case "reload" -> {
                if (!sender.hasPermission("blizzardriptide.admin")) { sender.sendMessage(ChatColor.RED + "No permission."); return true; }
                reloadSettings();
                sender.sendMessage(ChatColor.GREEN + "BlizzardRiptide config reloaded.");
            }
            case "toggle" -> {
                if (!sender.hasPermission("blizzardriptide.admin")) { sender.sendMessage(ChatColor.RED + "No permission."); return true; }
                enabled = !enabled;
                getConfig().set("enabled", enabled);
                saveConfig();
                sender.sendMessage(ChatColor.AQUA + "BlizzardRiptide now " + (enabled ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
            }
            default -> sender.sendMessage(ChatColor.RED + "Unknown subcommand. Try /" + label);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], Arrays.asList("status", "reload", "toggle"), out);
        }
        Collections.sort(out);
        return out;
    }
}