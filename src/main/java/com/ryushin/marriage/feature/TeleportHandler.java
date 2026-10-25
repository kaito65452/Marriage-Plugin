package com.ryushin.marriage.feature;

import com.ryushin.marriage.MarriagePlugin;
import com.ryushin.marriage.data.Marriage;
import com.ryushin.marriage.hook.EssentialsHook;
import com.ryushin.marriage.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

public class TeleportHandler {

    private final MarriagePlugin plugin;

    public TeleportHandler(MarriagePlugin plugin) {
        this.plugin = plugin;
    }

    public void handleTeleport(Player player, Marriage marriage) {
        if (!player.hasPermission("marriage.tp")) {
            player.sendMessage(MessageUtil.get(plugin, "no-permission"));
            return;
        }

        if (!plugin.getConfig().getBoolean("tp.enabled", true)) {
            player.sendMessage(MessageUtil.get(plugin, "tp-disabled"));
            return;
        }

        EssentialsHook essHook = plugin.getEssentialsHook();
        if (essHook == null || !essHook.isAvailable()) {
            player.sendMessage(MessageUtil.get(plugin, "tp-essentials-unavailable"));
            return;
        }

        UUID partnerUuid = marriage.getOtherPlayer(player.getUniqueId());
        Player partner = Bukkit.getPlayer(partnerUuid);

        // Jangan bocorkan keberadaan pasangan jika sedang vanish
        if (partner == null || !partner.isOnline() || essHook.isVanished(partner)) {
            player.sendMessage(MessageUtil.get(plugin, "partner-offline"));
            return;
        }

        boolean allowCrossWorld = plugin.getConfig().getBoolean("tp.allow-cross-world", true);
        if (!allowCrossWorld && !player.getWorld().equals(partner.getWorld())) {
            player.sendMessage(MessageUtil.get(plugin, "tp-different-world"));
            return;
        }

        // Cek cooldown persisten dari SQLite
        plugin.getDatabase().getCooldownAsync(player.getUniqueId(), "tp").thenAccept(expiresAt -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                long now = System.currentTimeMillis();
                if (expiresAt != null && expiresAt > now) {
                    long remainingSeconds = (expiresAt - now + 999L) / 1000L;
                    long minutes = remainingSeconds / 60;
                    long seconds = remainingSeconds % 60;
                    player.sendMessage(MessageUtil.get(plugin, "tp-cooldown", Map.of(
                            "minutes", String.valueOf(minutes),
                            "seconds", String.valueOf(seconds)
                    )));
                    return;
                }

                // Jalankan teleportasi via EssentialsX
                boolean success = essHook.teleportToPlayer(player, partner);
                if (success) {
                    int cooldownSec = plugin.getConfig().getInt("tp.cooldown-seconds", 600);
                    long newExpiresAt = now + (cooldownSec * 1000L);
                    plugin.getDatabase().setCooldownAsync(player.getUniqueId(), "tp", newExpiresAt);

                    player.sendMessage(MessageUtil.get(plugin, "tp-success", Map.of("player", partner.getName())));
                } else {
                    player.sendMessage(MessageUtil.get(plugin, "tp-failed"));
                }
            });
        });
    }
}
