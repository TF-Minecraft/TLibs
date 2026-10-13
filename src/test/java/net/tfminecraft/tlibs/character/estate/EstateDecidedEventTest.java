package net.tfminecraft.tlibs.character.estate;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.tfminecraft.tlibs.character.CharacterRef;

class EstateDecidedEventTest {

    @Test
    void carriesTheDecision() {
        EstateDecision decision = new EstateDecision(UUID.randomUUID(),
                new CharacterRef(UUID.randomUUID(), UUID.randomUUID()), Optional.empty(), Instant.now(),
                Optional.empty(), Optional.empty(), Optional.empty(), Recipient.FROZEN, Recipient.FROZEN,
                Recipient.FROZEN, Recipient.SENDER, 1);

        EstateDecidedEvent event = new EstateDecidedEvent(decision);

        assertSame(decision, event.getDecision());
        assertSame(EstateDecidedEvent.getHandlerList(), event.getHandlers());
        assertFalse(event.isAsynchronous());
        assertThrows(NullPointerException.class, () -> new EstateDecidedEvent(null));
    }
}
