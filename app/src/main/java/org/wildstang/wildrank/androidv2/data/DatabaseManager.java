package org.wildstang.wildrank.androidv2.data;

import android.content.Context;
import android.util.Log;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.Document;
import com.couchbase.lite.Manager;
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryEnumerator;
import com.couchbase.lite.QueryRow;
import com.couchbase.lite.UnsavedRevision;
import com.couchbase.lite.View;
import com.couchbase.lite.android.AndroidContext;

import org.wildstang.wildrank.androidv2.SyncUtilities;
import org.wildstang.wildrank.androidv2.UserHelper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class DatabaseManager {

    private static DatabaseManager instance;

    private Manager internalManager;
    private Database internalDatabase;
    private Manager externalManager;

    private DatabaseManager(Context context) throws IOException, CouchbaseLiteException {
        internalManager = new Manager(new com.couchbase.lite.android.AndroidContext(context.getApplicationContext()), Manager.DEFAULT_OPTIONS);
        internalDatabase = internalManager.getDatabase(DatabaseManagerConstants.DB_NAME);
        initializeViews();
    }

    public static DatabaseManager getInstance(Context context) throws CouchbaseLiteException, IOException {
        if (instance == null) {
            instance = new DatabaseManager(context);
        }
        return instance;
    }

    private void initializeViews() {
        com.couchbase.lite.View matchViewByNumber = internalDatabase.getView(DatabaseManagerConstants.MATCH_LIST_VIEW_BY_NUMBER);
        matchViewByNumber.setMap((document, emitter) -> {
            Object docType = document.get(DatabaseManagerConstants.DOC_TYPE);
            if (docType != null) {
                if (docType.toString().equals(DatabaseManagerConstants.MATCH_TYPE)) {
                    emitter.emit(document.get("match_number"), null);
                }
            }
        }, "8");

        com.couchbase.lite.View matchViewByKey = internalDatabase.getView(DatabaseManagerConstants.MATCH_LIST_VIEW_BY_KEY);
        matchViewByKey.setMap((document, emitter) -> {
            Object docType = document.get(DatabaseManagerConstants.DOC_TYPE);
            if (docType != null) {
                if (docType.toString().equals(DatabaseManagerConstants.MATCH_TYPE)) {
                    emitter.emit(document.get("key"), null);
                }
            }
        }, "1");

        com.couchbase.lite.View matchResultsView = internalDatabase.getView(DatabaseManagerConstants.MATCH_RESULT_VIEW);
        matchResultsView.setMap((document, emitter) -> {
            Object docType = document.get(DatabaseManagerConstants.DOC_TYPE);
            if (docType != null) {
                if (docType.toString().equals(DatabaseManagerConstants.MATCH_RESULT_TYPE)) {
                    emitter.emit(document.get("team_key"), null);
                }
            }
        }, "1");

        View usersView = internalDatabase.getView(DatabaseManagerConstants.USER_VIEW_BY_ID);
        usersView.setMap((document, emitter) -> {
            Object docType = document.get(DatabaseManagerConstants.DOC_TYPE);
            if (docType != null) {
                if (docType.toString().equals(DatabaseManagerConstants.USER_TYPE)) {
                    emitter.emit(document.get("id"), null);
                }
            }
        }, "1");

        View teamsView = internalDatabase.getView(DatabaseManagerConstants.TEAM_LIST_VIEW_BY_NUMBER);
        teamsView.setMap((document, emitter) -> {
            Object docType = document.get(DatabaseManagerConstants.DOC_TYPE);
            if (docType != null) {
                if (docType.toString().equals(DatabaseManagerConstants.TEAM_TYPE)) {
                    emitter.emit(document.get("team_number"), null);
                }
            }
        }, "1");

        View pitResultsView = internalDatabase.getView(DatabaseManagerConstants.PIT_RESULTS_VIEW);
        pitResultsView.setMap((document, emitter) -> {
            Object docType = document.get(DatabaseManagerConstants.DOC_TYPE);
            if (docType != null) {
                if (docType.toString().equals(DatabaseManagerConstants.PIT_RESULTS_TYPE)) {
                    emitter.emit(document.get("team_key"), null);
                }
            }
        }, "1");
    }

    public Manager getInternalManager() {
        return internalManager;
    }

    public Database getInternalDatabase() {
        return internalDatabase;
    }

    public Database getExternalDatabase(Context context) throws Exception {
        try {
            // Custom Couchbase Manager that points to the flash drive
            com.couchbase.lite.Context externalContext = new AndroidContext(context.getApplicationContext()) {
                @Override
                public File getFilesDir() {
                    return new File(SyncUtilities.getExternalRootDirectory(context));
                }
            };
            if (externalManager == null) {
                externalManager = new Manager(externalContext, Manager.DEFAULT_OPTIONS);
            }
            Database externalDatabase = externalManager.getExistingDatabase(DatabaseManagerConstants.DB_NAME);
            if (externalDatabase == null) {
                throw new Exception("Error opening database!");
            }
            return externalDatabase;
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Error opening database!");
        }
    }

    public void dumpDatabaseContentsToLog() {
        try {
            Query allDocsQuery = internalDatabase.createAllDocumentsQuery();
            QueryEnumerator result = allDocsQuery.run();
            for (Iterator<QueryRow> it = result; it.hasNext(); ) {
                QueryRow row = it.next();
                Document doc = row.getDocument();
                Log.d("wildrank", "Document contents: " + doc.getProperties());
            }
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
    }

    /*
     * Matches
     */

    //Returns a query of all the matches
    public Query getAllMatches() {
        Query query = internalDatabase.getView(DatabaseManagerConstants.MATCH_LIST_VIEW_BY_NUMBER).createQuery();
        query.setDescending(false);
        return query;
    }

    //Returns a query of all match results for a specific match key
    public Document getMatchFromKey(String matchKey) throws CouchbaseLiteException {
        Query query = internalDatabase.getView(DatabaseManagerConstants.MATCH_LIST_VIEW_BY_KEY).createQuery();
        query.setDescending(false);
        query.setStartKey(matchKey);
        query.setEndKey(matchKey);
        QueryEnumerator results = query.run();
        if (results.getCount() > 0) {
            return results.getRow(0).getDocument();
        } else {
            return null;
        }
    }

    //Returns a document for a team whose key is given
    public Document getTeamFromKey(String teamKey) throws CouchbaseLiteException {
        return internalDatabase.getExistingDocument("team:" + teamKey);
    }

    /*
     * Match results
     */

    public void saveMatchResults(MatchResultsModel matchResults) throws CouchbaseLiteException {
        Document document = internalDatabase.getDocument(matchResults.getMatchKey() + ":" + matchResults.getTeamKey());
        UnsavedRevision revision = document.createRevision();
        HashMap<String, Object> properties = new HashMap<>();
        properties.put("type", DatabaseManagerConstants.MATCH_RESULT_TYPE);
        properties.put("users", matchResults.getUserIds());
        properties.put("match_key", matchResults.getMatchKey());
        properties.put("team_key", matchResults.getTeamKey());
        properties.put("data", matchResults.getData());
        revision.setProperties(properties);
        revision.save();
    }

    public Document getMatchResults(String matchKey, String teamKey) {
        return internalDatabase.getDocument(matchKey + ":" + teamKey);
    }

    public List<Document> getMatchResultsForTeam(String teamKey) throws CouchbaseLiteException {
        Query query = internalDatabase.getView(DatabaseManagerConstants.MATCH_RESULT_VIEW).createQuery();
        query.setDescending(false);
        query.setStartKey(teamKey);
        query.setEndKey(teamKey);
        QueryEnumerator results = query.run();
        if (results.getCount() > 0) {
            List<Document> matches = new ArrayList<>();
            for (Iterator<QueryRow> it = results; it.hasNext(); ) {
                matches.add(it.next().getDocument());
            }
            return matches;
        } else {
            return null;
        }
    }

    //Returns a query of all the match results
    public Query getAllCompleteMatches() {
        Query query = internalDatabase.getView(DatabaseManagerConstants.MATCH_RESULT_VIEW).createQuery();
        query.setDescending(false);
        return query;
    }

    public boolean isMatchScouted(String matchKey, String teamKey) {
        return (internalDatabase.getExistingDocument(matchKey + ":" + teamKey) != null);
    }

    /*
     * Users
     */

    public Document getUserById(String id) throws CouchbaseLiteException {
        return internalDatabase.getExistingDocument("user:" + id);
    }

    /*
     * Teams
     */
    public Query getAllTeams() {
        Query query = internalDatabase.getView(DatabaseManagerConstants.TEAM_LIST_VIEW_BY_NUMBER).createQuery();
        query.setDescending(false);
        return query;
    }

    /*
     * Team Images
     */
    public Document getTeamImagesDocument(String teamKey) {
        return internalDatabase.getExistingDocument("images:" + teamKey);
    }

    /*
     * Pit Scouting
     */

    public boolean isTeamPitScouted(String teamKey) {
        return (internalDatabase.getExistingDocument("pit:" + teamKey) != null);
    }

    public void savePitResults(PitResultsModel pitResults) throws CouchbaseLiteException {
        Document document = internalDatabase.getDocument("pit:" + pitResults.getTeamKey());
        UnsavedRevision revision = document.createRevision();
        HashMap<String, Object> properties = new HashMap<>();
        properties.put("type", DatabaseManagerConstants.PIT_RESULTS_TYPE);
        properties.put("users", pitResults.getUserIds());
        properties.put("team_key", pitResults.getTeamKey());
        properties.put("data", pitResults.getData());
        revision.setProperties(properties);
        revision.save();
    }

    /*
     * General
     */

    /**
     * This method will take note of the current database state so we can reference it later when
     * syncing. If we know the state of the internal and external databases at the time of the last
     * sync, we can determine what's changed and act appropriately.
     * <p>
     * Database state will be saved as a list of maps, with each map containing the following
     * properties: document_id: the id of the document (String) deleted: if this document is deleted
     * (boolean) document_revision: the last known revision of this document (String)
     */
    public void trackCurrentInternalDatabaseState() {
        trackDatabaseState(internalDatabase, DatabaseManagerConstants.LAST_KNOWN_INTERNAL_DATABASE_STATE_DOCUMENT_ID);
    }

    public void trackCurrentExternalDatabaseState(Database externalDatabase) {
        trackDatabaseState(externalDatabase, DatabaseManagerConstants.LAST_KNOWN_EXTERNAL_DATABASE_STATE_DOCUMENT_ID);
    }

    private void trackDatabaseState(Database trackingDatabase, String saveDocumentId) {
        Query allDocsQuery = trackingDatabase.createAllDocumentsQuery();
        try {
            QueryEnumerator queryenum = allDocsQuery.run();
            List<Document> allDocuments = new ArrayList<>();
            for (Iterator<QueryRow> it = queryenum; it.hasNext(); ) {
                QueryRow row = it.next();
                allDocuments.add(row.getDocument());
            }

            List<Map<String, Object>> documentStates = new ArrayList<>();
            for (Document document : allDocuments) {
                // Don't track the document that stores the document state
                if (document.getId().equals(saveDocumentId)) {
                    continue;
                }

                Map<String, Object> documentMap = new HashMap<>();
                documentMap.put(DatabaseManagerConstants.DOCUMENT_ID, document.getId());
                documentMap.put(DatabaseManagerConstants.DELETED, document.isDeleted());
                documentMap.put(DatabaseManagerConstants.DOCUMENT_REVISION, document.getCurrentRevisionId());
                documentStates.add(documentMap);
            }

            // Persist the new data in the local database
            Document document = internalDatabase.getDocument(saveDocumentId);
            UnsavedRevision revision = document.createRevision();
            HashMap<String, Object> properties = new HashMap<>();
            properties.put(DatabaseManagerConstants.DATA_KEY, documentStates);
            revision.setProperties(properties);
            revision.save();
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
    }

    public DatabaseState getLastKnownInternalState() {
        return getLastKnownStateFromDocumentId(DatabaseManagerConstants.LAST_KNOWN_INTERNAL_DATABASE_STATE_DOCUMENT_ID);
    }

    public DatabaseState getLastKnownExternalState() {
        return getLastKnownStateFromDocumentId(DatabaseManagerConstants.LAST_KNOWN_EXTERNAL_DATABASE_STATE_DOCUMENT_ID);
    }

    private DatabaseState getLastKnownStateFromDocumentId(String docId) {
        Document doc = internalDatabase.getExistingDocument(docId);
        if (doc == null) {
            return null;
        }

        List<Map<String, Object>> states = (List<Map<String, Object>>) doc.getProperty(DatabaseManagerConstants.DATA_KEY);
        DatabaseState databaseState = new DatabaseState();

        for (Map<String, Object> state : states) {
            String id = (String) state.get(DatabaseManagerConstants.DOCUMENT_ID);
            String revisionId = (String) state.get(DatabaseManagerConstants.DOCUMENT_REVISION);
            boolean deleted = (Boolean) state.get(DatabaseManagerConstants.DELETED);
            databaseState.addStateRecord(new DocumentState(docId, revisionId, deleted));
        }

        return databaseState;
    }

    public void saveNotes(String teamKey, String note, Context c) throws CouchbaseLiteException {
        if (!note.equals("")) {
            Boolean existed = (internalDatabase.getExistingDocument("notes:" + teamKey) != null);
            Document document = internalDatabase.getDocument("notes:" + teamKey);
            UnsavedRevision revision = document.createRevision();

            HashMap<String, Object> properties = new HashMap<>();
            properties.put("type", DatabaseManagerConstants.NOTES_RESULTS_TYPE);
            properties.put("users", UserHelper.getLoggedInUsersAsArray(c));
            properties.put("team_key", teamKey);

            List<String> notesList;
            if (existed) {
                notesList = (ArrayList<String>) document.getProperties().get("notes");
            } else {
                notesList = new ArrayList<>();
            }

            notesList.add(note);
            properties.put("notes", notesList);

            revision.setProperties(properties);
            revision.save();
        }
    }

    public String[] getNotes(String teamKey) {
        Boolean existed = (internalDatabase.getExistingDocument("notes:" + teamKey) != null);
        Document document = internalDatabase.getDocument("notes:" + teamKey);
        List<String> notesList;
        if (existed) {
            notesList = (ArrayList<String>) document.getProperties().get("notes");
        } else {
            notesList = new ArrayList<>();
        }
        return notesList.toArray(new String[notesList.size()]);
    }

    public class DatabaseState {
        Map<String, DocumentState> states;

        public DatabaseState() {
            states = new HashMap<>();
        }

        public void addStateRecord(DocumentState state) {
            states.put(state.docId, state);
        }

        public boolean documentExists(String docId) {
            return states.containsKey(docId);
        }

        public DocumentState getDocStateForId(String docId) {
            return states.get(docId);
        }
    }

    public class DocumentState {
        public String docId, revisionId;
        public boolean deleted;

        public DocumentState(String docId, String revisionId, boolean deleted) {
            this.docId = docId;
            this.revisionId = revisionId;
            this.deleted = deleted;
        }
    }
}
