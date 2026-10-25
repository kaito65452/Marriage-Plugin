package com.ryushin.marriage.data;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

public class Database {

    private final File dbFile;
    private Connection connection;
    private final Logger logger;

    public Database(File dbFile) {
        this.dbFile = dbFile;
        this.logger = Logger.getLogger("MarriagePlugin");
    }

    public void connect() {
        try {
            File parent = dbFile.getParentFile();

            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);

            logger.info("[Database] Berhasil tersambung ke SQLite: " + dbFile.getName());
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[Database] Gagal tersambung ke SQLite", e);
        }
    }

    public void initTables() {
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

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sqlMarriage);
            stmt.execute(sqlFamily);
            logger.info("[Database] Tabel berhasil di init");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[Database] Gagal membuat table", e);
        }
    }

    public void saveMarriage(Marriage marriage) {
        String sql =
                "INSERT INTO marriage " +
                "(player1_uuid, player2_uuid, married_at) " +
                "VALUES (?, ?, ?)";

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

    public Marriage getMarriageByPlayer(UUID uuid) {
        String sql =
                "SELECT * FROM marriage " +
                "WHERE player1_uuid = ? OR player2_uuid = ?";

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

    public void deleteMarriage(int marriageId) {
        String sql = "DELETE FROM marriage WHERE id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, marriageId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[Database] Gagal hapus marriage", e);
        }
    }

    public void close() {
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
