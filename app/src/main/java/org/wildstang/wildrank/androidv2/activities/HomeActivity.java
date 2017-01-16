package org.wildstang.wildrank.androidv2.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.wildstang.wildrank.androidv2.R;
import org.wildstang.wildrank.androidv2.UserHelper;
import org.wildstang.wildrank.androidv2.fragments.MatchScoutingMainFragment;
import org.wildstang.wildrank.androidv2.fragments.NotesMainFragment;
import org.wildstang.wildrank.androidv2.fragments.PitScoutingMainFragment;
import org.wildstang.wildrank.androidv2.fragments.ScoutersFragment;
import org.wildstang.wildrank.androidv2.fragments.TeamSummariesMainFragment;
import org.wildstang.wildrank.androidv2.fragments.WhiteboardFragment;
import org.wildstang.wildrank.androidv2.models.UserModel;

import java.util.List;

/**
 * Serves as the jumping-off point of the entire app. It utilizes a navigation drawer to jump
 * between various top-level screens, including match scouting, pit scouting, and data viewing.
 * <p>
 * When you switch modes, the app swaps in the appropriate main fragment for the given mode. The
 * modes are defined in the enum Mode; new modes should be added there, and any setup that should be
 * done when that mode is selected should be defined in switchToMode().
 */
public class HomeActivity extends AppCompatActivity {

    public static final String PREF_IS_APP_CONFIGURED = "is_app_configured";
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle drawerToggle;
    private ListView navigationDrawerList;
    private Toolbar toolbar;
    private Mode currentMode = null;

    @Override
    public void onResume() {
        super.onResume();

        // If we're resuming, the assigned team could have changed. Set up the color of the Toolbar again.
        setUpToolbar();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        boolean isAppConfigured = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREF_IS_APP_CONFIGURED, false);
        boolean isLoggedIn = UserHelper.isUserLoggedIn(this);

        if (isAppConfigured) {
            // The app has been set up! Go ahead and display stuff.
            if (isLoggedIn) {
                // Default to match scouting mode
                switchToMode(Mode.MATCH_SCOUTING);
            } else {
                // A user needs to be looged in first before we can begin
                startActivity(new Intent(this, UserLoginActivity.class));
                finish();
            }
        } else {
            /* The app hasn't been setup yet. Launch the setup activity and finish this one.
             * After setup is finished, we will relaunch this one.
             */
            startActivity(new Intent(this, AppSetupActivity.class));
            finish();
        }

        setUpToolbar();

