package com.retrodev.blastem;
import org.libsdl.app.SDLActivity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.View;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;


public class BlastEmActivity extends SDLActivity
{
	static final int DOC_TREE_CODE = 4242;
    static final int BIOS_FILE_CODE = 4243;
    private boolean biosPickerPending;
    private volatile String biosPickerResult;

    // Called by the emulator thread: null means pending, empty means cancelled.
    public String pickBiosFile() {
        if (biosPickerPending) {
            String result = biosPickerResult;
            if (result != null) biosPickerPending = false;
            return result;
        }
        biosPickerPending = true;
        biosPickerResult = null;
        runOnUiThread(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, BIOS_FILE_CODE);
            } catch (RuntimeException error) {
                biosPickerResult = "";
                android.widget.Toast.makeText(this, "Could not open BIOS picker", android.widget.Toast.LENGTH_LONG).show();
            }
        });
        return null;
    }

    private void importBiosFile(Uri uri) {
        new Thread(() -> {
            File destination = null;
            try {
                String name = "bios.bin";
                try (Cursor cursor = getContentResolver().query(uri,
                        new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) name = cursor.getString(0);
                }
                name = name.replaceAll("[^A-Za-z0-9._-]", "_");
                if (name.isEmpty() || name.equals(".") || name.equals("..")) name = "bios.bin";
                if (name.length() > 120) name = name.substring(name.length() - 120);
                File directory = new File(getFilesDir(), "bios");
                if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create BIOS folder");
                destination = new File(directory, java.util.UUID.randomUUID() + "-" + name);
                try (java.io.InputStream input = getContentResolver().openInputStream(uri);
                     java.io.OutputStream output = new java.io.FileOutputStream(destination)) {
                    if (input == null) throw new IOException("Cannot read selected BIOS");
                    byte[] buffer = new byte[8192];
                    int count, total = 0;
                    while ((count = input.read(buffer)) != -1) {
                        total += count;
                        if (total > 4 * 1024 * 1024) throw new IOException("BIOS file exceeds 4 MB");
                        output.write(buffer, 0, count);
                    }
                    if (total == 0) throw new IOException("BIOS file is empty");
                }
                biosPickerResult = destination.getAbsolutePath();
            } catch (Exception error) {
                if (destination != null) destination.delete();
                Log.e("BlastEm", "BIOS import failed", error);
                biosPickerResult = "";
                runOnUiThread(() -> android.widget.Toast.makeText(this,
                        "Could not import BIOS: " + error.getMessage(), android.widget.Toast.LENGTH_LONG).show());
            }
        }, "BlastEm-BIOS-import").start();
    }
	boolean chooseDirInProgress = false;
	String chooseDirResult = null;
	Map<String, Uri> uriMap = new HashMap<String, Uri>();
    // Keep display names in native paths so extension detection and CUE siblings work.
    private String libraryTree;

    @Override protected String[] getArguments() {
        String rom = getIntent().getStringExtra("library_rom");
        if (libraryTree == null || rom == null) return new String[0];
        String path = libraryTree + "/" + rom;
        String machine = getIntent().getStringExtra("library_machine");
        return machine == null ? new String[]{path} : new String[]{"-m", machine, path};
    }

    @Override protected void onDestroy() {
        boolean finished = isFinishing();
        super.onDestroy();
        // Native emulator globals must start fresh for the next game.
        // The launcher runs in the main process and stays alive.
        if (finished) android.os.Process.killProcess(android.os.Process.myPid());
    }

    private Uri resolveUri(String path) {
        Uri cached = uriMap.get(path);
        if (cached != null) return cached;
        if (libraryTree == null || !path.startsWith(libraryTree + "/")) return null;
        Uri tree = Uri.parse(libraryTree);
        String id = DocumentsContract.getTreeDocumentId(tree);
        Uri current = DocumentsContract.buildDocumentUriUsingTree(tree, id);
        StringBuilder key = new StringBuilder(libraryTree);
        try {
            java.util.ArrayDeque<String> parts = new java.util.ArrayDeque<>();
            for (String part : path.substring(libraryTree.length() + 1).replace('\\', '/').split("/")) {
                if (part.isEmpty() || part.equals(".")) continue;
                if (part.equals("..")) {
                    if (parts.isEmpty()) return null;
                    parts.removeLast();
                } else parts.addLast(part);
            }
            for (String name : parts) {
                boolean found = false;
                try (Cursor c = getContentResolver().query(
                        DocumentsContract.buildChildDocumentsUriUsingTree(tree, id),
                        new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                                DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
                    if (c == null) return null;
                    while (c.moveToNext()) if (name.equals(c.getString(1))) {
                        id = c.getString(0);
                        current = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                        key.append("/").append(name); uriMap.put(key.toString(), current);
                        found = true; break;
                    }
                }
                if (!found) return null;
            }
            return current;
        } catch (RuntimeException error) {
            Log.w("BlastEm", "Library document unavailable", error); return null;
        }
    }

	@Override
    protected void onCreate(Bundle savedInstanceState) {
        libraryTree = getIntent().getStringExtra("library_tree");
        if (libraryTree != null) {
            Uri tree = Uri.parse(libraryTree);
            uriMap.put(libraryTree, DocumentsContract.buildDocumentUriUsingTree(tree,
                    DocumentsContract.getTreeDocumentId(tree)));
        }
		super.onCreate(savedInstanceState);
		
		//set immersive mode on devices that support it
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			View blah = mSurface;
			blah.setSystemUiVisibility(
				View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
			);
		}
	}
	
	public String getRomPath() {
        if (libraryTree != null) return libraryTree;
		if (chooseDirInProgress) {
			if (chooseDirResult != null) {
				chooseDirInProgress = false;
				return chooseDirResult;
			}
			return null;
		}
		String extStorage = Environment.getExternalStorageDirectory().getAbsolutePath();
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
			return extStorage;
		}
		chooseDirInProgress = true;
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		//intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(extStorage));
		/*intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
		intent.putExtra("android.content.extra.FANCY", true);
		intent.putExtra("android.content.extra.SHOW_FILESIZE", true);*/
		startActivityForResult(intent, DOC_TREE_CODE);
		return null;
	}
	
	public String[] readUriDir(String uriString) {
		Uri uri = resolveUri(uriString);
		if (uri == null) {
			return new String[0];
		}
		//adapted from some androidx.documentfile
		final ContentResolver resolver = getContentResolver();
        final ArrayList<String> results = new ArrayList<>();

        Cursor c = null;
        try {
			Log.i("BlastEm", "getTreeDocumentId: " + DocumentsContract.getTreeDocumentId(uri));
			final Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri,
                DocumentsContract.isDocumentUri(this, uri) ? DocumentsContract.getDocumentId(uri) : DocumentsContract.getTreeDocumentId(uri)
			);
            c = resolver.query(
				childrenUri, new String[] {
					DocumentsContract.Document.COLUMN_DOCUMENT_ID,
					DocumentsContract.Document.COLUMN_DISPLAY_NAME,
					DocumentsContract.Document.COLUMN_MIME_TYPE
				}, null, null, null
			);
            while (c.moveToNext()) {
                final String documentId = c.getString(0);
				String name = c.getString(1);
				final String mime = c.getString(2);
                final Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(uri,
                        documentId);
                uriMap.put(uriString + "/" + name, documentUri);
				if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
					name += "/";
				}
				results.add(name);
            }
        } catch (Exception e) {
            Log.w("BlastEm", "Failed query: " + e);
        } finally {
            if (c != null) {
				c.close();
			}
        }
		return results.toArray(new String[0]);
	}
	
	public int openUriAsFd(String uriString, String mode) {
		Uri uri = resolveUri(uriString);
		if (uri == null) {
			Log.w("BlastEm", "Did not find URI in map: " + uriString);
			return 0;
		}
		if (mode.equals("rb")) {
			mode = "r";
		} else if (mode.equals("wb")) {
			mode = "w";
		}
		try {
			ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, mode);
			if (pfd != null) {
				return pfd.detachFd();
			}
			Log.w("BlastEm", "openFileDescriptor returned null: " + uriString);
		} catch (FileNotFoundException e) {
			Log.w("BlastEm", "Failed to open URI: " + e);
		} catch (IllegalArgumentException e) {
			Log.w("BlastEm", "Failed to open URI: " + e);
		}
		return 0;
	}
	
	public String[] getAssetsList(String path) {
		try {
			return getAssets().list(path);
		} catch (IOException e) {
			Log.w("BlastEm", "Failed to get assets at '" + path + "': " + e);
		}
		return new String[0];
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
		if (requestCode == BIOS_FILE_CODE) {
            if (resultCode == RESULT_OK && resultData != null && resultData.getData() != null) {
                importBiosFile(resultData.getData());
            } else {
                biosPickerResult = "";
            }
        } else if (requestCode == DOC_TREE_CODE) {
			if (resultCode == RESULT_OK && resultData != null) {
				Uri uri = resultData.getData();
				getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
				chooseDirResult = uri.toString();
				uriMap.put(chooseDirResult, uri);
				Log.i("BlastEm", "ACTION_OPEN_DOCUMENT_TREE got URI " + chooseDirResult);
			} else {
				Log.i("BlastEm", "ACTION_OPEN_DOCUMENT_TREE failed! ");
				chooseDirResult = "";
			}
		} else {
			super.onActivityResult(requestCode, resultCode, resultData);
		}
	}
}