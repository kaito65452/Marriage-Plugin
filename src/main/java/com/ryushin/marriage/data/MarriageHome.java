package com.ryushin.marriage.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public class MarriageHome {

    private int id;
    private final int marriageId;
    private final String name;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final UUID createdBy;
    private final long createdAt;

    public MarriageHome(int marriageId, String name, Location loc, UUID createdBy) {
        this(0, marriageId, name, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch(), createdBy, System.currentTimeMillis());
    }

    public MarriageHome(int id, int marriageId, String name, String worldName,
                        double x, double y, double z, float yaw, float pitch,
                        UUID createdBy, long createdAt) {
        this.id = id;
        this.marriageId = marriageId;
        this.name = name;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z, yaw, pitch);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getMarriageId() { return marriageId; }
    public String getName() { return name; }
    public String getWorldName() { return worldName; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public UUID getCreatedBy() { return createdBy; }
    public long getCreatedAt() { return createdAt; }
}
