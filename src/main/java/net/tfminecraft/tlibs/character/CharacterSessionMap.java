package net.tfminecraft.tlibs.character;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * Per-session state, at most one value per account. A value is stored against the session that
 * put it and is readable only while that session is current, so a late callback can neither read
 * a stale value nor write into the next session.
 *
 * <p>When a session ends, the map calls {@code onEnd(session, value)} and then removes the entry.
 * Do not keep persisted state such as debts, refunds or character cooldowns here.
 *
 * <p>Main thread only. Call {@link #close()} when the owning plugin disables.
 */
public final class CharacterSessionMap<V> {

    private record Entry<V>(CharacterSession session, V value) {
    }

    private final Map<UUID, Entry<V>> entries = new HashMap<>();
    private final Set<CharacterSession> ending = new HashSet<>();
    private final BiConsumer<CharacterSession, V> onEnd;
    private final Listener listener = new EndListener();
    private boolean closed;

    public CharacterSessionMap(Plugin plugin) {
        this(plugin, null);
    }

    /**
     * @param onEnd called with the stored value when its session ends, before it is removed; may
     *              be {@code null}
     */
    public CharacterSessionMap(Plugin plugin, BiConsumer<CharacterSession, V> onEnd) {
        Objects.requireNonNull(plugin, "plugin");
        Characters.requireMainThread();
        this.onEnd = onEnd;
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    /**
     * Stores the value for the session's account, replacing any earlier value.
     *
     * @return false, storing nothing, if the session is not current
     */
    public boolean put(CharacterSession session, V value) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(value, "value");
        Characters.requireMainThread();
        if (closed) {
            throw new IllegalStateException("CharacterSessionMap is closed");
        }
        if (!Characters.isCurrent(session)) {
            return false;
        }
        entries.put(ownerOf(session), new Entry<>(session, value));
        return true;
    }

    /** The value stored by this session, if the session is still current. */
    public Optional<V> get(CharacterSession session) {
        Objects.requireNonNull(session, "session");
        Characters.requireMainThread();
        Entry<V> entry = entries.get(ownerOf(session));
        if (entry == null || !entry.session().equals(session) || !Characters.isCurrent(session)) {
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    /** The value stored by the player's current session. */
    public Optional<V> get(Player player) {
        return Characters.currentSession(player).flatMap(this::get);
    }

    /**
     * Removes the value if it was stored by this session. Does not call {@code onEnd}.
     *
     * @return the removed value, or empty if this session had none
     */
    public Optional<V> remove(CharacterSession session) {
        Objects.requireNonNull(session, "session");
        Characters.requireMainThread();
        Entry<V> entry = entries.get(ownerOf(session));
        if (entry == null || !entry.session().equals(session)) {
            return Optional.empty();
        }
        entries.remove(ownerOf(session));
        return Optional.of(entry.value());
    }

    /**
     * Stops listening and flushes remaining values through {@code onEnd}, even if the provider
     * has already stopped. Safe to call again or from a callback. All values are flushed before
     * any callback failures are reported together as an {@link IllegalStateException}.
     */
    public void close() {
        Characters.requireMainThread();
        if (closed) {
            return;
        }
        closed = true;
        HandlerList.unregisterAll(listener);
        IllegalStateException failure = null;
        for (Entry<V> entry : List.copyOf(entries.values())) {
            try {
                end(entry.session());
            } catch (RuntimeException | Error ex) {
                if (failure == null) {
                    failure = new IllegalStateException("Failed to flush character session state");
                }
                failure.addSuppressed(ex);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void end(CharacterSession session) {
        UUID owner = ownerOf(session);
        Entry<V> entry = entries.get(owner);
        if (entry == null || !entry.session().equals(session)) {
            return;
        }
        if (!ending.add(session)) {
            return;
        }
        try {
            if (onEnd != null) {
                onEnd.accept(session, entry.value());
            }
        } finally {
            entries.remove(owner, entry);
            ending.remove(session);
        }
    }

    private static UUID ownerOf(CharacterSession session) {
        return session.ref().ownerId();
    }

    private final class EndListener implements Listener {
        @EventHandler(priority = EventPriority.MONITOR)
        public void onSessionEnd(CharacterSessionEndEvent event) {
            end(event.getSession());
        }
    }
}
