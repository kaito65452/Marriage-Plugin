package com.ryushin.marriage.feature;

import com.ryushin.marriage.MarriagePlugin;
import com.ryushin.marriage.data.Marriage;
import com.ryushin.marriage.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class KissHandler {

    private final MarriagePlugin plugin;
    private final Map<UUID, Long> kissCooldowns = new HashMap<>();

    public KissHandler(MarriagePlugin plugin) {
        this.plugin = plugin;
    }

    public void handleKiss(Player player, Marriage marriage) {
        if (!player.hasPermission("marriage.kiss")) {
            player.sendMessage(MessageUtil.get(plugin, "no-permission"));
            return;
        }

        if (!plugin.getConfig().getBoolean("kiss.enabled", true)) {
            player.sendMessage(MessageUtil.get(plugin, "kiss-disabled"));
            return;
        }

        UUID partnerUuid = marriage.getOtherPlayer(player.getUniqueId());
        Player partner = Bukkit.getPlayer(partnerUuid);

        if (partner == null || !partner.isOnline()) {
            player.sendMessage(MessageUtil.get(plugin, "partner-offline"));
            return;
        }

        if (!player.getWorld().equals(partner.getWorld())) {
            player.sendMessage(MessageUtil.get(plugin, "kiss-different-world"));
            return;
        }

        double maxDistance = plugin.getConfig().getDouble("kiss.max-distance", 2.0);
        Location loc1 = player.getLocation();
        Location loc2 = partner.getLocation();

        // Cek apakah di blok yang sama atau berjarak <= maxDistance
        if (loc1.distanceSquared(loc2) > maxDistance * maxDistance) {
            player.sendMessage(MessageUtil.get(plugin, "kiss-too-far"));
            return;
        }

        long now = System.currentTimeMillis();
        int cooldownSec = plugin.getConfig().getInt("kiss.cooldown-seconds", 30);
        Long lastKiss = kissCooldowns.get(player.getUniqueId());

        if (lastKiss != null && now < lastKiss + (cooldownSec * 1000L)) {
            long remaining = Math.max(1, ((lastKiss + (cooldownSec * 1000L)) - now + 999L) / 1000L);
            player.sendMessage(MessageUtil.get(plugin, "kiss-cooldown", Map.of("seconds", String.valueOf(remaining))));
            return;
        }

        kissCooldowns.put(player.getUniqueId(), now);

        // Efek partikel & suara
        String particleName = plugin.getConfig().getString("kiss.particle", "HEART");
        String soundName = plugin.getConfig().getString("kiss.sound", "ENTITY_EXPERIENCE_ORB_PICKUP");

        try {
            Particle particle = Particle.valueOf(particleName.toUpperCase());
            player.getWorld().spawnParticle(particle, loc1.clone().add(0, 1.2, 0), 6, 0.3, 0.3, 0.3, 0.05);
            partner.getWorld().spawnParticle(particle, loc2.clone().add(0, 1.2, 0), 6, 0.3, 0.3, 0.3, 0.05);
        } catch (IllegalArgumentException ignored) {}

        try {
            String formattedKey = soundName.toLowerCase().replace('_', '.');
            org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.minecraft(formattedKey);
            Sound sound = org.bukkit.Registry.SOUNDS.get(key);
            if (sound != null) {
                player.playSound(loc1, sound, 1.0f, 1.2f);
                partner.playSound(loc2, sound, 1.0f, 1.2f);
            }
        } catch (Exception ignored) {}

        player.sendMessage(MessageUtil.get(plugin, "kiss-success", Map.of("player", partner.getName())));
        partner.sendMessage(MessageUtil.get(plugin, "kiss-received", Map.of("player", player.getName())));
    }
}
