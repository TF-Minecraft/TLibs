package net.tfminecraft.tlibs.character.estate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
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

import net.tfminecraft.tlibs.character.CharacterIdentity;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterStatus;

class EstatesTest {
    private ServerMock server;
    private Plugin plugin;
    private final CharacterRef testator = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
    private final UUID deathId = UUID.randomUUID();
    private final EstateDecision decision = decision(deathId, 2);

    @BeforeEach
    void start() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    @Test
    void withoutServicesNothingIsKnownAndGuildsAreUnavailable() {
        assertFalse(Estates.available());
        assertEquals(Optional.empty(), Estates.decision(deathId));
        assertEquals(List.of(), Estates.decided());
        assertEquals(Optional.empty(), Estates.stateOf(testator.characterId()));
        assertFalse(Estates.report(deathId, "test", 2, Outcome.done()));
        assertFalse(Estates.guardHolds(decision));
        assertEquals(List.of(), Estates.participants());
        assertEquals(Optional.empty(), Estates.fallback());
        assertSame(DeathGuild.UNAVAILABLE, Estates.deathGuild(testator.characterId(), deathId));
        assertSame(GuildLookup.UNAVAILABLE, Estates.guild(UUID.randomUUID()));
        assertSame(GuildLookup.UNAVAILABLE, Estates.guildByName("Thalendor"));
    }

    @Test
    void delegatesToTheRegisteredService() {
        EstateService service = registerService();
        when(service.decision(deathId)).thenReturn(Optional.of(decision));
        when(service.decided()).thenReturn(List.of(decision));
        when(service.stateOf(testator.characterId())).thenReturn(Optional.of(EstateState.DECIDED));
        Outcome outcome = Outcome.retry("body still leased");

        assertTrue(Estates.available());
        assertEquals(Optional.of(decision), Estates.decision(deathId));
        assertEquals(List.of(decision), Estates.decided());
        assertEquals(Optional.of(EstateState.DECIDED), Estates.stateOf(testator.characterId()));
        assertTrue(Estates.report(deathId, "rpcharacters-body", 2, outcome));
        verify(service).report(deathId, "rpcharacters-body", 2, outcome);
    }

    @Test
    void decidedEstatesAreAnImmutableCopy() {
        EstateService service = registerService();
        List<EstateDecision> mutable = new ArrayList<>(List.of(decision));
        when(service.decided()).thenReturn(mutable);

        List<EstateDecision> decided = Estates.decided();
        mutable.clear();

        assertEquals(List.of(decision), decided);
        assertThrows(UnsupportedOperationException.class, () -> decided.add(decision));
    }

    @Test
    void reportsNeedADeathParticipantAndOutcome() {
        EstateService service = registerService();

        assertThrows(NullPointerException.class, () -> Estates.report(null, "test", 1, Outcome.done()));
        assertThrows(NullPointerException.class, () -> Estates.report(deathId, null, 1, Outcome.done()));
        assertThrows(NullPointerException.class, () -> Estates.report(deathId, "test", 1, null));
        assertThrows(IllegalArgumentException.class, () -> Estates.report(deathId, " ", 1, Outcome.done()));
        assertThrows(IllegalArgumentException.class, () -> Estates.report(deathId, "test", -1, Outcome.done()));
        verifyNoInteractions(service);
    }

    @Test
    void followsRegistrationAndUnregistrationWithoutCaching() {
        EstateService service = registerService();
        EstateFallback fallback = mock(EstateFallback.class);
        EstateParticipant participant = participant("denareconomy");
        server.getServicesManager().register(EstateFallback.class, fallback, plugin, ServicePriority.Normal);
        server.getServicesManager().register(EstateParticipant.class, participant, plugin, ServicePriority.Normal);
        when(fallback.guild(any())).thenReturn(GuildLookup.GONE);
        assertTrue(Estates.available());
        assertSame(GuildLookup.GONE, Estates.guild(UUID.randomUUID()));

        server.getServicesManager().unregisterAll(plugin);

        assertFalse(Estates.available());
        assertFalse(Estates.report(deathId, "test", 2, Outcome.done()));
        assertEquals(List.of(), Estates.participants());
        assertSame(GuildLookup.UNAVAILABLE, Estates.guild(UUID.randomUUID()));
        verifyNoInteractions(service);
    }

