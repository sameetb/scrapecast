package org.sb.scrapecast.scraper;

import android.content.Context;
import android.net.Uri;
import android.support.v4.content.AsyncTaskLoader;
import android.util.Log;
import android.util.Pair;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.common.images.WebImage;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by sam on 21-12-2017.
 */
public class AudioScraper extends AsyncTaskLoader<Pair<List<Dir>, List<MediaInfo>>>
{
    private static final String TAG = AudioScraper.class.getSimpleName();
    public static final Charset DEFAULT_CHARSET = Charset.defaultCharset();

    private final URI url;

    public AudioScraper(Context context, URI url)
    {
        super(context);
        this.url = url;
    }

    @Override
    public Pair<List<Dir>, List<MediaInfo>> loadInBackground() {
        try {
            return buildMediaInfo();
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch media data", e);
            return null;
        }
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

    public Pair<List<Dir>, List<MediaInfo>> buildMediaInfo()
    {
        ArrayList<Dir> dirs = new ArrayList<>();
        ArrayList<MediaInfo> music = new ArrayList<>();
        ArrayList<URI> albumImgURI = new ArrayList<>();
		List<Pair<String, URI>> anchors = parse();
		
		for(Pair<String, URI> anchor : anchors)
		{
            String file = anchor.second.getPath();
			if(file.endsWith(".jpg") || file.endsWith(".JPG"))
			{
				albumImgURI.add(anchor.second);
				break;
			}
		}
		
        for(Pair<String, URI> anchor : anchors)
        {
            String file = anchor.second.getPath();
            if(file.endsWith("/") && !isParent(anchor))
            {
                dirs.add(new Dir(anchor.first, anchor.second.normalize()));
            }
            else if(file.endsWith(".mp3") || file.endsWith(".MP3"))
            {
                //it is a media file
                music.add(buildAudioMediaInfo(anchor.first, anchor.second, albumImgURI, "audio/mpeg"));
            }
            /*else if(file.endsWith(".flac") || file.endsWith(".FLAC"))
            {
                //it is a media file
                music.add(buildAudioMediaInfo(anchor.first, anchor.second, albumImgURI, "audio/flac"));
            }*/
            else if(file.endsWith(".ogg") || file.endsWith(".OGG") 
				|| file.endsWith(".opus") || file.endsWith(".OPUS")
				|| file.endsWith(".oga") || file.endsWith(".OGA"))
            {
                //it is a media file
                music.add(buildAudioMediaInfo(anchor.first, anchor.second, albumImgURI, "audio/ogg"));
            }
            else if(file.endsWith(".aac") || file.endsWith(".AAC"))
            {
                //it is a media file
                music.add(buildAudioMediaInfo(anchor.first, anchor.second, albumImgURI, "audio/aac"));
            }
            else if(file.endsWith(".m3u") || file.endsWith(".pls") || file.endsWith(".sfv"))
            {
                //it is a playlist
                dirs.add(new PlayList(anchor.first, anchor.second.normalize(), albumImgURI));
            }
        }
		
        Log.d(TAG, "Loaded " + dirs.size() + " directories and " + music.size() + " tracks from " + url);
        return new Pair<List<Dir>, List<MediaInfo>>(dirs, music);
    }

    private boolean isParent(Pair<String, URI> anchor) {
        return anchor.first.compareToIgnoreCase("Parent Directory") == 0
          || anchor.first.compareToIgnoreCase("../") == 0
          || anchor.second.equals(url);
    }

    private static MediaInfo buildAudioMediaInfo(String title, URI url, List<URI> albumImgURI, String mimeType)
    {
        return buildAudioMediaInfo(pretty(title), url, mimeType, albumImgURI, MediaInfo.STREAM_TYPE_BUFFERED);
    }

    private static String pretty(String title) {
        int endIndex = title.lastIndexOf('.');
        return endIndex > 0 ? title.substring(0, endIndex) : title;
    }

    public static MediaInfo buildAudioMediaInfo(String title, URI url, String mimeType, List<URI> albumImgURIs, int streamType)
    {
        MediaMetadata audioMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK);

        audioMetadata.putString(MediaMetadata.KEY_TITLE, title);
		if(albumImgURIs != null && !albumImgURIs.isEmpty())
		for(URI albumImgURI : albumImgURIs)
			audioMetadata.addImage(new WebImage(Uri.parse(albumImgURI.toString())));
			
        return new MediaInfo.Builder(url.toString())
                .setStreamType(streamType)
                .setContentType(mimeType)
                .setMetadata(audioMetadata)
                .build();
    }

    private List<Pair<String, URI>> parse()
    {
        InputStream is = null;
        try {
            Log.d(TAG, "GET " + url);
            URLConnection urlConnection = url.toURL().openConnection();
            is = new BufferedInputStream(urlConnection.getInputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    urlConnection.getInputStream(), DEFAULT_CHARSET), 1024);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return extractAnchor(sb.toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse url " + url, e);
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

    private List<Pair<String, URI>> extractAnchor(String sb)
    {
        ArrayList<Pair<String, URI>> anchors = new ArrayList<>();
        Pattern pAnchor = Pattern.compile("<a href=\"(.+?)\">(.+?)</a>");
        Pattern pHtmlEsc = Pattern.compile("&(.+?);");
        Matcher m = pAnchor.matcher(sb);
        while(m.find())
        try
        {
            String href = m.group(1);
            String name = m.group(2);
            if(name.endsWith("..&gt;")) //name is truncated
            {
                name = URLDecoder.decode(href, "UTF-8");
                Log.d(TAG, "Using href " + href + " as name " + name);
            }
            anchors.add(new Pair<String, URI>(unescapeHtml(name, pHtmlEsc), url.resolve(href)));
        }
        catch(Exception ex)
        {
            Log.e(TAG, "Failed to parse anchor ", ex);
        }
        Log.d(TAG, "Found " + anchors.size() + " links in " + url);
        return anchors;
    }

    private static String unescapeHtml(String name, Pattern p) {

		boolean matched = false;
        Matcher m = p.matcher(name);
        StringBuffer sb = new StringBuffer(name.length());
        while(m.find())
        {
			matched = true;
            String val = m.group(1);
            m.appendReplacement(sb, unescapeChar(val));
        }
		if(matched)
		{
			m.appendTail(sb);
			return sb.toString();
		}
		return name;	
    }

    private static String unescapeChar(String val) {
        if(val.equals("amp")) return "&";
        else if(val.equals("lt")) return "<";
        else if(val.equals("gt")) return ">";
        else if(val.equals("quot")) return "\"";

        if(val.startsWith("#")) {
            val = val.substring(1);
            int radix = 10;
            if (val.startsWith("x") || val.startsWith("X")) {
                radix = 16;
                val = val.substring(1);
            }

            try {
                return String.valueOf((char) Integer.parseInt(val, radix));
            } catch (NumberFormatException e) {
            }
        }
        return val;
    }
}
