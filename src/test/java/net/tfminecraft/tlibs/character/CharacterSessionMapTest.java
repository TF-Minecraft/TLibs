package net.tfminecraft.tlibs.character;

import static net.tfminecraft.tlibs.character.CharacterSessionEndEvent.Reason.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class CharacterSessionMapTest {
    private ServerMock server;
    private Plugin plugin;
    private FakeIdentity identity;
    private PlayerMock player;
    private CharacterRef alice;
    private CharacterRef bob;
    private final List<String> ended = new ArrayList<>();

    @BeforeEach
    void start() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        identity = new FakeIdentity();
        server.getServicesManager().register(CharacterIdentity.class, identity, plugin, ServicePriority.Normal);
        player = server.addPlayer();
        alice = new CharacterRef(player.getUniqueId(), UUID.randomUUID());
        bob = new CharacterRef(player.getUniqueId(), UUID.randomUUID());
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    private CharacterSessionMap<String> recordingMap() {
        return new CharacterSessionMap<>(plugin, (session, value) -> ended.add(session.sessionId() + ":" + value));
    }

    @Test
    void readsBackAValueDuringTheSessionThatPutIt() {
        CharacterSessionMap<String> map = recordingMap();
        CharacterSession session = identity.start(player, alice);

        assertTrue(map.put(session, "mana"));
        assertEquals(Optional.of("mana"), map.get(session));
        assertEquals(Optional.of("mana"), map.get(player));
        assertTrue(map.put(session, "more mana"));
        assertEquals(Optional.of("more mana"), map.get(session));
    }

    @Test
    void returningToTheSameCharacterStartsWithNothing() {
        CharacterSessionMap<String> map = recordingMap();
        CharacterSession firstAlice = identity.start(player, alice);
        map.put(firstAlice, "first");

        identity.end(player, SWITCH);
        CharacterSession asBob = identity.start(player, bob);
        assertEquals(Optional.empty(), map.get(player));
        identity.end(player, SWITCH);
        CharacterSession secondAlice = identity.start(player, alice);

        assertEquals(alice, secondAlice.ref());
        assertEquals(Optional.empty(), map.get(secondAlice));
        assertEquals(Optional.empty(), map.get(firstAlice));
        assertEquals(Optional.empty(), map.get(player));
        assertFalse(map.put(firstAlice, "late"));
        assertFalse(map.put(asBob, "late"));
        assertEquals(Optional.empty(), map.get(player));
        assertEquals(List.of(firstAlice.sessionId() + ":first"), ended);
    }

    @Test
    void latePutForAnEndedSessionDoesNotOverwriteTheNextSession() {
        CharacterSessionMap<String> map = recordingMap();
        CharacterSession old = identity.start(player, alice);
        identity.end(player, QUIT);
        CharacterSession next = identity.start(player, alice);
        map.put(next, "current");

        assertFalse(map.put(old, "stale"));
        assertEquals(Optional.of("current"), map.get(next));
        assertEquals(Optional.empty(), map.get(old));
    }

    @Test
    void storedValueIsUnreadableOnceTheSessionIsNoLongerCurrent() {
        CharacterSessionMap<String> map = new CharacterSessionMap<>(plugin);
        CharacterSession session = identity.start(player, alice);
        map.put(session, "value");
        server.getServicesManager().unregisterAll(plugin);

        assertEquals(Optional.empty(), map.get(session));
        assertFalse(map.put(session, "value"));
    }

    @Test
    void sessionEndCallsOnEndOnceWithTheValueBeforeRemovingIt() {
        List<Optional<String>> storedDuringEnd = new ArrayList<>();
        AtomicReference<CharacterSessionMap<String>> map = new AtomicReference<>();
        map.set(new CharacterSessionMap<>(plugin, (session, value) -> {
            ended.add(value);
            storedDuringEnd.add(map.get().remove(session));
        }));
        CharacterSession session = identity.start(player, alice);
        map.get().put(session, "flush me");

        identity.end(player, DEATH);
        server.getPluginManager().callEvent(new CharacterSessionEndEvent(player, session, SHUTDOWN));

        assertEquals(List.of("flush me"), ended);
        assertEquals(List.of(Optional.of("flush me")), storedDuringEnd);
        assertEquals(Optional.empty(), map.get().remove(session));
    }

    @Test
    void entryIsRemovedEvenWhenOnEndFails() {
        CharacterSessionMap<String> map = new CharacterSessionMap<>(plugin, (session, value) -> {
            ended.add(value);
            throw new IllegalStateException("flush failed");
        });
        CharacterSession session = identity.start(player, alice);
        map.put(session, "value");

        try {
            identity.end(player, QUIT);
        } catch (RuntimeException ignored) {
            // MockBukkit may rethrow listener failures; the server only logs them.
        }

        assertEquals(List.of("value"), ended);
        assertEquals(Optional.empty(), map.remove(session));
    }

    @Test
    void endOfAnotherSessionLeavesTheEntryAlone() {
        CharacterSessionMap<String> map = recordingMap();
        CharacterSession session = identity.start(player, alice);
        map.put(session, "kept");

        CharacterSession stranger = new CharacterSession(new CharacterRef(UUID.randomUUID(), UUID.randomUUID()), 99);
        server.getPluginManager().callEvent(new CharacterSessionEndEvent(player, stranger, QUIT));
        server.getPluginManager().callEvent(
                new CharacterSessionEndEvent(player, new CharacterSession(alice, session.sessionId() + 1), QUIT));

        assertEquals(Optional.of("kept"), map.get(session));
        assertEquals(List.of(), ended);
    }

    @Test
    void mapWithoutOnEndStillClearsEndedSessions() {
        CharacterSessionMap<String> map = new CharacterSessionMap<>(plugin);
        CharacterSession session = identity.start(player, alice);
        map.put(session, "value");

        identity.end(player, SWITCH);

        assertEquals(Optional.empty(), map.remove(session));
    }

    @Test
    void removeOnlyTakesTheSessionsOwnValue() {
        CharacterSessionMap<String> map = recordingMap();
        CharacterSession old = identity.start(player, alice);
        identity.end(player, SWITCH);
        CharacterSession current = identity.start(player, bob);
        map.put(current, "bob's");

        assertEquals(Optional.empty(), map.remove(old));
        assertEquals(Optional.empty(), map.remove(new CharacterSession(new CharacterRef(UUID.randomUUID(), UUID.randomUUID()), 1)));
        assertEquals(Optional.of("bob's"), map.get(current));
        assertEquals(Optional.of("bob's"), map.remove(current));
        assertEquals(Optional.empty(), map.get(current));
        identity.end(player, QUIT);
        assertEquals(List.of(), ended);
    }

    @Test
    void closeBeforeProviderShutdownFlushesAllEntriesOnce() {
        CharacterSessionMap<String> map = recordingMap();
        PlayerMock second = server.addPlayer();
        CharacterSession first = identity.start(player, alice);
        CharacterSession next = identity.start(second,
                new CharacterRef(second.getUniqueId(), UUID.randomUUID()));
        map.put(first, "first");
        map.put(next, "second");

        map.close();
        map.close();
        identity.end(player, SHUTDOWN);
        identity.end(second, SHUTDOWN);

        assertEquals(2, ended.size());
        assertTrue(ended.contains(first.sessionId() + ":first"));
        assertTrue(ended.contains(next.sessionId() + ":second"));
        assertEquals(Optional.empty(), map.remove(first));
        assertEquals(Optional.empty(), map.remove(next));
    }

    @Test
    void closeAfterProviderUnregistersStillFlushesAndDrainsEveryEntryOnFailure() {
        CharacterSessionMap<String> map = new CharacterSessionMap<>(plugin, (session, value) -> {
            ended.add(value);
            if (value.equals("first")) {
                throw new IllegalArgumentException("first failed");
            }
            throw new UnsupportedOperationException("second failed");
        });
        PlayerMock second = server.addPlayer();
        CharacterSession first = identity.start(player, alice);
        CharacterSession next = identity.start(second,
                new CharacterRef(second.getUniqueId(), UUID.randomUUID()));
        map.put(first, "first");
        map.put(next, "second");
        server.getServicesManager().unregisterAll(plugin);

        IllegalStateException failure = assertThrows(IllegalStateException.class, map::close);

        assertEquals(2, failure.getSuppressed().length);
        assertEquals(2, ended.size());
        assertTrue(ended.containsAll(List.of("first", "second")));
        assertEquals(Optional.empty(), map.remove(first));
        assertEquals(Optional.empty(), map.remove(next));
        assertDoesNotThrow(map::close);
    }

    @Test
    void closeDoesNotCatchErrors() {
        CharacterSessionMap<String> map = new CharacterSessionMap<>(plugin, (session, value) -> {
            throw new AssertionError("fatal");
        });
        map.put(identity.start(player, alice), "value");

        assertThrows(AssertionError.class, map::close);
    }

    @Test
    void putEndsAValueLeftByAnEarlierSessionWhoseEndWasMissed() {
        CharacterSessionMap<String> map = recordingMap();
        CharacterSession missed = identity.start(player, alice);
        map.put(missed, "left behind");
        CharacterSession next = identity.start(player, bob);

        assertTrue(map.put(next, "fresh"));
        assertTrue(map.put(next, "fresher"));

        assertEquals(List.of(missed.sessionId() + ":left behind"), ended);
        assertEquals(Optional.of("fresher"), map.get(next));
        assertEquals(Optional.empty(), map.remove(missed));
    }

    @Test
    void failingOnEndForALeftValueIsLoggedAndThePutStillStores() {
        CharacterSessionMap<String> map = new CharacterSessionMap<>(plugin, (session, value) -> {
            ended.add(value);
            throw new IllegalStateException("flush failed");
        });
        CharacterSession missed = identity.start(player, alice);
        map.put(missed, "left behind");
        CharacterSession next = identity.start(player, bob);

        assertTrue(map.put(next, "fresh"));

        assertEquals(List.of("left behind"), ended);
        assertEquals(Optional.of("fresh"), map.get(next));
        assertEquals(Optional.empty(), map.remove(missed));
    }

    @Test
    void endingSessionDoesNotRemoveAValuePutByTheNextSessionDuringOnEnd() {
        AtomicReference<CharacterSessionMap<String>> map = new AtomicReference<>();
        AtomicReference<CharacterSession> next = new AtomicReference<>();
        map.set(new CharacterSessionMap<>(plugin, (session, value) -> {
            ended.add(value);
            next.set(identity.start(player, bob));
            map.get().put(next.get(), "next");
        }));
        map.get().put(identity.start(player, alice), "first");

        identity.end(player, SWITCH);

        assertEquals(List.of("first"), ended);
        assertEquals(Optional.of("next"), map.get().get(next.get()));
    }

    @Test
    void nestedEndAndCloseFromCallbackDoNotFlushTheSameEntryAgain() {
        AtomicReference<CharacterSessionMap<String>> map = new AtomicReference<>();
        map.set(new CharacterSessionMap<>(plugin, (session, value) -> {
            ended.add(value);
            server.getPluginManager().callEvent(new CharacterSessionEndEvent(player, session, QUIT));
            map.get().close();
        }));
        CharacterSession session = identity.start(player, alice);
        map.get().put(session, "once");

        identity.end(player, QUIT);

        assertEquals(List.of("once"), ended);
        assertEquals(Optional.empty(), map.get().remove(session));
        assertThrows(IllegalStateException.class, () -> map.get().put(session, "late"));
    }

    @Test
    void closeStopsListeningAndRejectsFurtherPuts() {
        CharacterSessionMap<String> map = recordingMap();
        CharacterSession session = identity.start(player, alice);
        map.put(session, "value");
        int registered = CharacterSessionEndEvent.getHandlerList().getRegisteredListeners().length;

        map.close();

        assertEquals(registered - 1, CharacterSessionEndEvent.getHandlerList().getRegisteredListeners().length);
        assertEquals(Optional.empty(), map.get(session));
        assertThrows(IllegalStateException.class, () -> map.put(session, "again"));
        identity.end(player, QUIT);
        assertEquals(List.of(session.sessionId() + ":value"), ended);
    }

    @Test
    void rejectsNullArgumentsAndCallsFromOtherThreads() throws Exception {
        assertThrows(NullPointerException.class, () -> new CharacterSessionMap<String>(null));
        CharacterSessionMap<String> map = recordingMap();
        CharacterSession session = identity.start(player, alice);
        assertThrows(NullPointerException.class, () -> map.put(null, "value"));
        assertThrows(NullPointerException.class, () -> map.put(session, null));
        assertThrows(NullPointerException.class, () -> map.get((CharacterSession) null));
        assertThrows(NullPointerException.class, () -> map.remove(null));

        List<Executable> calls = List.of(
                () -> new CharacterSessionMap<String>(plugin),
                () -> map.put(session, "value"),
                () -> map.get(session),
                () -> map.get(player),
                () -> map.remove(session),
                map::close);
        for (Executable call : calls) {
            assertThrows(IllegalStateException.class, () -> CharactersTest.runOffMainThread(call));
        }
        assertEquals(Optional.empty(), map.get(session));
    }
}
