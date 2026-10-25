package com.ryushin.marriage.data;

import java.util.UUID;

public class Marriage {

    private int id;
    private final UUID player1Uuid;
    private final UUID player2Uuid;
    private final long marriedAt;

    public Marriage(UUID player1Uuid, UUID player2Uuid) {
        this(0, player1Uuid, player2Uuid, System.currentTimeMillis());
    }

    public Marriage(int id, UUID player1Uuid, UUID player2Uuid, long marriedAt) {
        this.id = id;
        this.player1Uuid = player1Uuid;
        this.player2Uuid = player2Uuid;
        this.marriedAt = marriedAt;
    }

    public UUID getOtherPlayer(UUID uuid) {
        if (uuid.equals(player1Uuid)) return player2Uuid;
        return player1Uuid;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public UUID getPlayer1Uuid() { return player1Uuid; }
    public UUID getPlayer2Uuid() { return player2Uuid; }
    public long getMarriedAt() { return marriedAt; }
}
