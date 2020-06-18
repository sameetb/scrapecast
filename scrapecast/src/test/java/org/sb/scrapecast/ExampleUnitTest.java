package org.sb.scrapecast;

import android.util.Log;
import android.util.Pair;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;

import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * To work on unit tests, switch the Test Artifact in the Build Variants view.
 */
public class ExampleUnitTest {
    private final URI url = URI.create("http://example.com:80/");
    private final String TAG = "tag";

    public void addition_isCorrect() throws Exception {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void testScrape()
    {
        //buildMediaInfo();
        System.out.println(unescapeHtml("K.C. &#x26; The Sunshine Band - &#68; Please Don't Go.mp3"));
    }

    public Pair<List<URI>, List<MediaInfo>> buildMediaInfo()
    {
        ArrayList<URI> dirs = new ArrayList<>();
        ArrayList<MediaInfo> music = new ArrayList<>();
        for(Pair<String, URI> anchor : parse())
        {
            String file = anchor.second.getPath();
            if(file.endsWith("/"))
            {
                dirs.add(anchor.second.normalize());
            }
            else if(file.endsWith("mp3") || file.endsWith("MP3"))
            {
                //it is a media file
                music.add(buildAudioMediaInfo(anchor.first, anchor.second, "audio/mpeg"));
            }

        }
        Log.d(TAG, "Loaded " + dirs.size() + " directories and " + music.size() + " tracks from " + url);
        return new Pair<List<URI>, List<MediaInfo>>(dirs, music);
    }
    private static MediaInfo buildAudioMediaInfo(String title, URI url, String mimeType)
    {
        MediaMetadata audioMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK);

        audioMetadata.putString(MediaMetadata.KEY_TITLE, title);

        return new MediaInfo.Builder(url.toString())
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(mimeType)
                .setMetadata(audioMetadata)
                .build();
    }

    private List<Pair<String, URI>> parse()
    {
        InputStream is = null;
        try {
            URLConnection urlConnection = url.toURL().openConnection();
            is = new BufferedInputStream(urlConnection.getInputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    urlConnection.getInputStream(), Charset.defaultCharset()), 1024);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return extractAnchor(sb.toString());
        } catch (Exception e) {
            System.out.println("Failed to parse url " + url); e.printStackTrace();
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
        Pattern p = Pattern.compile("<a href=\"(.+?)\">(.+?)</a>");
        Matcher m = p.matcher(sb);
        while(m.find())
            try
            {
                String href = m.group(1);
                String name = m.group(2);
                anchors.add(new Pair<String, URI>(name, url.resolve(href)));
            }
            catch(Exception ex)
            {
                System.out.println("Failed to parse anchor "); ex.printStackTrace();
            }
        System.out.println("Found " + anchors.size() + " links in " + url);
        return anchors;
    }

    private String unescapeHtml(String name) {

        Pattern p = Pattern.compile("&(.+?);");
        Matcher m = p.matcher(name);
        StringBuffer sb = new StringBuffer(name.length());
        while(m.find())
        {
            String val = m.group(1);
            m.appendReplacement(sb, mapEscape(val));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String mapEscape(String val) {
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

    @Test
    public void testResolve()
    {
        System.out.println(URI.create("http://www.google.ca").resolve("http://www.cbc.ca"));
        System.out.println(URI.create("http://www.google.ca/a/b/c.mp3").resolve("."));
        System.out.println(URI.create("http://www.google.ca/a/b/c.mp3").resolve(".."));
        System.out.println(URI.create("http://www.google.ca/a/b/c.mp3").resolve("../.."));
        System.out.println(URI.create("http://www.google.ca/a/b/c.mp3").resolve("/../"));
    }
}