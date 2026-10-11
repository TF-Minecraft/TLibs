package net.tfminecraft.tlibs.character;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CharacterValuesTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID character = UUID.randomUUID();
    private final CharacterRef ref = new CharacterRef(owner, character);

    @Test
    void refRequiresBothIdsAndComparesByValue() {
        assertThrows(NullPointerException.class, () -> new CharacterRef(null, character));
        assertThrows(NullPointerException.class, () -> new CharacterRef(owner, null));
        assertEquals(new CharacterRef(owner, character), ref);
        assertNotEquals(new CharacterRef(owner, UUID.randomUUID()), ref);
    }

    @Test
    void sessionRequiresRefAndDiffersBySessionId() {
        assertThrows(NullPointerException.class, () -> new CharacterSession(null, 1));
        assertEquals(new CharacterSession(ref, 4), new CharacterSession(ref, 4));
        assertNotEquals(new CharacterSession(ref, 4), new CharacterSession(ref, 5));
    }

    @Test
    void infoCopiesTraitsInLowerCaseAndCannotBeChanged() {
        List<String> source = new ArrayList<>(List.of("Brave", "keen_EYED", "brave"));
        Instant played = Instant.parse("2026-10-11T12:00:00Z");
        CharacterInfo info = new CharacterInfo(ref, "Alice", CharacterStatus.ALIVE, "elf",
                new java.util.LinkedHashSet<>(source), 3600, played);
        source.clear();

        assertEquals(List.of("brave", "keen_eyed"), List.copyOf(info.traits()));
        assertThrows(UnsupportedOperationException.class, () -> info.traits().add("other"));
        assertEquals(ref, info.ref());
        assertEquals("Alice", info.name());
        assertEquals("elf", info.raceId());
        assertEquals(3600, info.playtimeSeconds());
        assertEquals(played, info.lastPlayedAt());
        assertTrue(info.alive());
    }

    @Test
    void infoAllowsMissingRaceAndLastPlayedButNotOtherNulls() {
        CharacterInfo info = new CharacterInfo(ref, "Bob", CharacterStatus.DEAD, null, Set.of(), 0, null);
        assertNull(info.raceId());
        assertNull(info.lastPlayedAt());
        assertFalse(info.alive());
        assertFalse(new CharacterInfo(ref, "Bob", CharacterStatus.MISSING, null, Set.of(), 0, null).alive());

        assertThrows(NullPointerException.class,
                () -> new CharacterInfo(null, "Bob", CharacterStatus.DEAD, null, Set.of(), 0, null));
        assertThrows(NullPointerException.class,
                () -> new CharacterInfo(ref, null, CharacterStatus.DEAD, null, Set.of(), 0, null));
        assertThrows(NullPointerException.class,
                () -> new CharacterInfo(ref, "Bob", null, null, Set.of(), 0, null));
        assertThrows(NullPointerException.class,
                () -> new CharacterInfo(ref, "Bob", CharacterStatus.DEAD, null, null, 0, null));
        assertThrows(NullPointerException.class, () -> new CharacterInfo(ref, "Bob", CharacterStatus.DEAD, null,
                new java.util.HashSet<>(Arrays.asList("brave", null)), 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> new CharacterInfo(ref, "Bob", CharacterStatus.DEAD, null, Set.of(), -1, null));
    }
}
