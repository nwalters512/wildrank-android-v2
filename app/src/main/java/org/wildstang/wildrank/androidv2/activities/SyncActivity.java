package org.wildstang.wildrank.androidv2.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.couchbase.lite.Database;
import com.couchbase.lite.Document;
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryEnumerator;
import com.couchbase.lite.QueryRow;
import com.couchbase.lite.UnsavedRevision;

import org.wildstang.wildrank.androidv2.R;
import org.wildstang.wildrank.androidv2.SyncUtilities;
import org.wildstang.wildrank.androidv2.data.DatabaseManager;
import org.wildstang.wildrank.androidv2.data.DatabaseManagerConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SyncActivity extends AppCompatActivity {

    //TextView internalDatabaseContents;
    //TextView externalDatabaseContents;
    ProgressBar bar;
    TextView text;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_sync);

        //internalDatabaseContents = (TextView) findViewById(R.id.internal_contents);
        //externalDatabaseContents = (TextView) findViewById(R.id.external_contents);
        bar = (ProgressBar) findViewById(R.id.progressBar);
        text = (TextView) findViewById(R.id.textView);

        beginSync();
    }

    private void beginSync() {
        if (!SyncUtilities.isFlashDriveConnected()) {
            showDriveNotMountedWarning();
        } else {
            new SyncTask().execute();
        }
    }

    private void showDriveNotMountedWarning() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Connect flash drive");
        builder.setMessage("Please connect a flash drive that has been set up using the desktop application.");
        builder.setPositiveButton("Try again", (dialog, which) -> beginSync());
        builder.setNegativeButton("Cancel", (dialog, which) -> SyncActivity.this.finish());
        builder.create().show();
    }

    /**
     * Syncs the internal and external databases
     */
    private void syncDatabases() throws Exception {
        /* First, read the current states and last known states into memory */

        Database externalDatabase = DatabaseManager.getInstance(this).getExternalDatabase(this);
        Database internalDatabase = DatabaseManager.getInstance(this).getInternalDatabase();
        externalDatabase.open();

        DatabaseManager.DatabaseState lastExternalState = DatabaseManager.getInstance(this).getLastKnownExternalState();
        DatabaseManager.DatabaseState lastInternalState = DatabaseManager.getInstance(this).getLastKnownInternalState();

        // We use transactions to speed things up and to keep everything atomic
        internalDatabase.runInTransaction(() -> externalDatabase.runInTransaction(() -> {
            try {
                // Now, iterate through all the current records in the internal database
                Query query = internalDatabase.createAllDocumentsQuery();
                query.setAllDocsMode(Query.AllDocsMode.ALL_DOCS);
                QueryEnumerator result = query.run();
                //Log.d("wildrank", "QueryEnumerator length: " + result.getCount());
                int j = 0;
                bar.setMax(result.getCount());
                for (Iterator<QueryRow> it = result; it.hasNext(); j++) {
                    bar.setProgress(j);
                    QueryRow row = it.next();
                    Document doc = row.getDocument();
                    String docId = doc.getId();
                    //Log.d("wildrank", "current doc id: " + docId);
                    if (docId.equals(DatabaseManagerConstants.LAST_KNOWN_INTERNAL_DATABASE_STATE_DOCUMENT_ID) || docId.equals(DatabaseManagerConstants.LAST_KNOWN_EXTERNAL_DATABASE_STATE_DOCUMENT_ID)) {
                        // Skip the state tracking things
                        continue;
                    }

                    // Notes are special, we have to manually sync them
                    if (doc.getProperty(DatabaseManagerConstants.DOC_TYPE) != null) {
                        if (doc.getProperty(DatabaseManagerConstants.DOC_TYPE).equals(DatabaseManagerConstants.NOTES_RESULTS_TYPE)) {
                            if (externalDatabase.getExistingDocument(docId) == null) {
                                // The notes don't exist externally. Copy over the internal document.
                                Document document = externalDatabase.getDocument(docId);
                                UnsavedRevision revision = document.createRevision();
                                revision.setProperties(doc.getProperties());
                                revision.save();
                            } else {
                                // These notes exist both internally and externally. Manual data merge time!

                                // Get each list of notes
                                ArrayList<String> internalNotes = (ArrayList<String>) doc.getProperty("notes");
                                if (internalNotes == null) {
                                    internalNotes = new ArrayList<>();
                                }

                                ArrayList<String> externalNotes = (ArrayList<String>) externalDatabase.getDocument(docId).getProperty("notes");
                                if (externalNotes == null) {
                                    externalNotes = new ArrayList<>();
                                }

                                // Determine the last note that each list has in common
                                int lastInCommonIndex = 0;
                                for (int i = 0; i < internalNotes.size(); i++) {
                                    if (i >= externalNotes.size()) {
                                        // Current index is outside the bounds of the external notes array.
                                        // Go back to the last index and break out of here!
                                        lastInCommonIndex = i - 1;
                                        break;
                                    }
                                    if (externalNotes.get(i).equals(internalNotes.get(i))) {
                                        // The lists both have this note in common
                                        lastInCommonIndex = i;
                                    } else {
                                        // We've found the point at which the lists differ.
                                        break;
                                    }
                                }

                                // Add all the external notes past the last note in common to the internal notes
                                for (int i = (lastInCommonIndex + 1); i < externalNotes.size(); i++) {
                                    internalNotes.add(externalNotes.get(i));
                                }

                                // Remove duplicates
                                // We do it this way instead of using a set so we can sort of maintain chronological order
                                List<String> newNotes = new ArrayList<>();
                                for (String string : internalNotes) {
                                    if (!newNotes.contains(string)) {
                                        newNotes.add(string);
                                    }
                                }

                                // Create a new set of properties with these notes based on the existing internal properties
                                Map<String, Object> newProps = new HashMap<>(doc.getProperties());
                                newProps.remove("notes");
                                newProps.put("notes", newNotes);

                                // Save the documents both internally and externally
                                Document document = internalDatabase.getDocument(docId);
                                UnsavedRevision revision = document.createRevision();
                                revision.setProperties(newProps);
                                revision.save();

                                document = externalDatabase.getDocument(docId);
                                revision = document.createRevision();
                                revision.setProperties(newProps);
                                revision.save();
                            }
                            continue;
                        }
                    }

                    // Check if the record exists in the external database
                    if (externalDatabase.getExistingDocument(docId) == null) {
                        // If not, this is a new document that should immediately be synced to the external database
                        Document document = externalDatabase.getDocument(docId);
                        UnsavedRevision revision = document.createRevision();
                        revision.setProperties(doc.getProperties());
                        revision.save();
                    } else {
                        // If it does exist externally, compare the revision IDs
                        if (externalDatabase.getDocument(docId).getCurrentRevisionId().equals(doc.getCurrentRevisionId())) {
                            // If the revision IDs are the same, no action is required
                            continue;
                        } else {
                            // If they are not the same, then the local document has been modified since the time of the last sync
                            // Compare the revision IDs of the external document both currently and at the time of the last sync
                            DatabaseManager.DocumentState lastKnownExternalRevisionState = lastExternalState.getDocStateForId(docId);
                            DatabaseManager.DocumentState lastKnownInternalRevisionState = lastInternalState.getDocStateForId(docId);
                            if (lastKnownExternalRevisionState == null && lastKnownInternalRevisionState == null) {
                                // The document was newly created both internally and externally.
                                // For now, keep the external one.
                                // TODO more advanced conflict resolution
                                Document document = internalDatabase.getDocument(docId);
                                UnsavedRevision revision = document.createRevision();
                                revision.setProperties(externalDatabase.getDocument(docId).getProperties());
                                revision.save();
                                continue;
                            } else if (lastKnownExternalRevisionState == null) {
                                // The document existed locally at the time of the last sync but
                                Log.d("wildrank", "Last known external revision state is null for doc " + docId);
                            } else {
                                Log.d("wildrank", "Last known internal revision state is null for doc " + docId);
                            }
                            String currentExternalRevisionId = externalDatabase.getDocument(docId).getCurrentRevisionId();
                            String lastKnownExternalRevisionId = lastExternalState.getDocStateForId(docId).revisionId;
                            String currentInternalRevisionId = internalDatabase.getDocument(docId).getCurrentRevisionId();
                            String lastKnownInternalRevisionId = lastInternalState.getDocStateForId(doc.getId()).revisionId;
                            if (!(currentExternalRevisionId.equals(lastKnownExternalRevisionId) && (currentInternalRevisionId.equals(lastKnownInternalRevisionId)))) {
                                // The document was modified externally but not internally
                                // Replace the internal document with the external one
                                Document document = internalDatabase.getDocument(docId);
                                UnsavedRevision revision = document.createRevision();
                                revision.setProperties(externalDatabase.getDocument(docId).getProperties());
                                revision.save();
                            } else if ((currentExternalRevisionId.equals(lastKnownExternalRevisionId) && !(currentInternalRevisionId.equals(lastKnownInternalRevisionId)))) {
                                // The document was modified internally but not externally
                                // Replace the external document with the internal one
                                Document document = externalDatabase.getDocument(docId);
                                UnsavedRevision revision = document.createRevision();
                                revision.setProperties(doc.getProperties());
                                revision.save();
                            } else {
                                // If they are not the same, we have a conflict. Prompt the user to resolve it. Perhaps just show the raw JSON objects and let them choose?
                            }
                        }
                    }

                    // TODO: handle deletions
                }

                // Now we can sync the external database back to the internal one
                query = externalDatabase.createAllDocumentsQuery();
                query.setAllDocsMode(Query.AllDocsMode.ALL_DOCS);
                result = query.run();
                bar.setMax(result.getCount());
                //Log.d("wildrank", "QueryEnumerator length: " + result.getCount());
                // Iterate through all the current records in the external database
                j = 0;
                for (Iterator<QueryRow> it = result; it.hasNext(); j++) {
                    bar.setProgress(j);
                    QueryRow row = it.next();
                    Document doc = row.getDocument();
                    String docId = doc.getId();
                    if (docId.equals(DatabaseManagerConstants.LAST_KNOWN_INTERNAL_DATABASE_STATE_DOCUMENT_ID) || docId.equals(DatabaseManagerConstants.LAST_KNOWN_EXTERNAL_DATABASE_STATE_DOCUMENT_ID)) {
                        // Skip the state tracking things
                        continue;
                    }

                    // Check if the document key exists in the internal database
                    if (internalDatabase.getExistingDocument(docId) == null) {
                        // If it does not, this is a new document and can be immediately synced to the internal database.
                        Document document = internalDatabase.getDocument(docId);
                        UnsavedRevision revision = document.createRevision();
                        revision.setProperties(doc.getProperties());
                        revision.save();
                    } else {
                        // If the document exists internally, it woudl already have been handled during the first
                        // half of the sync process. Nothing to do here.
                    }

                    // TODO: still need to handle deletions
                }
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
            return true;
        }));

        externalDatabase.close();
    }
/*
    private void readDatabases() {
        Log.d("wildrank", "reading databasees");
        try {

            Database internalDatabase = DatabaseManager.getInstance(this).getInternalDatabase();
            Database externalDatabase = DatabaseManager.getInstance(this).getExternalDatabase(this);
            externalDatabase.open();

            StringBuilder externalString = new StringBuilder();
            Query query = externalDatabase.createAllDocumentsQuery();
            query.setAllDocsMode(Query.AllDocsMode.ALL_DOCS);
            QueryEnumerator result = query.run();
            Log.d("wildrank", "QueryEnumerator length: " + result.getCount());
            for (Iterator<QueryRow> it = result; it.hasNext(); ) {
                QueryRow row = it.next();
                Document doc = row.getDocument();
                externalString.append(doc.getProperties()).append("\n");
            }
            externalDatabase.close();
            externalDatabaseContents.setText(externalString.toString());

            StringBuilder internalString = new StringBuilder();
            query = internalDatabase.createAllDocumentsQuery();
            query.setAllDocsMode(Query.AllDocsMode.ALL_DOCS);
            result = query.run();
            Log.d("wildrank", "QueryEnumerator length: " + result.getCount());
            for (Iterator<QueryRow> it = result; it.hasNext(); ) {
                QueryRow row = it.next();
                Document doc = row.getDocument();
                internalString.append(doc.getProperties()).append("\n");
            }
            internalDatabaseContents.setText(internalString.toString());

            Log.d("wildrank", internalDatabaseContents.toString());
            Log.d("wildrank", externalDatabaseContents.toString());
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error reading!", Toast.LENGTH_SHORT).show();
        }
    }*/

    private class SyncTask extends AsyncTask<Void, Void, SyncTask.SyncResult> {

        @Override
        protected SyncTask.SyncResult doInBackground(Void... params) {
            try {
                syncDatabases();
                SyncResult r = new SyncResult();
                r.result = SyncResult.RESULT_SUCCESS;
                return r;
            } catch (Exception e) {
                e.printStackTrace();
                SyncResult r = new SyncResult();
                r.result = SyncResult.RESULT_ERROR;
                return r;
            }
        }

        @Override
        protected void onPostExecute(SyncResult result) {
            super.onPostExecute(result);
            switch (result.result) {
                case SyncResult.RESULT_SUCCESS:
                    startActivity(new Intent(android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
                    bar.setEnabled(false);
                    text.setText("Done!");
                    Toast.makeText(SyncActivity.this, "Scroll down, press \"Unmount\", press back button.", Toast.LENGTH_LONG).show();
                    break;
                case SyncResult.RESULT_ERROR:
                    Toast.makeText(SyncActivity.this, "Error syncing databases. Check logcat.", Toast.LENGTH_LONG).show();
                    break;
            }
            //readDatabases();
        }

        protected class SyncResult {
            public static final int RESULT_SUCCESS = 0;
            public static final int RESULT_ERROR = 1;

            public int result;
        }
    }
}
