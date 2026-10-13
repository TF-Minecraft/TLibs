package net.tfminecraft.tlibs.character.estate;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.tfminecraft.tlibs.character.CharacterRef;

class EstateValuesTest {
    private final CharacterRef testator = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
    private final CharacterRef heir = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());

    @Test
    void recipientsSayWhereAPartGoes() {
        Recipient toHeir = Recipient.character(heir);
        Recipient toBank = Recipient.account("char:" + heir.characterId() + ":bank");

        assertEquals(Recipient.Kind.CHARACTER, toHeir.kind());
        assertTrue(toHeir.isCharacter());
        assertEquals(Optional.of(heir), toHeir.character());
        assertEquals(Optional.empty(), toHeir.account());
        assertEquals(new Recipient.Character(heir), toHeir);

        assertEquals(Recipient.Kind.ACCOUNT, toBank.kind());
        assertFalse(toBank.isCharacter());
        assertEquals(Optional.of("char:" + heir.characterId() + ":bank"), toBank.account());
        assertEquals(Optional.empty(), toBank.character());

        assertEquals(Recipient.Kind.WAITING, Recipient.WAITING.kind());
        assertTrue(Recipient.WAITING.isWaiting());
        assertFalse(Recipient.WAITING.isFrozen());
        assertEquals(Recipient.Kind.FROZEN, Recipient.FROZEN.kind());
        assertTrue(Recipient.FROZEN.isFrozen());
        assertEquals(Recipient.Kind.SENDER, Recipient.SENDER.kind());
        assertFalse(Recipient.SENDER.isCharacter());
        assertEquals(Optional.empty(), Recipient.SENDER.character());
        assertEquals(new Recipient.Waiting(), Recipient.WAITING);

        assertArrayEquals(new String[] { "ACCOUNT", "CHARACTER", "WAITING", "FROZEN", "SENDER" },
                Arrays.stream(Recipient.Kind.values()).map(Enum::name).toArray());
    }

    @Test
    void recipientsNeedSomewhereToGo() {
        assertThrows(NullPointerException.class, () -> Recipient.character(null));
        assertThrows(NullPointerException.class, () -> Recipient.account(null));
        assertThrows(IllegalArgumentException.class, () -> Recipient.account(" "));
    }

    @Test
    void doneCarriesImmutableCountsAndMovedIds() {
        Map<String, Integer> counts = new HashMap<>(Map.of("locks", 3));
        List<String> moved = new ArrayList<>(List.of("a.yml", "b.yml", "c.yml"));

        Outcome outcome = Outcome.done(counts, moved);
        counts.clear();
        moved.clear();

        Outcome.Done done = assertInstanceOf(Outcome.Done.class, outcome);
        assertEquals(Outcome.Kind.DONE, done.kind());
        assertEquals(Map.of("locks", 3), done.counts());
        assertEquals(List.of("a.yml", "b.yml", "c.yml"), done.movedIds());
        assertThrows(UnsupportedOperationException.class, () -> done.movedIds().add("d.yml"));
        assertEquals(new Outcome.Done(Map.of(), List.of()), Outcome.done());
    }

    @Test
    void otherOutcomesCarryTheirReason() {
        assertEquals(Outcome.Kind.RETRY, Outcome.retry("body still leased").kind());
        assertEquals("body still leased", ((Outcome.Retry) Outcome.retry("body still leased")).reason());
        assertEquals(Outcome.Kind.BLOCKED, Outcome.blocked("trade intent").kind());
        assertEquals("trade intent", ((Outcome.Blocked) Outcome.blocked("trade intent")).reason());
        assertEquals(Outcome.Kind.AT_RESTART, Outcome.AT_RESTART.kind());
        assertEquals(new Outcome.AtRestart(), Outcome.AT_RESTART);
        assertArrayEquals(new String[] { "DONE", "RETRY", "AT_RESTART", "BLOCKED" },
                Arrays.stream(Outcome.Kind.values()).map(Enum::name).toArray());
    }

    @Test
    void outcomesRefuseMissingOrNegativeValues() {
        assertThrows(IllegalArgumentException.class, () -> Outcome.retry(""));
        assertThrows(NullPointerException.class, () -> Outcome.blocked(null));
        assertThrows(IllegalArgumentException.class, () -> Outcome.done(Map.of("locks", -1), List.of()));
        assertThrows(IllegalArgumentException.class, () -> Outcome.done(Map.of(" ", 1), List.of()));
        Map<String, Integer> nullCount = new HashMap<>();
        nullCount.put("locks", null);
        assertThrows(IllegalArgumentException.class, () -> Outcome.done(nullCount, List.of()));
        Map<String, Integer> nullKind = new HashMap<>();
        nullKind.put(null, 1);
        assertThrows(IllegalArgumentException.class, () -> Outcome.done(nullKind, List.of()));
        assertThrows(NullPointerException.class, () -> Outcome.done(null, List.of()));
        assertThrows(NullPointerException.class, () -> Outcome.done(Map.of(), null));
        assertThrows(NullPointerException.class, () -> Outcome.done(Map.of(), Arrays.asList("a", null)));
    }

    @Test
    void holdingsAreEmptyOnlyWhenNothingIsCountedOrNamed() {
        assertTrue(Holdings.NONE.isEmpty());
        assertTrue(new Holdings(Map.of("vehicles", 0), List.of()).isEmpty());
        assertFalse(new Holdings(Map.of("vehicles", 1), List.of()).isEmpty());
        assertFalse(new Holdings(Map.of(), List.of("Horse cart at 100 64 200")).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new Holdings(Map.of("vehicles", -1), List.of()));
        assertThrows(NullPointerException.class, () -> new Holdings(Map.of(), null));

        List<String> labels = new ArrayList<>(List.of("Shop 4"));
        Holdings holdings = new Holdings(Map.of("shops", 1), labels);
        labels.clear();
        assertEquals(List.of("Shop 4"), holdings.labels());
    }

    @Test
    void participantsHoldNothingUnlessTheyDescribeIt() {
        EstateParticipant participant = new EstateParticipant() {
            @Override
            public String id() {
                return "test";
            }

            @Override
            public Outcome settle(EstateDecision decision) {
                return Outcome.done();
            }
        };

        assertSame(Holdings.NONE, participant.describe(UUID.randomUUID()));
    }

    @Test
    void decisionsKeepEveryPartAndTellWhetherAnyWaits() {
        UUID deathId = UUID.randomUUID();
        UUID guild = UUID.randomUUID();
        Instant decidedAt = Instant.parse("2026-10-14T12:00:00Z");
        EstateDecision decision = new EstateDecision(deathId, testator, Optional.empty(), decidedAt,
                Optional.of(heir), Optional.of(guild), Optional.empty(),
                Recipient.account("char:" + heir.characterId() + ":bank"), Recipient.character(heir),
                Recipient.WAITING, Recipient.character(heir), 1);

        assertEquals(deathId, decision.deathId());
        assertEquals(testator, decision.testator());
        assertEquals(Optional.empty(), decision.diedAt());
        assertEquals(Optional.of(guild), decision.deathGuild());
        assertTrue(decision.anyWaiting());
        assertTrue(decision(Recipient.WAITING, Recipient.FROZEN).anyWaiting());
        assertFalse(decision(Recipient.FROZEN, Recipient.FROZEN).anyWaiting());
        assertFalse(decision(Recipient.character(heir), Recipient.character(heir)).anyWaiting());
    }

    @Test
    void decisionsRefuseRecipientsAPartCannotHave() {
        Recipient bank = Recipient.account("char:" + heir.characterId() + ":bank");
        Recipient person = Recipient.character(heir);

        assertThrows(IllegalArgumentException.class, () -> decision(person, person, person, person, 0));
        assertThrows(IllegalArgumentException.class,
                () -> decision(Recipient.WAITING, person, person, person, 0));
        assertThrows(IllegalArgumentException.class, () -> decision(bank, bank, person, person, 0));
        assertThrows(IllegalArgumentException.class,
                () -> decision(bank, Recipient.SENDER, person, person, 0));
        assertThrows(IllegalArgumentException.class, () -> decision(bank, person, bank, person, 0));
        assertThrows(IllegalArgumentException.class,
                () -> decision(bank, person, person, Recipient.FROZEN, 0));
        assertThrows(IllegalArgumentException.class,
                () -> decision(bank, person, person, Recipient.WAITING, 0));
        assertThrows(IllegalArgumentException.class, () -> decision(bank, person, person, person, -1));
        assertThrows(NullPointerException.class, () -> decision(null, person, person, person, 0));
        assertDoesNotThrow(() -> decision(Recipient.FROZEN, Recipient.FROZEN, Recipient.FROZEN,
                Recipient.SENDER, 0));
    }

    @Test
    void decisionsRefuseMissingFields() {
        UUID deathId = UUID.randomUUID();
        Instant now = Instant.now();
        Optional<UUID> none = Optional.empty();
        Recipient f = Recipient.FROZEN;
        Recipient s = Recipient.SENDER;
        assertThrows(NullPointerException.class, () -> new EstateDecision(null, testator, Optional.empty(), now,
                Optional.empty(), none, none, f, f, f, s, 1));
        assertThrows(NullPointerException.class, () -> new EstateDecision(deathId, null, Optional.empty(), now,
                Optional.empty(), none, none, f, f, f, s, 1));
        assertThrows(NullPointerException.class, () -> new EstateDecision(deathId, testator, null, now,
                Optional.empty(), none, none, f, f, f, s, 1));
        assertThrows(NullPointerException.class, () -> new EstateDecision(deathId, testator, Optional.empty(),
                null, Optional.empty(), none, none, f, f, f, s, 1));
        assertThrows(NullPointerException.class, () -> new EstateDecision(deathId, testator, Optional.empty(),
                now, null, none, none, f, f, f, s, 1));
        assertThrows(NullPointerException.class, () -> new EstateDecision(deathId, testator, Optional.empty(),
                now, Optional.empty(), null, none, f, f, f, s, 1));
        assertThrows(NullPointerException.class, () -> new EstateDecision(deathId, testator, Optional.empty(),
                now, Optional.empty(), none, null, f, f, f, s, 1));
    }

    @Test
    void guildAnswersKeepUnavailableApartFromGoneAndNone() {
        UUID incarnation = UUID.randomUUID();
        UUID leader = UUID.randomUUID();
        GuildRef ref = new GuildRef(incarnation, "Thalendor", Optional.empty(), Optional.of(leader),
                "guild:" + incarnation);

        assertNotEquals(GuildLookup.UNAVAILABLE, GuildLookup.GONE);
        assertInstanceOf(GuildLookup.Unavailable.class, GuildLookup.UNAVAILABLE);
        assertInstanceOf(GuildLookup.Gone.class, GuildLookup.GONE);
        assertSame(ref, assertInstanceOf(GuildLookup.Live.class, GuildLookup.live(ref)).guild());
        assertThrows(NullPointerException.class, () -> GuildLookup.live(null));

        assertNotEquals(DeathGuild.UNAVAILABLE, DeathGuild.NONE);
        assertInstanceOf(DeathGuild.Unavailable.class, DeathGuild.UNAVAILABLE);
        assertInstanceOf(DeathGuild.None.class, DeathGuild.NONE);
        DeathGuild.Guild guild = assertInstanceOf(DeathGuild.Guild.class,
                DeathGuild.guild(incarnation, false, Optional.empty(), Optional.empty()));
        assertEquals(incarnation, guild.incarnation());
        assertFalse(guild.live());
        assertThrows(NullPointerException.class,
                () -> DeathGuild.guild(null, true, Optional.empty(), Optional.empty()));
        assertThrows(NullPointerException.class, () -> DeathGuild.guild(incarnation, true, null, Optional.empty()));
        assertThrows(NullPointerException.class, () -> DeathGuild.guild(incarnation, true, Optional.empty(), null));
    }

    @Test
    void guildRefsNeedANameAndTreasury() {
        UUID incarnation = UUID.randomUUID();
        Optional<UUID> none = Optional.empty();
        assertThrows(NullPointerException.class, () -> new GuildRef(null, "G", none, none, "guild:x"));
        assertThrows(IllegalArgumentException.class, () -> new GuildRef(incarnation, "", none, none, "guild:x"));
        assertThrows(NullPointerException.class, () -> new GuildRef(incarnation, "G", null, none, "guild:x"));
        assertThrows(NullPointerException.class, () -> new GuildRef(incarnation, "G", none, null, "guild:x"));
        assertThrows(IllegalArgumentException.class, () -> new GuildRef(incarnation, "G", none, none, " "));
    }

    @Test
    void estateStatesKeepTheirStoredNames() {
        assertArrayEquals(new String[] { "OPEN", "HELD", "CANCELLED", "DECIDED", "REVERTED" },
                Arrays.stream(EstateState.values()).map(Enum::name).toArray());
    }

    private EstateDecision decision(Recipient property, Recipient guildLocks) {
        return decision(Recipient.FROZEN, property, guildLocks, Recipient.SENDER, 1);
    }

    private EstateDecision decision(Recipient money, Recipient property, Recipient guildLocks, Recipient letters,
            long revision) {
        return new EstateDecision(UUID.randomUUID(), testator, Optional.of(Instant.now()), Instant.now(),
                Optional.empty(), Optional.empty(), Optional.empty(), money, property, guildLocks, letters, revision);
    }
}
