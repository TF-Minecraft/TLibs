package net.tfminecraft.tlibs.character.estate;

import java.util.Objects;
import java.util.Optional;

import net.tfminecraft.tlibs.character.CharacterRef;

/**
 * Where one part of an estate goes. Refer to the cases qualified, such as {@code Recipient.Character},
 * so they do not shadow {@code java.lang.Character}.
 */
public sealed interface Recipient {

    /** Stable names for storage and display. */
    enum Kind {
        ACCOUNT, CHARACTER, WAITING, FROZEN, SENDER
    }

    /** Waiting for the death guild to have a leader, or to be gone. */
    Recipient WAITING = new Waiting();
    /** Held for staff. */
    Recipient FROZEN = new Frozen();
    /** Letters go back to whoever sent them. */
    Recipient SENDER = new Sender();

    static Recipient account(String accountId) {
        return new Account(accountId);
    }

    static Recipient character(CharacterRef character) {
        return new Character(character);
    }

    Kind kind();

    /** The receiving character, or empty unless this is {@link Kind#CHARACTER}. */
    default Optional<CharacterRef> character() {
        return Optional.empty();
    }

    /** The receiving ledger account, or empty unless this is {@link Kind#ACCOUNT}. */
    default Optional<String> account() {
        return Optional.empty();
    }

    default boolean isCharacter() {
        return kind() == Kind.CHARACTER;
    }

    default boolean isWaiting() {
        return kind() == Kind.WAITING;
    }

    default boolean isFrozen() {
        return kind() == Kind.FROZEN;
    }

    /** A ledger account, such as {@code char:<id>:bank} or {@code guild:<incarnation>}. */
    record Account(String accountId) implements Recipient {
        public Account {
            Counts.text(accountId, "accountId");
        }

        @Override
        public Kind kind() {
            return Kind.ACCOUNT;
        }

        @Override
        public Optional<String> account() {
            return Optional.of(accountId);
        }
    }

    record Character(CharacterRef ref) implements Recipient {
        public Character {
            Objects.requireNonNull(ref, "ref");
        }

        @Override
        public Kind kind() {
            return Kind.CHARACTER;
        }

        @Override
        public Optional<CharacterRef> character() {
            return Optional.of(ref);
        }
    }

    record Waiting() implements Recipient {
        @Override
        public Kind kind() {
            return Kind.WAITING;
        }
    }

    record Frozen() implements Recipient {
        @Override
        public Kind kind() {
            return Kind.FROZEN;
        }
    }

    record Sender() implements Recipient {
        @Override
        public Kind kind() {
            return Kind.SENDER;
        }
    }
}
