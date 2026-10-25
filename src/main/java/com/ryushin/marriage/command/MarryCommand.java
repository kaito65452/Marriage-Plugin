package com.ryushin.marriage.command;

import com.ryushin.marriage.MarriagePlugin;
import com.ryushin.marriage.data.Marriage;
import com.ryushin.marriage.feature.GiftHandler;
import com.ryushin.marriage.feature.HomeHandler;
import com.ryushin.marriage.feature.KissHandler;
import com.ryushin.marriage.feature.TeleportHandler;
import com.ryushin.marriage.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MarryCommand implements CommandExecutor, TabCompleter {

    private final MarriagePlugin plugin;
    private final KissHandler kissHandler;
    private final GiftHandler giftHandler;
    private final TeleportHandler teleportHandler;
    private final HomeHandler homeHandler;

    // key = target, value = sender
    private final Map<UUID, UUID> pendingProposals = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Object marriageLock = new Object();

    public MarryCommand(MarriagePlugin plugin) {
        this.plugin = plugin;
        this.kissHandler = new KissHandler(plugin);
        this.giftHandler = new GiftHandler(plugin);
        this.teleportHandler = new TeleportHandler(plugin);
        this.homeHandler = new HomeHandler(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0) {
            String sub = args[0].toLowerCase();
            if (sub.equals("reload")) {
                handleReload(sender);
                return true;
            }
            if (sub.equals("debug")) {
                handleDebug(sender, args);
                return true;
            }
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Command ini hanya bisa digunakan oleh player.");
            return true;
        }

        if (!player.hasPermission("marriage.use")) {
            player.sendMessage(MessageUtil.get(plugin, "no-permission"));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(MessageUtil.get(plugin, "usage-marry"));
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "propose" -> handlePropose(player, args);
            case "accept" -> handleAccept(player);
            case "deny" -> handleDeny(player);
            case "divorce" -> handleDivorce(player);
            case "info" -> handleInfo(player);
            case "kiss" -> withMarriage(player, m -> kissHandler.handleKiss(player, m));
            case "gift" -> withMarriage(player, m -> giftHandler.handleGift(player, m, args));
            case "tp" -> withMarriage(player, m -> teleportHandler.handleTeleport(player, m));
            case "sethome" -> withMarriage(player, m -> homeHandler.handleSetHome(player, m, args));
            case "home" -> withMarriage(player, m -> homeHandler.handleHome(player, m, args));
            default -> player.sendMessage(MessageUtil.get(plugin, "unknown-action"));
        }

        return true;
    }

    private void withMarriage(Player player, java.util.function.Consumer<Marriage> action) {
        plugin.getDatabase().getMarriageByPlayerAsync(player.getUniqueId()).thenAccept(marriage -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (marriage == null) {
                    player.sendMessage(MessageUtil.get(plugin, "not-married"));
                    return;
                }
                action.accept(marriage);
            });
        });
    }

    private void handlePropose(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.get(plugin, "usage-propose"));
            return;
        }

        UUID senderUuid = sender.getUniqueId();
        long now = System.currentTimeMillis();
        int cooldownSec = plugin.getConfig().getInt("marry.cooldown-seconds", 60);
        int timeoutSec = plugin.getConfig().getInt("marry.proposal-timeout-seconds", 120);

        synchronized (marriageLock) {
            Long cooldownEnd = cooldowns.get(senderUuid);
            if (cooldownEnd != null) {
                if (cooldownEnd > now) {
                    long remaining = Math.max(1, (cooldownEnd - now + 999L) / 1000L);
                    sender.sendMessage(MessageUtil.get(plugin, "cooldown-active", Map.of("seconds", String.valueOf(remaining))));
                    return;
                }
                cooldowns.remove(senderUuid);
            }

            if (pendingProposals.containsValue(senderUuid)) {
                sender.sendMessage(MessageUtil.get(plugin, "already-have-pending-proposal"));
                return;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(MessageUtil.get(plugin, "player-not-found"));
                return;
            }

            UUID targetUuid = target.getUniqueId();
            if (senderUuid.equals(targetUuid)) {
                sender.sendMessage(MessageUtil.get(plugin, "cannot-propose-self"));
                return;
            }

            // Cek jika target sudah pernah melamar pengirim (proposal silang)
            if (senderUuid.equals(pendingProposals.get(senderUuid)) || targetUuid.equals(pendingProposals.get(senderUuid))) {
                sender.sendMessage(MessageUtil.get(plugin, "target-already-proposed-to-you", Map.of("player", target.getName())));
                return;
            }

            if (pendingProposals.containsKey(targetUuid)) {
                sender.sendMessage(MessageUtil.get(plugin, "target-has-pending-proposal"));
                return;
            }

            // Validasi DB async agar tidak blok main thread
            plugin.getDatabase().getMarriageByPlayerAsync(senderUuid).thenAccept(senderMarriage -> {
                if (senderMarriage != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(MessageUtil.get(plugin, "already-married-self")));
                    return;
                }

                plugin.getDatabase().getMarriageByPlayerAsync(targetUuid).thenAccept(targetMarriage -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (targetMarriage != null) {
                            sender.sendMessage(MessageUtil.get(plugin, "target-already-married", Map.of("player", target.getName())));
                            return;
                        }

                        synchronized (marriageLock) {
                            if (pendingProposals.containsKey(targetUuid) || pendingProposals.containsValue(senderUuid)) {
                                return;
                            }

                            pendingProposals.put(targetUuid, senderUuid);
                            cooldowns.put(senderUuid, now + (cooldownSec * 1000L));

                            sender.sendMessage(MessageUtil.get(plugin, "propose-sent", Map.of(
                                    "player", target.getName(),
                                    "timeout", String.valueOf(timeoutSec)
                            )));
                            target.sendMessage(MessageUtil.get(plugin, "propose-received", Map.of(
                                    "player", sender.getName(),
                                    "timeout", String.valueOf(timeoutSec)
                            )));

                            plugin.debugLog("Propose: " + sender.getName() + " -> " + target.getName());

                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    synchronized (marriageLock) {
                                        UUID current = pendingProposals.get(targetUuid);
                                        if (!senderUuid.equals(current)) return;
                                        pendingProposals.remove(targetUuid);

                                        Player targetP = Bukkit.getPlayer(targetUuid);
                                        Player senderP = Bukkit.getPlayer(senderUuid);
                                        if (targetP != null) {
                                            String sName = senderP != null ? senderP.getName() : "player";
                                            targetP.sendMessage(MessageUtil.get(plugin, "propose-expired-target", Map.of("player", sName)));
                                        }
                                        if (senderP != null) {
                                            senderP.sendMessage(MessageUtil.get(plugin, "propose-expired-sender"));
                                        }
                                    }
                                }
                            }.runTaskLater(plugin, timeoutSec * 20L);
                        }
                    });
                });
            });
        }
    }

    private void handleAccept(Player player) {
        synchronized (marriageLock) {
            UUID playerUuid = player.getUniqueId();
            UUID proposerUuid = pendingProposals.remove(playerUuid);

            if (proposerUuid == null) {
                player.sendMessage(MessageUtil.get(plugin, "no-pending-proposal"));
                return;
            }

            plugin.getDatabase().getMarriageByPlayerAsync(playerUuid).thenAccept(pMarriage -> {
                if (pMarriage != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(MessageUtil.get(plugin, "already-married-self")));
                    return;
                }

                plugin.getDatabase().getMarriageByPlayerAsync(proposerUuid).thenAccept(propMarriage -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (propMarriage != null) {
                            player.sendMessage(MessageUtil.get(plugin, "proposal-invalid-already-married"));
                            return;
                        }

                        Marriage marriage = new Marriage(proposerUuid, playerUuid);
                        plugin.getDatabase().saveMarriageAsync(marriage).thenRun(() -> {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                Player proposer = Bukkit.getPlayer(proposerUuid);
                                String pName = proposer != null ? proposer.getName() : "pasanganmu";
                                player.sendMessage(MessageUtil.get(plugin, "marriage-success", Map.of("player", pName)));
                                if (proposer != null) {
                                    proposer.sendMessage(MessageUtil.get(plugin, "marriage-success-notify-proposer", Map.of("player", player.getName())));
                                }
                                plugin.debugLog("Marriage created: " + proposerUuid + " + " + playerUuid);
                            });
                        });
                    });
                });
            });
        }
    }

    private void handleDeny(Player player) {
        synchronized (marriageLock) {
            UUID proposerUuid = pendingProposals.remove(player.getUniqueId());
            if (proposerUuid == null) {
                player.sendMessage(MessageUtil.get(plugin, "no-pending-proposal"));
                return;
            }

            player.sendMessage(MessageUtil.get(plugin, "propose-denied"));
            Player proposer = Bukkit.getPlayer(proposerUuid);
            if (proposer != null) {
                proposer.sendMessage(MessageUtil.get(plugin, "propose-denied-notify", Map.of("player", player.getName())));
            }
        }
    }

    private void handleDivorce(Player player) {
        plugin.getDatabase().getMarriageByPlayerAsync(player.getUniqueId()).thenAccept(marriage -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (marriage == null) {
                    player.sendMessage(MessageUtil.get(plugin, "not-married"));
                    return;
                }

                UUID partnerUuid = marriage.getOtherPlayer(player.getUniqueId());
                plugin.getDatabase().deleteMarriageAsync(marriage.getId()).thenRun(() -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage(MessageUtil.get(plugin, "divorce-success"));
                        Player partner = Bukkit.getPlayer(partnerUuid);
                        if (partner != null && partner.isOnline()) {
                            partner.sendMessage(MessageUtil.get(plugin, "divorce-notify", Map.of("player", player.getName())));
                        }
                        plugin.debugLog("Divorce: " + player.getUniqueId() + " x " + partnerUuid);
                    });
                });
            });
        });
    }

    private void handleInfo(Player player) {
        plugin.getDatabase().getMarriageByPlayerAsync(player.getUniqueId()).thenAccept(marriage -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (marriage == null) {
                    player.sendMessage("§7Kamu belum menikah.");
                    return;
                }

                UUID partnerUuid = marriage.getOtherPlayer(player.getUniqueId());
                Player partner = Bukkit.getPlayer(partnerUuid);
                String partnerName = partner != null ? partner.getName() : Bukkit.getOfflinePlayer(partnerUuid).getName();
                if (partnerName == null) partnerName = partnerUuid.toString();

                boolean online = partner != null && partner.isOnline();
                String statusOnline = online ? "§a● Online" : "§7● Offline";
                String duration = formatDurasi(System.currentTimeMillis() - marriage.getMarriedAt());

                player.sendMessage("§8§m                                        ");
                player.sendMessage("§d§l          ❤ STATUS PERNIKAHAN ❤");
                player.sendMessage("§8§m                                        ");
                player.sendMessage("");
                player.sendMessage(" §7Pasangan     §8» §f" + partnerName + "  " + statusOnline);
                player.sendMessage(" §7Menikah sejak §8» §f" + duration + " yang lalu");
                player.sendMessage("");
                player.sendMessage("§8§m                                        ");
            });
        });
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("marriage.admin")) {
            sender.sendMessage(MessageUtil.get(plugin, "no-permission"));
            return;
        }
        plugin.reloadPluginConfig();
        sender.sendMessage(MessageUtil.get(plugin, "config-reloaded"));
    }

    private void handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission("marriage.admin")) {
            sender.sendMessage(MessageUtil.get(plugin, "no-permission"));
            return;
        }

        if (args.length < 2) {
            String status = plugin.isDebug() ? "&aON" : "&cOFF";
            sender.sendMessage(MessageUtil.get(plugin, "debug-status", Map.of("status", status)));
            sender.sendMessage(MessageUtil.get(plugin, "debug-usage"));
            return;
        }

        boolean newState = switch (args[1].toLowerCase()) {
            case "on", "true", "enable" -> true;
            case "off", "false", "disable" -> false;
            default -> plugin.isDebug();
        };

        plugin.setDebug(newState);
        String status = newState ? "&aON" : "&cOFF";
        sender.sendMessage(MessageUtil.get(plugin, "debug-changed", Map.of("status", status)));
    }

    private String formatDurasi(long millis) {
        long detik = millis / 1000;
        long hari = detik / 86400;
        long jam = (detik % 86400) / 3600;
        long menit = (detik % 3600) / 60;

        if (hari > 0) return hari + " hari " + jam + " jam";
        if (jam > 0) return jam + " jam " + menit + " menit";
        if (menit > 0) return menit + " menit";
        return "baru saja";
    }

    public void clearProposalsFor(UUID uuid) {
        synchronized (marriageLock) {
            pendingProposals.remove(uuid);
            pendingProposals.entrySet().removeIf(entry -> uuid.equals(entry.getValue()));
            // cooldowns TIDAK dihapus saat quit agar tidak bisa dieksploitasi lewat relog
        }
        homeHandler.clearConfirmsFor(uuid);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            list.add("propose");
            list.add("accept");
            list.add("deny");
            list.add("divorce");
            list.add("info");

            if (sender.hasPermission("marriage.kiss")) list.add("kiss");
            if (sender.hasPermission("marriage.gift")) list.add("gift");
            if (sender.hasPermission("marriage.tp")) list.add("tp");
            if (sender.hasPermission("marriage.home")) {
                list.add("sethome");
                list.add("home");
            }
            if (sender.hasPermission("marriage.admin")) {
                list.add("reload");
                list.add("debug");
            }

            return filterStartsWith(list, args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("propose")) {
                List<String> players = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.getName().equalsIgnoreCase(sender.getName())) {
                        players.add(p.getName());
                    }
                }
                return filterStartsWith(players, args[1]);
            }
            if (sub.equals("gift") && sender.hasPermission("marriage.gift")) {
                return filterStartsWith(List.of("money", "item"), args[1]);
            }
            if (sub.equals("debug") && sender.hasPermission("marriage.admin")) {
                return filterStartsWith(List.of("on", "off"), args[1]);
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("sethome")) {
            return filterStartsWith(List.of("confirm"), args[2]);
        }

        return List.of();
    }

    private List<String> filterStartsWith(List<String> input, String prefix) {
        List<String> result = new ArrayList<>();
        String lower = prefix.toLowerCase();
        for (String item : input) {
            if (item.toLowerCase().startsWith(lower)) {
                result.add(item);
            }
        }
        return result;
    }
}
