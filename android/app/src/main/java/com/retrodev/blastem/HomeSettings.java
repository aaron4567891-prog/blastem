package com.retrodev.blastem;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.AtomicFile;
import android.view.KeyEvent;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Settings panel hosted by the library, using the emulator's own config file. */
final class HomeSettings extends LinearLayout {
    static final int PICK_BIOS = 6101;
    private static final String[] PAGES = {"Video", "Audio", "System", "Input", "BIOS"};
    private final HomeActivity host;
    private final AtomicFile file;
    private final LinearLayout navigation, rows;
    private final ScrollView scroll;
    private ConfigDocument config;
    private int page;
    private String pendingBios, bindingProfile = "default";

    HomeSettings(HomeActivity activity, Bundle state) {
        super(activity); host = activity;
        file = new AtomicFile(new File(host.getFilesDir(), "blastem.cfg"));
        setOrientation(VERTICAL);
        if (state != null) {
            page = Math.max(0, Math.min(PAGES.length - 1, state.getInt("settings_page", 0)));
            pendingBios = state.getString("pending_bios");
        }
        HorizontalScrollView bar = new HorizontalScrollView(host);
        navigation = new LinearLayout(host); bar.addView(navigation); addView(bar);
        for (int i = 0; i < PAGES.length; i++) {
            final int index = i;
            navigation.addView(host.button(PAGES[i], () -> { page = index; refresh(); }));
        }
        TextView note = host.label("Changes are saved automatically and apply when you launch a game.", 14);
        addView(note);
        scroll = new ScrollView(host); scroll.setFillViewport(true);
        rows = new LinearLayout(host); rows.setOrientation(VERTICAL);
        rows.setPadding(host.dp(4), host.dp(8), host.dp(4), host.dp(16));
        scroll.addView(rows); addView(scroll, new LayoutParams(-1, 0, 1));
        refresh();
    }

    void saveState(Bundle state) {
        state.putInt("settings_page", page); state.putString("pending_bios", pendingBios);
    }

