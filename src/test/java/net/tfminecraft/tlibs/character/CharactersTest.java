package net.tfminecraft.tlibs.character;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

class CharactersTest {
    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private final CharacterRef ref = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
    private final CharacterSession session = new CharacterSession(ref, 1);
    private final CharacterInfo info = new CharacterInfo(ref, "Alice", CharacterStatus.ALIVE, null, Set.of(), 0, null);

    @BeforeEach
    void start() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Steve");
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    @Test
    void withoutProviderEverythingIsUnknownAndNamesFallBackToTheAccount() {
        assertFalse(Characters.available());
        assertEquals(Optional.empty(), Characters.currentSession(player));
        assertFalse(Characters.isCurrent(session));
        assertEquals(Optional.empty(), Characters.info(ref.characterId()));
        assertEquals(Optional.empty(), Characters.characters(ref.ownerId()));
        assertEquals(Optional.empty(), Characters.lastPlayed(ref.ownerId()));
        assertEquals(Optional.empty(), Characters.legacyHeir(ref.ownerId()));
        assertEquals("Steve", Characters.displayName(player));
    }

    @Test
    void providersWithoutReviewedHeirsReportNone() {
        assertEquals(Optional.empty(), new FakeIdentity().legacyHeir(UUID.randomUUID()));
    }

    @Test
    void delegatesToTheRegisteredProvider() {
        CharacterIdentity identity = mock(CharacterIdentity.class);
        when(identity.currentSession(player)).thenReturn(Optional.of(session));
        when(identity.isCurrent(session)).thenReturn(true);
        when(identity.info(ref.characterId())).thenReturn(Optional.of(info));
        when(identity.characters(ref.ownerId())).thenReturn(Optional.of(List.of(info)));
        when(identity.lastPlayed(ref.ownerId())).thenReturn(Optional.of(info));
        when(identity.displayName(player)).thenReturn("Masked stranger");
        when(identity.legacyHeir(ref.ownerId())).thenReturn(Optional.of(ref));
        server.getServicesManager().register(CharacterIdentity.class, identity, plugin, ServicePriority.Normal);

        assertTrue(Characters.available());
        assertEquals(Optional.of(session), Characters.currentSession(player));
        assertTrue(Characters.isCurrent(session));
        assertEquals(Optional.of(info), Characters.info(ref.characterId()));
        assertEquals(Optional.of(List.of(info)), Characters.characters(ref.ownerId()));
        assertEquals(Optional.of(info), Characters.lastPlayed(ref.ownerId()));
        assertEquals(Optional.of(ref), Characters.legacyHeir(ref.ownerId()));
        assertEquals("Masked stranger", Characters.displayName(player));
    }

    @Test
    void keepsUnknownAccountsApartFromAccountsWithNoCharacters() {
        CharacterIdentity identity = mock(CharacterIdentity.class);
        UUID unreadable = UUID.randomUUID();
        when(identity.characters(unreadable)).thenReturn(Optional.empty());
        when(identity.characters(ref.ownerId())).thenReturn(Optional.of(List.of()));
        server.getServicesManager().register(CharacterIdentity.class, identity, plugin, ServicePriority.Normal);

        assertEquals(Optional.empty(), Characters.characters(unreadable));
        assertEquals(Optional.of(List.of()), Characters.characters(ref.ownerId()));
    }

    @Test
    void characterListsAreImmutableCopies() {
        CharacterIdentity identity = mock(CharacterIdentity.class);
        List<CharacterInfo> mutable = new ArrayList<>(List.of(info));
        when(identity.characters(ref.ownerId())).thenReturn(Optional.of(mutable));
        server.getServicesManager().register(CharacterIdentity.class, identity, plugin, ServicePriority.Normal);

        List<CharacterInfo> characters = Characters.characters(ref.ownerId()).orElseThrow();
        mutable.clear();

        assertEquals(List.of(info), characters);
        assertThrows(UnsupportedOperationException.class, () -> characters.add(info));
    }

    @Test
    void followsRegistrationAndUnregistrationWithoutCaching() {
        CharacterIdentity identity = mock(CharacterIdentity.class);
        when(identity.isCurrent(session)).thenReturn(true);
        server.getServicesManager().register(CharacterIdentity.class, identity, plugin, ServicePriority.Normal);
        assertTrue(Characters.isCurrent(session));

        server.getServicesManager().unregisterAll(plugin);
        assertFalse(Characters.available());
        assertFalse(Characters.isCurrent(session));
    }

    @Test
    void rejectsCallsFromOtherThreads() throws Exception {
        CharacterIdentity identity = mock(CharacterIdentity.class);
        server.getServicesManager().register(CharacterIdentity.class, identity, plugin, ServicePriority.Normal);
        List<Executable> calls = List.of(
                Characters::available,
                () -> Characters.currentSession(player),
                () -> Characters.isCurrent(session),
                () -> Characters.info(ref.characterId()),
                () -> Characters.characters(ref.ownerId()),
                () -> Characters.lastPlayed(ref.ownerId()),
                () -> Characters.legacyHeir(ref.ownerId()),
                () -> Characters.displayName(player));
        for (Executable call : calls) {
            assertThrows(IllegalStateException.class, () -> runOffMainThread(call));
        }
        verifyNoInteractions(identity);
    }

    static void runOffMainThread(Executable call) throws Throwable {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                call.execute();
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        thread.start();
        thread.join();
        if (failure.get() != null) {
            throw failure.get();
        }
    }
}
