package net.tfminecraft.tlibs.character.estate;

import java.util.UUID;

/**
 * Guild answers for estates, registered in Bukkit's {@code ServicesManager} by SimpleFactions. Call it
 * through {@link Estates}, which answers {@code UNAVAILABLE} when no service is registered.
 *
 * <p>Every method runs on the main thread, only reads, and never creates a guild or treasury.
 * {@code UNAVAILABLE} must never be read as "no guild" or "gone".
 */
public interface EstateFallback {

    /** The guild the character was in at the death {@code deathId}. */
    DeathGuild deathGuild(UUID characterId, UUID deathId);

    /** A guild by incarnation. A realm is looked up by its ruling guild's incarnation. */
    GuildLookup guild(UUID incarnation);

    /** A guild by name, ignoring case; {@code GONE} when no guild has that name now. */
    GuildLookup guildByName(String name);
}
