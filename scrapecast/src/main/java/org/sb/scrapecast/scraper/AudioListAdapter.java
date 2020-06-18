package org.sb.scrapecast.scraper;

import android.content.Context;
import android.graphics.Color;
import android.support.v7.widget.RecyclerView;
import android.text.Layout;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;

import org.sb.scrapecast.R;

import java.util.Collections;
import java.util.List;

/**
 * Created by sam on 21-12-2017.
 */
public class AudioListAdapter  extends RecyclerView.Adapter<AudioListAdapter.ViewHolder> {

    private final ItemClickListener mClickListener;
    private final Context mAppContext;
    private List<Dir> dirs;
    private List<MediaInfo> tracks;

    public AudioListAdapter(ItemClickListener clickListener, Context context) {
        mClickListener = clickListener;
        mAppContext = context.getApplicationContext();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        Context context = viewGroup.getContext();
        View parent = LayoutInflater.from(context).inflate(R.layout.audio_browse_row, viewGroup, false);
        return new ViewHolder(parent);
    }

    @Override
    public void onBindViewHolder(final ViewHolder viewHolder, final int position) {

        if(position >= dirs.size()) {
            final MediaInfo item = tracks.get(position - dirs.size());
            MediaMetadata mm = item.getMetadata();
            viewHolder.setTitle(mm.getString(MediaMetadata.KEY_TITLE));
            viewHolder.mTitleView.setTextColor(Color.BLUE);

            viewHolder.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View view)
                {
                    mClickListener.itemClicked(view, item, ItemClickListener.ACTION.PLAY, position);
                }
            });
            viewHolder.setOnLongClickListener(new View.OnLongClickListener()
            {
                @Override
                public boolean onLongClick(View view)
                {
                    mClickListener.itemClicked(view, item, ItemClickListener.ACTION.MENU, position);
                    return true;
                }
            });
            viewHolder.playBtn.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View view)
                {
                    mClickListener.itemClicked(view, item, ItemClickListener.ACTION.PLAY, position);
                }
            });
            viewHolder.queueBtn.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View view)
                {
                    mClickListener.itemClicked(view, item, ItemClickListener.ACTION.QUEUE, position);
                }
            });
        }
        else
        {
            final Dir item = dirs.get(position);
            viewHolder.setTitle(item.title);

            viewHolder.mTitleView.setTextColor(item instanceof PlayList ? Color.DKGRAY : Color.BLACK);
            viewHolder.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mClickListener.itemClicked(view, item, position);
                }
            });
            viewHolder.mTitleView.setLongClickable(false);
            viewHolder.playBtn.setVisibility(View.GONE);
            viewHolder.queueBtn.setVisibility(View.GONE);
        }
        viewHolder.mParent.setBackgroundColor(mAppContext.getResources().getColor(
                                                                R.color.bg_item_normal_state, null));

    }

    @Override
    public int getItemCount() {
        return (dirs == null? 0 :dirs.size()) + (tracks == null ? 0 : tracks.size());
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final View mParent;
        private final ImageButton queueBtn;
        private final ImageButton playBtn;
        private final TextView mTitleView;

        private ViewHolder(View parent) {
            super(parent);
            mParent = parent;
            mTitleView = (TextView) parent.findViewById(R.id.textViewTitle);;
            queueBtn = (ImageButton) parent.findViewById(R.id.btn_queue);
            playBtn = (ImageButton) parent.findViewById(R.id.btn_play);
        }

        public void setTitle(String title) {
            mTitleView.setText(title);
        }

        public void setOnClickListener(View.OnClickListener listener) {
            mTitleView.setOnClickListener(listener);
            mParent.setOnClickListener(listener);
        }

        public void setOnLongClickListener(View.OnLongClickListener onLongClickListener) {
            mTitleView.setLongClickable(true);
            mTitleView.setOnLongClickListener(onLongClickListener);
            mParent.setLongClickable(true);
            mParent.setOnLongClickListener(onLongClickListener);
        }
    }

    public void setData(Pair<List<Dir>, List<MediaInfo>> data) {
        dirs = data.first;
        tracks = data.second;
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return super.getItemId(position);
    }

    /**
     * A listener called when an item is clicked in the video list.
     */
    public interface ItemClickListener {

        enum ACTION {PLAY, QUEUE, MENU};

        void itemClicked(View view, MediaInfo item, ACTION action, int position);
        void itemClicked(View view, Dir item, int position);
    }

    List<MediaInfo> getAllTracks()
    {
        return Collections.unmodifiableList(tracks);
    }
}
