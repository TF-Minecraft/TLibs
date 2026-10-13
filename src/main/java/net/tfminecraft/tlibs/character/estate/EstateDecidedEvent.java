package net.tfminecraft.tlibs.character.estate;

import java.util.Objects;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fires on the main thread after an estate decision, or a later revision of it, has been saved. It is
 * a wake-up only: delivery is not durable, so participants also reconcile over
 * {@link Estates#decided()} and must not rely on seeing every revision.
 */
public final class EstateDecidedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final EstateDecision decision;

    public EstateDecidedEvent(EstateDecision decision) {
        this.decision = Objects.requireNonNull(decision, "decision");
    }

    public EstateDecision getDecision() {
        return decision;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
