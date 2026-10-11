package net.tfminecraft.tlibs.character;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Minimal provider that starts and ends sessions the way RPCharacters does. */
final class FakeIdentity implements CharacterIdentity {
    private final Map<UUID, CharacterSession> current = new HashMap<>();
    private long nextSessionId = 1;

    CharacterSession start(Player player, CharacterRef ref) {
        CharacterSession session = new CharacterSession(ref, nextSessionId++);
        current.put(player.getUniqueId(), session);
        Bukkit.getPluginManager().callEvent(new CharacterSessionStartEvent(player, session));
        return session;
    }

    void end(Player player, CharacterSessionEndEvent.Reason reason) {
        CharacterSession session = current.remove(player.getUniqueId());
        Bukkit.getPluginManager().callEvent(new CharacterSessionEndEvent(player, session, reason));
    }

    @Override
    public Optional<CharacterSession> currentSession(Player player) {
        return Optional.ofNullable(current.get(player.getUniqueId()));
    }

    @Override
    public boolean isCurrent(CharacterSession session) {
        return session.equals(current.get(session.ref().ownerId()));
    }

    @Override
    public Optional<CharacterInfo> info(UUID characterId) {
        return Optional.empty();
    }

    @Override
    public Optional<List<CharacterInfo>> characters(UUID ownerId) {
        return Optional.empty();
    }

    @Override
    public Optional<CharacterInfo> lastPlayed(UUID ownerId) {
        return Optional.empty();
    }

    @Override
    public String displayName(Player player) {
        return player.getName();
    }
}
