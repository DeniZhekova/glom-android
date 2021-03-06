package com.abborg.glom.service;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import com.abborg.glom.ApplicationState;
import com.abborg.glom.Const;
import com.abborg.glom.R;
import com.abborg.glom.activities.MainActivity;
import com.abborg.glom.data.DataProvider;
import com.abborg.glom.di.ComponentInjector;
import com.google.android.gms.gcm.GcmListenerService;

import javax.inject.Inject;

/**
 * Created by Boat on 13/9/58.
 *
 * http://stackoverflow.com/questions/32137660/android-gcm-duplicate-push-after-notification-dismiss
 * If a notification is not consumed, and the app is closed (in the recent app), the notification will arrive again
 */
public class MessageService extends GcmListenerService {

    @Inject
    ApplicationState appState;

    @Inject
    DataProvider dataProvider;

    private static final String TAG = "MessageService";

    private static final int LOCATION_UPDATE = 0;
    private static final int NEW_MESSAGE = 1;
    private static final int EDIT_MESSAGE = 2;
    private static final int DELETE_MESSAGE = 3;
    private static final int SERVER_ACK_MESSAGE = 4;

    @Override
    public void onCreate() {
        ComponentInjector.INSTANCE.getApplicationComponent().inject(this);
    }

    @Override
    public void onMessageSent(String messageId) {
        Log.d(TAG, "Message is sent with id " + messageId);
    }

    @Override
    public void onDeletedMessages() {
        Log.d(TAG, "Message is deleted");
    }

    @Override
    public void onSendError(String msgId, String error) {
        Log.d(TAG, "Message " + msgId + " failed to send with error " + error);
    }

