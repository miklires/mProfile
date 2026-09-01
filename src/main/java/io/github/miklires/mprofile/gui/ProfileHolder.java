package io.github.miklires.mprofile.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public final class ProfileHolder implements InventoryHolder {
    private final UUID playerId;
    private Inventory inventory;
    private final Map<Integer, ProfileAction> actions = new HashMap<>();

    public ProfileHolder(UUID playerId) { this.playerId = playerId; }
    public UUID playerId() { return playerId; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }
    void action(int slot, ProfileAction action) { actions.put(slot, action); }
    ProfileAction action(int slot) { return actions.get(slot); }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}
