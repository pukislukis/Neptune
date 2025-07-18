package dev.lrxh.neptune.providers.listeners;

import dev.lrxh.neptune.Neptune;
import dev.lrxh.neptune.configs.impl.SettingsLocale; // Import the new setting
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.MoistureChangeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;

import java.util.Objects;

public class LobbyListener implements Listener {

    // Helper method to check if protection is disabled in the player's world
    private boolean isProtectionDisabled(Player player) {
        return SettingsLocale.DISABLED_PROTECTION_WORLDS.getStringList().contains(player.getWorld().getName());
    }

    @EventHandler
    public void onCreatureSpawnEvent(CreatureSpawnEvent event) {
        // Global world events like this are generally kept, as they control the environment
        if (!(event.getEntity() instanceof ArmorStand)) event.setCancelled(true);
        if (!event.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.EGG)) event.setCancelled(true);
    }

    @EventHandler
    public void onWeatherChange(WeatherChangeEvent event) {
        // Global world events like this are generally kept
        event.setCancelled(true);
    }

    @EventHandler
    public void onLeavesDecay(LeavesDecayEvent event) {
        // Global world events like this are generally kept
        event.setCancelled(true);
    }

    @EventHandler
    public void onHangingBreak(HangingBreakEvent event) {
        // Global world events like this are generally kept
        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockSpread(BlockSpreadEvent event) {
        // Global world events like this are generally kept
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (isProtectionDisabled(event.getPlayer())) { // Check for player specific event
            return;
        }
        if (event.getItemDrop().getItemStack().getType().equals(Material.GLASS_BOTTLE)) {
            event.getItemDrop().remove();
        }
    }

    @EventHandler
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (isProtectionDisabled(player)) { // Check for player specific event
            return;
        }

        if (event.getAction() == EntityPotionEffectEvent.Action.ADDED) {
            PotionEffect newEffect = event.getNewEffect();
            if (newEffect != null) {
                player.setMetadata("max_duration_" + newEffect.getType().getName(), new FixedMetadataValue(Neptune.get(), newEffect.getDuration()));
            }
        }

        if (event.getAction() == EntityPotionEffectEvent.Action.REMOVED ||
                event.getAction() == EntityPotionEffectEvent.Action.CLEARED) {
            PotionEffect oldEffect = event.getOldEffect();
            if (oldEffect != null) {
                player.removeMetadata("max_duration_" + oldEffect.getType().getName(), Neptune.get());
            }
        }
    }

    @EventHandler
    public void onMoistureChange(MoistureChangeEvent event) {
        // This event doesn't directly involve a player for cancellation, so keeping it as is or
        // adding world-specific logic requires more context on what this particular event should do.
        // For now, it remains cancelled globally by the plugin's default.
        event.setCancelled(true);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        // This event teleports player to their current location for respawn.
        // It might conflict with custom spawn points if protection is disabled.
        // If you want standard respawn behavior in disabled worlds, you might need to adjust this.
        Player player = event.getPlayer();
        if (isProtectionDisabled(player)) { // Check for player specific event
            return; // Allow default respawn logic
        }
        event.setRespawnLocation(player.getLocation());
    }

    @EventHandler
    public void onSoilChange(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (isProtectionDisabled(player)) { // Check for player specific event
            return;
        }
        if (event.getAction() == Action.PHYSICAL && Objects.requireNonNull(event.getClickedBlock()).getType() == Material.FARMLAND)
            event.setCancelled(true);
    }
}