package net.tfminecraft.tlibs.character.estate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Estate records, registered in Bukkit's {@code ServicesManager} by RPCharacters. Most plugins call
 * it through {@link Estates}.
 *
 * <p>Every method must be called on the main thread. Returned values are immutable snapshots and are
 * never {@code null}; {@link Estates} reads a {@code null} answer as empty.
 */
public interface EstateService {

    /** The decision for this death, DECIDED or REVERTED, or empty if it has not been decided. */
    Optional<EstateDecision> decision(UUID deathId);

    /** Every DECIDED estate at its current revision; REVERTED estates are left out. */
    List<EstateDecision> decided();

    /** The state of the estate for the character's latest death, or empty if it has none. */
    Optional<EstateState> stateOf(UUID characterId);

    /**
     * Records a participant's outcome for the revision it answered. An outcome for an older revision
     * than the current one does not count towards the estate.
     */
    void report(UUID deathId, String participantId, long revision, Outcome outcome);
}
