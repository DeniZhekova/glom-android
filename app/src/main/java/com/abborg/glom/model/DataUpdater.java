package com.abborg.glom.model;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.util.Log;

import com.abborg.glom.utils.DBHelper;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * Class that wraps around model to perform CRUD operations on database and 
 * make necessary network operations
 *
 * Created by Boat on 22/9/58.
 */
public class DataUpdater {

    private User currentUser;
    
    private SQLiteDatabase database;
    
    private DBHelper dbHelper;

    private static final String TAG = "DATA PROVIDER";
    
    private String[] circleColumns = { DBHelper.CIRCLE_COLUMN_ID,
            DBHelper.CIRCLE_COLUMN_NAME };
    
    private String[] userColumns = { DBHelper.USER_COLUMN_ID, DBHelper.USER_COLUMN_NAME,
            DBHelper.USER_COLUMN_AVATAR_ID };

    private String[] userCircleColumns = { DBHelper.USERCIRCLE_COLUMN_USER_ID, DBHelper.USERCIRCLE_COLUMN_CIRCLE_ID,
            DBHelper.USERCIRCLE_COLUMN_LATITUDE, DBHelper.USERCIRCLE_COLUMN_LONGITUDE };

    public DataUpdater(Context context, User currentUser) {
        this.currentUser = currentUser;
        dbHelper = new DBHelper(context);
    }

    public void open() throws SQLException {
        database = dbHelper.getWritableDatabase();
    }

    public void close() {
        dbHelper.close();
    }

    public void resetCircles() {
        try {
            database.beginTransaction();

            database.execSQL("DELETE FROM " + DBHelper.TABLE_CIRCLES);
            database.execSQL("DELETE FROM " + DBHelper.TABLE_USERS);
            database.execSQL("DELETE FROM " + DBHelper.TABLE_USER_CIRCLE);
            database.execSQL("DELETE FROM " + DBHelper.TABLE_EVENTS);

            database.setTransactionSuccessful();
        }
        finally {
            database.endTransaction();
        }
    }

    //TODO update db when user broadcasts location in a circle

    /**
     * Creates a new circle, with the current user, and the specified users in it.
     *
     * @param name The name or title of the circle to create with
     * @param users The list of users to add at the time of creation along with current user
     * @param id The id of this circle. Leave null to randomly generate one
     * @return The created circle
     */
    public Circle createCircle(String name, List<User> users, String id) {
        Circle circle = Circle.createCircle(name, currentUser);
        if (id != null) circle.setId(id);
        circle.addUsers(users);

        database.beginTransaction();

        try {
            // insert new record into CIRCLES table (record is unique per circle)
            ContentValues values = new ContentValues();
            values.put(DBHelper.CIRCLE_COLUMN_ID, circle.getId());
            values.put(DBHelper.CIRCLE_COLUMN_NAME, circle.getTitle());
            long insertId = database.insert(DBHelper.TABLE_CIRCLES, null, values);
            Log.d(TAG, "Inserted circle with _id: " + insertId + ", id: " + circle.getId() + ", name: " +
                    circle.getTitle() + ", userlist: " + circle.getUserListString());

            for (User user : circle.getUsers()) {
                user.setCurrentCircle(circle);

                // insert new users into USER table if unique (record is unique per user)
                values.clear();
                values.put(DBHelper.USER_COLUMN_ID, user.getId());
                values.put(DBHelper.USER_COLUMN_NAME, user.getName());
                values.put(DBHelper.USER_COLUMN_AVATAR_ID, user.getAvatar());
                insertId = database.insert(DBHelper.TABLE_USERS, null, values);
                Log.d(TAG, "Inserted user with _id: " +  insertId + ", id: " + user.getId() + " into " + DBHelper.TABLE_USERS);

                // insert the user-circle association into the USERCIRCLE table (record is unique per association)
                values.clear();
                values.put(DBHelper.USERCIRCLE_COLUMN_USER_ID, user.getId());
                values.put(DBHelper.USERCIRCLE_COLUMN_CIRCLE_ID, circle.getId());
                values.put(DBHelper.USERCIRCLE_COLUMN_LATITUDE, user.getLocation().getLatitude());
                values.put(DBHelper.USERCIRCLE_COLUMN_LONGITUDE, user.getLocation().getLongitude());
                insertId = database.insert(DBHelper.TABLE_USER_CIRCLE, null, values);
                Log.d(TAG, "Inserted user with _id: " + insertId + ", userId: " + user.getId() + ", circleId: " + circle.getId() + " into " + DBHelper.TABLE_USER_CIRCLE);
            }

            database.setTransactionSuccessful();
        }
        catch (SQLException ex) {
            Log.e(TAG, ex.getMessage());
        }
        finally {
            database.endTransaction();
        }

        //TODO send request to GCM and server to create new group

        return circle;
    }