    @Test
    void listsEveryRegisteredParticipant() {
        EstateParticipant money = participant("denareconomy");
        EstateParticipant locks = participant("thievery");
        Plugin other = MockBukkit.createMockPlugin("Thievery");
        server.getServicesManager().register(EstateParticipant.class, money, plugin, ServicePriority.Normal);
        server.getServicesManager().register(EstateParticipant.class, locks, other, ServicePriority.Normal);

        List<EstateParticipant> participants = Estates.participants();

        assertEquals(Set.of(money, locks), Set.copyOf(participants));
        assertEquals(2, participants.size());
        assertThrows(UnsupportedOperationException.class, () -> participants.add(money));

        server.getServicesManager().unregister(EstateParticipant.class, locks);
        assertEquals(List.of(money), Estates.participants());
    }

    @Test
    void passesGuildQuestionsToTheFallback() {
        EstateFallback fallback = mock(EstateFallback.class);
        server.getServicesManager().register(EstateFallback.class, fallback, plugin, ServicePriority.Normal);
        UUID incarnation = UUID.randomUUID();
        GuildRef ref = new GuildRef(incarnation, "Thalendor", Optional.empty(), Optional.empty(),
                "guild:" + incarnation);
        DeathGuild atDeath = DeathGuild.guild(incarnation, true, Optional.empty(), Optional.empty());
        when(fallback.deathGuild(testator.characterId(), deathId)).thenReturn(atDeath);
        when(fallback.guild(incarnation)).thenReturn(GuildLookup.live(ref));
        when(fallback.guildByName("thalendor")).thenReturn(GuildLookup.GONE);

        assertEquals(Optional.of(fallback), Estates.fallback());
        assertEquals(atDeath, Estates.deathGuild(testator.characterId(), deathId));
        assertEquals(GuildLookup.live(ref), Estates.guild(incarnation));
        assertSame(GuildLookup.GONE, Estates.guildByName("thalendor"));
    }

    @Test
    void guardHoldsWhileTheTestatorIsDeadWithThisDeathAndTheEstateIsDecidedAtThisRevision() {
        EstateService service = registerService();
        CharacterIdentity identity = registerIdentity(CharacterStatus.DEAD, deathId);
        when(service.stateOf(testator.characterId())).thenReturn(Optional.of(EstateState.DECIDED));
        when(service.decision(deathId)).thenReturn(Optional.of(decision));

        assertTrue(Estates.guardHolds(decision));

        when(service.decision(deathId)).thenReturn(Optional.of(decision(deathId, 3)));
        assertFalse(Estates.guardHolds(decision), "a newer revision exists");

        EstateDecision rewritten = new EstateDecision(deathId, testator, decision.diedAt(), decision.decidedAt(),
                Optional.empty(), Optional.empty(), Optional.empty(), Recipient.account("server:void"),
                Recipient.FROZEN, Recipient.FROZEN, Recipient.SENDER, decision.revision());
        when(service.decision(deathId)).thenReturn(Optional.of(rewritten));
        assertFalse(Estates.guardHolds(decision), "the recipients changed without a new revision");

        when(service.decision(deathId)).thenReturn(Optional.empty());
        assertFalse(Estates.guardHolds(decision), "the service no longer has the decision");

        when(service.decision(deathId)).thenReturn(Optional.of(decision));
        CharacterRef otherOwner = new CharacterRef(UUID.randomUUID(), testator.characterId());
        when(identity.info(testator.characterId())).thenReturn(Optional.of(
                new CharacterInfo(otherOwner, "Ilvaran", CharacterStatus.DEAD, null, Set.of(), 0, null)));
        assertFalse(Estates.guardHolds(decision), "the character belongs to another account");
        when(identity.info(testator.characterId())).thenReturn(Optional.of(info(CharacterStatus.DEAD)));

        when(service.decision(deathId)).thenReturn(Optional.of(decision));
        when(service.stateOf(testator.characterId())).thenReturn(Optional.of(EstateState.REVERTED));
        assertFalse(Estates.guardHolds(decision), "staff reverted the estate");

        when(service.stateOf(testator.characterId())).thenReturn(Optional.of(EstateState.DECIDED));
        when(identity.deathId(testator.characterId())).thenReturn(Optional.of(UUID.randomUUID()));
        assertFalse(Estates.guardHolds(decision), "the testator died again");

        when(identity.deathId(testator.characterId())).thenReturn(Optional.of(deathId));
        when(identity.info(testator.characterId())).thenReturn(Optional.of(info(CharacterStatus.ALIVE)));
        assertFalse(Estates.guardHolds(decision), "the testator was revived");

        when(identity.info(testator.characterId())).thenReturn(Optional.empty());
        assertFalse(Estates.guardHolds(decision), "the testator cannot be read");
    }