    private ConfigDocument load() throws IOException {
        InputStream stream;
        try { stream = file.openRead(); }
        catch (FileNotFoundException missing) { stream = host.getAssets().open("default.cfg"); }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return ConfigDocument.read(reader);
        }
    }

    private boolean save(String path, String value) { return save(Collections.singletonMap(path, value)); }

    private boolean save(Map<String, String> changes) {
        FileOutputStream output = null;
        try {
            // Re-read so edits preserve changes made by the in-game menu.
            ConfigDocument updated = load();
            for (Map.Entry<String, String> change : changes.entrySet()) {
                if (change.getValue() == null) updated.remove(change.getKey());
                else updated.put(change.getKey(), change.getValue());
            }
            output = file.startWrite();
            output.write(updated.serialize().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output); output = null; config = updated;
            return true;
        } catch (IOException | IllegalArgumentException error) {
            if (output != null) file.failWrite(output);
            Toast.makeText(host, "Settings could not be saved: " + error.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    void refresh() {
        rows.removeAllViews();
        for (int i = 0; i < navigation.getChildCount(); i++) navigation.getChildAt(i).setSelected(page == i);
        try { config = load(); }
        catch (IOException error) {
            rows.addView(host.label("Cannot read settings: " + error.getMessage(), 18));
            return;
        }
        switch (page) {
            case 0: video(); break;
            case 1: audio(); break;
            case 2: system(); break;
            case 3: input(); break;
            case 4: bios(); break;
        }
        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    private void heading(String text) { rows.addView(host.label(text, 21)); }

    private Button row(String title, String value, Runnable click) {
        Button button = host.button(title + "  ·  " + value, click);
        button.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        button.setPadding(host.dp(14), host.dp(8), host.dp(14), host.dp(8));
        button.setMinHeight(host.dp(52)); button.setTextSize(17);
        rows.addView(button, new LayoutParams(-1, -2));
        return button;
    }

    private void show(AlertDialog dialog) {
        dialog.setOnKeyListener((d, key, event) -> {
            if (key != KeyEvent.KEYCODE_BUTTON_B) return false;
            if (event.getAction() == KeyEvent.ACTION_UP) d.cancel();
            return true;
        });
        dialog.show();
    }

    private void toggle(String title, String path, boolean fallback) {
        Switch control = new Switch(host);
        control.setText(title); control.setTextSize(17); control.setTextColor(Color.WHITE);
        control.setPadding(host.dp(14), host.dp(6), host.dp(14), host.dp(6));
        control.setMinHeight(host.dp(52));
        control.setChecked(config.get(path, fallback ? "on" : "off").equals("on"));
        final boolean[] restoring = {false};
        control.setOnCheckedChangeListener((button, checked) -> {
            if (!restoring[0] && !save(path, checked ? "on" : "off")) {
                restoring[0] = true; control.setChecked(!checked); restoring[0] = false;
            }
        });
        rows.addView(control, new LayoutParams(-1, -2));
    }

    private void choice(String title, String path, String fallback, String[] values, String[] names) {
        String value = config.get(path, fallback);
        int selected = Arrays.asList(values).indexOf(value);
        final int current = selected;
        final Button[] button = new Button[1];
        button[0] = row(title, selected < 0 ? value : names[selected], () -> show(new AlertDialog.Builder(host)
                .setTitle(title).setSingleChoiceItems(names, current, (dialog, which) -> {
                    String next = values[which];
                    if (path.startsWith("bindings/") && next.equals("none")) next = null;
                    if (save(path, next)) {
                        button[0].setText(title + "  ·  " + names[which]);
                        // Refresh the selected index when this row is opened again.
                        refreshKeepingPosition(button[0]);
                        dialog.dismiss();
                    }
                }).setNegativeButton("Cancel", null).create()));
    }

    private void refreshKeepingPosition(View previous) {
        int index = rows.indexOfChild(previous), y = scroll.getScrollY();
        refresh();
        if (index >= 0 && index < rows.getChildCount()) rows.getChildAt(index).requestFocus();
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    private void number(String title, String path, String fallback, double min, double max, double step, String unit) {
        // Read the legacy key as well until the native config migration has been persisted.
        String current = config.get(path, path.equals("audio/cdda_gain") ? config.get("audio/cdd_gain", fallback) : fallback);
        double parsed;
        try { parsed = Double.parseDouble(current); } catch (NumberFormatException e) { parsed = Double.parseDouble(fallback); }
        if (Double.isNaN(parsed) || Double.isInfinite(parsed)) parsed = Double.parseDouble(fallback);
        final double initial = Math.max(min, Math.min(max, parsed));
        final Button[] button = new Button[1];
        button[0] = row(title, current + unit, () -> {
            LinearLayout body = new LinearLayout(host); body.setOrientation(VERTICAL);
            body.setPadding(host.dp(20), host.dp(12), host.dp(20), host.dp(12));
            TextView value = host.label("", 20); body.addView(value);
            SeekBar slider = new SeekBar(host); slider.setMax((int)Math.round((max - min) / step));
            slider.setProgress((int)Math.round((initial - min) / step));
            body.addView(slider, new LayoutParams(-1, host.dp(54)));
            Runnable display = () -> value.setText(format(min + slider.getProgress() * step, step) + unit);
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) { display.run(); }
                public void onStartTrackingTouch(SeekBar bar) { }
                public void onStopTrackingTouch(SeekBar bar) { }
            });
            display.run();
            AlertDialog dialog = new AlertDialog.Builder(host).setTitle(title).setView(body)
                    .setNegativeButton("Cancel", null).setPositiveButton("Save", (d, which) -> {
                        if (save(path, format(min + slider.getProgress() * step, step))) refreshKeepingPosition(button[0]);
                    }).create();
            show(dialog); slider.requestFocus();
        });
    }

    private String format(double value, double step) {
        return step < 1 ? String.format(Locale.ROOT, "%.1f", value) : Long.toString(Math.round(value));
    }

    private void video() {
        heading("Display");
        toggle("Fullscreen", "video/fullscreen", false);
        toggle("OpenGL renderer", "video/gl", true);
        toggle("Scanlines", "video/scanlines", false);
        toggle("Integer scaling", "video/integer_scaling", false);
        choice("Aspect ratio", "video/aspect", "4:3", new String[]{"4:3", "16:9", "stretch"}, new String[]{"4:3", "16:9", "Stretch to screen"});
        choice("Filtering", "video/scaling", "linear", new String[]{"nearest", "linear"}, new String[]{"Sharp pixels", "Smooth"});
        choice("VSync", "video/vsync", "off", new String[]{"off", "on", "tear"}, new String[]{"Off", "On", "On, tear if late"});
        shaders();
        for (String standard : new String[]{"ntsc", "pal"}) {
            heading(standard.toUpperCase(Locale.ROOT) + " overscan");
            String[] edges = {"top", "bottom", "left", "right"};
            String[] defaults = standard.equals("ntsc") ? new String[]{"2", "1", "13", "14"} : new String[]{"21", "17", "13", "14"};
            for (int i = 0; i < edges.length; i++) number(edges[i], "video/" + standard + "/overscan/" + edges[i], defaults[i], 0, 32, 1, " px");
        }
    }

    private void shaders() {
        try {
            ArrayList<String> files = new ArrayList<>();
            for (String name : host.getAssets().list("shaders"))
                if (name.endsWith(".f.glsl") && !name.startsWith("extra_window")) files.add(name);
            Collections.sort(files);
            String current = config.get("video/fragment_shader", "default.f.glsl");
            String[] labels = new String[files.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = files.get(i).replace(".f.glsl", "");
            final Button[] button = new Button[1];
            button[0] = row("Shader", current.replace(".f.glsl", ""), () -> show(new AlertDialog.Builder(host)
                    .setTitle("Shader").setSingleChoiceItems(labels, files.indexOf(current), (dialog, which) -> {
                        String fragment = files.get(which), vertex = fragment.replace(".f.glsl", ".v.glsl");
                        try { if (!Arrays.asList(host.getAssets().list("shaders")).contains(vertex)) vertex = "default.v.glsl"; }
                        catch (IOException error) { vertex = "default.v.glsl"; }
                        Map<String, String> change = new LinkedHashMap<>();
                        change.put("video/fragment_shader", fragment); change.put("video/vertex_shader", vertex);
                        if (save(change)) { dialog.dismiss(); refreshKeepingPosition(button[0]); }
                    }).setNegativeButton("Cancel", null).create()));
        } catch (IOException error) { rows.addView(host.label("Shaders could not be listed.", 14)); }
    }

    private void audio() {
        heading("Audio output");
        choice("Sample rate", "audio/rate", "48000", new String[]{"192000", "96000", "48000", "44100", "22050"}, new String[]{"192000 Hz", "96000 Hz", "48000 Hz", "44100 Hz", "22050 Hz"});
        choice("Buffer size", "audio/buffer", "512", new String[]{"1024", "512", "256", "128", "64"}, new String[]{"1024 samples", "512 samples", "256 samples", "128 samples", "64 samples"});
        number("Lowpass cutoff", "audio/lowpass_cutoff", "3390", 100, 24000, 10, " Hz");
        heading("Volume");
        number("Overall", "audio/gain", "0", -30, 30, .5, " dB");
        number("FM", "audio/fm_gain", "0", -30, 30, .5, " dB");
        number("PSG", "audio/psg_gain", "0", -30, 30, .5, " dB");
        number("Sega CD PCM", "audio/rf5c164_gain", "-6", -30, 30, .5, " dB");
        number("Sega CD music", "audio/cdda_gain", "-9.5", -30, 30, .5, " dB");
        choice("FM DAC", "audio/fm_dac", "auto", new String[]{"auto", "zero_offset", "linear"}, new String[]{"Default for model", "Zero offset", "Linear"});
    }

    private void system() {
        heading("System");
        choice("Default region", "system/default_region", "U", new String[]{"U", "J", "E"}, new String[]{"Americas", "Japan", "Europe"});
        toggle("Force selected region", "system/force_region", false);
        choice("Sync source", "system/sync_source", "audio", new String[]{"audio", "video"}, new String[]{"Audio", "Video"});
        choice("Save state format", "ui/state_format", "native", new String[]{"native", "gst"}, new String[]{"BlastEm", "Genecyst"});
        choice("Initial RAM", "system/ram_init", "zero", new String[]{"zero", "random"}, new String[]{"Zero", "Random"});
        try (Reader reader = new InputStreamReader(host.getAssets().open("systems.cfg"), StandardCharsets.UTF_8)) {
            ConfigDocument systems = ConfigDocument.read(reader);
            ArrayList<String> md = new ArrayList<>(), mdNames = new ArrayList<>(), sms = new ArrayList<>(), smsNames = new ArrayList<>();
            for (String key : systems.children("")) {
                if (systems.get(key + "/show", "yes").equals("no")) continue;
                boolean genesis = systems.get(key + "/vdp", "genesis").equals("genesis");
                if (genesis) { md.add(key); mdNames.add(systems.get(key + "/name", key)); }
                sms.add(key); smsNames.add(systems.get(key + "/name", key));
            }
            choice("Genesis model", "system/model", "md1va3", md.toArray(new String[0]), mdNames.toArray(new String[0]));
            choice("Master System model", "sms/system/model", "md1va3", sms.toArray(new String[0]), smsNames.toArray(new String[0]));
        } catch (IOException ignored) { }
    }

    private void input() {
        heading("Hotkeys");
        final Button[] shortcut = new Button[1];
        shortcut[0] = row("Open game menu", MenuHotkeys.label(host),
                () -> MenuHotkeys.edit(host, () -> refreshKeepingPosition(shortcut[0])));
        rows.addView(host.label("Hold the first button, then press the second. Start alone still works in games with the default Select + Start shortcut. Start opens Settings in the library.", 14));
        heading("Emulated controllers");
        for (int port = 1; port <= 2; port++) {
            String[] values = {"none", "gamepad2." + port, "gamepad3." + port, "gamepad6." + port, "mouse." + port};
            String[] names = {"Disconnected", "2-button pad", "3-button pad", "6-button pad", "Mouse"};
            choice("Genesis port " + port, "io/devices/" + port, "gamepad6." + port, values, names);
            choice("Master System port " + port, "sms/io/devices/" + port, "gamepad2." + port, values, names);
        }
        heading("Controller bindings");
        List<String> profiles = config.children("bindings/pads");
        if (!profiles.contains(bindingProfile)) bindingProfile = "default";
        row("Layout", bindingProfile, () -> show(new AlertDialog.Builder(host).setTitle("Controller layout")
                .setSingleChoiceItems(profiles.toArray(new String[0]), profiles.indexOf(bindingProfile), (dialog, which) -> {
                    bindingProfile = profiles.get(which); dialog.dismiss(); refresh();
                }).setNegativeButton("Cancel", null).create()));
        rows.addView(host.label("Edit the layout used by your controller. Device-specific layouts override Default. L1/R1 switch systems only in the library.", 14));
        String base = "bindings/pads/" + bindingProfile + "/";
        String[] targets = {"none", "gamepads.n.a", "gamepads.n.b", "gamepads.n.c", "gamepads.n.x", "gamepads.n.y", "gamepads.n.z", "gamepads.n.start", "gamepads.n.mode", "gamepads.n.up", "gamepads.n.down", "gamepads.n.left", "gamepads.n.right", "ui.menu", "ui.pause", "ui.save_state", "ui.load_state", "ui.soft_reset", "ui.next_speed", "ui.prev_speed", "ui.sms_pause"};
        String[] names = {"Unassigned", "A", "B", "C", "X", "Y", "Z", "Start", "Mode", "Up", "Down", "Left", "Right", "Menu", "Pause", "Save state", "Load state", "Reset", "Next speed", "Previous speed", "Master System pause"};
        for (String section : new String[]{"buttons", "axes"}) {
            heading(section.equals("buttons") ? "Buttons" : "Thumbsticks and triggers");
            LinkedHashSet<String> keys = new LinkedHashSet<>(config.children(base + section));
            keys.addAll(Arrays.asList(section.equals("buttons")
                    ? new String[]{"a", "b", "x", "y", "leftshoulder", "rightshoulder", "start", "back", "guide", "leftstick", "rightstick"}
                    : new String[]{"leftx.negative", "leftx.positive", "lefty.negative", "lefty.positive", "rightx.negative", "rightx.positive", "righty.negative", "righty.positive", "lefttrigger", "righttrigger"}));
            for (String key : keys) choice(inputName(key), base + section + "/" + key, "none", targets, names);
        }
    }

    private String inputName(String name) {
        switch (name) {
            case "leftshoulder": return "L1";
            case "rightshoulder": return "R1";
            case "lefttrigger": return "L2";
            case "righttrigger": return "R2";
            case "leftstick": return "L3";
            case "rightstick": return "R3";
            case "back": return "Select / Back";
            case "guide": return "Guide";
            case "leftx.negative": return "Left stick - left";
            case "leftx.positive": return "Left stick - right";
            case "lefty.negative": return "Left stick - up";
            case "lefty.positive": return "Left stick - down";
            case "rightx.negative": return "Right stick - left";
            case "rightx.positive": return "Right stick - right";
            case "righty.negative": return "Right stick - up";
            case "righty.positive": return "Right stick - down";
            default: return name.toUpperCase(Locale.ROOT);
        }
    }

    private void bios() {
        heading("BIOS files");
        String[][] choices = {{"TMSS ROM", "tmss_path", "tmss.md"}, {"Sega CD - USA", "scd_bios_us", "cdbios.md"},
                {"Sega CD - Japan", "scd_bios_jp", "cdbios.md"}, {"Sega CD - Europe", "scd_bios_eu", "cdbios.md"},
                {"32X - Main SH2", "s32x_main_bios", "32X_M_BIOS.bin"}, {"32X - Sub SH2", "s32x_sub_bios", "32X_S_BIOS.bin"},
                {"32X - 68000", "s32x_68k_bios", "32X_G_BIOS.bin"}, {"ColecoVision", "coleco_bios_path", "colecovision_bios.col"}};
        for (String[] item : choices) {
            String path = "system/" + item[1], stored = config.get(path, item[2]);
            String name = new File(stored).getName().replaceFirst("^[0-9a-fA-F-]{36}-", "");
            row(item[0], name, () -> show(new AlertDialog.Builder(host).setTitle(item[0])
                    .setItems(new String[]{"Choose BIOS file", "Use default"}, (dialog, which) -> {
                        if (which == 0) {
                            pendingBios = path;
                            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
                            host.startActivityForResult(intent, PICK_BIOS);
                        } else if (save(path, item[2])) refresh();
                    }).setNegativeButton("Cancel", null).create()));
        }
    }

    void importBios(Uri uri) {
        String target = pendingBios; pendingBios = null;
        if (target == null || uri == null) return;
        Toast.makeText(host, "Importing BIOS…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            File destination = null;
            try {
                String name = "bios.bin";
                try (Cursor cursor = host.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) name = cursor.getString(0);
                }
                name = name.replaceAll("[^A-Za-z0-9._-]", "_");
                if (name.length() > 120) name = name.substring(name.length() - 120);
                File directory = new File(host.getFilesDir(), "bios");
                if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create BIOS folder");
                destination = new File(directory, UUID.randomUUID() + "-" + name);
                try (InputStream input = host.getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(destination)) {
                    if (input == null) throw new IOException("Cannot read BIOS");
                    byte[] buffer = new byte[8192]; int count, total = 0;
                    while ((count = input.read(buffer)) != -1) {
                        total += count; if (total > 4 * 1024 * 1024) throw new IOException("BIOS file exceeds 4 MB");
                        out.write(buffer, 0, count);
                    }
                    if (total == 0) throw new IOException("BIOS file is empty");
                }
                final File imported = destination;
                host.runOnUiThread(() -> {
                    if (host.isDestroyed() || !save(target, imported.getAbsolutePath())) imported.delete();
                    else refresh();
                });
            } catch (Exception error) {
                if (destination != null) destination.delete();
                host.runOnUiThread(() -> { if (!host.isDestroyed()) Toast.makeText(host, "Could not import BIOS: " + error.getMessage(), Toast.LENGTH_LONG).show(); });
            }
        }, "BlastEm-BIOS-import").start();
    }
}
