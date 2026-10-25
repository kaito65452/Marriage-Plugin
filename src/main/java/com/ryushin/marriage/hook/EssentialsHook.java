package com.ryushin.marriage.hook;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.plugin.Plugin;

public class EssentialsHook {

    private Essentials essentials;

    public EssentialsHook() {
        setupEssentials();
    }

    public boolean setupEssentials() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("Essentials");
        if (plugin instanceof Essentials ess && ess.isEnabled()) {
            this.essentials = ess;
            return true;
        }
        this.essentials = null;
        return false;
    }

    public boolean isAvailable() {
        return essentials != null && essentials.isEnabled();
    }

    public boolean isVanished(Player player) {
        if (!isAvailable() || player == null) return false;
        User user = essentials.getUser(player);
        return user != null && user.isVanished();
    }

    public boolean teleport(Player player, Location destination) {
        if (!isAvailable()) return false;
        User user = essentials.getUser(player);
        if (user == null) return false;
        try {
            user.getTeleport().teleport(destination, null, TeleportCause.COMMAND);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean teleportToPlayer(Player player, Player target) {
        if (!isAvailable()) return false;
        User user = essentials.getUser(player);
        if (user == null) return false;
        try {
            user.getTeleport().teleport(target, null, TeleportCause.COMMAND);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
