package net.tfminecraft.tlibs.character;

import java.util.Objects;

/**
 * One stretch of play as a character. Every activation gets a fresh {@code sessionId}, including a
 * reconnect or switching back to the same character.
 *
 * <p>Work that finishes later should record the session when it starts and check
 * {@link Characters#isCurrent(CharacterSession)} just before applying its effect.
 */
public record CharacterSession(CharacterRef ref, long sessionId) {

    public CharacterSession {
        Objects.requireNonNull(ref, "ref");
    }
}
