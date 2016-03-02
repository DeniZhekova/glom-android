package com.abborg.glom.activities;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.SQLException;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.abborg.glom.AppState;
import com.abborg.glom.Const;
import com.abborg.glom.R;
import com.abborg.glom.data.DataUpdater;
import com.abborg.glom.fragments.CircleFragment;
import com.abborg.glom.fragments.DiscoverFragment;
import com.abborg.glom.fragments.DrawerFragment;
import com.abborg.glom.fragments.EventFragment;
import com.abborg.glom.fragments.LocationFragment;
import com.abborg.glom.interfaces.BroadcastLocationListener;
import com.abborg.glom.interfaces.EventChangeListener;
import com.abborg.glom.model.Circle;
import com.abborg.glom.model.CircleInfo;
import com.abborg.glom.model.User;
import com.abborg.glom.service.RegistrationIntentService;
import com.abborg.glom.utils.CircleTransform;
import com.bumptech.glide.Glide;
import com.flipboard.bottomsheet.BottomSheetLayout;
import com.oguzdev.circularfloatingactionmenu.library.FloatingActionMenu;
import com.oguzdev.circularfloatingactionmenu.library.SubActionButton;

import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity
        implements DrawerFragment.FragmentDrawerListener, Handler.Callback,
        AdapterView.OnItemClickListener {

    protected static final String TAG = "MainActivity";

    /**
     * The global class at the application context level
     */
    private AppState appState;

    /**
     * Abstracts data management via SQLite and network operations
     */
    public DataUpdater dataUpdater;

    /**
     * A receiver for incoming location updates and other various GCM push updates
     */
    private BroadcastReceiver broadcastReceiver;

    /**
     * Adapter for the tab view
     */
    private ViewPagerAdapter adapter;

    /**
     * Handler that indicates when asynchronous events are completed to update UI
     */
    private Handler handler;

    /**
     * Event change listeners
     */
    private List<EventChangeListener> eventChangeListeners;

    // UI elements
    private TabLayout tabLayout;
    private int[] tabIcons = {
            R.drawable.ic_tab_circle,
            R.drawable.ic_tab_location,
            R.drawable.ic_tab_event,
            R.drawable.ic_tab_discover
    };
    private BottomSheetLayout bottomSheet;
    private View broadcastLocationSheetLayout;
    private SwitchCompat broadcastLocationToggle;
    private CoordinatorLayout mainCoordinatorLayout;
    private FloatingActionMenu avatarActionMenu;
    private Animation fadeInAnim;
    private Animation fadeOutAnim;
    private RelativeLayout overlayLayout;
    private ImageView menuOverlay;
    private ImageView avatarIcon;
    private SubActionButton.Builder lCSubBuilder;
    private FrameLayout.LayoutParams blueContentParams;
    private boolean isRadialMenuOptionsOpening;

    /**
     * View pager adapter that controls the pages
     */
    class ViewPagerAdapter extends FragmentPagerAdapter {
        private final List<Fragment> mFragmentList = new ArrayList<>();
        private final List<String> mFragmentTitleList = new ArrayList<>();

        public ViewPagerAdapter(FragmentManager manager) {
            super(manager);
        }

        @Override
        public Fragment getItem(int position) {
            return mFragmentList.get(position);
        }

        @Override
        public int getCount() {
            return mFragmentList.size();
        }

        public void addFragment(Fragment fragment, String title) {
            mFragmentList.add(fragment);
            mFragmentTitleList.add(title);
        }

        @Override
        public CharSequence getPageTitle(int position) {
//            return mFragmentTitleList.get(position);
            return null;
        }
    }

    /**********************************************************
     * View Initializations
     **********************************************************/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // set up handler for receiving all messages
        handler = new Handler(this);

        setupData();

        setupView();

        setupBroadcastReceiver();

        addEventChangeListener((LocationFragment) adapter.getItem(1));
        addEventChangeListener((EventFragment) adapter.getItem(2));

        setupService();
    }

    @Override
    protected void onStart() {
        super.onStart();

        // connect to the Google Play API
        appState.connectGoogleApiClient();
        appState.setKeepGoogleApiClientAlive(false);

        // register local broadcast receiver
        LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(this);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(getResources().getString(R.string.ACTION_RECEIVE_LOCATION));
        intentFilter.addAction(getResources().getString(R.string.ACTION_USER_LOCATION_UPDATE));
        broadcastManager.registerReceiver(broadcastReceiver, intentFilter);

        // get database writable object
        try {
            dataUpdater.open();
        }
        catch (SQLException ex) {
            Log.e(TAG, ex.getMessage());
        }
    }

    @Override
    protected void onStop() {
        // unregister the local broadcast receiver
        LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(this);
        broadcastManager.unregisterReceiver(broadcastReceiver);

        // disconnect google api client
        if (!appState.shouldKeepGoogleApiAlive()) appState.disconnectGoogleApiClient();

        // close database
        dataUpdater.close();

        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    private void setupData() {
        appState = AppState.getInstance(this);
        dataUpdater = appState.getDataUpdater();
        dataUpdater.setHandler(handler);
    }

    private void setupView() {
        setContentView(R.layout.activity_main);
        mainCoordinatorLayout = (CoordinatorLayout) findViewById(R.id.parentView);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(appState.getActiveCircle().getTitle()
                    + " (" + appState.getActiveCircle().getUsers().size() + ")");
        }

        final ViewPager viewPager = (ViewPager) findViewById(R.id.viewpager);
        setupViewPager(viewPager);
        tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(viewPager);
        setupTabIcons();

        // set up the navigation drawer
        DrawerFragment drawerFragment = (DrawerFragment)
                getSupportFragmentManager().findFragmentById(R.id.fragment_navigation_drawer);
        drawerFragment.setUp(R.id.fragment_navigation_drawer, (DrawerLayout) findViewById(R.id.drawer_layout), toolbar);
        drawerFragment.setDrawerListener(this);

        // set up the bottom sheets
        bottomSheet = (BottomSheetLayout) findViewById(R.id.bottomsheet);
        bottomSheet.setShouldDimContentView(false);
        broadcastLocationSheetLayout = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_broadcast_location, bottomSheet, false);
        broadcastLocationToggle = (SwitchCompat) broadcastLocationSheetLayout.findViewById(R.id.toggleBroadcastLocationSwitch);
        broadcastLocationToggle.setChecked(appState.getActiveCircle().isUserBroadcastingLocation());

        // set up broadcast location sheet
        final ImageButton endTimePickerHourIncr = (ImageButton) broadcastLocationSheetLayout.findViewById(R.id.endTimePickerHourIncr);
        final TextView endTimePickerHour = (TextView) broadcastLocationSheetLayout.findViewById(R.id.endTimePickerHour);
        endTimePickerHourIncr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int hour = Integer.parseInt(endTimePickerHour.getText().toString());
                int incrHour = hour+1 > 12 ? 1 : hour+1;
                endTimePickerHour.setText(incrHour + "");
            }
        });
        final ImageButton endTimePickerMinuteIncr = (ImageButton) broadcastLocationSheetLayout.findViewById(R.id.endTimePickerMinuteIncr);
        final TextView endTimePickerMinute = (TextView) broadcastLocationSheetLayout.findViewById(R.id.endTimePickerMinute);
        endTimePickerMinuteIncr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int minute = Integer.parseInt(endTimePickerMinute.getText().toString());
                int incrMinute = minute+1 > 59 ? 0 : minute+1;
                endTimePickerMinute.setText(String.format("%02d", incrMinute));
            }
        });
        final ImageButton endTimePickerHourDecr = (ImageButton) broadcastLocationSheetLayout.findViewById(R.id.endTimePickerHourDecr);
        endTimePickerHourDecr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int hour = Integer.parseInt(endTimePickerHour.getText().toString());
                int decrHour = hour-1 < 1 ? 12 : hour-1;
                endTimePickerHour.setText(decrHour + "");
            }
        });
        final ImageButton endTimePickerMinuteDecr = (ImageButton) broadcastLocationSheetLayout.findViewById(R.id.endTimePickerMinuteDecr);
        endTimePickerMinuteDecr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int minute = Integer.parseInt(endTimePickerMinute.getText().toString());
                int decrMinute = minute-1 < 0 ? 59 : minute-1;
                endTimePickerMinute.setText(String.format("%02d", decrMinute));
            }
        });
        final TextView endTimeAMPMPicker = (TextView) broadcastLocationSheetLayout.findViewById(R.id.endTimePickerAMPM);
        endTimeAMPMPicker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String amPm = endTimeAMPMPicker.getText().toString();
                if (amPm.equals(getResources().getString(R.string.time_unit_before_noon))) {
                    endTimeAMPMPicker.setText(getResources().getString(R.string.time_unit_after_noon));
                }
                else {
                    endTimeAMPMPicker.setText(getResources().getString(R.string.time_unit_before_noon));
                }
            }
        });

        broadcastLocationToggle.setOnClickListener(new CompoundButton.OnClickListener() {

            @Override
            public void onClick(View buttonView) {
                CircleFragment circleFragment = (CircleFragment) adapter.getItem(0);
                LocationFragment locationFragment = (LocationFragment) adapter.getItem(1);
                if (circleFragment != null) {
                    DateTime now = new DateTime();

                    // if end hour - start hour is negative, add 24 to get the duration from current hour
                    // convert to 24-hour time
                    String amPm = endTimeAMPMPicker.getText().toString();
                    int endHour = Integer.parseInt(endTimePickerHour.getText().toString());
                    if (amPm.equals(getResources().getString(R.string.time_unit_after_noon)) && endHour != 12) {
                        endHour += 12;
                    }
                    else if (amPm.equals(getResources().getString(R.string.time_unit_before_noon)) && endHour == 12) {
                        endHour = 0;
                    }

                    int hourDiff = endHour - now.getHourOfDay();
                    if (hourDiff < 0) {
                        hourDiff += 24;
                    }
                    DateTime endTime = now.plusHours(hourDiff);

                    Duration durationFromNow = new Duration(now, endTime);
                    Long duration = durationFromNow.getMillis();

                    // update UI
                    circleFragment.toggleBroadcastingLocation(duration);
                    setBroadcastLocationListener(appState.getActiveCircle().isUserBroadcastingLocation(), locationFragment);
                }
            }
        });

        // set up the floating action button for all fragments
        android.support.design.widget.FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                // click event for circle fragment
                if (adapter.getItem(viewPager.getCurrentItem()) instanceof CircleFragment) {
                    showRadialMenuOptions(appState.getActiveUser());
                }
                else if (adapter.getItem(viewPager.getCurrentItem()) instanceof LocationFragment) {

                }
                else if (adapter.getItem(viewPager.getCurrentItem()) instanceof EventFragment) {
                    showRadialMenuOptions(appState.getActiveUser());
                }
            }
        });

        // initialize the second relative layout for overlay and avatar menu
        overlayLayout = (RelativeLayout) findViewById(R.id.overlayLayout);

        // initialize the overlay imageview
        menuOverlay = new ImageView(this);
        RelativeLayout.LayoutParams menuOverlayParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT);
        menuOverlay.setLayoutParams(menuOverlayParams);
        menuOverlay.setBackgroundColor(getResources().getColor(R.color.menuOverlay));

        // initialize the overlay avatar icon with radial menu
        avatarIcon = new ImageView(this);
        RelativeLayout.LayoutParams avatarIconParams = new RelativeLayout.LayoutParams(
                getResources().getDimensionPixelSize(R.dimen.avatar_menu_size),
                getResources().getDimensionPixelSize(R.dimen.avatar_menu_size));
        avatarIconParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        avatarIcon.setLayoutParams(avatarIconParams);

        // add fade-in / fade-out animation when visibilty changes
        fadeInAnim = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
        fadeOutAnim = AnimationUtils.loadAnimation(this, android.R.anim.fade_out);
        fadeInAnim.setDuration(10);
        fadeOutAnim.setDuration(10);

        menuOverlay.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (!isRadialMenuOptionsOpening) hideMenuOverlay(true);
            }
        });

        // add the overlay and avatar icon to the layout
        overlayLayout.addView(menuOverlay, 0);
        overlayLayout.addView(avatarIcon, 1);

        // hide the layout for now until an avatar is clicked
        overlayLayout.setVisibility(RelativeLayout.GONE);

        int blueSubActionButtonSize = getResources().getDimensionPixelSize(R.dimen.blue_sub_action_button_size);
        int blueSubActionButtonContentMargin = getResources().getDimensionPixelSize(R.dimen.blue_sub_action_button_content_margin);

        lCSubBuilder = new SubActionButton.Builder(this);
        lCSubBuilder.setBackgroundDrawable(getResources().getDrawable(R.drawable.button_action_blue_selector));

        blueContentParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        blueContentParams.setMargins(blueSubActionButtonContentMargin,
                blueSubActionButtonContentMargin,
                blueSubActionButtonContentMargin,
                blueSubActionButtonContentMargin);
        lCSubBuilder.setLayoutParams(blueContentParams);

        // Set custom layout params
        FrameLayout.LayoutParams blueParams = new FrameLayout.LayoutParams(blueSubActionButtonSize, blueSubActionButtonSize);
        lCSubBuilder.setLayoutParams(blueParams);
    }

    private void setupBroadcastReceiver() {

        // register the local broadcast receiver for our gcm listener service updates
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            //TODO keep track of received message of user and circleId to show appropriate
            //TODO notifications
            //TODO retrive notification counts from USER table
            public void onReceive(Context context, Intent intent) {

                // location updates from MessageListenerService
                // updates from OTHER users
                if ( intent.getAction().equals(getResources().getString(R.string.ACTION_RECEIVE_LOCATION)) ) {
                    List<User> users = dataUpdater.getLocationUpdates(intent, appState.getActiveCircle());

                    if (users != null && users.size() > 0) {

                        // only update the location markers when the map is visible and this is the current circle
                        LocationFragment map = (LocationFragment) adapter.getItem(1);
                        if (map.isFragmentVisible) {
                            map.updateUserMarkers(users);
                        }

                        Toast.makeText(context, users.get(0).getId() + ": " + users.get(0).getLocation().getLatitude()
                                + ", " + users.get(0).getLocation().getLongitude(), Toast.LENGTH_SHORT).show();
                    }
                    else {
                        Toast.makeText(context, "Received location update from server", Toast.LENGTH_SHORT).show();
                    }
                }

                // location updates from CirclePushService
                // user's OWN location updates
                else if (intent.getAction().equals(getResources().getString(R.string.ACTION_USER_LOCATION_UPDATE))) {
                    Location location = intent.getParcelableExtra(getResources().getString(R.string.EXTRA_USER_LOCATION_UPDATE));
                    List<String> circleBroadcastList = intent.getStringArrayListExtra(getResources().getString(R.string.EXTRA_CIRCLES_LOCATION_UPDATE));

                    // only update the location markers when the map is visible
                    // and this circle is in the broadcast list
                    String circleId = appState.getActiveCircle().getId();
                    LocationFragment map = (LocationFragment) adapter.getItem(1);

                    // update the user's location in this circle
                    User currentUser = null;
                    for (User user : appState.getActiveCircle().getUsers()) {
                        if (user.getId().equals(appState.getActiveUser().getId())) {
                            currentUser = user;
                            currentUser.setLocation(location);
                        }
                    }

                    if (map.isFragmentVisible && circleBroadcastList.contains(circleId)) {
                        if (currentUser != null) {
                            map.updateUserMarkers(Arrays.asList(currentUser));
                        }
                    }
                }
            }
        };
    }

    private void setupService() {
        // start IntentService to register this application with GCM
        Intent intent = new Intent(this, RegistrationIntentService.class);
        intent.putExtra(getResources().getString(R.string.EXTRA_SEND_TOKEN_USER_ID), appState.getActiveUser().getId());
        startService(intent);
    }

    private void setBroadcastLocationListener(boolean enabled, BroadcastLocationListener listener) {
        if (listener != null) {
            if (enabled)
                listener.onBroadcastLocationEnabled();
            else
                listener.onBroadcastLocationDisabled();
        }
    }

    private void addEventChangeListener(EventChangeListener listener) {
        if (listener != null) {
            if (eventChangeListeners == null) {
                eventChangeListeners = new ArrayList<>();
                eventChangeListeners.add(listener);
            }
            else eventChangeListeners.add(listener);
        }
    }

    private void setupTabIcons() {
        tabLayout.getTabAt(0).setIcon(tabIcons[0]);
        tabLayout.getTabAt(1).setIcon(tabIcons[1]);
        tabLayout.getTabAt(2).setIcon(tabIcons[2]);
        tabLayout.getTabAt(3).setIcon(tabIcons[3]);
    }

    private void setupViewPager(ViewPager viewPager) {
        adapter = new ViewPagerAdapter(getSupportFragmentManager());
        adapter.addFragment(new CircleFragment(), "Circle");
        adapter.addFragment(new LocationFragment(), "Map");
        adapter.addFragment(new EventFragment(), "Event");
        adapter.addFragment(new DiscoverFragment(), "Discover");
        viewPager.setAdapter(adapter);
    }

    public void showRadialMenuOptions(User user) {
        showMenuOverlay(true);

        // load the avatar picture
        Glide.with(this)
                .load(user.getAvatar()).fitCenter()
                .transform(new CircleTransform(this))
                .override((int) getResources().getDimension(R.dimen.user_avatar_width),
                        (int) getResources().getDimension(R.dimen.user_avatar_height))
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .crossFade(1000)
                .into(avatarIcon);

        // load the menu based on user permission list
        avatarActionMenu = setupAvatarOptionMenu(this, lCSubBuilder, blueContentParams, user, avatarIcon);
        handler.postDelayed(new Runnable() {

            @Override
            public void run() {
                if (!avatarActionMenu.isOpen())
                    avatarActionMenu.open(true);
            }
        }, 50);
    }

    private SubActionButton setIconFromPermission(final Activity activity, SubActionButton.Builder builder,
                                                  FrameLayout.LayoutParams params, final User user, int userPerm) {
        ImageView icon = new ImageView(activity);
        SubActionButton actionButton = null;

        switch(userPerm) {
            case User.MEDIA_IMAGE_RECEIVE:
                icon.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_picture));
                actionButton = builder.setContentView(icon, params).build();
                actionButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Toast.makeText(activity, "Sending image is not supported yet", Toast.LENGTH_SHORT).show();
                    }
                });
                break;
            case User.MEDIA_AUDIO_RECEIVE:
                icon.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_audio));
                actionButton = builder.setContentView(icon, params).build();
                actionButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Toast.makeText(activity, "Sending audio is not supported yet", Toast.LENGTH_SHORT).show();
                    }
                });
                break;
            case User.MEDIA_VIDEO_RECEIVE: icon.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_video));
                actionButton = builder.setContentView(icon, params).build();
                actionButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Toast.makeText(activity, "Sending video is not supported yet", Toast.LENGTH_SHORT).show();
                    }
                });
                break;
            case User.ALARM_RECEIVE: icon.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_alarm));
                actionButton = builder.setContentView(icon, params).build();
                actionButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Toast.makeText(activity, "Sending alarm is not supported yet", Toast.LENGTH_SHORT).show();
                    }
                });
                break;
            case User.NOTE_RECEIVE: icon.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_note));
                actionButton = builder.setContentView(icon, params).build();
                actionButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Toast.makeText(activity, "Sending note is not supported yet", Toast.LENGTH_SHORT).show();
                    }
                });
                break;
            case User.LOCATION_REQUEST_RECEIVE: icon.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_place));
                actionButton = builder.setContentView(icon, params).build();
                actionButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (user.getId().equals(appState.getActiveUser().getId())) {
                            //TODO broadcast location dialog setting interval and duration of updates
                            hideMenuOverlay(false);
                            showBroadcastLocationMenuOptions();
                        }
                        else {
                            hideMenuOverlay(false);
                            Toast.makeText(activity, "Location request sent to " + user.getName(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                break;
            case User.CREATE_EVENT: icon.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_planner));
                actionButton = builder.setContentView(icon, params).build();
                final Context context = this;
                actionButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        hideMenuOverlay(false);
                        Intent intent = new Intent(context, EventActivity.class);
                        intent.setAction(getResources().getString(R.string.ACTION_CREATE_EVENT));

                        // make sure to not disconnect Google Api just yet
                        appState.setKeepGoogleApiClientAlive(true);
                        startActivityForResult(intent, Const.CREATE_EVENT_RESULT_CODE);
                    }
                });
                break;
            default: return null;
        }

        return actionButton;
    }

    private void showBroadcastLocationMenuOptions() {
        bottomSheet.showWithSheetView(broadcastLocationSheetLayout);
//            float peekTranslation = bottomSheetLayout.findViewById(R.id.toggleBroadcastLocationSwitch).getHeight();
//            bottomSheet.setPeekSheetTranslation(peekTranslation);
//            bottomSheet.peekSheet();
    }

    private FloatingActionMenu setupAvatarOptionMenu(final Activity activity, SubActionButton.Builder builder,
                                                     FrameLayout.LayoutParams params, User user, ImageView avatarIcon) {
        FloatingActionMenu.Builder menuBuilder =  new FloatingActionMenu.Builder(activity);
        List<Integer> userMenuOptions = user.getUserPermission();
        for (int option : userMenuOptions) {
            SubActionButton actionButton = setIconFromPermission(activity, builder, params, user, option);
            menuBuilder.addSubActionView(actionButton);
        }

        menuBuilder.setStateChangeListener(new FloatingActionMenu.MenuStateChangeListener() {
            @Override
            public void onMenuOpened(FloatingActionMenu floatingActionMenu) {
                isRadialMenuOptionsOpening = true;
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        isRadialMenuOptionsOpening = false;
                    }
                }, 700);
            }

            @Override
            public void onMenuClosed(FloatingActionMenu floatingActionMenu) {
                isRadialMenuOptionsOpening = false;
            }
        });

        return menuBuilder.setRadius(getResources().getDimensionPixelSize(R.dimen.avatar_menu_radius_large))
                .setStartAngle(0)
                .setEndAngle(360)
                .attachTo(avatarIcon)
                .build();
    }

    private void showMenuOverlay(boolean animated) {
        fadeInAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                Log.d(TAG, "Opening avatar menu");
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {

            }
        });
        overlayLayout.setVisibility(RelativeLayout.VISIBLE);
        menuOverlay.startAnimation(fadeInAnim);
    }

    private void hideMenuOverlay(boolean animated) {
        fadeOutAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                Log.d(TAG, "Clicked outside of avatar icon");
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                overlayLayout.setVisibility(RelativeLayout.GONE);
            }
        });
        if (animated) menuOverlay.startAnimation(fadeOutAnim);
        else overlayLayout.setVisibility(RelativeLayout.GONE);
        if (avatarActionMenu != null) avatarActionMenu.close(animated);
    }

    private void updateView(Circle circle) {
        AppState.getInstance(this).setActiveCircle(circle);

        if (getSupportActionBar() != null) getSupportActionBar().setTitle(circle.getTitle() + " (" + circle.getUsers().size() + ")");

        // update broadcast location sheet
        broadcastLocationToggle.setChecked(appState.getActiveCircle().isUserBroadcastingLocation());

        // refresh all fragments
        ((CircleFragment) adapter.getItem(0)).update();
        ((LocationFragment) adapter.getItem(1)).update(true);
        ((EventFragment) adapter.getItem(2)).update();
    }

    /**********************************************************
     * Handler
     **********************************************************/
    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case Const.MSG_GET_USERS:
                Circle circle = appState.getActiveCircle();
                if (getSupportActionBar() != null)
                    getSupportActionBar().setTitle(circle.getTitle() + " (" + circle.getUsers().size() + ")");
                CircleFragment circleFragment = (CircleFragment) adapter.getItem(0);
                circleFragment.update();

                LocationFragment mapFragment = (LocationFragment) adapter.getItem(1);
                mapFragment.updateUserMarkers(circle.getUsers());
                break;
            case Const.MSG_EVENT_DELETED:
                String id = (String) msg.obj;
                for (EventChangeListener listener : eventChangeListeners) {
                    listener.onEventDeleted(id);
                }

                dataUpdater.deleteEvent(id, appState.getActiveCircle());

                Snackbar.make(mainCoordinatorLayout, getResources().getQuantityString(R.plurals.notification_delete_item,
                        1, 1), Snackbar.LENGTH_LONG)
                        .setAction(getResources().getString(R.string.menu_item_undo), new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                //TODO
                            }
                        }).show();
                break;
        }

        return false;
    }

    public Handler getHandler() { return handler; }

    /**********************************************************
     * Menu Handler
     **********************************************************/
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_search) {
            return true;
        }
        else if (id == R.id.action_chat) {
            return true;
        }
        else if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**********************************************************
     * Drawer Handler
     **********************************************************/
    @Override
    public void onDrawerItemSelected(View view, int position) {
        List<CircleInfo> circles = appState.getAllCircleInfo();
        CircleInfo c = circles.get(position);
        Circle selected = dataUpdater.getCircleById(c.id);

        if (selected != null) {
            //TODO save date to SQLITE for this circle

            // update the view
            updateView(selected);
            Toast.makeText(this, "Select circle ID: " + selected.getId() + ", title: " + selected.getTitle() +
                    ", users: " + selected.getUsers().size(), Toast.LENGTH_SHORT).show();
        }
    }

    /**********************************************************
     * Activity Finish Handler
     **********************************************************/
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case Const.CREATE_EVENT_RESULT_CODE:
                if (resultCode == RESULT_OK) {
                    String id = data.getStringExtra(getResources().getString(R.string.EXTRA_EVENT_ID));
                    for (EventChangeListener listener : eventChangeListeners) {
                        listener.onEventAdded(id);
                    }
                }
                break;

            case Const.UPDATE_EVENT_RESULT_CODE:
                if (resultCode == RESULT_OK) {
                    String id = data.getStringExtra(getResources().getString(R.string.EXTRA_EVENT_ID));
                    for (EventChangeListener listener : eventChangeListeners) {
                        listener.onEventModified(id);
                    }
                }
                break;
            default:
                break;
        }
    }

    /**********************************************************
     * User Grid Click Handler
     **********************************************************/
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        User user = appState.getActiveCircle().getUsers().get(position);
        showRadialMenuOptions(user);
    }
}