    /**
     * Delete the circle and remove all association of users within this circle
     * TODO
     *
     * @param circle
     */
    public void deleteCircle(Circle circle) {
        String id = circle.getId();
        database.delete(DBHelper.TABLE_CIRCLES, DBHelper.CIRCLE_COLUMN_ID + " = " + id, null);

        //TODO send request to GCM and server to delete group
    }

    /**
     * TODO
     *
     * @param circle
     * @param users
     */
    public Circle addUsersToCircle(Circle circle, List<User> users) { return null; }

    /**
     * TODO
     *
     * @param circle
     * @param users
     * @return
     */
    public Circle removeUsersFromCircle(Circle circle, List<User> users) { return null; }

    /**
     * Retrieves the list of users in the specified circle
     *
     * @param circle
     * @return
     */
    public List<User> getUsersInCircle(Circle circle) {
        List<User> users = new ArrayList<User>();

        // get the user info from USERS table and the user location from USERCIRCLE table
        // SELECT id,name,avatarId,location
        String selectColumns = DBHelper.USER_COLUMN_ID + "," + DBHelper.USER_COLUMN_NAME + "," + DBHelper.USER_COLUMN_AVATAR_ID + "," +
                DBHelper.USERCIRCLE_COLUMN_LATITUDE + "," + DBHelper.USERCIRCLE_COLUMN_LONGITUDE;

        String query = "SELECT " + selectColumns + " FROM " + DBHelper.TABLE_USERS + ", " + DBHelper.TABLE_USER_CIRCLE + " WHERE " +
                DBHelper.TABLE_USERS + "." + DBHelper.USER_COLUMN_ID + "=" + DBHelper.TABLE_USER_CIRCLE + "." +
                DBHelper.USERCIRCLE_COLUMN_USER_ID + " AND " + DBHelper.TABLE_USER_CIRCLE + "." + DBHelper.USERCIRCLE_COLUMN_CIRCLE_ID + "=" +
                "'" + circle.getId() + "'";
        Cursor userCursor = database.rawQuery(query, null);

        userCursor.moveToFirst();
        while (!userCursor.isAfterLast()) {
            User user = serializeUser(userCursor, circle);
            users.add(user);
            userCursor.moveToNext();
        }
        userCursor.close();

        //TODO sync with server to make sure the circle is updated

        return users;
    }

    private User serializeUser(Cursor cursor, Circle circle) {
        User user = new User(null, null, null);
        user.setId(cursor.getString(0));
        user.setName(cursor.getString(1));
        user.setAvatar(cursor.getString(2));

        Location location = new Location("");
        location.setLatitude(cursor.getDouble(3));
        location.setLongitude(cursor.getDouble(4));
        user.setLocation(location);

        //TODO set all user permission to receive everything
        List<Integer> userPerm = new ArrayList<Integer>();
        userPerm.add(User.MEDIA_IMAGE_RECEIVE);
        userPerm.add(User.MEDIA_AUDIO_RECEIVE);
        userPerm.add(User.MEDIA_VIDEO_RECEIVE);
        userPerm.add(User.ALARM_RECEIVE);
        userPerm.add(User.NOTE_RECEIVE);
        userPerm.add(User.LOCATION_REQUEST_RECEIVE);
        userPerm.add(User.SHOUT_RECEIVE);
        userPerm.add(User.SECRET_MESSAGE);
        userPerm.add(User.SONG_SNIPPET_RECEIVE);
        userPerm.add(User.POLL_RECEIVE);
        user.setUserPermission(userPerm);

        user.setCurrentCircle(circle);
        Log.d(TAG, "Query user for circle(" + circle.getId() + ") id: " + user.getId() + ", name: " + user.getName() + ", avatarId: " + cursor.getString(2)
                + ", location: " + user.getLocation().getLatitude() + ", " + user.getLocation().getLongitude());

        return user;
    }

