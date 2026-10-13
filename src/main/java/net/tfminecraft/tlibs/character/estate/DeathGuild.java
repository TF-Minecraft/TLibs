package net.tfminecraft.tlibs.character.estate;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The guild a character died in. {@link #UNAVAILABLE} means "cannot tell now" and must never be read
 * as {@link #NONE}: postpone instead.
 */
public sealed interface DeathGuild {

    /** The guild provider is missing, not ready, or has not recorded this death yet. */
    DeathGuild UNAVAILABLE = new Unavailable();
    /** The character died without a guild. */
    DeathGuild NONE = new None();

    static DeathGuild guild(UUID incarnation, boolean live, Optional<UUID> leader, Optional<UUID> realm) {
        return new Guild(incarnation, live, leader, realm);
    }

    record Unavailable() implements DeathGuild {
    }

    record None() implements DeathGuild {
    }

    /**
     * @param incarnation the incarnation of the guild at death
     * @param live        whether that incarnation still exists; false means it is gone
     * @param leader      the leader's character ID now, if the guild is live and the seat is filled;
     *                    the leader may be dead
     * @param realm       the incarnation of the realm's ruling guild recorded at death, if known;
     *                    unknown counts as gone
     */
    record Guild(UUID incarnation, boolean live, Optional<UUID> leader, Optional<UUID> realm)
            implements DeathGuild {
        public Guild {
            Objects.requireNonNull(incarnation, "incarnation");
            Objects.requireNonNull(leader, "leader");
            Objects.requireNonNull(realm, "realm");
        }
    }
}