        drawerLayout = (DrawerLayout) findViewById(R.id.nav_drawer_layout);
        navigationDrawerList = (ListView) findViewById(R.id.navigation_drawer_list);
        setUpNavigationDrawer();
    }

    private void setUpToolbar() {
        toolbar.setTitleTextColor(Color.WHITE);

        String team = PreferenceManager.getDefaultSharedPreferences(this).getString("assignedTeam", "red_1");
        if (team.contains("red")) {
            toolbar.setBackgroundColor(getResources().getColor(R.color.material_red));

        } else {
            toolbar.setBackgroundColor(getResources().getColor(R.color.material_blue));
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        drawerToggle.syncState();
    }

    public void setUpNavigationDrawer() {
        // set a custom shadow that overlays the main content when the drawer opens
        // drawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        // set up the drawer's list view with items and click listener

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);

        // ActionBarDrawerToggle ties together the the proper interactions
        // between the navigation drawer and the action bar app icon.
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.drawer_open, R.string.drawer_close);
        drawerLayout.setDrawerListener(drawerToggle);
        // Defer code dependent on restoration of previous instance state.
        drawerLayout.post(drawerToggle::syncState);

        // Create array of mode names for list adapter
        String[] modeNames = new String[Mode.values().length];
        for (Mode mode : Mode.values()) {
            modeNames[mode.ordinal()] = mode.getTitle(this);
        }

        navigationDrawerList.setOnItemClickListener((parent, view, position, id) -> onItemSelected(position));
        navigationDrawerList.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, modeNames));
    }

    /**
     * Called when an item is selected from the navigation drawer.
     *
     * @param position the position of the selected item in the list. This should correspond to the
     *                 ordinal of the enum representing this mode.
     */
    private void onItemSelected(int position) {
        if (position < 0 || position > Mode.values().length - 1) {
            return;
        }

        Mode mode = Mode.values()[position];
        switchToMode(mode);
        drawerLayout.closeDrawers();
    }

    /**
     * Called when a mode is selected in the navigation drawer. This should insert the correct
     * fragment into the main fragment container.
     *
     * @param mode The mode that was selected. May be the same as the current mode.
     */
    private void switchToMode(Mode mode) {
        if (currentMode != null && currentMode == mode) {
            return;
        }
        switch (mode) {
            case MATCH_SCOUTING:
                //Match scouting
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new MatchScoutingMainFragment()).commit();
                break;
            case PIT_SCOUTING:
                // Pit scouting
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new PitScoutingMainFragment()).commit();
                break;
            case NOTES:
                // Notes
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new NotesMainFragment()).commit();
                break;
            case SCOUTERS:
                // Scouters
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new ScoutersFragment()).commit();
                break;
            case WHITEBOARD:
                // Whiteboard
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new WhiteboardFragment()).commit();
                break;
            case TEAM_SUMMARIES:
                // Team Summaries
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new TeamSummariesMainFragment()).commit();
                break;
            default:
                break;
        }
        currentMode = mode;
        getSupportActionBar().setTitle(currentMode.getTitle());
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Forward the new configuration the drawer toggle component.
        drawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_home, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_users) {
            showUserDialog();
            return true;
        } else if (id == R.id.action_sync) {
            startActivity(new Intent(this, SyncActivity.class));
            return true;
        } else if (drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showUserDialog() {
        FragmentManager fm = getSupportFragmentManager();
        ManageUsersDialog usersDialog = new ManageUsersDialog();
        usersDialog.show(fm, "users_dialog");
    }

    // Defines "modes" that can be switched to from the navigation drawer
    private enum Mode {
        MATCH_SCOUTING(R.string.mode_match_scouting),
        PIT_SCOUTING(R.string.mode_pit_scouting),
        NOTES(R.string.mode_notes),
        SCOUTERS(R.string.mode_scouters),
        WHITEBOARD(R.string.mode_whiteboard),
        TEAM_SUMMARIES(R.string.mode_team_summaries);

        private final int titleRes;

        Mode(@StringRes int titleRes) {
            this.titleRes = titleRes;
        }

        public int getTitle() {
            return this.titleRes;
        }

        public String getTitle(Context context) {
            return context.getString(this.titleRes);
        }
    }

    public static class ManageUsersDialog extends DialogFragment {
        private ListView usersList;

        public ManageUsersDialog() {
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                    .setTitle("Manage Users")
                    .setPositiveButton("Done", (dialog, which) -> dialog.dismiss())
                    .setNegativeButton("Log out all users", (dialog1, which) -> {
                        UserHelper.logOutAllUsers(getContext());
                        startActivity(new Intent(getContext(), UserLoginActivity.class));
                        getActivity().finish();
                    })
                    .setNeutralButton("Add user", (dialog, which) -> {
                        Intent i = new Intent(getActivity(), UserLoginActivity.class);
                        // Don't create a new Home activity after the user is logged in; simply return to this one.
                        i.putExtra(UserLoginActivity.EXTRA_CREATE_NEW_HOME, false);
                        startActivity(i);
                    });

            View v = getActivity().getLayoutInflater().inflate(R.layout.users_dialog, null);
            usersList = (ListView) v.findViewById(R.id.list);
            setupUsersList();
            builder.setView(v);

            return builder.create();
        }

        void setupUsersList() {
            List<UserModel> users = UserHelper.getLoggedInUserModelsAsList(getActivity());
            final ArrayAdapter<UserModel> adapter = new ArrayAdapter<>(getActivity(), R.layout.list_item_user, users);
            usersList.setAdapter(adapter);
            usersList.setOnItemClickListener((parent, view1, position, id) -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle("Confirm logout");
                builder.setMessage("Are you sure you want to log this user out?");
                final UserModel user = (UserModel) parent.getAdapter().getItem(position);
                builder.setPositiveButton("Yes", (dialog1, which) -> {
                    UserHelper.logOutUser(getActivity(), user.userId);
                    adapter.remove(user);
                    adapter.notifyDataSetChanged();
                    if (UserHelper.getLoggedInUsers(getActivity()).size() == 0) {
                        // No more users are logged in
                        // Prompt a new one to log in
                        startActivity(new Intent(getActivity(), UserLoginActivity.class));
                        getActivity().finish();
                    }
                    dialog1.dismiss();
                });
                builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
                builder.show();
            });
        }
    }
}