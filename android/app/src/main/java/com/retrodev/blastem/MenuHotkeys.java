package com.retrodev.blastem;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import android.widget.TextView;
import java.util.LinkedHashSet;

/** Android menu shortcuts are separate from the emulator's game button mappings. */
final class MenuHotkeys {
    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("menu_hotkeys", Context.MODE_PRIVATE);
    }

    static MenuShortcut load(Context context) {
        SharedPreferences prefs = preferences(context);
        int modifier = prefs.getInt("modifier", KeyEvent.KEYCODE_BUTTON_SELECT);
        int trigger = prefs.getInt("trigger", KeyEvent.KEYCODE_BUTTON_START);
        if ((modifier != 0 && !mappable(modifier)) || !mappable(trigger) || modifier == trigger)
            return new MenuShortcut(KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START);
        return new MenuShortcut(modifier, trigger);
    }

    static String label(Context context) {
        MenuShortcut shortcut = load(context);
        return shortcut.modifier == 0 ? name(shortcut.trigger)
                : name(shortcut.modifier) + " + " + name(shortcut.trigger);
    }

    private static boolean mappable(int key) {
        // System Back/Home and volume remain Android controls; they are never captured.
        return (key >= KeyEvent.KEYCODE_BUTTON_A && key <= KeyEvent.KEYCODE_BUTTON_MODE)
                || (key >= KeyEvent.KEYCODE_BUTTON_1 && key <= KeyEvent.KEYCODE_BUTTON_16)
                || key == KeyEvent.KEYCODE_MENU;
    }

    private static String name(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_BUTTON_START: return "Start";
            case KeyEvent.KEYCODE_BUTTON_SELECT: return "Select";
            case KeyEvent.KEYCODE_BUTTON_MODE: return "Guide";
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return "L3";
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return "R3";
            case KeyEvent.KEYCODE_MENU: return "Menu";
            default: return KeyEvent.keyCodeToString(key).replace("KEYCODE_BUTTON_", "");
        }
    }

    static void edit(Activity host, Runnable changed) {
        new AlertDialog.Builder(host, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Open game menu")
                .setItems(new String[]{"Assign button or combination", "Restore Select + Start"}, (dialog, which) -> {
                    if (which == 0) capture(host, changed);
                    else {
                        preferences(host).edit().remove("modifier").remove("trigger").apply();
                        changed.run();
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private static void capture(Activity host, Runnable changed) {
        TextView prompt = new TextView(host);
        prompt.setText("Press and release one button, or hold the first button and press a second.\n\nUse a combination to keep the game's normal buttons available. Swipe Back to cancel.");
        prompt.setTextSize(18);
        int padding = (int)(24 * host.getResources().getDisplayMetrics().density);
        prompt.setPadding(padding, padding, padding, padding);
        LinkedHashSet<Integer> held = new LinkedHashSet<>(), chosen = new LinkedHashSet<>();
        AlertDialog dialog = new AlertDialog.Builder(host, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Assign menu hotkey").setView(prompt).setNegativeButton("Cancel", null).create();
        dialog.setOnKeyListener((d, key, event) -> {
            if (!mappable(key)) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                held.add(key); chosen.add(key);
                if (chosen.size() <= 2) {
                    StringBuilder text = new StringBuilder();
                    for (int button : chosen) {
                        if (text.length() > 0) text.append(" + ");
                        text.append(name(button));
                    }
                    prompt.setText(text + "\n\nRelease the buttons to save.");
                } else prompt.setText("Use one or two buttons. Release them and try again.");
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                held.remove(key);
                if (held.isEmpty()) {
                    if (!event.isCanceled() && !chosen.isEmpty() && chosen.size() <= 2) {
                        Integer[] keys = chosen.toArray(new Integer[0]);
                        preferences(host).edit().putInt("modifier", keys.length == 2 ? keys[0] : 0)
                                .putInt("trigger", keys[keys.length - 1]).apply();
                        d.dismiss(); changed.run();
                    }
                    chosen.clear();
                }
            }
            return true;
        });
        dialog.show();
    }
}
