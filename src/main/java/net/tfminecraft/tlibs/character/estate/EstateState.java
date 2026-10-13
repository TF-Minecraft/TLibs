package net.tfminecraft.tlibs.character.estate;

/** Where one death's estate stands. */
public enum EstateState {
    /** Created at death; the clock is running. */
    OPEN,
    /** Staff stopped the clock. */
    HELD,
    /** The testator was revived before the decision. */
    CANCELLED,
    /** Recipients are fixed and participants settle. */
    DECIDED,
    /** Staff stopped all further movement. Participants must not act. */
    REVERTED
}
