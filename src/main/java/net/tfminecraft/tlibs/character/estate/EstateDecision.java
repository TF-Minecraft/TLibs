package net.tfminecraft.tlibs.character.estate;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.tfminecraft.tlibs.character.CharacterRef;

/**
 * Who receives each part of one death's estate. Written once at the decision and never re-derived;
 * a waiting part being resolved or a staff assignment makes a new {@code revision}.
 *
 * @param deathId    the death this estate belongs to
 * @param testator   the dead character
 * @param diedAt     when the character died; empty for deaths saved before death times were kept
 * @param decidedAt  when the recipients were first fixed
 * @param heir       the living will heir at the decision, if any
 * @param deathGuild the incarnation of the guild the testator died in, if any
 * @param deathRealm the incarnation of that guild's realm's ruling guild, if known
 * @param money      {@link Recipient.Kind#ACCOUNT ACCOUNT} or {@link Recipient.Kind#FROZEN FROZEN}
 * @param property   {@code CHARACTER}, {@code WAITING} or {@code FROZEN}
 * @param guildLocks {@code CHARACTER}, {@code WAITING} or {@code FROZEN}
 * @param letters    {@code CHARACTER} or {@code SENDER}
 * @param revision   grows with every change to the recipients
 */
public record EstateDecision(UUID deathId, CharacterRef testator, Optional<Instant> diedAt, Instant decidedAt,
        Optional<CharacterRef> heir, Optional<UUID> deathGuild, Optional<UUID> deathRealm,
        Recipient money, Recipient property, Recipient guildLocks, Recipient letters, long revision) {

    private static final Set<Recipient.Kind> MONEY = Set.of(Recipient.Kind.ACCOUNT, Recipient.Kind.FROZEN);
    private static final Set<Recipient.Kind> HOLDINGS = Set.of(Recipient.Kind.CHARACTER, Recipient.Kind.WAITING,
            Recipient.Kind.FROZEN);
    private static final Set<Recipient.Kind> LETTERS = Set.of(Recipient.Kind.CHARACTER, Recipient.Kind.SENDER);

    public EstateDecision {
        Objects.requireNonNull(deathId, "deathId");
        Objects.requireNonNull(testator, "testator");
        Objects.requireNonNull(diedAt, "diedAt");
        Objects.requireNonNull(decidedAt, "decidedAt");
        Objects.requireNonNull(heir, "heir");
        Objects.requireNonNull(deathGuild, "deathGuild");
        Objects.requireNonNull(deathRealm, "deathRealm");
        require(money, "money", MONEY);
        require(property, "property", HOLDINGS);
        require(guildLocks, "guildLocks", HOLDINGS);
        require(letters, "letters", LETTERS);
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
    }

    /** Whether any part still waits for the death guild. Waiting parts hold back a revival. */
    public boolean anyWaiting() {
        return property.isWaiting() || guildLocks.isWaiting();
    }

    private static void require(Recipient recipient, String part, Set<Recipient.Kind> allowed) {
        Objects.requireNonNull(recipient, part);
        if (!allowed.contains(recipient.kind())) {
            throw new IllegalArgumentException(part + " cannot go to " + recipient.kind());
        }
    }
}
