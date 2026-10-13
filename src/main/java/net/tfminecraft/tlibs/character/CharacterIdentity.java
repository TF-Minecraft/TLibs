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
 *
 * <p>A provider must:
 * <ul>
 *   <li>fire {@link CharacterSessionEndEvent} for a session, and let it finish, before starting
 *       the account's next session;</li>
 *   <li>never nest transitions for one account, such as starting a session from an end
 *       listener;</li>
 *   <li>never reuse a session ID;</li>
 *   <li>fire a {@link CharacterSessionEndEvent.Reason#SHUTDOWN SHUTDOWN} end for every active
 *       session when it disables;</li>
 *   <li>return immutable lists.</li>
 * </ul>
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

    /** The most recent death's durable identity, retained after revival; empty if unknown. */
    default Optional<UUID> deathId(UUID characterId) {
        return Optional.empty();
    }

    /**
     * Living characters whose real name, {@link CharacterInfo#name()}, equals {@code name} ignoring
     * case. Masks and temporary aliases never match. Several characters can share a name, so the
     * result may hold more than one; it is empty when none match. The list is immutable.
     */
    default List<CharacterInfo> findByName(String name) {
        return List.of();
    }

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
     * The character that inherits what the account owned before characters had their own bodies,
     * money and memberships, or empty if the account has no reviewed heir. Plugins moving
     * account-wide data onto characters give it to this character, so nothing is duplicated.
     */
    default Optional<CharacterRef> legacyHeir(UUID ownerId) {
        return Optional.empty();
    }

    /**
     * Whether {@link #legacyHeir(UUID)} answers from the reviewed heirs. Until it does, an empty
     * heir means "not known yet", so plugins must not migrate or discard account-wide data.
     */
    default boolean legacyHeirsReady() {
        return false;
    }

    /**
     * The name other players see now, including masks and temporary aliases. Use
     * {@link CharacterInfo#name()} for the real name.
     */
    String displayName(Player player);
}