    public List<Circle> getCircles() {
        List<Circle> circles = new ArrayList<Circle>();

        Cursor cursor = database.query(DBHelper.TABLE_CIRCLES,
                circleColumns, null, null, null, null, null);

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            Circle circle = serializeCircle(cursor);
            circles.add(circle);
            cursor.moveToNext();
        }
        cursor.close();
        return circles;
    }

    private Circle serializeCircle(Cursor cursor) {
        Circle circle = Circle.createCircle(null, currentUser);
        circle.setId(cursor.getString(0));
        circle.setTitle(cursor.getString(1));

        List<User> users = getUsersInCircle(circle);
        circle.setUsers(users);
        return circle;
    }

    public void updateUserLocation(User user, Circle circle) {
        ContentValues values = new ContentValues();
        values.put(DBHelper.USERCIRCLE_COLUMN_LATITUDE, user.getLocation().getLatitude());
        values.put(DBHelper.USERCIRCLE_COLUMN_LONGITUDE, user.getLocation().getLongitude());
        int rowAffected = database.update(DBHelper.TABLE_USER_CIRCLE, values,
                DBHelper.USERCIRCLE_COLUMN_USER_ID + "='" + user.getId() + "' AND " +
                        DBHelper.USERCIRCLE_COLUMN_CIRCLE_ID + "='" + circle.getId() + "'", null);
        Log.d(TAG, "Updated " + rowAffected + " row(s) in " + DBHelper.TABLE_USER_CIRCLE);
    }

    /**
     * Create a new event under an optionally specified circle
     */
    public Event createEvent(String name, Circle circle, List<User> hosts, DateTime time, String place, Location location,
                             int discoverType, List<User> invitees, boolean showHosts,
                             boolean showInvitees, boolean showAttendees, String note) {
        Event event = Event.createEvent(name, circle, hosts, time, place, location, discoverType, invitees, showHosts,  showInvitees,
                showAttendees, note);

        database.beginTransaction();

        try {
            ContentValues values = new ContentValues();
            values.put(DBHelper.EVENT_COLUMN_ID, event.getId());
            if (circle != null) values.put(DBHelper.EVENT_COLUMN_CIRCLE_ID, circle.getId());
            values.put(DBHelper.EVENT_COLUMN_NAME, event.getName());
            values.put(DBHelper.EVENT_COLUMN_DATETIME, event.getDateTime().getMillis());
            values.put(DBHelper.EVENT_COLUMN_PLACE, event.getPlace());
            if (event.getLocation() != null) {
                values.put(DBHelper.EVENT_COLUMN_LATITUDE, event.getLocation().getLatitude());
                values.put(DBHelper.EVENT_COLUMN_LONGITUDE, event.getLocation().getLongitude());
            }
            values.put(DBHelper.EVENT_COLUMN_NOTE, event.getNote());
            long insertId = database.insert(DBHelper.TABLE_EVENTS, null, values);
            Log.d(TAG, "Inserted event with _id: " + insertId + ", id: " + event.getId() + ", name: " +
                    event.getName() + ", time: " + event.getDateTime() + ", place: " + event.getPlace());

            database.setTransactionSuccessful();
        }
        catch (SQLException ex) {
            Log.e(TAG, ex.getMessage());
        }
        finally {
            database.endTransaction();
        }

        //TODO send update to server of the created event

        return event;
    }

    /**
     * Retrieves list of events within this circle that are cached
     *
     * @param circle
     * @return
     */
    public List<Event> getCircleEvents(Circle circle) {
        //TODO send request to server

        List<Event> events = new ArrayList<>();

        DateTimeFormatter formatter = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm:ss");
        DateTime postTime = formatter.parseDateTime("14/10/2015 17:00:00");

        String query = "SELECT * FROM " + DBHelper.TABLE_EVENTS + " WHERE " +
                DBHelper.TABLE_EVENTS + "." + DBHelper.EVENT_COLUMN_CIRCLE_ID + "='" + circle.getId() + "'";
        Cursor cursor = database.rawQuery(query, null);

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            Event event = serializeEvent(cursor, circle);

            //TODO retrieve last action and action timestamp from server
            //TODO for now hardcode this
            event.setLastAction(new FeedAction(FeedAction.CREATE_EVENT, currentUser, postTime));

            events.add(event);
            cursor.moveToNext();
        }
        cursor.close();

        return events;
    }

    private Event serializeEvent(Cursor cursor, Circle circle) {
        String id = cursor.getString(0);
        String name = cursor.getString(2);
        DateTime time = new DateTime(cursor.getLong(3));
        String place = cursor.getString(4);
        Location location = new Location("");
        location.setLatitude(cursor.getDouble(5));
        location.setLongitude(cursor.getDouble(6));
        String note = cursor.getString(7);

        return Event.createEvent(id, circle, name, time, place, location, note);
    }
}
