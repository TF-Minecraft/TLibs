package net.tfminecraft.tlibs.character.estate;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A guild that exists now.
 *
 * @param incarnation     this incarnation of the guild; a guild re-founded under the same name has
 *                        another
 * @param name            the guild's name
 * @param realm           the incarnation of its realm's ruling guild, if it has a realm
 * @param leader          the leader's character ID, if the seat is filled; the leader may be dead
 * @param treasuryAccount the guild's ledger account, {@code guild:<incarnation>}
 */
public record GuildRef(UUID incarnation, String name, Optional<UUID> realm, Optional<UUID> leader,
        String treasuryAccount) {

    public GuildRef {
        Objects.requireNonNull(incarnation, "incarnation");
        Counts.text(name, "name");
        Objects.requireNonNull(realm, "realm");
        Objects.requireNonNull(leader, "leader");
        Counts.text(treasuryAccount, "treasuryAccount");
    }
}
