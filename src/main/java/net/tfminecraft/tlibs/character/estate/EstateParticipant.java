package net.tfminecraft.tlibs.character.estate;

import java.util.UUID;

/**
 * Moves one plugin's records for decided estates. Each plugin registers at most one in Bukkit's
 * {@code ServicesManager}; {@link Estates#participants()} lists them.
 *
 * <p>Every call runs on the main thread. A participant must:
 * <ul>
 *   <li>act only while {@link Estates#guardHolds(EstateDecision)} holds: the testator is DEAD with
 *       the decision's {@code deathId} and the estate is DECIDED at that revision;</li>
 *   <li>be idempotent: settling the same revision again moves nothing twice, and a crash between
 *       its own writes is repaired from its own journal at boot;</li>
 *   <li>leave {@code WAITING} and {@code FROZEN} parts where they are;</li>
 *   <li>also reconcile over {@link Estates#decided()} at boot and hourly, and report each result
 *       with {@link Estates#report}, giving the revision it answered.</li>
 * </ul>
 */
public interface EstateParticipant {

    /** Stable and unique, such as {@code "denareconomy"}; estate records store it. */
    String id();

    /**
     * Makes this plugin's records match the decision. {@link Outcome.Done DONE} counts only for the
     * decision's revision, including when there was nothing to move.
     */
    Outcome settle(EstateDecision decision);

    /** What the character holds today that this participant would move. */
    default Holdings describe(UUID characterId) {
        return Holdings.NONE;
    }
}
