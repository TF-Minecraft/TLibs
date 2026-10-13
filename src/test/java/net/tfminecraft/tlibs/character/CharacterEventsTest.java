package net.tfminecraft.tlibs.character;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.junit.jupiter.api.Test;

class CharacterEventsTest {
    private final Player player = mock(Player.class);
    private final CharacterRef ref = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
    private final CharacterSession session = new CharacterSession(ref, 7);

    @Test
    void sessionStartCarriesPlayerAndSession() {
        CharacterSessionStartEvent event = new CharacterSessionStartEvent(player, session);
        assertSame(player, event.getPlayer());
        assertSame(session, event.getSession());
        assertSame(CharacterSessionStartEvent.getHandlerList(), event.getHandlers());
        assertFalse(event instanceof Cancellable);
        assertFalse(event.isAsynchronous());
        assertThrows(NullPointerException.class, () -> new CharacterSessionStartEvent(null, session));
        assertThrows(NullPointerException.class, () -> new CharacterSessionStartEvent(player, null));
    }

    @Test
    void sessionEndCarriesPlayerSessionAndReason() {
        CharacterSessionEndEvent event = new CharacterSessionEndEvent(player, session,
                CharacterSessionEndEvent.Reason.DEATH);
        assertSame(player, event.getPlayer());
        assertSame(session, event.getSession());
        assertEquals(CharacterSessionEndEvent.Reason.DEATH, event.getReason());
        assertSame(CharacterSessionEndEvent.getHandlerList(), event.getHandlers());
        assertNotSame(CharacterSessionStartEvent.getHandlerList(), event.getHandlers());
        assertFalse(event instanceof Cancellable);
        assertArrayEquals(new String[] { "SWITCH", "QUIT", "DEATH", "SHUTDOWN" },
                java.util.Arrays.stream(CharacterSessionEndEvent.Reason.values()).map(Enum::name).toArray());
        assertThrows(NullPointerException.class,
                () -> new CharacterSessionEndEvent(null, session, CharacterSessionEndEvent.Reason.QUIT));
        assertThrows(NullPointerException.class,
                () -> new CharacterSessionEndEvent(player, null, CharacterSessionEndEvent.Reason.QUIT));
        assertThrows(NullPointerException.class, () -> new CharacterSessionEndEvent(player, session, null));
    }

    @Test
    void deathCarriesItsDetailsAndAllowsNoKiller() {
        UUID deathId = UUID.randomUUID();
        Instant at = Instant.parse("2026-10-11T12:00:00Z");
        UUID killer = UUID.randomUUID();
        CharacterRef killerCharacter = new CharacterRef(killer, UUID.randomUUID());

        CharacterDeathEvent killed = new CharacterDeathEvent(ref, deathId, at, killer, killerCharacter, "pvp");
        assertSame(ref, killed.getCharacter());
        assertSame(deathId, killed.getDeathId());
        assertSame(at, killed.getAt());
        assertSame(killer, killed.getKillerId());
        assertSame(killerCharacter, killed.getKillerCharacter());
        assertEquals("pvp", killed.getCause());
        assertSame(CharacterDeathEvent.getHandlerList(), killed.getHandlers());
        assertFalse(killed instanceof Cancellable);
        assertFalse(killed.isAsynchronous());

        CharacterDeathEvent fell = new CharacterDeathEvent(ref, deathId, at, null, null, "fall");
        assertNull(fell.getKillerId());
        assertNull(fell.getKillerCharacter());
        CharacterDeathEvent noCharacter = new CharacterDeathEvent(ref, deathId, at, killer, null, "pvp");
        assertSame(killer, noCharacter.getKillerId());
        assertNull(noCharacter.getKillerCharacter());
    }

    @Test
    void deathRejectsMissingDetailsAndAKillerCharacterFromAnotherAccount() {
        UUID deathId = UUID.randomUUID();
        Instant at = Instant.now();
        CharacterRef killerCharacter = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
        assertThrows(NullPointerException.class, () -> new CharacterDeathEvent(null, deathId, at, null, null, "x"));
        assertThrows(NullPointerException.class, () -> new CharacterDeathEvent(ref, null, at, null, null, "x"));
        assertThrows(NullPointerException.class, () -> new CharacterDeathEvent(ref, deathId, null, null, null, "x"));
        assertThrows(NullPointerException.class, () -> new CharacterDeathEvent(ref, deathId, at, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new CharacterDeathEvent(ref, deathId, at, null, killerCharacter, "pvp"));
        assertThrows(IllegalArgumentException.class,
                () -> new CharacterDeathEvent(ref, deathId, at, UUID.randomUUID(), killerCharacter, "pvp"));
    }

    @Test
    void revivalCarriesTheCharacterAndTheDeathItCameBackFrom() {
        UUID deathId = UUID.randomUUID();

        CharacterRevivedEvent event = new CharacterRevivedEvent(ref, deathId);

        assertSame(ref, event.getCharacter());
        assertSame(deathId, event.getPreviousDeathId());
        assertSame(CharacterRevivedEvent.getHandlerList(), event.getHandlers());
        assertNotSame(CharacterDeathEvent.getHandlerList(), event.getHandlers());
        assertFalse(event.isAsynchronous());
        assertThrows(NullPointerException.class, () -> new CharacterRevivedEvent(null, deathId));
        assertThrows(NullPointerException.class, () -> new CharacterRevivedEvent(ref, null));
    }
}
