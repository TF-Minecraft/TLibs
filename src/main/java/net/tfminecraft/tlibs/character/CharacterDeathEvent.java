package net.tfminecraft.tlibs.character;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fires on the main thread after a character's permanent death has been saved. It does not fire if
 * the save fails.
 *
 * <p>{@code deathId} is saved on the character, so separate deaths stay distinct after a revival.
 * Delivery is not durable: a crash between the save and this event loses it, and a later replay
 * may deliver it again, so consumers must be idempotent by {@code deathId}.
 */
public class CharacterDeathEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final CharacterRef character;
    private final UUID deathId;
    private final Instant at;
    private final UUID killerId;
    private final CharacterRef killerCharacter;
    private final String cause;

    /**
     * @param killerId        the killer's account, or {@code null}
     * @param killerCharacter the killer's character, or {@code null}; owned by {@code killerId}
     */
    public CharacterDeathEvent(CharacterRef character, UUID deathId, Instant at, UUID killerId,
            CharacterRef killerCharacter, String cause) {
        this.character = Objects.requireNonNull(character, "character");
        this.deathId = Objects.requireNonNull(deathId, "deathId");
        this.at = Objects.requireNonNull(at, "at");
        this.cause = Objects.requireNonNull(cause, "cause");
        if (killerCharacter != null && !killerCharacter.ownerId().equals(killerId)) {
            throw new IllegalArgumentException("killerCharacter must belong to killerId");
        }
        this.killerId = killerId;
        this.killerCharacter = killerCharacter;
    }

    public CharacterRef getCharacter() {
        return character;
    }

    public UUID getDeathId() {
        return deathId;
    }

    /** When the character died. */
    public Instant getAt() {
        return at;
    }

    /** The killer's account, or {@code null}. */
    public UUID getKillerId() {
        return killerId;
    }

    /** The killer's character, or {@code null}. */
    public CharacterRef getKillerCharacter() {
        return killerCharacter;
    }

    public String getCause() {
        return cause;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
