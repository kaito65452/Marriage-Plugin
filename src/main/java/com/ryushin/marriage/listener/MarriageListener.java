package com.ryushin.marriage.listener;

import com.ryushin.marriage.command.MarryCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class MarriageListener implements Listener {

    private final MarryCommand marryCommand;

    public MarriageListener(MarryCommand marryCommand) {
        this.marryCommand = marryCommand;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        marryCommand.clearProposalsFor(event.getPlayer().getUniqueId());
    }
}
