package com.retrodev.blastem;

import java.io.*;
import java.util.*;

/** BlastEm's line-based configuration, including comments and unknown settings. */
final class ConfigDocument {
    private static final class Entry {
        String key, value, comment;
        ArrayList<Entry> children;
    }
    private final ArrayList<Entry> root = new ArrayList<>();

    static ConfigDocument read(Reader source) throws IOException {
        ConfigDocument result = new ConfigDocument();
        ArrayDeque<ArrayList<Entry>> stack = new ArrayDeque<>();
        stack.push(result.root);
        BufferedReader reader = new BufferedReader(source);
        String line;
        while ((line = reader.readLine()) != null) {
            String text = line.trim();
            if (text.startsWith("}")) {
                if (stack.size() == 1) throw new IOException("Unexpected closing brace in settings");
                stack.pop();
                continue;
            }
            Entry entry = new Entry();
            if (text.isEmpty() || text.startsWith("#")) {
                entry.comment = text;
            } else if (text.endsWith("{")) {
                entry.key = text.substring(0, text.length() - 1).trim();
                if (entry.key.isEmpty()) throw new IOException("Unnamed settings section");
                entry.children = new ArrayList<>();
            } else {
                int split = 0;
                while (split < text.length() && !Character.isWhitespace(text.charAt(split))) split++;
                if (split == text.length()) throw new IOException("Missing value for " + text);
                entry.key = text.substring(0, split);
                entry.value = text.substring(split).trim();
                if (entry.value.isEmpty()) throw new IOException("Missing value for " + entry.key);
            }
            stack.peek().add(entry);
            if (entry.children != null) stack.push(entry.children);
        }
        if (stack.size() != 1) throw new IOException("Unclosed settings section");
        return result;
    }

    private static Entry find(List<Entry> entries, String key) {
        // The native parser gives the final occurrence precedence.
        Entry found = null;
        for (Entry entry : entries) if (key.equals(entry.key)) found = entry;
        return found;
    }

    private Entry entry(String path, boolean create) {
        String[] parts = path.split("/");
        List<Entry> entries = root;
        for (int i = 0; i < parts.length; i++) {
            Entry found = find(entries, parts[i]);
            if (found == null) {
                if (!create) return null;
                found = new Entry(); found.key = parts[i]; entries.add(found);
                if (i < parts.length - 1) found.children = new ArrayList<>();
            }
            if (i == parts.length - 1) return found;
            if (found.children == null) {
                if (!create) return null;
                throw new IllegalArgumentException("Setting is not a section: " + parts[i]);
            }
            entries = found.children;
        }
        return null;
    }

    String get(String path, String fallback) {
        Entry found = entry(path, false);
        return found == null || found.value == null ? fallback : found.value;
    }

    void put(String path, String value) {
        if (value == null || value.trim().isEmpty() || value.indexOf('\n') >= 0 ||
                value.indexOf('\r') >= 0 || value.indexOf('\0') >= 0 || value.trim().endsWith("{"))
            throw new IllegalArgumentException("Invalid setting value");
        Entry found = entry(path, true);
        if (found.children != null) throw new IllegalArgumentException("Cannot replace a settings section");
        found.value = value;
    }

    List<String> children(String path) {
        Entry found = path.isEmpty() ? null : entry(path, false);
        List<Entry> entries = path.isEmpty() ? root : found == null ? null : found.children;
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (entries != null) for (Entry entry : entries) if (entry.key != null) keys.add(entry.key);
        return new ArrayList<>(keys);
    }

    void remove(String path) {
        int split = path.lastIndexOf('/');
        Entry parent = split < 0 ? null : entry(path.substring(0, split), false);
        List<Entry> entries = split < 0 ? root : parent == null ? null : parent.children;
        String key = path.substring(split + 1);
        if (entries != null) for (int i = entries.size() - 1; i >= 0; i--)
            if (key.equals(entries.get(i).key)) entries.remove(i);
    }

    String serialize() {
        StringBuilder text = new StringBuilder();
        write(root, "", text);
        return text.toString();
    }

    private static void write(List<Entry> entries, String indent, StringBuilder text) {
        for (Entry entry : entries) {
            if (entry.comment != null) {
                text.append(entry.comment.isEmpty() ? "" : indent + entry.comment).append('\n');
            } else if (entry.children != null) {
                text.append(indent).append(entry.key).append(" {\n");
                write(entry.children, indent + "\t", text);
                text.append(indent).append("}\n");
            } else {
                text.append(indent).append(entry.key).append(' ').append(entry.value).append('\n');
            }
        }
    }
}
