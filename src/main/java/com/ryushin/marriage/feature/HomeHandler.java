package com.ryushin.marriage.feature;

import com.ryushin.marriage.MarriagePlugin;
import com.ryushin.marriage.data.Marriage;
import com.ryushin.marriage.data.MarriageHome;
import com.ryushin.marriage.hook.EssentialsHook;
import com.ryushin.marriage.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public class HomeHandler {

    private final MarriagePlugin plugin;
    private final Map<UUID, PendingHomeConfirm> pendingConfirms = new HashMap<>();
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,16}$");

    private record PendingHomeConfirm(String name, Location location, long expiresAt) {}

    public HomeHandler(MarriagePlugin plugin) {
        this.plugin = plugin;
    }

    public void handleSetHome(Player player, Marriage marriage, String[] args) {
        if (!player.hasPermission("marriage.home")) {
            player.sendMessage(MessageUtil.get(plugin, "no-permission"));
            return;
        }

        if (!plugin.getConfig().getBoolean("home.enabled", true)) {
            player.sendMessage(MessageUtil.get(plugin, "home-disabled"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(MessageUtil.get(plugin, "home-set-usage"));
            return;
        }

        String homeName = args[1].toLowerCase();
        if (!NAME_PATTERN.matcher(homeName).matches()) {
            player.sendMessage(MessageUtil.get(plugin, "home-invalid-name"));
            return;
        }

        boolean isConfirmed = args.length >= 3 && args[2].equalsIgnoreCase("confirm");
        UUID playerUuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (isConfirmed) {
            PendingHomeConfirm pending = pendingConfirms.remove(playerUuid);
            Location locToSave = (pending != null && pending.name().equalsIgnoreCase(homeName) && now < pending.expiresAt())
                    ? pending.location()
                    : player.getLocation();

            saveHomeDirect(player, marriage, homeName, locToSave);
            return;
        }

        // Ambil daftar home yang sudah ada dari DB async
        plugin.getDatabase().getHomesByMarriageAsync(marriage.getId()).thenAccept(homes -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                MarriageHome existing = homes.stream()
                        .filter(h -> h.getName().equalsIgnoreCase(homeName))
                        .findFirst()
                        .orElse(null);

                if (existing != null) {
                    // Minta konfirmasi overwrite
                    pendingConfirms.put(playerUuid, new PendingHomeConfirm(homeName, player.getLocation(), now + 30000L));

                    player.sendMessage(MessageUtil.get(plugin, "home-overwrite-prompt", Map.of("name", homeName)));

                    Component clickMsg = Component.text("» [KLIK DI SINI UNTUK KONFIRMASI TIMPA HOME]")
                            .color(NamedTextColor.YELLOW)
                            .decorate(TextDecoration.BOLD)
                            .clickEvent(ClickEvent.runCommand("/marry sethome " + homeName + " confirm"))
                            .hoverEvent(HoverEvent.showText(Component.text("Klik untuk menimpa home '" + homeName + "'")));

                    player.sendMessage(clickMsg);
                    return;
                }

                int maxHomes = plugin.getConfig().getInt("home.max-homes", 3);
                if (homes.size() >= maxHomes) {
                    player.sendMessage(MessageUtil.get(plugin, "home-limit-reached", Map.of("max", String.valueOf(maxHomes))));
                    return;
                }

                saveHomeDirect(player, marriage, homeName, player.getLocation());
            });
        });
    }

    private void saveHomeDirect(Player player, Marriage marriage, String homeName, Location loc) {
        MarriageHome home = new MarriageHome(marriage.getId(), homeName, loc, player.getUniqueId());
        plugin.getDatabase().saveHomeAsync(home).thenRun(() -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(MessageUtil.get(plugin, "home-set-success", Map.of("name", homeName)));
            });
        });
    }

    public void handleHome(Player player, Marriage marriage, String[] args) {
        if (!player.hasPermission("marriage.home")) {
            player.sendMessage(MessageUtil.get(plugin, "no-permission"));
            return;
        }

        if (!plugin.getConfig().getBoolean("home.enabled", true)) {
            player.sendMessage(MessageUtil.get(plugin, "home-disabled"));
            return;
        }

        EssentialsHook essHook = plugin.getEssentialsHook();
        if (essHook == null || !essHook.isAvailable()) {
            player.sendMessage(MessageUtil.get(plugin, "home-essentials-unavailable"));
            return;
        }

        plugin.getDatabase().getHomesByMarriageAsync(marriage.getId()).thenAccept(homes -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (homes.isEmpty()) {
                    player.sendMessage(MessageUtil.get(plugin, "home-list-empty"));
                    return;
                }

                // Jika tanpa argumen nama
                if (args.length < 2) {
                    if (homes.size() == 1) {
                        teleportToHome(player, homes.get(0), essHook);
                    } else {
                        displayHomeList(player, homes);
                    }
                    return;
                }

                String targetName = args[1].toLowerCase();
                MarriageHome home = homes.stream()
                        .filter(h -> h.getName().equalsIgnoreCase(targetName))
                        .findFirst()
                        .orElse(null);

                if (home == null) {
                    player.sendMessage(MessageUtil.get(plugin, "home-not-found", Map.of("name", targetName)));
                    return;
                }

                teleportToHome(player, home, essHook);
            });
        });
    }

    private void teleportToHome(Player player, MarriageHome home, EssentialsHook essHook) {
        Location loc = home.toLocation();
        if (loc == null || loc.getWorld() == null) {
            player.sendMessage(MessageUtil.get(plugin, "home-world-missing"));
            return;
        }

        if (!isSafeLocation(loc)) {
            player.sendMessage(MessageUtil.get(plugin, "home-unsafe"));
            return;
        }

        boolean success = essHook.teleport(player, loc);
        if (success) {
            player.sendMessage(MessageUtil.get(plugin, "home-teleport-success", Map.of("name", home.getName())));
        } else {
            player.sendMessage(MessageUtil.get(plugin, "home-teleport-failed"));
        }
    }

    private boolean isSafeLocation(Location loc) {
        Block feet = loc.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block below = feet.getRelative(0, -1, 0);

        if (feet.getType() == Material.LAVA || head.getType() == Material.LAVA || feet.getType() == Material.FIRE) {
            return false;
        }

        if (feet.getType().isSolid() || head.getType().isSolid()) {
            return false;
        }

        return below.getType().isSolid();
    }

    private void displayHomeList(Player player, List<MarriageHome> homes) {
        player.sendMessage("§8§m                                        ");
        player.sendMessage("§d§l           ❀ DAFTAR HOME KELUARGA ❀");
        player.sendMessage("§8§m                                        ");
        player.sendMessage("");
        for (MarriageHome h : homes) {
            player.sendMessage(" §7» §e" + h.getName() + " §8(World: §f" + h.getWorldName() + "§8, X: §f" + (int)h.getX() + " §8Y: §f" + (int)h.getY() + " §8Z: §f" + (int)h.getZ() + "§8)");
        }
        player.sendMessage("");
        player.sendMessage("§7Ketik: §f/marry home <nama>");
        player.sendMessage("§8§m                                        ");
    }

    public void clearConfirmsFor(UUID uuid) {
        pendingConfirms.remove(uuid);
    }
}
