package org.sb.scrapecast.scraper;

import android.content.Context;
import android.support.v4.content.AsyncTaskLoader;
import android.util.Log;
import android.util.Pair;
import android.net.Uri;

import com.google.android.gms.cast.MediaInfo;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by sam on 27-12-2017.
 */
public class PlayListLoader extends AsyncTaskLoader<List<MediaInfo>> {

	private final PlayList pl;

    public PlayListLoader(Context context, PlayList pl) {
        super(context);
        this.pl = pl;
    }

    @Override
    public List<MediaInfo> loadInBackground() {
        return parse();
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

    enum TYPE {M3U, PLS, SFV};

    private static final String TAG = PlayListLoader.class.getSimpleName();

    private List<MediaInfo> parse()
    {
        InputStream is = null;
        try {
            Log.d(TAG, "GET " + pl.uri);
            URLConnection urlConnection = pl.uri.toURL().openConnection();
            is = new BufferedInputStream(urlConnection.getInputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    urlConnection.getInputStream(), AudioScraper.DEFAULT_CHARSET), 1024);
            TYPE type = type(pl.uri.getPath());
            final URI parent = pl.uri.resolve(".").normalize();
            ArrayList<MediaInfo> media = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                final Pair<String, URI> itm = parseLine(line, parent, type);
                if(itm != null)
                    media.add(AudioScraper.buildAudioMediaInfo(itm.first, itm.second, "audio/mpeg", pl.imageUri, MediaInfo.STREAM_TYPE_BUFFERED));
            }
			Log.d(TAG, "Found " + media.size() + " items in playlist \"" + pl.title + "\"");
            return media;
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse url " + pl.uri, e);
            return Collections.emptyList();
        } finally {
            if (null != is) {
                try {
                    is.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private TYPE type(String path) {
        if(path.endsWith(".sfv")) return TYPE.SFV;
        if(path.endsWith(".pls")) return TYPE.PLS;
        if(path.endsWith(".m3u")) return TYPE.M3U;
        return  TYPE.M3U;
    }

    private Pair<String, URI> parseLine(String line, URI parent, TYPE type)
    {
        switch(type)
        {
            case SFV:
                if(line.startsWith(";")) return null;
                int lastSpace = line.lastIndexOf(' ');
                if(lastSpace > 0)
                {
                    return parseLine(line.substring(0, lastSpace - 1), parent, TYPE.M3U);
                }
                return null;

            case M3U:
				if(line.isEmpty() || line.startsWith("#")) return null;
                if(line.endsWith(".mp3") || line.endsWith(".MP3"))
                    return new Pair<>(line, parent.resolve(Uri.encode(line)));
                return null;

            case PLS:
                if(line.startsWith("[")) return null;
                String[] parts = line.split("=");
                if(parts.length == 2)
                    return parseLine(parts[1], parent, TYPE.M3U);
                return null;

            default:
                return null;
        }
    }

}
