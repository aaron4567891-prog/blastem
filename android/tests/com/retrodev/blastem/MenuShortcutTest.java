package com.retrodev.blastem;

/** Standalone checks for input isolation; no Android runtime required. */
public final class MenuShortcutTest {
    private static final int SELECT = 109, START = 108, A = 96, R3 = 107;
    private static void expect(int actual, int expected, String reason) {
        if (actual != expected) throw new AssertionError(reason + ": " + actual);
    }
    private static int down(MenuShortcut s, int key) { return s.key(key, true, 0, false); }
    private static int up(MenuShortcut s, int key) { return s.key(key, false, 0, false); }

    public static void main(String[] args) {
        MenuShortcut chord = new MenuShortcut(SELECT, START);
        expect(down(chord, START), MenuShortcut.FORWARD, "Normal game Start down");
        expect(up(chord, START), MenuShortcut.FORWARD, "Normal game Start up");
        expect(down(chord, A), MenuShortcut.FORWARD, "Other game buttons unchanged");
        expect(up(chord, A), MenuShortcut.FORWARD, "Other button release unchanged");
        expect(down(chord, SELECT), MenuShortcut.CONSUME, "Buffer modifier");
        expect(up(chord, SELECT), MenuShortcut.FORWARD_MODIFIER_TAP, "Standalone Select still reaches game");

        expect(down(chord, SELECT), MenuShortcut.CONSUME, "Chord modifier never reaches game");
        expect(down(chord, START), MenuShortcut.CONSUME, "Chord Start never reaches game");
        expect(chord.key(START, true, 1, false), MenuShortcut.CONSUME, "Ignore held-button repeats");
        expect(up(chord, START), MenuShortcut.OPEN_MENU, "Open on trigger release");
        chord.reset();
        expect(up(chord, SELECT), MenuShortcut.CONSUME, "No stray Select action after popup");
        expect(down(chord, START), MenuShortcut.FORWARD, "Start restored after menu");
        expect(up(chord, START), MenuShortcut.FORWARD, "Release restored after menu");

        down(chord, SELECT); down(chord, START);
        expect(up(chord, SELECT), MenuShortcut.CONSUME, "Reverse release order suppresses modifier");
        expect(up(chord, START), MenuShortcut.OPEN_MENU, "Reverse release order opens once");
        chord.reset();
        down(chord, SELECT); down(chord, START);
        expect(chord.key(START, false, 0, true), MenuShortcut.CONSUME, "Canceled press never opens popup");
        chord.reset();
        expect(up(chord, SELECT), MenuShortcut.CONSUME, "Background reset clears held modifier");

        MenuShortcut single = new MenuShortcut(0, R3);
        expect(down(single, R3), MenuShortcut.CONSUME, "Mapped single button reserved for menu");
        expect(up(single, R3), MenuShortcut.OPEN_MENU, "Mapped single button opens menu");
        expect(up(single, R3), MenuShortcut.FORWARD, "Duplicate release never reopens");
        expect(down(single, START), MenuShortcut.FORWARD, "Remapping preserves Start");
        System.out.println("MenuShortcut checks passed: normal buttons, chord isolation, repeats, release order, cancellation, remapping.");
    }
}
