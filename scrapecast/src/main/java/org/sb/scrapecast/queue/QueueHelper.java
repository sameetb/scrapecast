package org.sb.scrapecast.queue;

import android.content.Context;
import android.content.Intent;
import android.support.v7.widget.PopupMenu;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;

import org.sb.scrapecast.Utils;
import org.sb.scrapecast.player.ExpandedControlsActivity;
import org.sb.scrapecast.R;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by sam on 22-12-2017.
 */
public class QueueHelper {

    private static final String TAG = QueueHelper.class.getSimpleName();

    private static final int PRELOAD_TIME_S = 20;

    public interface MediaItemSupplier
    {
        MediaInfo currentItem();
        List<MediaInfo> allItems();
    }
    /**
     * Show a popup to select whether the selected item should play immediately, be added to the
     * end of queue or be added to the queue right after the current item.
     */
    public static void showQueuePopup(final Context context, View view, final MediaItemSupplier sup) {
        final CastSession castSession =
                CastContext.getSharedInstance(context).getSessionManager().getCurrentCastSession();
        if (castSession == null || !castSession.isConnected()) {
            Log.w(TAG, "showQueuePopup(): not connected to a cast device");
            Utils.showErrorDialog(context, "Please choose/connect to a cast device");
            return;
        }
        final QueueDataProvider provider = QueueDataProvider.getInstance(context);
        PopupMenu popup = new PopupMenu(context, view);
        popup.getMenuInflater().inflate(
                provider.isQueueDetached() || provider.getCount() == 0
                        ? R.menu.detached_popup_add_to_queue
                        : R.menu.popup_add_to_queue, popup.getMenu());
        PopupMenu.OnMenuItemClickListener clickListener = new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {

                final int menuItemId = menuItem.getItemId();
                return queueItem(menuItemId, context, sup, castSession.getRemoteMediaClient());
            }
        };
        popup.setOnMenuItemClickListener(clickListener);
        popup.show();
    }

    public static boolean queueItem(int menuItemId, Context context, MediaItemSupplier sup, RemoteMediaClient remoteMediaClient)
    {
        if (remoteMediaClient == null) {
            Log.w(TAG, "showQueuePopup(): null RemoteMediaClient");
            Utils.showToast(context, R.string.error_failed_to_connect);
            return false;
        }
        QueueDataProvider provider = QueueDataProvider.getInstance(context);
        MediaQueueItem mediaQueueItem = new MediaQueueItem.Builder(sup.currentItem()).setAutoplay(
                true).setPreloadTime(PRELOAD_TIME_S).build();
        String mediaTitle =  mediaQueueItem.getMedia().getMetadata().getString(MediaMetadata.KEY_TITLE);
        int toastMsgId = -1;

        if (provider.isQueueDetached() && provider.getCount() > 0) {
            switch(menuItemId)
            {
                case R.id.action_play_now:
                case R.id.action_add_to_queue:
                {
                    MediaQueueItem[] items = rebuildQueueAndAppend(provider.getItems(), mediaQueueItem);
                    Log.d(TAG, "Adding to remote queue \"" + mediaTitle + "\"");
                    remoteMediaClient.queueLoad(items, provider.getCount(),
                            MediaStatus.REPEAT_MODE_REPEAT_OFF, null);
                    break;
                }

                case R.id.action_add_all_to_queue:
                {
                    queueItems(remoteMediaClient, provider, sup.allItems());
                    break;
                }

                default:
                    return false;
            }
        } else {
            if (provider.getCount() == 0) {
                switch(menuItemId) {
                    case R.id.action_play_now:
                    case R.id.action_play_next:
                    case R.id.action_add_to_queue:
                        Log.d(TAG, "Loading remote queue with \"" + mediaTitle + "\"");
                        remoteMediaClient.queueLoad(new MediaQueueItem[]{mediaQueueItem}, 0,
                                MediaStatus.REPEAT_MODE_REPEAT_OFF, null);
                        toastMsgId = R.string.queue_item_added_to_play_next;
                        break;

                    case R.id.action_add_all_to_queue:
                        queueItems(remoteMediaClient, provider, sup.allItems());
                        toastMsgId = R.string.queue_item_added_to_queue;
                        break;

                    default:
                        return false;
                }
            } else {
                Log.d(TAG, "Updating remote queue with \"" + mediaTitle + "\"");
                int currentId = provider.getCurrentItemId();
                switch(menuItemId)
                {
                    case R.id.action_play_now:
                        remoteMediaClient.queueInsertAndPlayItem(mediaQueueItem, currentId, null);
                        break;

                    case R.id.action_play_next:
                    {
                        int currentPosition = provider.getPositionByItemId(currentId);
                        if (currentPosition == provider.getCount() - 1) {
                            //we are adding to the end of queue
                            remoteMediaClient.queueAppendItem(mediaQueueItem, null);
                        } else {
                            int nextItemId = provider.getItem(currentPosition + 1).getItemId();
                            remoteMediaClient.queueInsertItems(new MediaQueueItem[]{mediaQueueItem}, nextItemId, null);
                        }
                        toastMsgId = R.string.queue_item_added_to_play_next;
                        break;
                    }

                    case R.id.action_add_to_queue: {
                        remoteMediaClient.queueAppendItem(mediaQueueItem, null);
                        toastMsgId = R.string.queue_item_added_to_queue;
                        break;
                    }

                    case R.id.action_add_all_to_queue:
                        queueItems(remoteMediaClient, provider, sup.allItems());
                        toastMsgId = R.string.queue_item_added_to_queue;
                        break;

                    default:
                        return false;
                }
            }
        }
        if (menuItemId == R.id.action_play_now) {
            Intent intent = new Intent(context, ExpandedControlsActivity.class);
            context.startActivity(intent);
        }
        if (toastMsgId != -1) {
            String toastMsg = context.getString(toastMsgId, mediaTitle);
            if(!TextUtils.isEmpty(toastMsg))
                Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    public static MediaQueueItem[] rebuildQueue(List<MediaQueueItem> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        MediaQueueItem[] rebuiltQueue = new MediaQueueItem[items.size()];
        for (int i = 0; i < items.size(); i++) {
            rebuiltQueue[i] = rebuildQueueItem(items.get(i));
        }

        return rebuiltQueue;
    }

    public static MediaQueueItem[] rebuildQueueAndAppend(List<MediaQueueItem> items,
                                                         MediaQueueItem currentItem) {
        if (items == null || items.isEmpty()) {
            return new MediaQueueItem[]{currentItem};
        }
        MediaQueueItem[] rebuiltQueue = new MediaQueueItem[items.size() + 1];
        for (int i = 0; i < items.size(); i++) {
            rebuiltQueue[i] = rebuildQueueItem(items.get(i));
        }
        rebuiltQueue[items.size()] = currentItem;

        return rebuiltQueue;
    }

    public static MediaQueueItem[] rebuildQueueAndAppend(List<MediaQueueItem> items,
                                                         MediaQueueItem[] currentItems) {
        if (items == null || items.isEmpty()) {
            return currentItems;
        }
        MediaQueueItem[] rebuiltQueue = new MediaQueueItem[items.size() + currentItems.length];
        for (int i = 0; i < items.size(); i++) {
            rebuiltQueue[i] = rebuildQueueItem(items.get(i));
        }
        for(int i = items.size(), j = 0; j < currentItems.length; i++, j++)
            rebuiltQueue[i] = currentItems[j];

        return rebuiltQueue;
    }

    public static MediaQueueItem rebuildQueueItem(MediaQueueItem item) {
        return new MediaQueueItem.Builder(item).clearItemId().build();
    }

    public static boolean queuePlaylistItems(final Context context, List<MediaInfo> items) {
		final CastSession castSession =
                CastContext.getSharedInstance(context).getSessionManager().getCurrentCastSession();
        if (castSession == null || !castSession.isConnected()) {
            Log.w(TAG, "queuePlaylistItems(): not connected to a cast device");
            Utils.showErrorDialog(context, "Please choose/connect to a cast device");
            return false;
        }
		
        final RemoteMediaClient remoteMediaClient = castSession.getRemoteMediaClient();
        if (remoteMediaClient == null) {
            Log.w(TAG, "queuePlaylistItems(): null RemoteMediaClient");
            Utils.showToast(context, R.string.error_failed_to_connect);
            return false;
        }

        QueueDataProvider provider = QueueDataProvider.getInstance(context);
        queueItems(remoteMediaClient, provider, items).setResultCallback(new ResultCallback<RemoteMediaClient.MediaChannelResult>() {
            @Override
            public void onResult(RemoteMediaClient.MediaChannelResult mediaChannelResult) {
                Log.d(TAG, "Status = " + mediaChannelResult.getStatus() + ", custom = " + mediaChannelResult.getCustomData());
                //Utils.showToast(context, mediaChannelResult.getStatus().toString());
            }
        });
        return true;
    }

    private static PendingResult<RemoteMediaClient.MediaChannelResult> queueItems(final RemoteMediaClient remoteMediaClient,
										QueueDataProvider provider, List<MediaInfo> items) {

        MediaQueueItem[] newItmArray = new MediaQueueItem[items.size()];
        int i = 0;
        for (MediaInfo medInfo : items) {
            newItmArray[i++] = new MediaQueueItem.Builder(medInfo).setAutoplay(true).setPreloadTime(PRELOAD_TIME_S).build();
            //String medTitle = medInfo.getMetadata().getString(MediaMetadata.KEY_TITLE);
        }

        if(provider.isQueueDetached() && provider.getCount() > 0)
        {
            Log.d(TAG, "Loading remote queue with additional " + items.size() + " items");
            return remoteMediaClient.queueLoad(rebuildQueueAndAppend(provider.getItems(), newItmArray), provider.getCount(),
                    MediaStatus.REPEAT_MODE_REPEAT_OFF, null);
        }
        else if(provider.getCount() == 0) {
            Log.d(TAG, "Loading remote queue with " + items.size() + " items");
            return remoteMediaClient.queueLoad(newItmArray, 0, MediaStatus.REPEAT_MODE_REPEAT_OFF, null);
        }
        else
        {
            Log.d(TAG, "Appending remote queue with " + items.size() + " items");
            return remoteMediaClient.queueInsertItems(newItmArray, MediaQueueItem.INVALID_ITEM_ID, null);
        }
    }

    public static boolean isQueueEmpty(Context context)
    {
        return !(QueueDataProvider.getInstance(context).getCount() > 0);
    }


    public static void shuffleQueue(Context context)
    {
        final CastSession castSession =
                CastContext.getSharedInstance(context).getSessionManager().getCurrentCastSession();
        if (castSession == null || !castSession.isConnected()) {
            Log.w(TAG, "shuffleQueue(): not connected to a cast device");
            Utils.showErrorDialog(context, "Please choose/connect to a cast device");
            return;
        }
        final RemoteMediaClient remoteMediaClient = castSession.getRemoteMediaClient();
        if (remoteMediaClient == null) {
            Log.w(TAG, "shuffleQueue(): null RemoteMediaClient");
            Utils.showToast(context, R.string.error_failed_to_connect);
            return;
        }

        final QueueDataProvider provider = QueueDataProvider.getInstance(context);

        final int[] itemIds = new int[provider.getCount()];

        if(provider.isQueueDetached() || itemIds.length == 0) {
            Utils.showToast(context, "Queue is detached or empty");
            return;
        }

        int i = 0;
        for(MediaQueueItem it : provider.getItems())
            itemIds[i++] = it.getItemId();

        Collections.shuffle(Arrays.asList(itemIds));
        remoteMediaClient.queueReorderItems(itemIds, MediaQueueItem.INVALID_ITEM_ID, null)
                .setResultCallback(new ResultCallback<RemoteMediaClient.MediaChannelResult>()
                {
                    @Override
                    public void onResult(RemoteMediaClient.MediaChannelResult res)
                    {
                        if(res.getStatus().isSuccess())
                        {
                            remoteMediaClient.queueJumpToItem(itemIds[0], null);
                        }
                    }
                });
    }
}
