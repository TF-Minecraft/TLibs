package net.tfminecraft.tlibs.character;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;

/**
 * Character identity service, registered in Bukkit's {@code ServicesManager} by RPCharacters once
 * its character directory has loaded. Most plugins should call it through {@link Characters}.
 *
 * <p>Every method must be called on the main thread. Returned values are immutable snapshots that
 * are safe to hand to background work.
 */
public interface CharacterIdentity {

    /** The session the player is playing now, or empty if no character is active. */
    Optional<CharacterSession> currentSession(Player player);

    /**
     * Whether the session is still being played. It turns false as soon as
     * {@link CharacterSessionEndEvent} fires for it and never turns true again.
     */
    boolean isCurrent(CharacterSession session);

    /** A snapshot of any character, online or offline, or empty if it is unknown. */
    Optional<CharacterInfo> info(UUID characterId);

    /**
     * Every character the account owns. Empty means the account's records could not be read; an
     * empty list means the account has no characters. Callers must not treat unknown as none.
     */
    Optional<List<CharacterInfo>> characters(UUID ownerId);

    /**
     * The character the account played most recently, dead or alive, or empty if none is known.
     */
    Optional<CharacterInfo> lastPlayed(UUID ownerId);

    /**
     * The name other players see now, including masks and temporary aliases. Use
     * {@link CharacterInfo#name()} for the real name.
     */
    String displayName(Player player);
}
