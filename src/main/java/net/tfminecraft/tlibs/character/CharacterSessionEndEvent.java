package net.tfminecraft.tlibs.character;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Fires on the main thread when a session ends, while the outgoing character's data and body are
 * still loaded and before anything is saved or unloaded. The session is already no longer current,
 * so no new action can start against it, but listeners can still clean up against it.
 */
public class CharacterSessionEndEvent extends PlayerEvent {
    private static final HandlerList handlers = new HandlerList();

    /** Why the session ended. */
    public enum Reason {
        SWITCH,
        QUIT,
        DEATH,
        SHUTDOWN
    }

    private final CharacterSession session;
    private final Reason reason;

    public CharacterSessionEndEvent(Player player, CharacterSession session, Reason reason) {
        super(Objects.requireNonNull(player, "player"));
        this.session = Objects.requireNonNull(session, "session");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public CharacterSession getSession() {
        return session;
    }

    public Reason getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
