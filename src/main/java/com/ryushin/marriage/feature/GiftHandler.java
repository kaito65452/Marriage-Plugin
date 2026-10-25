package com.ryushin.marriage.feature;

import com.ryushin.marriage.MarriagePlugin;
import com.ryushin.marriage.data.Marriage;
import com.ryushin.marriage.hook.VaultHook;
import com.ryushin.marriage.util.MessageUtil;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GiftHandler {

    private final MarriagePlugin plugin;

    public GiftHandler(MarriagePlugin plugin) {
        this.plugin = plugin;
    }

    public void handleGift(Player player, Marriage marriage, String[] args) {
        if (!player.hasPermission("marriage.gift")) {
            player.sendMessage(MessageUtil.get(plugin, "no-permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(MessageUtil.get(plugin, "gift-usage"));
            return;
        }

        UUID partnerUuid = marriage.getOtherPlayer(player.getUniqueId());
        Player partner = Bukkit.getPlayer(partnerUuid);

        if (partner == null || !partner.isOnline()) {
            player.sendMessage(MessageUtil.get(plugin, "partner-offline"));
            return;
        }

        String sub = args[1].toLowerCase();
        if (sub.equals("money")) {
            handleGiftMoney(player, partner, args);
        } else if (sub.equals("item")) {
            handleGiftItem(player, partner);
        } else {
            player.sendMessage(MessageUtil.get(plugin, "gift-usage"));
        }
    }

    private void handleGiftMoney(Player player, Player partner, String[] args) {
        if (!plugin.getConfig().getBoolean("gift.money.enabled", true)) {
            player.sendMessage(MessageUtil.get(plugin, "gift-money-disabled"));
            return;
        }

        VaultHook vaultHook = plugin.getVaultHook();
        if (vaultHook == null || !vaultHook.isAvailable()) {
            player.sendMessage(MessageUtil.get(plugin, "gift-economy-unavailable"));
            return;
        }

        if (args.length < 3) {
            player.sendMessage(MessageUtil.get(plugin, "gift-money-usage"));
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(MessageUtil.get(plugin, "gift-money-invalid"));
            return;
        }

        if (!Double.isFinite(amount) || amount <= 0) {
            player.sendMessage(MessageUtil.get(plugin, "gift-money-invalid"));
            return;
        }

        double min = plugin.getConfig().getDouble("gift.money.min-amount", 1.0);
        double max = plugin.getConfig().getDouble("gift.money.max-amount", 1000000.0);

        if (amount < min || amount > max) {
            player.sendMessage(MessageUtil.get(plugin, "gift-money-limit", Map.of(
                    "min", vaultHook.format(min),
                    "max", vaultHook.format(max)
            )));
            return;
        }

        if (!vaultHook.getEconomy().has(player, amount)) {
            player.sendMessage(MessageUtil.get(plugin, "gift-money-no-funds"));
            return;
        }

        EconomyResponse withdrawResp = vaultHook.getEconomy().withdrawPlayer(player, amount);
        if (!withdrawResp.transactionSuccess()) {
            player.sendMessage(MessageUtil.get(plugin, "gift-money-failed"));
            return;
        }

        EconomyResponse depositResp = vaultHook.getEconomy().depositPlayer(partner, amount);
        if (!depositResp.transactionSuccess()) {
            // Rollback jika deposit gagal
            vaultHook.getEconomy().depositPlayer(player, amount);
            player.sendMessage(MessageUtil.get(plugin, "gift-money-failed"));
            return;
        }

        String formatted = vaultHook.format(amount);
        player.sendMessage(MessageUtil.get(plugin, "gift-money-sent", Map.of(
                "amount", formatted,
                "player", partner.getName()
        )));
        partner.sendMessage(MessageUtil.get(plugin, "gift-money-received", Map.of(
                "amount", formatted,
                "player", player.getName()
        )));
    }

    private void handleGiftItem(Player player, Player partner) {
        if (!plugin.getConfig().getBoolean("gift.item.enabled", true)) {
            player.sendMessage(MessageUtil.get(plugin, "gift-item-disabled"));
            return;
        }

        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand.getType().isAir() || inHand.getAmount() <= 0) {
            player.sendMessage(MessageUtil.get(plugin, "gift-item-empty"));
            return;
        }

        List<String> blacklist = plugin.getConfig().getStringList("gift.item.blacklist");
        for (String b : blacklist) {
            if (inHand.getType().name().equalsIgnoreCase(b)) {
                player.sendMessage(MessageUtil.get(plugin, "gift-item-blacklisted"));
                return;
            }
        }

        ItemStack toSend = inHand.clone();
        HashMap<Integer, ItemStack> leftover = partner.getInventory().addItem(toSend);

        int leftoverCount = 0;
        for (ItemStack left : leftover.values()) {
            leftoverCount += left.getAmount();
        }

        int delivered = toSend.getAmount() - leftoverCount;
        if (delivered <= 0) {
            player.sendMessage(MessageUtil.get(plugin, "gift-item-inventory-full", Map.of("player", partner.getName())));
            return;
        }

        if (inHand.getAmount() <= delivered) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            inHand.setAmount(inHand.getAmount() - delivered);
            player.getInventory().setItemInMainHand(inHand);
        }

        String itemName = toSend.getType().name().toLowerCase().replace('_', ' ');
        player.sendMessage(MessageUtil.get(plugin, "gift-item-sent", Map.of(
                "amount", String.valueOf(delivered),
                "item", itemName,
                "player", partner.getName()
        )));
        partner.sendMessage(MessageUtil.get(plugin, "gift-item-received", Map.of(
                "amount", String.valueOf(delivered),
                "item", itemName,
                "player", player.getName()
        )));
    }
}
