package org.sb.scrapecast.queue;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import org.json.JSONException;
import org.json.JSONObject;
import org.sb.scrapecast.R;
import org.sb.scrapecast.Utils;
import org.sb.scrapecast.player.CastPreference;
import org.sb.scrapecast.scraper.AudioScraper;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.images.WebImage;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * An activity to show the queue list
 */
public class QueueListViewActivity extends AppCompatActivity {

    private static final String FRAGMENT_LIST_VIEW = "list view";
    private static final String TAG = "QueueListViewActivity";

    private final RemoteMediaClient.Listener mRemoteMediaClientListener =
            new MyRemoteMediaClientListener();
    private final SessionManagerListener<CastSession> mSessionManagerListener =
            new MySessionManagerListener();
    private CastContext mCastContext;
    private RemoteMediaClient mRemoteMediaClient;
    private View mEmptyView;

    private class MySessionManagerListener implements SessionManagerListener<CastSession> {

        @Override
        public void onSessionEnded(CastSession session, int error) {
            if (mRemoteMediaClient != null) {
                mRemoteMediaClient.removeListener(mRemoteMediaClientListener);
            }
            mRemoteMediaClient = null;
            mEmptyView.setVisibility(View.VISIBLE);
        }

        @Override
        public void onSessionResumed(CastSession session, boolean wasSuspended) {
            mRemoteMediaClient = getRemoteMediaClient();
            if (mRemoteMediaClient != null) {
                mRemoteMediaClient.addListener(mRemoteMediaClientListener);
            }
        }

        @Override
        public void onSessionStarted(CastSession session, String sessionId) {
            mRemoteMediaClient = getRemoteMediaClient();
            if (mRemoteMediaClient != null) {
                mRemoteMediaClient.addListener(mRemoteMediaClientListener);
            }
        }

        @Override
        public void onSessionStarting(CastSession session) {
        }

        @Override
        public void onSessionStartFailed(CastSession session, int error) {
        }

        @Override
        public void onSessionEnding(CastSession session) {
        }

        @Override
        public void onSessionResuming(CastSession session, String sessionId) {
        }

        @Override
        public void onSessionResumeFailed(CastSession session, int error) {
        }

        @Override
        public void onSessionSuspended(CastSession session, int reason) {
            if (mRemoteMediaClient != null) {
                mRemoteMediaClient.removeListener(mRemoteMediaClientListener);
            }
            mRemoteMediaClient = null;
        }
    }

    private class MyRemoteMediaClientListener implements RemoteMediaClient.Listener {

        @Override
        public void onStatusUpdated() {
            updateMediaQueue();
        }

        @Override
        public void onQueueStatusUpdated() {
            updateMediaQueue();
        }

        @Override
        public void onMetadataUpdated() {
        }

        @Override
        public void onPreloadStatusUpdated() {
        }

        @Override
        public void onSendingRemoteMediaRequest() {
        }

        @Override
        public void onAdBreakStatusUpdated() {
        }

