package net.tfminecraft.tlibs.character.estate;

import java.util.List;
import java.util.Map;

/** A participant's answer to one decision revision. */
public sealed interface Outcome {

    /** Stable names for storage and display. */
    enum Kind {
        DONE, RETRY, AT_RESTART, BLOCKED
    }

    /** The participant moves its records at the next restart. */
    Outcome AT_RESTART = new AtRestart();

    /** Everything this participant holds for the estate is where the decision says. */
    static Outcome done(Map<String, Integer> counts, List<String> movedIds) {
        return new Done(counts, movedIds);
    }

    /** Nothing to move at this revision: the parts are not settled or the testator holds none. */
    static Outcome done() {
        return new Done(Map.of(), List.of());
    }

    /** Try again later; the provider calls again within the hour. */
    static Outcome retry(String reason) {
        return new Retry(reason);
    }

    /** Listed for staff and never retried until staff clear it. */
    static Outcome blocked(String reason) {
        return new Blocked(reason);
    }

    Kind kind();

    /**
     * @param counts   records moved per kind, such as {@code "locks" -> 12}
     * @param movedIds the ID of every record moved (shop IDs, file paths, vehicle IDs and so on)
     */
    record Done(Map<String, Integer> counts, List<String> movedIds) implements Outcome {
        public Done {
            counts = Counts.copy(counts);
            movedIds = Counts.copy(movedIds, "movedIds");
        }

        @Override
        public Kind kind() {
            return Kind.DONE;
        }
    }

    record Retry(String reason) implements Outcome {
        public Retry {
            Counts.text(reason, "reason");
        }

        @Override
        public Kind kind() {
            return Kind.RETRY;
        }
    }

    record AtRestart() implements Outcome {
        @Override
        public Kind kind() {
            return Kind.AT_RESTART;
        }
    }

    record Blocked(String reason) implements Outcome {
        public Blocked {
            Counts.text(reason, "reason");
        }

        @Override
        public Kind kind() {
            return Kind.BLOCKED;
        }
    }
}
