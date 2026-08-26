package io.github.miklires.mprofile.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class ProfileHolder implements InventoryHolder {
    private final UUID playerId;
    private Inventory inventory;

    public ProfileHolder(UUID playerId) { this.playerId = playerId; }
    public UUID playerId() { return playerId; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}
