package net.tfminecraft.tlibs.character.estate;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import net.tfminecraft.tlibs.character.CharacterStatus;
import net.tfminecraft.tlibs.character.Characters;

/**
 * Static access to estates: the {@link EstateService}, the registered {@link EstateParticipant}s and
 * the {@link EstateFallback}. Services are looked up on every call, so registration and
 * unregistration need no handling by callers.
 *
 * <p>Without an estate service, lookups return empty and reports are dropped. Without a fallback,
 * guild answers are {@code UNAVAILABLE}, which callers must treat as "postpone", never as "gone". A
 * service that wrongly answers {@code null} is read the same way: empty, no estates, or
 * {@code UNAVAILABLE}.
 *
 * <p>Every method must be called on the main thread and throws {@link IllegalStateException}
 * otherwise. {@link EstateDecidedEvent} and {@link net.tfminecraft.tlibs.character.CharacterRevivedEvent}
 * are wake-ups only; their delivery is not durable, so participants also reconcile over
 * {@link #decided()}.
 */
public final class Estates {

    private Estates() {
    }

    /** Whether an estate service is registered. */
    public static boolean available() {
        return service().isPresent();
    }

    /** @see EstateService#decision(UUID) */
    public static Optional<EstateDecision> decision(UUID deathId) {
        return service().flatMap(service -> decision(service, deathId));
    }

    /**
     * An immutable copy of the service's list without {@code null} entries, or an empty list without
     * a service.
     *
     * @see EstateService#decided()
     */
    public static List<EstateDecision> decided() {
        return service().map(service -> Optional.ofNullable(service.decided()).orElse(List.of()).stream()
                .filter(Objects::nonNull)
                .toList())
                .orElse(List.of());
    }

    /** @see EstateService#stateOf(UUID) */
    public static Optional<EstateState> stateOf(UUID characterId) {
        return service().flatMap(service -> stateOf(service, characterId));
    }

    /**
     * Reports a participant's outcome for the revision it answered.
     *
     * @return false if no estate service received it; report again at the next reconcile
     * @throws IllegalArgumentException if {@code participantId} is blank or {@code revision} negative
     * @see EstateService#report(UUID, String, long, Outcome)
     */
    public static boolean report(UUID deathId, String participantId, long revision, Outcome outcome) {
        Objects.requireNonNull(deathId, "deathId");
        Counts.text(participantId, "participantId");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        Objects.requireNonNull(outcome, "outcome");
        Optional<EstateService> service = service();
        service.ifPresent(estates -> estates.report(deathId, participantId, revision, outcome));
        return service.isPresent();
    }

    /**
     * Whether a participant may act on this decision now: the testator is DEAD with the decision's
     * {@code deathId}, and that estate is DECIDED (not reverted) with exactly this decision as its
     * current one. A revived testator, a later death, a newer revision or any other change to the
     * decision all fail it, as does a testator whose saved owner differs from the decision's. False
     * without the estate service or the character identity service.
     */
    public static boolean guardHolds(EstateDecision decision) {
        Objects.requireNonNull(decision, "decision");
        Optional<EstateService> service = service();
        if (service.isEmpty()) {
            return false;
        }
        UUID characterId = decision.testator().characterId();
        boolean dead = Characters.info(characterId)
                .map(info -> info.status() == CharacterStatus.DEAD && info.ref().equals(decision.testator()))
                .orElse(false);
        return dead
                && Characters.deathId(characterId).equals(Optional.of(decision.deathId()))
                && stateOf(service.get(), characterId).equals(Optional.of(EstateState.DECIDED))
                && decision(service.get(), decision.deathId()).equals(Optional.of(decision));
    }

    /**
     * Every registered participant, as an immutable list. Call every one; the order is not part of
     * the contract.
     */
    public static List<EstateParticipant> participants() {
        requireMainThread();
        return Bukkit.getServicesManager().getRegistrations(EstateParticipant.class).stream()
                .map(RegisteredServiceProvider::getProvider)
                .toList();
    }

    /** The registered guild fallback, or empty. */
    public static Optional<EstateFallback> fallback() {
        requireMainThread();
        return Optional.ofNullable(Bukkit.getServicesManager().load(EstateFallback.class));
    }

    /**
     * {@link DeathGuild#UNAVAILABLE} without a fallback.
     *
     * @see EstateFallback#deathGuild(UUID, UUID)
     */
    public static DeathGuild deathGuild(UUID characterId, UUID deathId) {
        return fallback().map(guilds -> guilds.deathGuild(characterId, deathId))
                .orElse(DeathGuild.UNAVAILABLE);
    }

    /**
     * {@link GuildLookup#UNAVAILABLE} without a fallback.
     *
     * @see EstateFallback#guild(UUID)
     */
    public static GuildLookup guild(UUID incarnation) {
        return fallback().map(guilds -> guilds.guild(incarnation)).orElse(GuildLookup.UNAVAILABLE);
    }

    /**
     * {@link GuildLookup#UNAVAILABLE} without a fallback.
     *
     * @see EstateFallback#guildByName(String)
     */
    public static GuildLookup guildByName(String name) {
        return fallback().map(guilds -> guilds.guildByName(name)).orElse(GuildLookup.UNAVAILABLE);
    }

    private static Optional<EstateDecision> decision(EstateService service, UUID deathId) {
        return Optional.ofNullable(service.decision(deathId)).flatMap(found -> found);
    }

    private static Optional<EstateState> stateOf(EstateService service, UUID characterId) {
        return Optional.ofNullable(service.stateOf(characterId)).flatMap(found -> found);
    }

    private static Optional<EstateService> service() {
        requireMainThread();
        return Optional.ofNullable(Bukkit.getServicesManager().load(EstateService.class));
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Estates must be used on the main thread");
        }
    }
}
