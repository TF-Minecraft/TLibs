package net.tfminecraft.tlibs.character;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Static access to the {@link CharacterIdentity} service. The service is looked up on every call,
 * so registration and unregistration need no handling by callers.
 *
 * <p>Without a provider, lookups return empty, {@link #isCurrent(CharacterSession)} returns false
 * and {@link #displayName(Player)} falls back to the account name. That fallback is fine for
 * display and gameplay checks, never for ownership or money.
 *
 * <p>Every method must be called on the main thread and throws {@link IllegalStateException}
 * otherwise.
 */
public final class Characters {

    private Characters() {
    }

    /** Whether a provider is registered. RPCharacters registers once its directory has loaded. */
    public static boolean available() {
        return provider().isPresent();
    }

    /** @see CharacterIdentity#currentSession(Player) */
    public static Optional<CharacterSession> currentSession(Player player) {
        return provider().flatMap(identity -> identity.currentSession(player));
    }

    /** @see CharacterIdentity#isCurrent(CharacterSession) */
    public static boolean isCurrent(CharacterSession session) {
        return provider().map(identity -> identity.isCurrent(session)).orElse(false);
    }

    /** @see CharacterIdentity#info(UUID) */
    public static Optional<CharacterInfo> info(UUID characterId) {
        return provider().flatMap(identity -> identity.info(characterId));
    }

    /** @see CharacterIdentity#deathId(UUID) */
    public static Optional<UUID> deathId(UUID characterId) {
        return provider().flatMap(identity -> identity.deathId(characterId));
    }

    /**
     * An immutable copy of the provider's list.
     *
     * @see CharacterIdentity#characters(UUID)
     */
    public static Optional<List<CharacterInfo>> characters(UUID ownerId) {
        return provider().flatMap(identity -> identity.characters(ownerId)).map(List::copyOf);
    }

    /** @see CharacterIdentity#lastPlayed(UUID) */
    public static Optional<CharacterInfo> lastPlayed(UUID ownerId) {
        return provider().flatMap(identity -> identity.lastPlayed(ownerId));
    }

    /** @see CharacterIdentity#legacyHeir(UUID) */
    public static Optional<CharacterRef> legacyHeir(UUID ownerId) {
        return provider().flatMap(identity -> identity.legacyHeir(ownerId));
    }

    /** @see CharacterIdentity#legacyHeirsReady() */
    public static boolean legacyHeirsReady() {
        return provider().map(CharacterIdentity::legacyHeirsReady).orElse(false);
    }

    /** @see CharacterIdentity#displayName(Player) */
    public static String displayName(Player player) {
        return provider().map(identity -> identity.displayName(player)).orElseGet(player::getName);
    }

    static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Character identity must be used on the main thread");
        }
    }

    private static Optional<CharacterIdentity> provider() {
        requireMainThread();
        return Optional.ofNullable(Bukkit.getServicesManager().load(CharacterIdentity.class));
    }
}
