package com.retrodev.blastem;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Standalone regression check: compile with ConfigDocument.java and run with default.cfg. */
public final class ConfigDocumentTest {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static Map<String, String> values(ConfigDocument document, String path) {
        Map<String, String> result = new TreeMap<>();
        for (String key : document.children(path)) {
            String child = path.isEmpty() ? key : path + "/" + key;
            String value = document.get(child, null);
            if (value == null) result.putAll(values(document, child));
            else result.put(child, value);
        }
        return result;
    }
    public static void main(String[] args) throws Exception {
        ConfigDocument defaults;
        try (Reader reader = Files.newBufferedReader(Paths.get(args[0]))) { defaults = ConfigDocument.read(reader); }
        Map<String, String> before = values(defaults, "");
        defaults.put("audio/cdda_gain", "0.0");
        defaults.put("system/scd_bios_us", "/data/user/0/app/files/bios/My BIOS.bin");
        defaults.put("video/ntsc/overscan/bottom", "2");
        ConfigDocument loaded = ConfigDocument.read(new StringReader(defaults.serialize()));
        Map<String, String> expected = new TreeMap<>(before);
        expected.put("audio/cdda_gain", "0.0");
        expected.put("system/scd_bios_us", "/data/user/0/app/files/bios/My BIOS.bin");
        expected.put("video/ntsc/overscan/bottom", "2");
        require(values(loaded, "").equals(expected), "Editing three settings must preserve all other settings and bindings");
        require(loaded.serialize().contains("#"), "Keep config comments");

        ConfigDocument duplicate = ConfigDocument.read(new StringReader("# user config\naudio {\n cdda_gain -9.5\n cdda_gain 0\n custom_gain 7\n}\n"));
        require(duplicate.get("audio/cdda_gain", "missing").equals("0"), "Last definition wins like the native parser");
        duplicate.put("audio/cdda_gain", "1.5");
        require(ConfigDocument.read(new StringReader(duplicate.serialize())).get("audio/cdda_gain", "missing").equals("1.5"), "Update effective definition");
        duplicate.remove("audio/cdda_gain");
        require(duplicate.get("audio/cdda_gain", null) == null, "Remove all duplicate definitions");
        require(duplicate.get("audio/custom_gain", "missing").equals("7"), "Preserve unknown settings");
        for (String broken : new String[]{"audio {\n gain 0\n", "}\n", "audio {\n gain\n}\n"}) {
            try { ConfigDocument.read(new StringReader(broken)); throw new AssertionError("Malformed config must not be overwritten"); }
            catch (IOException expectedError) { }
        }
        try { loaded.put("audio/gain", "0\nvideo {\n"); throw new AssertionError("Reject newlines in setting values"); }
        catch (IllegalArgumentException expectedError) { }
        System.out.println("ConfigDocument checks passed: settings preserved, nested edits, BIOS paths, duplicate keys, removal, invalid input.");
    }
}
