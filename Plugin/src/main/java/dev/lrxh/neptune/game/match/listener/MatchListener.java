package dev.lrxh.neptune.game.match.listener;

import dev.lrxh.neptune.API;
import dev.lrxh.neptune.configs.impl.MessagesLocale;
import dev.lrxh.neptune.configs.impl.SettingsLocale; // Import the new setting
import dev.lrxh.neptune.events.MatchParticipantDeathEvent;
import dev.lrxh.neptune.game.arena.Arena;
import dev.lrxh.neptune.game.arena.impl.StandAloneArena;
import dev.lrxh.neptune.game.kit.Kit;
import dev.lrxh.neptune.game.kit.impl.KitRule;
import dev.lrxh.neptune.game.match.Match;
import dev.lrxh.neptune.game.match.impl.MatchState;
import dev.lrxh.neptune.game.match.impl.participant.DeathCause;
import dev.lrxh.neptune.game.match.impl.participant.Participant;
import dev.lrxh.neptune.game.match.impl.participant.ParticipantColor;
import dev.lrxh.neptune.game.match.impl.team.TeamFightMatch;
import dev.lrxh.neptune.profile.ProfileService;
import dev.lrxh.neptune.profile.data.ProfileState;
import dev.lrxh.neptune.profile.impl.Profile;
import dev.lrxh.neptune.providers.clickable.Replacement;
import dev.lrxh.neptune.utils.CC;
import dev.lrxh.neptune.utils.LocationUtil;
import dev.lrxh.neptune.utils.WorldUtils;
import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener; // Make sure this import is present
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class MatchListener implements Listener { // Ensure implements Listener is here

    // Helper method to check if protection is disabled in the player's world
    private boolean isProtectionDisabled(Player player) {
        return SettingsLocale.DISABLED_PROTECTION_WORLDS.getStringList().contains(player.getWorld().getName());
    }

    @EventHandler
    public void onBlockPlaceEvent(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode().equals(GameMode.CREATIVE)) return;
        Profile profile = API.getProfile(player);
        if (profile == null) return;

        // Allow block placement if protection is disabled in this world and player is not in a match/kit editor
        if (isProtectionDisabled(player) && !profile.hasState(ProfileState.IN_GAME, ProfileState.IN_KIT_EDITOR)) {
            return;
        }

        Match match = profile.getMatch();
        Location blockLocation = event.getBlock().getLocation();

        if (profile.hasState(ProfileState.IN_KIT_EDITOR)) {
            event.setCancelled(true);
            player.sendMessage(CC.color("&cYou can't place blocks here!"));
            return;
        }

        if (match != null && match.getKit().is(KitRule.BUILD)) {
            if (match.getState().equals(MatchState.STARTING)) {
                event.setCancelled(true);
                player.sendMessage(CC.color("&cYou can't place blocks yet!"));
                return;
            }

            StandAloneArena arena = (StandAloneArena) match.getArena();

            // Check height limit
            if (blockLocation.getY() >= arena.getLimit()) {
                event.setCancelled(true);
                player.sendMessage(CC.color("&cYou have reached build limit!"));
                return;
            }

            // Check arena boundaries
            if (!LocationUtil.isInside(blockLocation, arena.getMin(), arena.getMax())) {
                event.setCancelled(true);
                player.sendMessage(CC.color("&cYou can't build outside the arena!"));
                return;
            }

            if (blockLocation.equals(match.getArena().getRedSpawn()) || blockLocation.equals(match.getArena().getRedSpawn().clone().add(0, 1, 0))) {
                event.setCancelled(true);
                return;
            }

            if (blockLocation.equals(match.getArena().getBlueSpawn()) || blockLocation.equals(match.getArena().getBlueSpawn().clone().add(0, 1, 0))) {
                event.setCancelled(true);
                return;
            }

            match.getPlacedBlocks().add(blockLocation);
        } else {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityPush(EntityPushedByEntityAttackEvent event) {
        // This event is usually for specific combat mechanics, likely not affected by world protection.
        if (!(event.getPushedBy() instanceof WindCharge wc)) return;
        Entity pushed = event.getEntity();

        if (!(wc.getShooter() instanceof Player shooter)) {
            event.setCancelled(true);
            return;
        }
        if (!(pushed instanceof Player target)) {
            return;
        }

        if (!target.canSee(shooter) || !shooter.canSee(target)) {
            event.setCancelled(true);
        }

        if (event.getPushedBy() instanceof Player attacker) {
            ItemStack item = attacker.getInventory().getItemInMainHand();
            if (item.getType() == Material.MACE) {
                if (!(event.getEntity() instanceof Player victim)) return;

                if (!victim.canSee(attacker) || !attacker.canSee(victim)) {
                    event.setCancelled(true);
                }
            }
        }
    }


    @EventHandler
    public void onPlayerDeathEvent(PlayerDeathEvent event) {
        // This event should generally always be handled by the plugin, regardless of world.
        Player player = event.getEntity();
        event.deathMessage(null);
        event.getDrops().clear();
        Profile profile = API.getProfile(player);
        if (profile == null) return;
        if (profile.getMatch() != null) {
            Match match = profile.getMatch();
            Participant participant = match.getParticipant(player.getUniqueId());
            if (participant == null) return;
            MatchParticipantDeathEvent deathEvent = new MatchParticipantDeathEvent(match, participant);
            Bukkit.getPluginManager().callEvent(deathEvent);
            participant.setDeathCause(participant.getLastAttacker() != null ? DeathCause.KILL : DeathCause.DIED);
            match.onDeath(participant);
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        // Fix: Explicitly cast event.getEntity() to Player to resolve 'player cannot be resolved'
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player player)) return; // Explicitly declare 'player' here

        Profile attackerProfile = API.getProfile(attacker);
        if (attackerProfile == null) return;

        // If protection is disabled in attacker's world and they are not in a game, allow interaction.
        if (isProtectionDisabled(attacker) && !attackerProfile.hasState(ProfileState.IN_GAME)) {
            return;
        }

        Profile profile = API.getProfile(player);
        if (profile == null) return;

        if (profile.getMatch() == null || attackerProfile.getState().equals(ProfileState.IN_SPECTATOR)) {
            event.setCancelled(true);
            return;
        }
        Match match = profile.getMatch();

        if (!attackerProfile.getMatch().getUuid().equals(match.getUuid())) {
            event.setCancelled(true);
            return;
        }

        if (match.getParticipant(attacker).isDead()) {
            event.setCancelled(true);
        }

        if (match instanceof TeamFightMatch teamFightMatch) {
            if (teamFightMatch.onSameTeam(player.getUniqueId(), attacker.getUniqueId())) {
                event.setCancelled(true);
            }
        }

        if (!match.getState().equals(MatchState.IN_ROUND)) {
            event.setCancelled(true);
        }

        match.getParticipant(player.getUniqueId()).setLastAttacker(match.getParticipant(attacker.getUniqueId()));
    }

    @EventHandler
    public void onPressurePlate(PlayerInteractEvent event) {
        // This is a game-specific mechanic (parkour), should not be affected by world protection.
        Player player = event.getPlayer();
        if (event.getAction() == Action.PHYSICAL) {
            Material blockType = event.getClickedBlock() != null ? event.getClickedBlock().getType() : null;
            if (blockType == null) return;
            Profile profile = API.getProfile(player);
            if (profile == null) return;
            Match match = profile.getMatch();
            if (match == null) return;
            if (!match.getKit().is(KitRule.PARKOUR)) return;
            if (!match.getState().equals(MatchState.IN_ROUND)) return;
            Participant participant = match.getParticipant(player);
            if (participant == null) return;
            if (blockType.equals(Material.HEAVY_WEIGHTED_PRESSURE_PLATE)) {
                if (participant.setCurrentCheckPoint(event.getClickedBlock().getLocation().clone().add(0, 1, 0))) {
                    match.broadcast(MessagesLocale.PARKOUR_CHECKPOINT,
                            new Replacement("<player>", participant.getNameColored()),
                            new Replacement("<checkpoint>", String.valueOf(participant.getCheckPoint())),
                            new Replacement("<time>", participant.getTime().formatSecondsMillis()));
                }
            } else if (blockType.equals(Material.LIGHT_WEIGHTED_PRESSURE_PLATE)) {
                match.win(participant);
                match.broadcast(MessagesLocale.PARKOUR_END,
                        new Replacement("<player>", participant.getNameColored()),
                        new Replacement("<time>", participant.getTime().formatSecondsMillis()));
            }
        }
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (player.getGameMode().equals(GameMode.CREATIVE)) return;
            Profile profile = API.getProfile(player);

            // Allow item pickup if protection is disabled in this world and player is not in a game
            if (isProtectionDisabled(player) && !profile.hasState(ProfileState.IN_GAME)) {
                return;
            }

            if (!profile.getState().equals(ProfileState.IN_GAME)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode().equals(GameMode.CREATIVE)) return;
        Profile profile = API.getProfile(player);
        if (profile == null) return;

        // Allow bucket empty if protection is disabled in this world and player is not in a match/kit editor
        if (isProtectionDisabled(player) && !profile.hasState(ProfileState.IN_GAME, ProfileState.IN_KIT_EDITOR)) {
            return;
        }

        Match match = profile.getMatch();
        Location blockLocation = event.getBlock().getLocation();
        if (profile.hasState(ProfileState.IN_KIT_EDITOR)) {
            event.setCancelled(true);
            player.sendMessage(CC.color("&cYou can't place blocks here!"));
            return;
        }
        if (match != null && match.getKit().is(KitRule.BUILD)) {
            if (match.getState().equals(MatchState.STARTING)) {
                event.setCancelled(true);
                player.sendMessage(CC.color("&cYou can't place blocks yet!"));
                return;
            }

            StandAloneArena arena = (StandAloneArena) match.getArena();

            if (blockLocation.getY() >= arena.getLimit()) {
                event.setCancelled(true);
                player.sendMessage(CC.color("&cYou have reached build limit!"));
                return;
            }

            // Check arena boundaries for bucket empty
            if (!LocationUtil.isInside(blockLocation, arena.getMin(), arena.getMax())) {
                event.setCancelled(true);
                player.sendMessage(CC.color("&cYou can't place water outside the arena!"));
                return;
            }

            match.getPlacedBlocks().add(blockLocation);
        } else {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        ProjectileSource shooter = event.getEntity().getShooter();
        if (!(shooter instanceof Player player)) return;

        Profile profile = API.getProfile(player);
        if (profile == null) return;

        // Allow projectile launch if protection is disabled in this world and player is not in a game
        if (isProtectionDisabled(player) && !profile.hasState(ProfileState.IN_GAME)) {
            return;
        }

        Match match = profile.getMatch();
        if (match == null) {
            event.setCancelled(true);
            return;
        }
        if (match.getState().equals(MatchState.STARTING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntityMonitor(EntityDamageByEntityEvent event) {
        // This is a complex event involving combat; careful consideration is needed.
        // If players are in a match, existing combat rules apply.
        // If not in a match, and world protection is disabled, standard Minecraft damage should apply.
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player player)) return; // Explicitly declare 'player' here

        Profile attackerProfile = API.getProfile(attacker.getUniqueId());
        if (attackerProfile == null) return;

        // If attacker is not in a match and protection is disabled in their world, allow damage
        if (isProtectionDisabled(attacker) && attackerProfile.getMatch() == null) {
            return;
        }

        Profile profile = API.getProfile(player);
        if (profile == null) return;

        if (profile.getMatch() == null || attackerProfile.getState().equals(ProfileState.IN_SPECTATOR)) {
            event.setCancelled(true);
            return;
        }
        Match match = profile.getMatch();

        if (!attackerProfile.getMatch().getUuid().equals(match.getUuid())) {
            event.setCancelled(true);
            return;
        }

        if (match.getParticipant(attacker).isDead()) {
            event.setCancelled(true);
        }

        if (match instanceof TeamFightMatch teamFightMatch) {
            if (teamFightMatch.onSameTeam(player.getUniqueId(), attacker.getUniqueId())) {
                event.setCancelled(true);
            }
        }

        if (!match.getState().equals(MatchState.IN_ROUND)) {
            event.setCancelled(true);
        }

        match.getParticipant(player.getUniqueId()).setLastAttacker(match.getParticipant(attacker.getUniqueId()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerHitEvent(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player target && event.getDamager() instanceof Player damager) {
            Profile targetProfile = API.getProfile(target);
            Profile playerProfile = API.getProfile(damager.getUniqueId());

            // If not in a game and protection is disabled in world, allow hit event to pass through
            if (isProtectionDisabled(target) && targetProfile.getMatch() == null) {
                return;
            }

            if (targetProfile.getState() == ProfileState.IN_GAME && playerProfile.getState().equals(ProfileState.IN_GAME) && damager.getAttackCooldown() >= 0.2) {
                Match match = targetProfile.getMatch();
                Participant opponent = match.getParticipant(target.getUniqueId());

                if (damager.getAttackCooldown() > 0.7)
                    match.getParticipant(damager.getUniqueId()).handleHit(opponent);
                opponent.resetCombo();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // If protection is disabled in the player's world AND they are not in a match, allow damage.
        if (isProtectionDisabled(player) && API.getProfile(player).getMatch() == null) {
            return;
        }

        if (!(event.getFinalDamage() >= player.getHealth())) return;
        if (player.getInventory().getItemInMainHand().getType().equals(Material.TOTEM_OF_UNDYING) ||
                player.getInventory().getItemInOffHand().getType().equals(Material.TOTEM_OF_UNDYING)) return;

        Profile profile = API.getProfile(player);
        if (profile == null) return;
        Match match = profile.getMatch();
        if (match == null) {
            event.setCancelled(true); // Still cancel if no match and not in disabled world
            return;
        }
        Participant participant = match.getParticipant(player.getUniqueId());
        participant.setDeathCause(DeathCause.DIED);
        match.onDeath(participant);
        player.setHealth(20.0f);
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerMoveEvent(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) return;
        Player player = event.getPlayer();
        Profile profile = API.getProfile(player);
        if (profile == null) return;

        // If protection is disabled in player's world and they are not in a match, allow move event to pass through.
        // However, gravity/death Y logic should still apply in game worlds even if listed as "disabled protection" if a match is active.
        if (isProtectionDisabled(player) && profile.getMatch() == null) {
            return;
        }

        Match match = profile.getMatch();
        if (match == null) return; // if match is null, further match-specific logic is skipped.

        Arena arena = match.getArena();

        Participant participant = match.getParticipant(player.getUniqueId());
        if (participant == null) return;
        if (participant.isFrozen()) {
            Location to = event.getTo();
            Location from = event.getFrom();
            if ((to.getX() != from.getX() || to.getZ() != from.getZ())) {
                player.teleport(from);
                return;
            }
        }

        if (player.getLocation().getY() <= arena.getDeathY() && !participant.isDead()) {
            if (match.getKit().is(KitRule.PARKOUR)) {
                if (participant.getCurrentCheckPoint() != null) {
                    player.teleport(participant.getCurrentCheckPoint());
                } else {
                    player.teleport(match.getSpawn(participant));
                }
            } else {
                participant.setDeathCause(DeathCause.DIED);
                match.onDeath(participant);
            }
            return;
        }
        if (match.getState().equals(MatchState.IN_ROUND)) {
            Block block = player.getLocation().getBlock();

            if (match.getKit().is(KitRule.DROPPER)) {
                if (block.getType() == Material.WATER) {
                    match.win(participant);
                }
                return;
            }

            if (match.getKit().is(KitRule.SUMO)) {
                if (block.getType() == Material.WATER) {
                    participant.setDeathCause(participant.getLastAttacker() != null ? DeathCause.KILL : DeathCause.DIED);
                    match.onDeath(participant);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // This seems to be a separate move event handler, keeping its original logic.
        // It does not include direct cancellations based on player interaction in general.
        if (!event.hasChangedPosition()) return;
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to.getY() > from.getY()) {
            Block currentBlock = player.getLocation().getBlock();

            if (currentBlock.getType() == Material.END_PORTAL) {

            }
        }
    }


    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // This is for specific block interactions, not general player interactions.
        // Likely intended to prevent certain material interactions.
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        Material blockType = clickedBlock.getType();

        if (isStrippable(blockType)) {
            event.setCancelled(true);
        }
    }

    private boolean isStrippable(Material material) {
        return material == Material.OAK_LOG ||
                material == Material.SPRUCE_LOG ||
                material == Material.BIRCH_LOG ||
                material == Material.JUNGLE_LOG ||
                material == Material.ACACIA_LOG ||
                material == Material.DARK_OAK_LOG ||
                material == Material.MANGROVE_LOG ||
                material == Material.CHERRY_LOG ||
                material == Material.CRIMSON_STEM ||
                material == Material.COPPER_BLOCK ||
                material == Material.WARPED_STEM;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            Profile profile = API.getProfile(player);
            if (profile == null) return;

            // If protection is disabled in the player's world AND they are not in a match, allow damage.
            if (isProtectionDisabled(player) && profile.getMatch() == null) {
                return;
            }

            Match match = profile.getMatch();
            if (match == null) {
                event.setCancelled(true); // Still cancel if no match and not in disabled world
                return;
            }

            Kit kit = match.getKit();

            if (profile.hasState(ProfileState.IN_SPECTATOR)) {
                event.setCancelled(true);
                return;
            }

            if (match.getState().equals(MatchState.STARTING) || match.getState().equals(MatchState.ENDING)) {
                event.setCancelled(true);
                return;
            }
            
            EntityDamageEvent.DamageCause cause = event.getCause();

            if (cause.equals(EntityDamageEvent.DamageCause.FALL)) {
                if (!kit.is(KitRule.FALL_DAMAGE)) {
                    event.setCancelled(true);
                    return;
                }
            }

            if (!kit.is(KitRule.DAMAGE)) {
                event.setDamage(0);
                return;
            }

            if (match.getParticipant(player).isDead()) {
                event.setCancelled(true);
                return;
            }

            // Pengecualian ditambahkan di sini
            if (cause.equals(EntityDamageEvent.DamageCause.FALL) ||
                cause.equals(EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) ||
                cause.equals(EntityDamageEvent.DamageCause.ENTITY_EXPLOSION)) {
                return;
            }
            
            event.setDamage(event.getDamage() * kit.getDamageMultiplier());
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            Profile profile = API.getProfile(player);
            if (profile == null) return;

            // Allow food level change if protection is disabled in this world and player is not in a game
            if (isProtectionDisabled(player) && !profile.hasState(ProfileState.IN_GAME)) {
                return;
            }

            Match match = profile.getMatch();
            if (!profile.getState().equals(ProfileState.IN_GAME)) {
                event.setCancelled(true);
                return;
            }
            if (match == null) return;

            if (!match.getKit().is(KitRule.HUNGER)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerRegainHealth(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player player) {
            Profile profile = API.getProfile(player);
            if (profile == null) return;

            // Allow health regain if protection is disabled in this world and player is not in a match
            if (isProtectionDisabled(player) && profile.getMatch() == null) {
                return;
            }

            Match match = profile.getMatch();
            if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED) {
                if (match != null && !match.getKit().is(KitRule.SATURATION_HEAL)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onBlockBreakEvent(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;
        Profile profile = API.getProfile(player);
        if (profile == null) return;

        // Allow block break if protection is disabled in this world and player is not in a game/spectator
        if (isProtectionDisabled(player) && !profile.hasState(ProfileState.IN_GAME, ProfileState.IN_SPECTATOR)) {
            return;
        }

        if (profile.getState() == ProfileState.IN_LOBBY || profile.getState() == ProfileState.IN_SPECTATOR) {
            event.setCancelled(true);
            return;
        }

        Match match = profile.getMatch();
        if (match == null) return; // if match is null, further match-specific logic is skipped.

        Material blockType = event.getBlock().getType();
        Location blockLocation = event.getBlock().getLocation();

        if (blockType == Material.FIRE) return;
        if (blockType.name().contains("BED")) return;

        boolean cancel = true;

        if (match.getKit().is(KitRule.BUILD) && match.getPlacedBlocks().contains(blockLocation)) {
            cancel = false;
        } else if (match.getKit().is(KitRule.ALLOW_ARENA_BREAK)) {
            if (match.getArena() instanceof StandAloneArena standAloneArena) {
                if (standAloneArena.getWhitelistedBlocks().contains(blockType)) {
                    cancel = false;
                }
            }
        }

        event.setCancelled(cancel);
    }


    @EventHandler()
    public void onBedBreak(BlockBreakEvent event) {
        // This is a game-specific mechanic (Bedwars), should not be affected by world protection.
        Player player = event.getPlayer();
        Profile profile = ProfileService.get().getByUUID(player.getUniqueId());
        Match match = profile.getMatch();
        Material blockType = event.getBlock().getType();
        if (match == null) return;

        if (!match.getKit().is(KitRule.BED_WARS)) return;

        if (match.getKit().is(KitRule.BED_WARS)) {
            if (blockType == Material.OAK_PLANKS || blockType == Material.END_STONE) {
                event.setCancelled(false);
            }
        }

        if (match.getKit().is(KitRule.BED_WARS)) {
            if (event.getBlock().getType().toString().contains("BED")) {
                Location bed = event.getBlock().getLocation();

                Participant participant = match.getParticipant(player.getUniqueId());
                if (participant == null) return;
                Location spawn = match.getSpawn(participant);
                Participant opponent = participant.getOpponent();
                Location opponentSpawn = match.getSpawn(opponent);
                ParticipantColor color = participant.getColor();

                if (bed.distanceSquared(spawn) > bed.distanceSquared(opponentSpawn)) {
                    event.setDropItems(false);
                    match.breakBed(opponent, participant);
                    match.sendTitle(opponent, CC.color(MessagesLocale.BED_BREAK_TITLE.getString()), CC.color(MessagesLocale.BED_BREAK_FOOTER.getString()), 20);
                    match.broadcast(color.equals(ParticipantColor.RED) ? MessagesLocale.BLUE_BED_BROKEN_MESSAGE : MessagesLocale.RED_BED_BROKEN_MESSAGE, new Replacement("<player>", participant.getNameColored()));
                } else {
                    event.setCancelled(true);
                    participant.sendMessage(MessagesLocale.CANT_BREAK_OWN_BED);
                }
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        ProjectileSource shooter = projectile.getShooter();

        if (shooter instanceof Player player) {
            if (player.getGameMode().equals(GameMode.CREATIVE)) return;
            Profile profile = API.getProfile(player);
            if (profile == null) return;

            // Allow projectile hit if protection is disabled in this world and player is not in a game
            if (isProtectionDisabled(player) && !profile.hasState(ProfileState.IN_GAME)) {
                return;
            }

            if (!profile.getState().equals(ProfileState.IN_GAME)) {
                event.setCancelled(true);
            } else {
                getMatchForPlayer(player).ifPresent(match -> {
                    if (projectile instanceof Projectile) {
                        match.getEntities().add(projectile);
                    }
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent event) {
        // This is a match-specific event for arena break rules, should not be affected by world protection.
        Player player = getNearbyPlayer(event.getBlock().getLocation());

        if (player == null) {
            event.setCancelled(true);
            return;
        }

        getMatchForPlayer(player).ifPresent(match -> {
            if (match.getKit().is(KitRule.ALLOW_ARENA_BREAK) && match.getArena() instanceof StandAloneArena standAloneArena) {
                List<Block> originalBlocks = new ArrayList<>(event.blockList());
                List<Block> allowedBlocks = originalBlocks.stream()
                        .filter(block -> standAloneArena.getWhitelistedBlocks().contains(block.getType()))
                        .toList();
                event.blockList().clear();
                event.blockList().addAll(allowedBlocks);
            }
        });

    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode().equals(GameMode.CREATIVE)) return;
        Profile profile = API.getProfile(player);
        if (profile == null) return;

        // Allow item drop if protection is disabled in this world and player is not in a game
        if (isProtectionDisabled(player) && !profile.hasState(ProfileState.IN_GAME)) {
            return;
        }

        if (!profile.getState().equals(ProfileState.IN_GAME)) {
            event.setCancelled(true);
        } else {
            profile.getMatch().getEntities().add(event.getItemDrop());
        }
    }

    private Player getNearbyPlayer(Location location) {
        return WorldUtils.getPlayersInRadius(location, 10)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private Optional<Match> getMatchForPlayer(Player player) {
        Profile profile = API.getProfile(player);
        return Optional.ofNullable(profile)
                .map(Profile::getMatch);
    }
}