    /**
     * Called when message is received.
     *
     * @param from SenderID of the sender.
     * @param data Data bundle containing message data as key/value pairs.
     *             For Set of keys use data.keySet().
     */
    // [START receive_message]
    @Override
    public void onMessageReceived(String from, Bundle data) {
        String message = data.getString(Const.JSON_SERVER_MESSAGE);
        Log.d(TAG, "Data received: " + data);
        Log.d(TAG, "Message: " + message);

        /**
         * Production applications would usually process the message here.
         * Eg: - Syncing with server.
         *     - Store message in local database.
         *     - Update UI.
         */
        String opCodeString = data.getString(Const.JSON_SERVER_OP);
        if (opCodeString != null) {
            try {
                int opCode = Integer.parseInt(opCodeString);
                Log.d(TAG, "Message of type " + opCode + " received");

                //TODO keep track of received message of user and circleId
                //TODO store it in USERS table under column notification
                switch(opCode) {

                    // MESSAGE TYPE 0: Location updates in circle
                    case LOCATION_UPDATE:
                        Intent locUpdateIntent = new Intent(getResources().getString(R.string.ACTION_RECEIVE_LOCATION));

                        // save updated location in DB
                        dataProvider.openDB();
                        dataProvider.onLocationUpdateReceived(data);

                        // broadcast update
                        locUpdateIntent.putExtra(getResources().getString(R.string.EXTRA_RECEIVE_LOCATION_USERS),
                                data.getString(Const.JSON_SERVER_USERIDS));
                        locUpdateIntent.putExtra(getResources().getString(R.string.EXTRA_RECEIVE_LOCATION_CIRCLE_ID),
                                data.getString(Const.JSON_SERVER_CIRCLEID));
                        LocalBroadcastManager.getInstance(this).sendBroadcast(locUpdateIntent);

                        sendNotification(message);
                        break;

                    // MESSAGE TYPE 1: incoming IM message
                    case NEW_MESSAGE: {
                        String senderId =  data.getString(Const.JSON_SERVER_SENDER);
                        String messageId = data.getString(Const.JSON_SERVER_MESSAGE_ID);
                        String content = data.getString(Const.JSON_SERVER_MESSAGE);
                        String circleId = data.getString(Const.JSON_SERVER_CIRCLEID);
                        String type = data.getString(Const.JSON_SERVER_MESSAGE_TYPE);
                        boolean messageIsValid = true;
                        if (TextUtils.isEmpty(senderId)) {
                            Log.e(TAG, "Invalid message received, missing sender id");
                            messageIsValid = false;
                        }
                        if (TextUtils.isEmpty(circleId)) {
                            Log.e(TAG, "Invalid message received, missing circle id");
                            messageIsValid = false;
                        }
                        if (TextUtils.isEmpty(type)) {
                            Log.e(TAG, "Invalid message received, missing message type");
                            messageIsValid = false;
                        }
                        if (messageIsValid) {
                            // update message in DB
                            // TODO

                            // broadcast update
                            Intent newMessageIntent = new Intent(getResources().getString(R.string.ACTION_NEW_MESSAGE));
                            newMessageIntent.putExtra(getResources().getString(R.string.EXTRA_MESSAGE_SENDER), senderId);
                            newMessageIntent.putExtra(getResources().getString(R.string.EXTRA_MESSAGE_ID), messageId);
                            newMessageIntent.putExtra(getResources().getString(R.string.EXTRA_MESSAGE_CONTENT), content);
                            newMessageIntent.putExtra(getResources().getString(R.string.EXTRA_MESSAGE_TYPE), type);
                            newMessageIntent.putExtra(getResources().getString(R.string.EXTRA_MESSAGE_CIRCLE_ID), circleId);
                            LocalBroadcastManager.getInstance(this).sendBroadcast(newMessageIntent);

//                            sendNotification(message);
                        }
                        break;
                    }

                    // MESSAGE TYPE 2: EDIT MESSAGE
                    case EDIT_MESSAGE: {
                        String senderId =  data.getString(Const.JSON_SERVER_SENDER);
                        String messageId = data.getString(Const.JSON_SERVER_MESSAGE_ID);
                        String circleId = data.getString(Const.JSON_SERVER_CIRCLEID);
                        boolean messageIsValid = true;
                        if (TextUtils.isEmpty(senderId)) {
                            Log.e(TAG, "Invalid message received, missing sender id");
                            messageIsValid = false;
                        }
                        if (TextUtils.isEmpty(circleId)) {
                            Log.e(TAG, "Invalid message received, missing circle id");
                            messageIsValid = false;
                        }
                        if (messageIsValid) {
                            //TODO edit in DB

                            // broadcast update
                        }
                        break;
                    }

                    // MESSAGE TYPE 3: DELETE MESSAGE
                    case DELETE_MESSAGE: {
                        String senderId =  data.getString(Const.JSON_SERVER_SENDER);
                        String messageId = data.getString(Const.JSON_SERVER_MESSAGE_ID);
                        String circleId = data.getString(Const.JSON_SERVER_CIRCLEID);
                        boolean messageIsValid = true;
                        if (TextUtils.isEmpty(senderId)) {
                            Log.e(TAG, "Invalid message received, missing sender id");
                            messageIsValid = false;
                        }
                        if (TextUtils.isEmpty(circleId)) {
                            Log.e(TAG, "Invalid message received, missing circle id");
                            messageIsValid = false;
                        }
                        if (messageIsValid) {
                            //TODO edit in DB

                            // broadcast update
                        }
                        break;
                    }

                    // MESSAGE TYPE 4: SERVER ACK
                    case SERVER_ACK_MESSAGE: {
                        String senderId =  data.getString(Const.JSON_SERVER_SENDER);
                        String messageId = data.getString(Const.JSON_SERVER_MESSAGE_ID);
                        String circleId = data.getString(Const.JSON_SERVER_CIRCLEID);
                        boolean messageIsValid = true;
                        if (TextUtils.isEmpty(senderId) || !TextUtils.equals(senderId, getResources().getString(R.string.gcm_senderId))) {
                            Log.e(TAG, "Invalid sender id received");
                            messageIsValid = false;
                        }
                        if (TextUtils.isEmpty(circleId)) {
                            Log.e(TAG, "Invalid message received, missing circle id");
                            messageIsValid = false;
                        }
                        if (TextUtils.isEmpty(messageId)) {
                            Log.e(TAG, "Invalid messageId received");
                            messageIsValid = false;
                        }
                        if (messageIsValid) {
                            //TODO edit in DB

                            // broadcast update
                            Intent intent = new Intent(getResources().getString(R.string.ACTION_SERVER_ACK_MESSAGE));
                            intent.putExtra(getResources().getString(R.string.EXTRA_MESSAGE_ID), messageId);
                            intent.putExtra(getResources().getString(R.string.EXTRA_MESSAGE_CIRCLE_ID), circleId);
                            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                        }
                        break;
                    }

                    default:
                        // do nothing for now
                        Log.e(TAG, "Unsupported opcode received, do nothing for now!");
                }
            }
            catch (NumberFormatException ex) {
                Log.e(TAG, ex.getMessage());
            }
        }

        if (from.startsWith("/topics/")) {
            // message received from some topic.
        } else {
            // normal downstream message.
        }

        // [END_EXCLUDE]
    }
    // [END receive_message]

    /**
     * Create and show a simple notification containing the received GCM message.
     * TODO Send notification with different intents
     *
     * @param message GCM message received.
     */
    private void sendNotification(String message) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, Const.NOTIFY_LOCATION_UPDATE, intent,
                PendingIntent.FLAG_ONE_SHOT);

        Uri defaultSoundUri= RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_action_alarm)
                .setContentTitle(getResources().getString(R.string.notification_title_location_update))
                .setContentText(message)
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.notify(Const.NOTIFY_LOCATION_UPDATE, notificationBuilder.build());
    }
}
