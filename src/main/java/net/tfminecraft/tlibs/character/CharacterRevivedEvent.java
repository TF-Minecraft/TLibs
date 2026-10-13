package net.tfminecraft.tlibs.character;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fires on the main thread after a dead character has been saved as alive again. It does not fire if
 * the save fails.
 *
 * <p>{@code previousDeathId} is the death the character came back from; a later death gets a new one.
 * Delivery is not durable, so consumers must also reconcile from {@link Characters#info} and
 * {@link Characters#deathId} after a restart.
 */
public final class CharacterRevivedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final CharacterRef character;
    private final UUID previousDeathId;

    public CharacterRevivedEvent(CharacterRef character, UUID previousDeathId) {
        this.character = Objects.requireNonNull(character, "character");
        this.previousDeathId = Objects.requireNonNull(previousDeathId, "previousDeathId");
    }

    public CharacterRef getCharacter() {
        return character;
    }

    public UUID getPreviousDeathId() {
        return previousDeathId;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
