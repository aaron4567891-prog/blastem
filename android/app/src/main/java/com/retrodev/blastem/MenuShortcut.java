package com.retrodev.blastem;

/** Ordered button chord. The modifier is buffered so a menu chord never reaches the game. */
final class MenuShortcut {
    static final int FORWARD = 0, CONSUME = 1, FORWARD_MODIFIER_TAP = 2, OPEN_MENU = 3;
    final int modifier, trigger;
    private boolean modifierHeld, modifierUsed, triggerCaptured;

    MenuShortcut(int modifier, int trigger) { this.modifier = modifier; this.trigger = trigger; }

    int key(int code, boolean down, int repeat, boolean canceled) {
        if (modifier != 0 && code == modifier) {
            if (down) {
                if (repeat == 0) { modifierHeld = true; modifierUsed = false; }
                return CONSUME;
            }
            boolean forward = modifierHeld && !modifierUsed && !canceled;
            modifierHeld = false;
            return forward ? FORWARD_MODIFIER_TAP : CONSUME;
        }
        if (code == trigger) {
            if (down) {
                if (repeat == 0 && (modifier == 0 || modifierHeld)) {
                    triggerCaptured = true;
                    modifierUsed = true;
                }
                return triggerCaptured ? CONSUME : FORWARD;
            }
            if (triggerCaptured) {
                triggerCaptured = false;
                return canceled ? CONSUME : OPEN_MENU;
            }
        }
        return FORWARD;
    }

    void reset() { modifierHeld = modifierUsed = triggerCaptured = false; }
}
