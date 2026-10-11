package net.tfminecraft.tlibs.character;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Fires on the main thread once a character is ready to play: after setup on join and after a
 * switch. From this point the session is current.
 */
public class CharacterSessionStartEvent extends PlayerEvent {
    private static final HandlerList handlers = new HandlerList();

    private final CharacterSession session;

    public CharacterSessionStartEvent(Player player, CharacterSession session) {
        super(Objects.requireNonNull(player, "player"));
        this.session = Objects.requireNonNull(session, "session");
    }

    public CharacterSession getSession() {
        return session;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
