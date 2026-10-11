package net.tfminecraft.tlibs.character;

import java.util.Objects;
import java.util.UUID;

/**
 * Durable identity of one character: the account that owns it and the character's own ID.
 */
public record CharacterRef(UUID ownerId, UUID characterId) {

    public CharacterRef {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(characterId, "characterId");
    }
}
