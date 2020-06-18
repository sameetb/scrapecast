package org.sb.scrapecast.scraper;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;

import org.sb.scrapecast.R;
import org.sb.scrapecast.Roots;
import org.sb.scrapecast.Utils;
import org.sb.scrapecast.player.ExpandedControlsActivity;
import org.sb.scrapecast.queue.QueueDataProvider;
import org.sb.scrapecast.queue.QueueHelper;

import java.net.URI;
import java.util.List;

/**
 * Created by sam on 22-12-2017.
 */
public class AudioBrowserFragment extends Fragment implements AudioListAdapter.ItemClickListener,
        LoaderManager.LoaderCallbacks<Pair<List<Dir>, List<MediaInfo>>>
{
    private static final String TAG = AudioBrowserFragment.class.getSimpleName();

    public static final String EXTRA_PARENT_TITLE_STRING = "EXTRA_PARENT_TITLE_STRING";
    public static final String EXTRA_TITLE_STRING = "EXTRA_TITLE_STRING";
    public static final String EXTRA_URI_SERIALIZABLE = "EXTRA_URI_SERIALIZABLE";

    private RecyclerView audioRecyclerView;
    private AudioListAdapter audioAdapter;
    private View mEmptyView;
    private View mLoadingView;
    private MediaInfo currentItem;
    private TextView mTextViewTitle;

    private final SessionManagerListener<CastSession> mSessionManagerListener =
            new SessionManagerListener<CastSession>() {

    @Override
    public void onSessionEnded(CastSession session, int error) {
        audioAdapter.notifyDataSetChanged();
        cancelCurrentItem();
    }

    @Override
    public void onSessionResumed(CastSession session, boolean wasSuspended) {
        audioAdapter.notifyDataSetChanged();
        playCurrentItem();
    }

    @Override
    public void onSessionStarted(CastSession session, String sessionId) {
        audioAdapter.notifyDataSetChanged();
        playCurrentItem();
    }

    private void playCurrentItem() {
        if (currentItem != null) {
            playRemoteMedia(currentItem, true, 0, null);
        }
    }

    private void cancelCurrentItem() {
        currentItem = null;
    }

    @Override
    public void onSessionStarting(CastSession session) {
    }

    @Override
    public void onSessionStartFailed(CastSession session, int error) {
        cancelCurrentItem();
    }

    @Override
    public void onSessionEnding(CastSession session) {
    }

    @Override
    public void onSessionResuming(CastSession session, String sessionId) {
    }

    @Override
    public void onSessionResumeFailed(CastSession session, int error) {
        cancelCurrentItem();
    }

    @Override
    public void onSessionSuspended(CastSession session, int reason) {
    }
};

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.audio_browser_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        audioRecyclerView = (RecyclerView) getView().findViewById(R.id.listTracks);

        mEmptyView = getView().findViewById(R.id.empty_view);
        mLoadingView = getView().findViewById(R.id.progress_indicator);

        String title = getActivity().getIntent().getStringExtra(EXTRA_TITLE_STRING);
        if(title != null && title.compareToIgnoreCase("Parent Directory") == 0)
            title = getActivity().getIntent().getStringExtra(EXTRA_PARENT_TITLE_STRING);
        mTextViewTitle = ((TextView) getView().findViewById(R.id.textViewTitle));
        mTextViewTitle.setText(title != null ? title : "Sites");

        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        audioRecyclerView.setLayoutManager(layoutManager);

        audioAdapter = new AudioListAdapter(this, getContext());
        audioRecyclerView.setAdapter(audioAdapter);

        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public void itemClicked(View view, final MediaInfo item, ACTION action, int position) {
        final QueueHelper.MediaItemSupplier mediaItemSupplier = getMediaItemSupplier(item);

        switch(action)
        {
            case MENU:
                QueueHelper.showQueuePopup(getActivity(), view, mediaItemSupplier);
                break;
            case PLAY:
                currentItem = item;
                playRemoteMedia(item, true, 0, null);
                break;
            case QUEUE:
                QueueHelper.queueItem(R.id.action_add_to_queue, getActivity(), mediaItemSupplier, getRemoteMediaClient());
                break;
        }
    }

    @NonNull
    private QueueHelper.MediaItemSupplier getMediaItemSupplier(final MediaInfo item)
    {
        return new QueueHelper.MediaItemSupplier()
        {
            @Override
            public MediaInfo currentItem()
            {
                return item;
            }

            @Override
            public List<MediaInfo> allItems()
            {
                return audioAdapter.getAllTracks();
            }
        };
    }

    private RemoteMediaClient getRemoteMediaClient()
    {
        CastContext mCastContext = CastContext.getSharedInstance(getActivity());
        CastSession mCastSession = mCastContext.getSessionManager().getCurrentCastSession();

        if (mCastSession == null || !mCastSession.isConnected()) {
            Log.w(TAG, "No CastSession found/connected");
            Utils.showErrorDialog(getActivity(), "Please choose/connect to a cast device");
            return null;
        }
        return mCastSession.getRemoteMediaClient();
    }

    private void playRemoteMedia(MediaInfo mSelectedMedia, boolean autoPlay, int position, final Runnable onQueueStatusUpdated) {

        if(mSelectedMedia != null) {
            final RemoteMediaClient remoteMediaClient = getRemoteMediaClient();
            if (remoteMediaClient == null) {
                Log.w(TAG, "showQueuePopup(): null RemoteMediaClient");
                Utils.showToast(getActivity(), R.string.error_failed_to_connect);
                return;
            }
            remoteMediaClient.addListener(rmListener(getActivity(), onQueueStatusUpdated, remoteMediaClient));

            /*remoteMediaClient.load(mSelectedMedia, autoPlay, position);
            Toast.makeText(getActivity(), getActivity().getString(R.string.queue_item_added_to_play_next,
                    mSelectedMedia.getMetadata().getString(MediaMetadata.KEY_TITLE)), Toast.LENGTH_SHORT).show();*/
            QueueHelper.queueItem(R.id.action_play_next, getActivity(), getMediaItemSupplier(mSelectedMedia), remoteMediaClient);
        }
        else
        {
            Log.d(TAG, "Nothing to play");
            Toast.makeText(getActivity(), "Nothing to play", Toast.LENGTH_SHORT).show();
        }
    }

    @NonNull
    private static RemoteMediaClient.Listener rmListener(final Activity ctx, final Runnable onQueueStatusUpdated,
                                                                    final RemoteMediaClient remoteMediaClient)
    {
        return new RemoteMediaClient.Listener() {
            @Override
            public void onStatusUpdated() {
                Intent intent = new Intent(ctx, ExpandedControlsActivity.class);
                ctx.startActivity(intent);
                if(onQueueStatusUpdated == null)
                    remoteMediaClient.removeListener(this);
            }

            @Override
            public void onMetadataUpdated() {
            }

            @Override
            public void onQueueStatusUpdated() {
                Log.d(TAG, "onQueueStatusUpdated()");
                if(onQueueStatusUpdated != null)
                {
                    new Handler().postDelayed(onQueueStatusUpdated, 5*1000);
                }
                remoteMediaClient.removeListener(this);
            }

            @Override
            public void onPreloadStatusUpdated() {
            }

            @Override
            public void onSendingRemoteMediaRequest() {
                Log.d(TAG, "onSendingRemoteMediaRequest()");
            }

            @Override
            public void onAdBreakStatusUpdated() {
            }
        };
    }

    @Override
    public void itemClicked(View view, Dir item, int position) {
        if(item instanceof PlayList)
        {
            showPlaylistPopup(getActivity(), view, (PlayList)item);
            return;
        }
        Intent intent = new Intent(getActivity(), AudioBrowserActivity.class);
        intent.putExtra(EXTRA_PARENT_TITLE_STRING, mTextViewTitle.getText().toString());

        if(item.title.compareToIgnoreCase("Parent Directory") == 0)
            intent.putExtra(EXTRA_TITLE_STRING, getActivity().getIntent().getStringExtra(EXTRA_PARENT_TITLE_STRING));
        else
            intent.putExtra(EXTRA_TITLE_STRING, item.title);
        intent.putExtra(EXTRA_URI_SERIALIZABLE, item.uri);
        startActivity(intent);
    }

    @Override
    public Loader<Pair<List<Dir>, List<MediaInfo>>> onCreateLoader(int id, Bundle args) {
        URI uri = (URI) getActivity().getIntent().getSerializableExtra(EXTRA_URI_SERIALIZABLE);
        if(uri == null) { //from MainActivity
            Log.d(TAG, "Loading roots");
            return loadRootsAndStreams();
        }
        else {
            Log.d(TAG, "Loading " + uri);
            return new AudioScraper(getActivity(), uri);
        }
    }

    private Loader<Pair<List<Dir>, List<MediaInfo>>> loadRootsAndStreams()
    {
        return new RootLoader(getActivity());
    }

    @Override
    public void onLoadFinished(Loader<Pair<List<Dir>, List<MediaInfo>>> loader, Pair<List<Dir>, List<MediaInfo>> data) {
        Log.d(TAG, "Setting data");
        audioAdapter.setData(data);
        mLoadingView.setVisibility(View.GONE);
        mEmptyView.setVisibility(null == data || (data.first.isEmpty() && data.second.isEmpty()) ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onLoaderReset(Loader<Pair<List<Dir>, List<MediaInfo>>> loader) {
        audioAdapter.setData(new Pair<List<Dir>, List<MediaInfo>>(null, null));
    }

    @Override
    public void onStart() {
        CastContext.getSharedInstance(getContext()).getSessionManager()
                .addSessionManagerListener(mSessionManagerListener, CastSession.class);
        super.onStart();
    }

    @Override
    public void onStop() {
        CastContext.getSharedInstance(getContext()).getSessionManager()
                .removeSessionManagerListener(mSessionManagerListener, CastSession.class);
        super.onStop();
    }

    private static class RootLoader extends AsyncTaskLoader<Pair<List<Dir>, List<MediaInfo>>>
    {
        public RootLoader(Context context) {
            super(context);
        }

        @Override
        public Pair<List<Dir>, List<MediaInfo>> loadInBackground()
        {
            return new Pair<>(
                    Roots.get(Roots.Name.ROOTS).all(new Roots.Maker<Dir>() {
                        @Override
                        public Dir make(String name, URI uri) {
                            return new Dir(name, uri);
                        }
                    }),
                    Roots.get(Roots.Name.STREAMS).all(new Roots.Maker<MediaInfo>() {
                        @Override
                        public MediaInfo make(String name, URI uri) {
                            return AudioScraper.buildAudioMediaInfo(name, uri, "audio/mpeg", null, MediaInfo.STREAM_TYPE_LIVE);
                        }
                    }));
        }

        @Override
        protected void onStartLoading() {
            super.onStartLoading();
            forceLoad();
        }

        /**
         * Handles a request to stop the Loader.
         */
        @Override
        protected void onStopLoading() {
            // Attempt to cancel the current load task if possible.
            cancelLoad();
        }
    }

    private void showPlaylistPopup(final Context context, View view, final PlayList pl) {
		final CastSession castSession =
				CastContext.getSharedInstance(context).getSessionManager().getCurrentCastSession();
		if (castSession == null || !castSession.isConnected()) {
			Log.w(TAG, "queuePlaylist(): not connected to a cast device");
			Utils.showErrorDialog(context, "Please choose/connect to a cast device");
			return;
		}
		
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Queue playlist \"" + pl.title + "\" ?");

        // Set up the buttons
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                loadPlaylist(pl, context);
            }
        });

        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    private void loadPlaylist(final PlayList pl, final Context context) {
        getLoaderManager().initLoader(pl.title.hashCode(), null,
                new LoaderManager.LoaderCallbacks<List<MediaInfo>>() {
                    @Override
                    public Loader<List<MediaInfo>> onCreateLoader(int id, Bundle args) {
                        return new PlayListLoader(AudioBrowserFragment.this.getActivity(), pl);
                    }

                    @Override
                    public void onLoadFinished(Loader<List<MediaInfo>> loader, final List<MediaInfo> data) {
                        getLoaderManager().destroyLoader(loader.getId());
                        queuePlaylist(data, pl, context);
                    }

                    @Override
                    public void onLoaderReset(Loader<List<MediaInfo>> loader) {

                    }
                });
    }

    private void queuePlaylist(final List<MediaInfo> data, final PlayList pl, final Context context) {
        if (data.size() > 0) {
			if (QueueHelper.queuePlaylistItems(context, data))
				Utils.showToast(context, "Playlist \"" + pl.title + "\" added to queue");
			else
				Utils.showToast(context, "Failed to queue playlist \"" + pl.title + "\"");
        } else
            Utils.showToast(context, "Playlist \"" + pl.title + "\" is empty");
    }


}
