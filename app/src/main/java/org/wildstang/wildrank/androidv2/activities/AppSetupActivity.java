package org.wildstang.wildrank.androidv2.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.couchbase.lite.Context;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.Document;
import com.couchbase.lite.Manager;
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryEnumerator;
import com.couchbase.lite.QueryRow;
import com.couchbase.lite.UnsavedRevision;
import com.couchbase.lite.android.AndroidContext;

import org.wildstang.wildrank.androidv2.R;
import org.wildstang.wildrank.androidv2.SyncUtilities;
import org.wildstang.wildrank.androidv2.Utilities;
import org.wildstang.wildrank.androidv2.data.DatabaseManager;
import org.wildstang.wildrank.androidv2.data.DatabaseManagerConstants;

import java.io.File;
import java.util.Iterator;
import java.util.Map;

public class AppSetupActivity extends AppCompatActivity implements View.OnClickListener {

    public static final int RESULT_CODE_MOUNT = 34;
    public static final int RESULT_CODE_UNMOUNT = 45;

    private Button beginLoadButton;
    private ProgressBar loadProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_setup);

        beginLoadButton = (Button) findViewById(R.id.button);
        loadProgressBar = (ProgressBar) findViewById(R.id.progress_bar);

        beginLoadButton.setOnClickListener(this);
    }

    private void beginDataLoad() {
        if (!SyncUtilities.isFlashDriveConnected()) {
            showExternalWarning();
        } else {
            // Load data from the flash drive
            new SetupTask().execute();
            loadProgressBar.setVisibility(View.VISIBLE);
        }
    }

    private void setupComplete() {
        startActivityForResult(new Intent(android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS), RESULT_CODE_UNMOUNT);
        Toast.makeText(this, "Scroll down, press \"Unmount\", press back button.", Toast.LENGTH_LONG).show();
        PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean(HomeActivity.PREF_IS_APP_CONFIGURED, true).commit();
    }

    private void showExternalWarning() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Connect flash drive");
        builder.setMessage("Please connect a flash drive that has been set up using the desktop application.");
        builder.setPositiveButton("Try again", (dialog, which) -> beginDataLoad());
        builder.setNegativeButton("Cancel", (dialog, which) -> AppSetupActivity.this.finish());
        builder.setCancelable(false).create().show();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.button) {
            beginDataLoad();
            findViewById(R.id.button).setEnabled(false);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RESULT_CODE_MOUNT) {
            //promptForDataSource();
        } else if (requestCode == RESULT_CODE_UNMOUNT) {
            setResult(Activity.RESULT_OK);
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        }
    }

    private class SetupTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            try {
                Manager internalManager = DatabaseManager.getInstance(AppSetupActivity.this).getInternalManager();

                // Custom Couchbase Manager that points to the flash drive
                Context context = new AndroidContext(AppSetupActivity.this) {
                    @Override
                    public File getFilesDir() {
                        return new File(Utilities.getExternalRootDirectory() + "/");
                    }
                };
                Manager externalManager = new Manager(context, Manager.DEFAULT_OPTIONS);

                Database internalDatabase = internalManager.getDatabase(DatabaseManagerConstants.DB_NAME);
                Database externalDatabase = externalManager.getExistingDatabase(DatabaseManagerConstants.DB_NAME);

                // Use a transaction to ensure that the sync is an all-or-nothing operation
                internalDatabase.runInTransaction(() -> {
                    try {
                        // Copy everything into the internal database
                        Query query = externalDatabase.createAllDocumentsQuery();
                        query.setAllDocsMode(Query.AllDocsMode.ALL_DOCS);
                        QueryEnumerator result = query.run();
                        Log.d("wildrank", "QueryEnumerator length: " + result.getCount());
                        for (Iterator<QueryRow> it = result; it.hasNext(); ) {
                            QueryRow row = it.next();
                            Document existingDoc = row.getDocument();
                            Log.d("wildrank", "Document contents: " + existingDoc.getProperties().toString());
                            Document doc = internalDatabase.getDocument(existingDoc.getId());
                            UnsavedRevision revision = doc.createRevision();
                            Map<String, Object> existingProperties = existingDoc.getProperties();
                            try {
                                revision.setProperties(existingProperties);
                                revision.save();
                            } catch (CouchbaseLiteException e) {
                                Log.d("Wildrank", "Error writing document");
                                e.printStackTrace();
                            }
                        }

                        DatabaseManager.getInstance(AppSetupActivity.this).trackCurrentInternalDatabaseState();
                        DatabaseManager.getInstance(AppSetupActivity.this).trackCurrentExternalDatabaseState(externalDatabase);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return false;
                    }

                    return true;
                });


                externalDatabase.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            AppSetupActivity.this.setupComplete();
        }
    }
}
