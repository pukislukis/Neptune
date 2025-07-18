package dev.lrxh.neptune.providers.placeholder;

import dev.lrxh.neptune.API;
import dev.lrxh.neptune.Neptune;
import dev.lrxh.neptune.profile.impl.Profile;
import lombok.AllArgsConstructor;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@AllArgsConstructor
public class PlaceholderImpl extends PlaceholderExpansion {
    private final Neptune plugin;

    @Override
    public @NotNull String getIdentifier() {
        return "neptune";
    }

    @Override
    public @NotNull String getAuthor() {
        return "lrxh";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean canRegister() {
        return plugin.isEnabled();
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String identifier) {
        if (player == null || !player.isOnline()) {
            return null;
        }
        Profile profile = API.getProfile(player);
        if (profile == null) {
            return null;
        }

        return PlaceholderManager.get().parse(player, identifier);
    }
}