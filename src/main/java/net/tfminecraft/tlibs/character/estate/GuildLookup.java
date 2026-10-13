package net.tfminecraft.tlibs.character.estate;

import java.util.Objects;

/**
 * The answer to a guild lookup. {@link #UNAVAILABLE} means "cannot tell now" and must never be read
 * as {@link #GONE}: postpone instead.
 */
public sealed interface GuildLookup {

    /** The guild provider is missing or not ready. */
    GuildLookup UNAVAILABLE = new Unavailable();
    /** No such guild exists any more. */
    GuildLookup GONE = new Gone();

    static GuildLookup live(GuildRef guild) {
        return new Live(guild);
    }

    record Unavailable() implements GuildLookup {
    }

    record Gone() implements GuildLookup {
    }

    record Live(GuildRef guild) implements GuildLookup {
        public Live {
            Objects.requireNonNull(guild, "guild");
        }
    }
}
