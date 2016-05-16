package com.abborg.glom.utils;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.abborg.glom.Const;
import com.abborg.glom.data.DataUpdater;
import com.abborg.glom.model.BoardItem;
import com.abborg.glom.model.Circle;
import com.abborg.glom.model.CloudProvider;
import com.abborg.glom.model.FileItem;
import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;

/**
 * Handles file transfer operations to supported cloud providers:
 * Dropbox, Google Drive, and Amazon S3. All file operations must be done
 * on a seperate thread than UI thread.
 *
 * Created by jitrapon on 12/5/16.
 */
public class FileTransfer {

    private static final String TAG = "FileTransfer";

    private Context context;

    /* The cloud to which the operation will be done from */
    private CloudProvider cloudProvider;

    /* Main thread handler */
    private Handler handler;

    private DataUpdater dataUpdater;

    /********** AMAZON S3 ************/
    private TransferUtility s3Transfer;
    private AmazonS3Client s3Client;

    public FileTransfer(DataUpdater updater, Context ctx, Handler hand) {
        context = ctx;
        handler = hand;
        dataUpdater = updater;
    }

    public void upload(final CloudProvider provider, final Circle circle, final FileItem item) {
        try {
            switch (provider) {
                case AMAZON_S3: {
                    if (s3Transfer == null) createAmazonS3Client();

                    Log.d(TAG, "Begin uploading to Amazon S3...");
                    s3Transfer.upload(
                            Const.AWS_S3_BUCKET,
                            circle.getId() + "/" + item.getName(),
                            item.getFile()
                    ).setTransferListener(new TransferListener() {
                        @Override
                        public void onStateChanged(int id, TransferState state) {
                            Log.d(TAG, "Amazon S3 upload id " + id + " state changed to " + state.name());

                            if (handler != null) {
                                switch (state) {
                                    case CANCELED:
                                    case FAILED:
                                        dataUpdater.setSyncStatus(item, Const.MSG_FILE_POST_FAILED, BoardItem.SYNC_ERROR);
                                        break;
                                    case COMPLETED:
                                        dataUpdater.requestPostFile(circle, item, provider);
                                        break;
                                }
                            }
                        }

                        @Override
                        public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                            int progress = (int) (bytesCurrent/bytesTotal * 100);
                            Log.d(TAG, "Amazon S3 upload id " + id + ", uploading at progress " + progress);
                            if (handler != null)
                                handler.sendMessage(handler.obtainMessage(
                                        Const.MSG_FILE_POST_IN_PROGRESS, BoardItem.SYNC_IN_PROGRESS, progress, item));
                        }

                        @Override
                        public void onError(int id, Exception ex) {
                            Log.e(TAG, "Amazon S3 upload id " + id + " has encountered an error due to " + ex.getMessage());
                            dataUpdater.setSyncStatus(item, Const.MSG_FILE_POST_FAILED, BoardItem.SYNC_ERROR);
                        }
                    });
                    break;
                }
                case DROPBOX: {

                    break;
                }
                case GOOGLE_DRIVE: {

                    break;
                }
                default:
                    break;
            }
        }
        catch (Exception ex) {
            Log.e(TAG, ex.getMessage());
            dataUpdater.setSyncStatus(item, Const.MSG_FILE_POST_FAILED, BoardItem.SYNC_ERROR);
        }
    }

    public void download(CloudProvider provider, FileItem item) {

    }

    public void delete(CloudProvider provider, Circle circle, FileItem item) {
        try {
            switch (provider) {
                case AMAZON_S3: {
                    if (s3Client == null) createAmazonS3Client();

                    Log.d(TAG, "Attempting to delete " + item.getName() + " from Amazon S3...");
                    s3Client.deleteObject(Const.AWS_S3_BUCKET, circle.getId() + "/" + item.getName());
                    dataUpdater.requestDeleteItem(circle, item);

                    break;
                }
                case DROPBOX: {

                    break;
                }
                case GOOGLE_DRIVE: {

                    break;
                }
                default:
                    break;
            }
        }
        catch (Exception ex) {
            Log.e(TAG, ex.getMessage());
            if (handler != null) {
                handler.sendMessage(handler.obtainMessage(Const.MSG_ITEM_DELETED_FAILED, item));
            }
        }
    }

    /******************************************************
     * VENDOR-SPECIFIC OPERATIONS
     ******************************************************/
    private void createAmazonS3Client() {
        CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
                context,
                Const.AWS_IDENTIY_POOL_ID,  // Identity Pool ID
                Regions.US_EAST_1           // Region
        );
        s3Client = new AmazonS3Client(credentialsProvider);
        s3Transfer = new TransferUtility(s3Client, context.getApplicationContext());
    }
}
