package com.ryushin.marriage;

import com.ryushin.marriage.command.MarryCommand;
import com.ryushin.marriage.data.Database;
import com.ryushin.marriage.hook.EssentialsHook;
import com.ryushin.marriage.hook.VaultHook;
import com.ryushin.marriage.listener.MarriageListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class MarriagePlugin extends JavaPlugin {
    private Database database;
    private static MarriagePlugin instance;

    private VaultHook vaultHook;
    private EssentialsHook essentialsHook;

    private boolean debug;
    private MarryCommand marryCommand;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        loadConfigValues();

        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning(
                    "Gagal membuat folder data plugin: "
                            + getDataFolder().getAbsolutePath()
            );
        }

        String databaseFileName = getConfig().getString("database.file-name", "marriage.db");
        File dbFile = new File(getDataFolder(), databaseFileName);

        database = new Database(dbFile);
        database.connect();
        database.initTables();

        vaultHook = new VaultHook();
        if (vaultHook.isAvailable()) {
            getLogger().info("[Hook] Vault Economy berhasil terhubung.");
        } else {
            getLogger().info("[Hook] Vault Economy tidak ditemukan. Fitur gift money dinonaktifkan.");
        }

        essentialsHook = new EssentialsHook();
        if (essentialsHook.isAvailable()) {
            getLogger().info("[Hook] EssentialsX berhasil terhubung.");
        } else {
            getLogger().info("[Hook] EssentialsX tidak ditemukan. Fitur TP dan Home dinonaktifkan.");
        }

        marryCommand = new MarryCommand(this);
        registerCommand("marry", marryCommand);

        getServer().getPluginManager().registerEvents(
                new MarriageListener(marryCommand),
                this
        );

        getLogger().info("MarriagePlugin Berhasil Dimuat! (v" + getDescription().getVersion() + ")");
    }

    @Override
    public void onDisable() {
        marryCommand = null;

        if (database != null) {
            database.close();
            database = null;
        }

        vaultHook = null;
        essentialsHook = null;
        instance = null;

        getLogger().info("MarriagePlugin Dinonaktifkan");
    }

    private void registerCommand(
            String commandName,
            org.bukkit.command.CommandExecutor executor
    ) {
        PluginCommand command = getCommand(commandName);

        if (command == null) {
            getLogger().severe(
                    "Command '/" + commandName
                            + "' tidak ditemukan di plugin.yml! "
                            + "Pastikan command tersebut terdaftar."
            );
            return;
        }

        command.setExecutor(executor);
        if (executor instanceof org.bukkit.command.TabCompleter tabCompleter) {
            command.setTabCompleter(tabCompleter);
        }
    }

    private void loadConfigValues() {
        var config = getConfig();
        debug = config.getBoolean("debug", false);
    }

    public void reloadPluginConfig() {
        reloadConfig();
        loadConfigValues();
        if (vaultHook != null) vaultHook.setupEconomy();
        if (essentialsHook != null) essentialsHook.setupEssentials();
        getLogger().info("Config berhasil di-reload. Debug mode: " + (debug ? "ON" : "OFF"));
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
        getConfig().set("debug", debug);
        saveConfig();
    }

    public void debugLog(String message) {
        if (debug) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    public Database getDatabase() {
        return database;
    }

    public VaultHook getVaultHook() {
        return vaultHook;
    }

    public EssentialsHook getEssentialsHook() {
        return essentialsHook;
    }

    public static MarriagePlugin getInstance() {
        return instance;
    }
}
