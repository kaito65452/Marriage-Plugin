package com.ryushin.marriage.data;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Database {

    private final File dbFile;
    private Connection connection;
    private final Logger logger;
    private final ExecutorService dbExecutor;

    public Database(File dbFile) {
        this.dbFile = dbFile;
        this.logger = Logger.getLogger("MarriagePlugin");
        this.dbExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "MarriagePlugin-DB");
            t.setDaemon(true);
            return t;
        });
    }

    public synchronized void connect() {
        try {
            File parent = dbFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);

            try (Statement s = connection.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL;");
                s.execute("PRAGMA busy_timeout=5000;");
                s.execute("PRAGMA synchronous=NORMAL;");
            }

            logger.info("[Database] Berhasil tersambung ke SQLite: " + dbFile.getName());
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[Database] Gagal tersambung ke SQLite", e);
        }
    }

    public synchronized void initTables() {
        String sqlMarriage =
                "CREATE TABLE IF NOT EXISTS marriage (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "player1_uuid TEXT NOT NULL, " +
                "player2_uuid TEXT NOT NULL, " +
                "bond_xp INTEGER DEFAULT 0, " +
                "bond_level INTEGER DEFAULT 1, " +
                "married_at INTEGER NOT NULL" +
                ")";

        String sqlFamily =
                "CREATE TABLE IF NOT EXISTS family_relation (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "player_uuid TEXT NOT NULL, " +
                "related_uuid TEXT NOT NULL, " +
                "relation_type TEXT NOT NULL" +
                ")";

        String sqlHome =
                "CREATE TABLE IF NOT EXISTS marriage_home (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "marriage_id INTEGER NOT NULL, " +
                "name TEXT NOT NULL, " +
                "world TEXT NOT NULL, " +
                "x REAL NOT NULL, " +
                "y REAL NOT NULL, " +
                "z REAL NOT NULL, " +
                "yaw REAL NOT NULL, " +
                "pitch REAL NOT NULL, " +
                "created_by TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL, " +
                "UNIQUE(marriage_id, name)" +
                ")";

        String sqlHomeIndex =
                "CREATE INDEX IF NOT EXISTS idx_home_marriage ON marriage_home(marriage_id)";

        String sqlCooldown =
                "CREATE TABLE IF NOT EXISTS marriage_cooldown (" +
                "player_uuid TEXT NOT NULL, " +
                "cooldown_type TEXT NOT NULL, " +
                "expires_at INTEGER NOT NULL, " +
                "PRIMARY KEY(player_uuid, cooldown_type)" +
                ")";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sqlMarriage);
            stmt.execute(sqlFamily);
            stmt.execute(sqlHome);
            stmt.execute(sqlHomeIndex);
            stmt.execute(sqlCooldown);
            logger.info("[Database] Tabel berhasil di init");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[Database] Gagal membuat table", e);
        }
    }

    public synchronized void saveMarriage(Marriage marriage) {
        String sql =
                "INSERT INTO marriage (player1_uuid, player2_uuid, married_at) VALUES (?, ?, ?)";

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, marriage.getPlayer1Uuid().toString());
            ps.setString(2, marriage.getPlayer2Uuid().toString());
            ps.setLong(3, marriage.getMarriedAt());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    marriage.setId(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[Database] Gagal simpan marriage", e);
        }
    }

    public CompletableFuture<Void> saveMarriageAsync(Marriage marriage) {
        return CompletableFuture.runAsync(() -> saveMarriage(marriage), dbExecutor);
    }

    public synchronized Marriage getMarriageByPlayer(UUID uuid) {
        String sql = "SELECT * FROM marriage WHERE player1_uuid = ? OR player2_uuid = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Marriage(
                            rs.getInt("id"),
                            UUID.fromString(rs.getString("player1_uuid")),
                            UUID.fromString(rs.getString("player2_uuid")),
                            rs.getLong("married_at")
                    );
                }
            }
            return null;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[Database] Gagal ambil data marriage", e);
            return null;
        }
    }

    public CompletableFuture<Marriage> getMarriageByPlayerAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> getMarriageByPlayer(uuid), dbExecutor);
    }

    public synchronized void deleteMarriage(int marriageId) {
        String sql = "DELETE FROM marriage WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, marriageId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[Database] Gagal hapus marriage", e);
        }
    }

    public CompletableFuture<Void> deleteMarriageAsync(int marriageId) {
        return CompletableFuture.runAsync(() -> {
            deleteMarriage(marriageId);
            deleteHomesByMarriage(marriageId);
        }, dbExecutor);
    }

    public synchronized void saveHome(MarriageHome home) {
        String sql = "INSERT INTO marriage_home (marriage_id, name, world, x, y, z, yaw, pitch, created_by, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(marriage_id, name) DO UPDATE SET " +
                "world=excluded.world, x=excluded.x, y=excluded.y, z=excluded.z, " +
                "yaw=excluded.yaw, pitch=excluded.pitch, created_by=excluded.created_by, created_at=excluded.created_at";

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, home.getMarriageId());
            ps.setString(2, home.getName());
            ps.setString(3, home.getWorldName());
            ps.setDouble(4, home.getX());
            ps.setDouble(5, home.getY());
            ps.setDouble(6, home.getZ());
            ps.setFloat(7, home.getYaw());
            ps.setFloat(8, home.getPitch());
            ps.setString(9, home.getCreatedBy().toString());
            ps.setLong(10, home.getCreatedAt());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    home.setId(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[Database] Gagal simpan home", e);
        }
    }

    public CompletableFuture<Void> saveHomeAsync(MarriageHome home) {
        return CompletableFuture.runAsync(() -> saveHome(home), dbExecutor);
    }

    public synchronized List<MarriageHome> getHomesByMarriage(int marriageId) {
        List<MarriageHome> homes = new ArrayList<>();
        String sql = "SELECT * FROM marriage_home WHERE marriage_id = ? ORDER BY name ASC";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, marriageId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    homes.add(new MarriageHome(
                            rs.getInt("id"),
                            rs.getInt("marriage_id"),
                            rs.getString("name"),
                            rs.getString("world"),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch"),
                            UUID.fromString(rs.getString("created_by")),
                            rs.getLong("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[Database] Gagal ambil list home", e);
        }
        return homes;
    }

    public CompletableFuture<List<MarriageHome>> getHomesByMarriageAsync(int marriageId) {
        return CompletableFuture.supplyAsync(() -> getHomesByMarriage(marriageId), dbExecutor);
    }

    public synchronized MarriageHome getHomeByName(int marriageId, String name) {
        String sql = "SELECT * FROM marriage_home WHERE marriage_id = ? AND name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, marriageId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new MarriageHome(
                            rs.getInt("id"),
                            rs.getInt("marriage_id"),
                            rs.getString("name"),
                            rs.getString("world"),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch"),
                            UUID.fromString(rs.getString("created_by")),
                            rs.getLong("created_at")
                    );
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[Database] Gagal ambil home by name", e);
        }
        return null;
    }

    public CompletableFuture<MarriageHome> getHomeByNameAsync(int marriageId, String name) {
        return CompletableFuture.supplyAsync(() -> getHomeByName(marriageId, name), dbExecutor);
    }

    public synchronized void deleteHome(int marriageId, String name) {
        String sql = "DELETE FROM marriage_home WHERE marriage_id = ? AND name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, marriageId);
            ps.setString(2, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[Database] Gagal hapus home", e);
        }
    }

    public CompletableFuture<Void> deleteHomeAsync(int marriageId, String name) {
        return CompletableFuture.runAsync(() -> deleteHome(marriageId, name), dbExecutor);
    }

    public synchronized void deleteHomesByMarriage(int marriageId) {
        String sql = "DELETE FROM marriage_home WHERE marriage_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, marriageId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[Database] Gagal hapus homes marriage", e);
        }
    }

    public synchronized long getCooldown(UUID uuid, String type) {
        String sql = "SELECT expires_at FROM marriage_cooldown WHERE player_uuid = ? AND cooldown_type = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, type);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("expires_at");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[Database] Gagal ambil cooldown", e);
        }
        return 0L;
    }

    public CompletableFuture<Long> getCooldownAsync(UUID uuid, String type) {
        return CompletableFuture.supplyAsync(() -> getCooldown(uuid, type), dbExecutor);
    }

    public synchronized void setCooldown(UUID uuid, String type, long expiresAt) {
        String sql = "INSERT INTO marriage_cooldown (player_uuid, cooldown_type, expires_at) " +
                "VALUES (?, ?, ?) ON CONFLICT(player_uuid, cooldown_type) DO UPDATE SET expires_at=excluded.expires_at";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, type);
            ps.setLong(3, expiresAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[Database] Gagal simpan cooldown", e);
        }
    }

    public CompletableFuture<Void> setCooldownAsync(UUID uuid, String type, long expiresAt) {
        return CompletableFuture.runAsync(() -> setCooldown(uuid, type, expiresAt), dbExecutor);
    }

    public void close() {
        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                dbExecutor.shutdownNow();
            }
        } catch (InterruptedException ignored) {
            dbExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("[Database] Koneksi ditutup");
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[Database] Gagal menutup Koneksi.", e);
        }
    }
}
