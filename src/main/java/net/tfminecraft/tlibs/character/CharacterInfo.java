package net.tfminecraft.tlibs.character;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable snapshot of a character, safe to hand to background work.
 *
 * @param ref             the character
 * @param name            the canonical plain name, without masks or aliases
 * @param status          whether the character is alive, dead or missing
 * @param raceId          the race ID, or {@code null} when none is set
 * @param traits          trait IDs in lower case
 * @param playtimeSeconds total playtime in seconds
 * @param lastPlayedAt    when the character was last played, or {@code null} if never
 */
public record CharacterInfo(CharacterRef ref, String name, CharacterStatus status, String raceId,
        Set<String> traits, long playtimeSeconds, Instant lastPlayedAt) {

    public CharacterInfo {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(traits, "traits");
        if (playtimeSeconds < 0) {
            throw new IllegalArgumentException("playtimeSeconds must not be negative");
        }
        Set<String> copy = new LinkedHashSet<>();
        for (String trait : traits) {
            copy.add(Objects.requireNonNull(trait, "trait").toLowerCase(Locale.ROOT));
        }
        traits = Collections.unmodifiableSet(copy);
    }

    /** Whether the status is {@link CharacterStatus#ALIVE}. */
    public boolean alive() {
        return status == CharacterStatus.ALIVE;
    }
}