        private void updateMediaQueue() {
            MediaStatus mediaStatus = mRemoteMediaClient.getMediaStatus();
            List<MediaQueueItem> queueItems =
                    (mediaStatus == null) ? null : mediaStatus.getQueueItems();
            if (queueItems == null || queueItems.isEmpty()) {
                mEmptyView.setVisibility(View.VISIBLE);
            } else {
                mEmptyView.setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.queue_activity);
        Log.d(TAG, "onCreate() was called");

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new QueueListViewFragment(), FRAGMENT_LIST_VIEW)
                    .commit();
        }
        setupActionBar();
        mEmptyView = findViewById(R.id.empty);
        mCastContext = CastContext.getSharedInstance(this);
        //mCastContext.registerLifecycleCallbacksBeforeIceCreamSandwich(this, savedInstanceState);
    }


    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.queue_list);
        }
    }

    @Override
    protected void onPause() {
        if (mRemoteMediaClient != null) {
            mRemoteMediaClient.removeListener(mRemoteMediaClientListener);
        }
        mCastContext.getSessionManager().removeSessionManagerListener(
                mSessionManagerListener, CastSession.class);
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.queue_menu, menu);
        CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), menu,
                R.id.media_route_menu_item);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(QueueListViewActivity.this, CastPreference.class));
                break;
            case R.id.action_clear_queue:
                QueueDataProvider.getInstance(getApplicationContext()).removeAll();
                break;
            case android.R.id.home:
                finish();
                break;
            case R.id.action_save_queue:
                dialogSavePlaylist(null, QueueDataProvider.getInstance(getApplicationContext()).getItems());
                break;
            case R.id.action_load_queue:
                dialogChoosePlaylist();
                break;
            case R.id.action_shuffle_queue:
                dialogShuffleQueue();
                break;
        }
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        return mCastContext.onDispatchVolumeKeyEventBeforeJellyBean(event)
                || super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        mCastContext.getSessionManager().addSessionManagerListener(
                mSessionManagerListener, CastSession.class);
        if (mRemoteMediaClient == null) {
            mRemoteMediaClient = getRemoteMediaClient();
        }
        if (mRemoteMediaClient != null) {
            mRemoteMediaClient.addListener(mRemoteMediaClientListener);
            MediaStatus mediaStatus = mRemoteMediaClient.getMediaStatus();
            List<MediaQueueItem> queueItems =
                    (mediaStatus == null) ? null : mediaStatus.getQueueItems();
            if (queueItems != null && !queueItems.isEmpty()) {
                mEmptyView.setVisibility(View.GONE);
            }
        }
        super.onResume();
    }

    private RemoteMediaClient getRemoteMediaClient() {
        CastSession castSession = mCastContext.getSessionManager().getCurrentCastSession();
        return (castSession != null && castSession.isConnected())
                ? castSession.getRemoteMediaClient() : null;
    }

    private void dialogSavePlaylist(String name, final List<MediaQueueItem> items)
    {
        if(items.size() == 0)
        {
            Utils.showToast(this, "Nothing to save, queue is empty!");
            return;
        }

        final EditText inputTitle = new EditText(this);
        inputTitle.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        inputTitle.setText((name != null ? name : "Playlist-" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())));

        new AlertDialog.Builder(this)
            .setTitle("Save as playlist ?")
            .setView(inputTitle)
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    dialog.dismiss();
                    String title = inputTitle.getText().toString();
                    if (title.length() != 0)
                        try
                        {
                            saveAsPlayList(title, items);
                        }
                        catch (Exception e)
                        {
                            Log.e(TAG, "Failed to save playlist " + title, e);
                            Utils.showToast(QueueListViewActivity.this, "Failed to save playlist " + title);
                        }
                    else
                        Utils.showErrorDialog(QueueListViewActivity.this, "Please provide a non empty name");
                }
            })
            .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    dialog.cancel();
                }
            })
            .create()
            .show();
    }

    private void saveAsPlayList(String name, List<MediaQueueItem> items) throws JSONException
    {
        final LinkedHashSet<String> infos = new LinkedHashSet<>();
        for(MediaQueueItem item : items)
            infos.add(toJson(item.getMedia()).toString());

        SharedPreferences pls = getSharedPreferences(getLocalClassName() + ".PlayLists", MODE_PRIVATE);
        SharedPreferences.Editor editor = pls.edit();
        editor.putStringSet(name, infos);
        editor.apply();
    }

    private static JSONObject toJson(MediaInfo media) throws JSONException
    {
        return new JSONObject()
                    .putOpt("title", media.getMetadata().getString(MediaMetadata.KEY_TITLE))
                    .put("uri", media.getContentId())
                    .putOpt("contentType", media.getContentType())
                    .putOpt("imageUri", imgUrl(media.getMetadata().getImages()))
                    .put("streamType", media.getStreamType());
    }

    private static String imgUrl(List<WebImage> images)
    {
        for(WebImage img : images)
            return img.getUrl().toString();
        return null;
    }

    private static MediaInfo fromJson(JSONObject json) throws Exception
    {
        return AudioScraper.buildAudioMediaInfo(
                                json.optString("title", "Unknown"),
                                URI.create(json.getString("uri")),
                                json.optString("contentType", "audio/mpeg"),
                                json.has("imageUri")?Collections.singletonList(URI.create(json.getString("imageUri"))):null,
                                json.optInt("streamType", MediaInfo.STREAM_TYPE_BUFFERED));
    }

    private void dialogChoosePlaylist()
    {
        final SharedPreferences pls = getSharedPreferences(getLocalClassName() + ".PlayLists", MODE_PRIVATE);
        String[] names = pls.getAll().keySet().toArray(new String[0]);
        if(names.length > 0)
        {
            final ArrayAdapter<String> adp = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);

            final Spinner spinner = new Spinner(this);
            spinner.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            spinner.setAdapter(adp);

            new AlertDialog.Builder(this)
                .setTitle("Choose a playlist.")
                .setView(spinner)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dialog.dismiss();
                        String title = (String)spinner.getSelectedItem();
                        if (title.length() != 0)
                            try
                            {
                                loadPlayList(title, pls.getStringSet(title, Collections.<String>emptySet()));
                            }
                            catch (Exception e)
                            {
                                Log.e(TAG, "Failed to load playlist " + title, e);
                                Utils.showToast(QueueListViewActivity.this, "Failed to load playlist " + title);
                            }
                        else
                            Utils.showErrorDialog(QueueListViewActivity.this, "Please provide a non empty name");
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dialog.cancel();
                    }
                })
                .create()
                .show();
        }
        else
            Utils.showToast(this, "No playlists found");

    }

    private void loadPlayList(String name, Set<String> infos) throws Exception
    {
        ArrayList<MediaInfo> media = new ArrayList<>();
        for(String info : infos)
        try
        {
            media.add(fromJson(new JSONObject(info)));
        }
        catch(Exception e)
        {
            Log.e(TAG, "Failed to deserialize: " + info);
            throw e;
        }
        QueueHelper.queuePlaylistItems(this, media);
    }

    private void dialogShuffleQueue()
    {
        new AlertDialog.Builder(this)
                .setTitle("Shuffle Queue ?")
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dialog.dismiss();
                        if(!QueueHelper.isQueueEmpty(QueueListViewActivity.this))
                            try
                            {
                                QueueHelper.shuffleQueue(QueueListViewActivity.this);
                            }
                            catch (Exception e)
                            {
                                Log.e(TAG, "Failed to shuffle queue", e);
                                Utils.showToast(QueueListViewActivity.this, "Failed to shuffle queue.");
                            }
                        else
                            Utils.showToast(QueueListViewActivity.this, "Queue is empty");
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dialog.cancel();
                    }
                })
                .create()
                .show();
    }

}
