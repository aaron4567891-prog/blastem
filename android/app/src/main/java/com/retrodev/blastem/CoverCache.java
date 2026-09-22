package com.retrodev.blastem;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Bounded decoded cache plus persistent downloads; requests share one worker pool. */
final class CoverCache {
    private final File directory;
    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> memory = new LruCache<String, Bitmap>(16 * 1024 * 1024) {
        protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getByteCount(); }
    };
    private final Map<String, List<ImageView>> waiting = new HashMap<>();
    private final Set<String> missed = new HashSet<>();
    private volatile boolean closed;
    CoverCache(Context context) {
        directory = new File(context.getFilesDir(), "boxart"); directory.mkdirs();
    }
    private String systemName(String id) {
        switch (id) {
            case "gen": return "Sega - Mega Drive - Genesis";
            case "cd": return "Sega - Mega-CD - Sega CD";
            case "32x": case "32xcd": return "Sega - 32X";
            case "sms": return "Sega - Master System - Mark III";
            case "gg": return "Sega - Game Gear";
            case "sg": case "sc": return "Sega - SG-1000";
            case "coleco": return "Coleco - ColecoVision";
            case "pico": return "Sega - PICO";
            default: return null;
        }
    }
    void bind(ImageView view, String system, String path) {
        String title = path.substring(path.lastIndexOf('/') + 1);
        int dot = title.lastIndexOf('.'); if (dot >= 0) title = title.substring(0, dot);
        String key = system + ":" + title;
        view.setTag(key);
        Bitmap cached = memory.get(key);
        if (cached != null) { view.setImageBitmap(cached); return; }
        view.setImageResource(android.R.drawable.ic_menu_gallery);
        String folder = systemName(system);
        if (folder == null || missed.contains(key) || closed) return;
        List<ImageView> pending = waiting.get(key);
        if (pending != null) { if (!pending.contains(view)) pending.add(view); return; }
        pending = new ArrayList<>(); pending.add(view); waiting.put(key, pending);
        final String coverTitle = title;
        workers.execute(() -> {
            Bitmap image = null;
            try {
                StringBuilder hash = new StringBuilder();
                for (byte b : MessageDigest.getInstance("SHA-256").digest(key.getBytes("UTF-8"))) hash.append(String.format("%02x", b & 255));
                File file = new File(directory, hash + ".png");
                if (file.isFile()) image = decode(file);
                if (image == null && !closed) {
                    String url = "https://thumbnails.libretro.com/" + encode(folder) + "/Named_Boxarts/"
                            + encode(coverTitle.replace('&', '_')) + ".png";
                    HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
                    connection.setConnectTimeout(5000); connection.setReadTimeout(5000);
                    File temp = new File(directory, hash + ".tmp");
                    try {
                        if (connection.getResponseCode() == 200) {
                            try (InputStream input = connection.getInputStream(); OutputStream output = new FileOutputStream(temp)) {
                                byte[] buffer = new byte[8192]; int count, total = 0;
                                while ((count = input.read(buffer)) != -1) {
                                    if (closed || (total += count) > 8 * 1024 * 1024) throw new IOException("Cover download cancelled or too large");
                                    output.write(buffer, 0, count);
                                }
                            }
                            image = decode(temp);
                            if (image != null) temp.renameTo(file);
                        }
                    } finally { connection.disconnect(); temp.delete(); }
                }
            } catch (Exception ignored) { }
            final Bitmap result = image;
            main.post(() -> {
                List<ImageView> targets = waiting.remove(key);
                if (closed) return;
                if (result != null) memory.put(key, result); else missed.add(key);
                if (targets != null && result != null) for (ImageView target : targets)
                    if (key.equals(target.getTag())) target.setImageBitmap(result);
            });
        });
    }
    private static Bitmap decode(File file) {
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), options);
        if (options.outWidth <= 0 || options.outHeight <= 0) return null;
        options.inSampleSize = 1;
        while (options.outWidth / options.inSampleSize > 512 || options.outHeight / options.inSampleSize > 512) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(file.getPath(), options);
    }
    private static String encode(String value) throws UnsupportedEncodingException { return URLEncoder.encode(value, "UTF-8").replace("+", "%20"); }
    void retryMissing() { missed.clear(); }
    void close() { closed = true; workers.shutdownNow(); waiting.clear(); memory.evictAll(); }
}
