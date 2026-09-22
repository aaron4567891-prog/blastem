package com.retrodev.blastem;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Persistent per-system library. ROM data stays in the selected document tree. */
public class HomeActivity extends Activity {
    private static final int PICK_FOLDER = 6100;
    private static final String[][] SYSTEMS = {
        {"gen", "Genesis / Mega Drive", "bin gen md smd zip gz"},
        {"cd", "Sega CD", "cue iso chd"},
        {"32x", "32X", "32x bin md zip gz"},
        {"32xcd", "32X CD", "cue iso chd"},
        {"sms", "Master System", "sms bin zip gz"},
        {"gg", "Game Gear", "gg bin zip gz"},
        {"sg", "SG-1000", "sg bin zip gz"},
        {"sc", "SC-3000", "sc sf7 bin zip gz"},
        {"coleco", "ColecoVision", "col rom bin zip gz"},
        {"pico", "Pico", "md bin zip gz"},
        {"copera", "Copera", "md bin zip gz"},
        {"laser", "LaserActive", "cue iso chd"}
    };
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private int selected, generation;
    private boolean launching;
    private String picking;
    private TextView heading, status;
    private Button folder;
    private GridView list;
    private CoverCache covers;
    private LinearLayout tabs;
    private final ArrayList<Game> games = new ArrayList<>();
    private ArrayAdapter<Game> adapter;
    private static class Game {
        final String title, path;
        Game(String title, String path) { this.title = title; this.path = path; }
        public String toString() { return title; }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        covers = new CoverCache(this);
        prefs = getSharedPreferences("system_library", MODE_PRIVATE);
        selected = Math.max(0, Math.min(SYSTEMS.length - 1, prefs.getInt("selected", 0)));
        if (state != null) picking = state.getString("picking");
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(12));
        root.setBackgroundColor(Color.rgb(20, 23, 31));
        TextView title = label("BlastEm", 26); root.addView(title);
        HorizontalScrollView strip = new HorizontalScrollView(this);
        tabs = new LinearLayout(this); strip.addView(tabs); root.addView(strip);
        for (int i = 0; i < SYSTEMS.length; i++) {
            final int index = i;
            Button tab = button(SYSTEMS[i][1], () -> select(index));
            tabs.addView(tab);
        }
        heading = label("", 22); root.addView(heading);
        HorizontalScrollView actionsScroll = new HorizontalScrollView(this);
        LinearLayout actions = new LinearLayout(this); actionsScroll.addView(actions);
        folder = button("Add ROM Folder", this::chooseFolder); actions.addView(folder);
        actions.addView(button("Refresh", () -> { covers.retryMissing(); scan(selected); }));
        actions.addView(button("Emulator Menu", () -> startActivity(new Intent(this, BlastEmActivity.class))));
        root.addView(actionsScroll);
        status = label("", 14); root.addView(status);
        list = new GridView(this);
        list.setNumColumns(GridView.AUTO_FIT); list.setColumnWidth(dp(160));
        list.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        list.setHorizontalSpacing(dp(10)); list.setVerticalSpacing(dp(10));
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        list.setSelector(highlight());
        adapter = new ArrayAdapter<Game>(this, android.R.layout.simple_list_item_1, games) {
            @Override public View getView(int position, View convert, ViewGroup parent) {
                LinearLayout card;
                ImageView art;
                TextView name;
                if (convert instanceof LinearLayout) {
                    card = (LinearLayout)convert; art = (ImageView)card.getChildAt(0); name = (TextView)card.getChildAt(1);
                } else {
                    card = new LinearLayout(HomeActivity.this); card.setOrientation(LinearLayout.VERTICAL);
                    card.setPadding(dp(8), dp(8), dp(8), dp(8));
                    card.setBackground(highlight());
                    art = new ImageView(HomeActivity.this); art.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    card.addView(art, new LinearLayout.LayoutParams(-1, dp(170)));
                    name = label("", 15); name.setGravity(android.view.Gravity.CENTER);
                    name.setMaxLines(2); name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    card.addView(name, new LinearLayout.LayoutParams(-1, dp(58)));
                }
                Game game = games.get(position); name.setText(game.title);
                covers.bind(art, SYSTEMS[selected][0], game.path);
                return card;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> launch(games.get(position)));
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(label("Box art: Libretro thumbnails Â· Downloaded covers are saved on this device", 12));
        setContentView(root); select(selected);
    }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private TextView label(String text, int size) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(size);
        v.setTextColor(Color.WHITE); v.setPadding(0, dp(6), 0, dp(6)); return v;
    }
    private GradientDrawable box(int fill, int edge) {
        GradientDrawable d = new GradientDrawable(); d.setColor(fill);
        d.setCornerRadius(dp(5)); d.setStroke(dp(2), edge); return d;
    }
    private StateListDrawable highlight() {
        StateListDrawable d = new StateListDrawable();
        for (int s : new int[]{android.R.attr.state_pressed, android.R.attr.state_focused, android.R.attr.state_selected})
            d.addState(new int[]{s}, box(0xff234e80, 0xff70b7ff));
        d.addState(new int[]{}, box(Color.TRANSPARENT, Color.TRANSPARENT)); return d;
    }
    private Button button(String name, Runnable click) {
        Button b = new Button(this); b.setText(name); b.setTextColor(Color.WHITE);
        b.setAllCaps(false); b.setBackground(highlight()); b.setOnClickListener(v -> click.run()); return b;
    }
    private void select(int index) {
        selected = index; generation++;
        prefs.edit().putInt("selected", index).apply();
        for (int i = 0; i < tabs.getChildCount(); i++) tabs.getChildAt(i).setSelected(i == index);
        heading.setText(SYSTEMS[index][1]);
        boolean hasFolder = prefs.contains("folder_" + SYSTEMS[index][0]);
        folder.setText(hasFolder ? "Change ROM Folder" : "Add ROM Folder");
        games.clear();
        try {
            JSONArray data = new JSONArray(prefs.getString("games_" + SYSTEMS[index][0], "[]"));
            for (int i = 0; i < data.length(); i++) {
                JSONObject entry = data.getJSONObject(i);
                games.add(new Game(entry.getString("title"), entry.getString("path")));
            }
        } catch (JSONException ignored) { }
        adapter.notifyDataSetChanged();
        status.setText(hasFolder ? games.size() + " games Ã‚Â· Folder remembered Ã‚Â· Refresh to find new games"
                : "Choose a ROM folder for this system. Subfolders are included.");
        if (hasFolder && !prefs.contains("games_" + SYSTEMS[index][0])) scan(index);
    }
    private void chooseFolder() {
        picking = SYSTEMS[selected][0];
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_FOLDER);
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("picking", picking); super.onSaveInstanceState(state);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_FOLDER || result != RESULT_OK || data == null || data.getData() == null || picking == null) return;
        try {
            Uri uri = data.getData();
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            prefs.edit().putString("folder_" + picking, uri.toString()).remove("games_" + picking).apply();
            for (int i = 0; i < SYSTEMS.length; i++) if (SYSTEMS[i][0].equals(picking)) { select(i); break; }
        } catch (SecurityException e) { status.setText("Folder access could not be saved. Please select it again."); }
        picking = null;
    }
    private void scan(int index) {
        String saved = prefs.getString("folder_" + SYSTEMS[index][0], null);
        if (saved == null) { chooseFolder(); return; }
        int token = ++generation;
        status.setText("Scanning ROM folderÃ¢â‚¬Â¦");
        worker.execute(() -> {
            try {
                Uri tree = Uri.parse(saved);
                ArrayList<Game> found = new ArrayList<>();
                Set<String> extensions = new HashSet<>(Arrays.asList(SYSTEMS[index][2].split(" ")));
                walk(tree, DocumentsContract.getTreeDocumentId(tree), "", extensions, found, new HashSet<>());
                found.sort((a, b) -> a.title.compareToIgnoreCase(b.title));
                JSONArray entries = new JSONArray();
                for (Game game : found) entries.put(new JSONObject().put("title", game.title).put("path", game.path));
                if (Thread.currentThread().isInterrupted()) return;
                // Do not let an old scan overwrite a newly selected folder.
                if (!saved.equals(prefs.getString("folder_" + SYSTEMS[index][0], null))) return;
                prefs.edit().putString("games_" + SYSTEMS[index][0], entries.toString()).apply();
                runOnUiThread(() -> { if (!isFinishing() && token == generation && selected == index) select(index); });
            } catch (Exception e) {
                runOnUiThread(() -> { if (!isFinishing() && token == generation) status.setText("Cannot scan folder. Check storage access or choose the folder again."); });
            }
        });
    }
    private void walk(Uri tree, String id, String prefix, Set<String> extensions, ArrayList<Game> found, Set<String> visited) throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        if (!visited.add(id)) return;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id);
        try (Cursor c = getContentResolver().query(children, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
            if (c == null) throw new java.io.IOException("Folder unavailable");
            while (c.moveToNext()) {
                String name = c.getString(1), path = prefix + name;
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(2))) {
                    walk(tree, c.getString(0), path + "/", extensions, found, visited);
                } else {
                    int dot = name.lastIndexOf('.');
                    if (dot >= 0 && extensions.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT)))
                        found.add(new Game(path.substring(0, path.lastIndexOf('.')), path));
                }
            }
        }
    }
    private void launch(Game game) {
        String tree = prefs.getString("folder_" + SYSTEMS[selected][0], null);
        if (tree == null) { chooseFolder(); return; }
        Intent intent = new Intent(this, BlastEmActivity.class);
        intent.putExtra("library_tree", tree);
        intent.putExtra("library_rom", game.path);
        String machine = SYSTEMS[selected][0];
        // CD and ColecoVision are detected by their media headers.
        if (!machine.equals("cd") && !machine.equals("coleco")) intent.putExtra("library_machine", machine);
        if (launching) return;
        launching = true;
        status.setText("Opening " + game.title + "…");
        worker.execute(() -> {
            try {
                Uri root = Uri.parse(tree);
                String id = DocumentsContract.getTreeDocumentId(root);
                for (String part : game.path.split("/")) {
                    boolean found = false;
                    try (Cursor c = getContentResolver().query(DocumentsContract.buildChildDocumentsUriUsingTree(root, id),
                            new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
                        if (c == null) throw new java.io.IOException("Folder unavailable");
                        while (c.moveToNext()) if (part.equals(c.getString(1))) { id = c.getString(0); found = true; break; }
                    }
                    if (!found) throw new java.io.IOException("Game moved or removed");
                }
                try (android.os.ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(DocumentsContract.buildDocumentUriUsingTree(root, id), "r")) {
                    if (fd == null) throw new java.io.IOException("Game unavailable");
                }
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) startActivity(intent); launching = false; });
            } catch (Exception error) {
                runOnUiThread(() -> { launching = false; if (!isFinishing()) status.setText("Cannot open game. Refresh the list or select the folder again."); });
            }
        });
    }
    @Override public void onDestroy() { generation++; worker.shutdownNow(); covers.close(); super.onDestroy(); }
}
