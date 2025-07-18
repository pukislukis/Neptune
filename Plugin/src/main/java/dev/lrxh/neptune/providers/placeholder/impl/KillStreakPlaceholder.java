package dev.lrxh.neptune.providers.placeholder.impl;

import dev.lrxh.neptune.API;
import dev.lrxh.neptune.game.match.Match;
import dev.lrxh.neptune.game.match.impl.participant.Participant;
import dev.lrxh.neptune.profile.data.ProfileState;
import dev.lrxh.neptune.profile.impl.Profile;
import dev.lrxh.neptune.providers.placeholder.Placeholder;
import org.bukkit.OfflinePlayer;

public class KillStreakPlaceholder implements Placeholder {
    @Override
    public String parse(OfflinePlayer player, String string) {
        Profile profile = API.getProfile(player);
        if (profile == null) return string;
        Match match = profile.getMatch();
        if (string.equals("killstreak")) {
            if (profile.getState() != ProfileState.IN_GAME || match == null) return "";
            Participant participant = match.getParticipant(player.getUniqueId());
            if (participant == null) return "";
            return String.valueOf(participant.getKillStreak());
        }

        return string;
    }
}