    @Test
    void readsNullAnswersFromABrokenServiceAsNothingKnown() {
        EstateService service = registerService();
        EstateFallback fallback = mock(EstateFallback.class);
        server.getServicesManager().register(EstateFallback.class, fallback, plugin, ServicePriority.Normal);
        registerIdentity(CharacterStatus.DEAD, deathId);
        when(service.decision(deathId)).thenReturn(null);
        when(service.decided()).thenReturn(null);
        when(service.stateOf(testator.characterId())).thenReturn(null);

        assertEquals(Optional.empty(), Estates.decision(deathId));
        assertEquals(List.of(), Estates.decided());
        assertEquals(Optional.empty(), Estates.stateOf(testator.characterId()));
        assertFalse(Estates.guardHolds(decision));
        assertSame(DeathGuild.UNAVAILABLE, Estates.deathGuild(testator.characterId(), deathId));
        assertSame(GuildLookup.UNAVAILABLE, Estates.guild(UUID.randomUUID()));
        assertSame(GuildLookup.UNAVAILABLE, Estates.guildByName("Thalendor"));

        when(service.decided()).thenReturn(Arrays.asList(decision, null));
        assertEquals(List.of(decision), Estates.decided());
    }

    @Test
    void guardFailsWithoutCharacterIdentity() {
        EstateService service = registerService();
        when(service.stateOf(testator.characterId())).thenReturn(Optional.of(EstateState.DECIDED));
        when(service.decision(deathId)).thenReturn(Optional.of(decision));

        assertFalse(Estates.guardHolds(decision));
        assertThrows(NullPointerException.class, () -> Estates.guardHolds(null));
    }

    @Test
    void rejectsCallsFromOtherThreads() {
        EstateService service = registerService();
        EstateFallback fallback = mock(EstateFallback.class);
        server.getServicesManager().register(EstateFallback.class, fallback, plugin, ServicePriority.Normal);
        List<Executable> calls = List.of(
                Estates::available,
                () -> Estates.decision(deathId),
                Estates::decided,
                () -> Estates.stateOf(testator.characterId()),
                () -> Estates.report(deathId, "test", 1, Outcome.done()),
                () -> Estates.guardHolds(decision),
                Estates::participants,
                Estates::fallback,
                () -> Estates.deathGuild(testator.characterId(), deathId),
                () -> Estates.guild(UUID.randomUUID()),
                () -> Estates.guildByName("Thalendor"));
        for (Executable call : calls) {
            assertThrows(IllegalStateException.class, () -> runOffMainThread(call));
        }
        verifyNoInteractions(service, fallback);
    }

    private EstateService registerService() {
        EstateService service = mock(EstateService.class);
        server.getServicesManager().register(EstateService.class, service, plugin, ServicePriority.Normal);
        return service;
    }

    private CharacterIdentity registerIdentity(CharacterStatus status, UUID currentDeathId) {
        CharacterIdentity identity = mock(CharacterIdentity.class);
        when(identity.info(testator.characterId())).thenReturn(Optional.of(info(status)));
        when(identity.deathId(testator.characterId())).thenReturn(Optional.of(currentDeathId));
        server.getServicesManager().register(CharacterIdentity.class, identity, plugin, ServicePriority.Normal);
        return identity;
    }

    private CharacterInfo info(CharacterStatus status) {
        return new CharacterInfo(testator, "Ilvaran", status, null, Set.of(), 0, null);
    }

    private EstateDecision decision(UUID death, long revision) {
        return new EstateDecision(death, testator, Optional.of(Instant.parse("2026-10-13T12:00:00Z")),
                Instant.parse("2026-10-14T12:00:00Z"), Optional.empty(), Optional.empty(), Optional.empty(),
                Recipient.FROZEN, Recipient.FROZEN, Recipient.FROZEN, Recipient.SENDER, revision);
    }

    private static EstateParticipant participant(String id) {
        EstateParticipant participant = mock(EstateParticipant.class);
        when(participant.id()).thenReturn(id);
        return participant;
    }

    private static void runOffMainThread(Executable call) throws Throwable {
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
