package com.ryushin.marriage;

import com.ryushin.marriage.command.MarryCommand;
import com.ryushin.marriage.command.FamilyCommand;
import com.ryushin.marriage.command.AdminCommand;
import com.ryushin.marriage.data.Database;
import com.ryushin.marriage.listener.FamilyListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class MarriagePlugin extends JavaPlugin {
    private Database database;
    private static MarriagePlugin instance;

    private boolean familySystemEnabled;
    private boolean debug;

    private MarryCommand marryCommand;
    private FamilyCommand familyCommand;

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

        marryCommand = new MarryCommand(this);
        registerCommand("marry", marryCommand);

        familyCommand = new FamilyCommand(this);
        registerCommand("family", familyCommand);

        registerCommand("marriageadmin", new AdminCommand(this));

        getServer().getPluginManager().registerEvents(
                new FamilyListener(familyCommand, marryCommand),
                this
        );

        getLogger().info("MarriagePlugin Berhasil Dimuat!");
    }

    @Override
    public void onDisable() {
        marryCommand = null;
        familyCommand = null;

        if (database != null) {
            database.close();
            database = null;
        }

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
        familySystemEnabled = config.getBoolean("family.enabled", true);
        debug = config.getBoolean("debug", false);
    }

    public void reloadPluginConfig() {
        reloadConfig();
        loadConfigValues();
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

    public boolean isFamilySystemEnabled() {
        return familySystemEnabled;
    }

    public static MarriagePlugin getInstance() {
        return instance;
    }
}
