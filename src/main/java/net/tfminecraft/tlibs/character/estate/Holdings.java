package net.tfminecraft.tlibs.character.estate;

import java.util.List;
import java.util.Map;

/**
 * What one participant holds for a character today, for the will screen and staff.
 *
 * @param counts records per kind, such as {@code "vehicles" -> 2}
 * @param labels short lines naming individual records, such as a vehicle and where it stands
 */
public record Holdings(Map<String, Integer> counts, List<String> labels) {

    public static final Holdings NONE = new Holdings(Map.of(), List.of());

    public Holdings {
        counts = Counts.copy(counts);
        labels = Counts.copy(labels, "labels");
    }

    /** Whether nothing is counted and nothing is named. */
    public boolean isEmpty() {
        return counts.values().stream().allMatch(count -> count == 0) && labels.isEmpty();
    }
}
