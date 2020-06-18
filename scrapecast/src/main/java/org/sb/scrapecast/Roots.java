package org.sb.scrapecast;

import android.content.SharedPreferences;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by sam on 23-12-2017.
 */
public class Roots
{
    public enum Name {ROOTS, STREAMS};

    private static final Map<Name, Roots> singleton = new ConcurrentHashMap<Name, Roots>();

    private final SharedPreferences prefs;

    public Roots(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    static synchronized Roots init(Name prefNm, SharedPreferences prefs)
    {
        Roots roots = singleton.get(prefNm);
        if(roots == null) {
            roots = new Roots(prefs);
            initRoots(roots, "/" + prefNm.name() + ".properties");
            singleton.put(prefNm, roots);
        }
        return roots;
    }

    public static Roots get(Name prefNm)
    {
        return singleton.get(prefNm);
    }

    public Roots add(String title, URI uri)
    {
        String uriStr = uri.toString();
        if(uriStr.length() == 0)
            return remove(title);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(title, uriStr);
        editor.commit();
        return this;
    }

    public Roots remove(String title)
    {
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(title);
        editor.commit();
        return this;
    }

    public <T> List<T> all(Maker<T> mk)
    {
        ArrayList<T> dirs = new ArrayList<>();
        for(Map.Entry<String, ?> e : prefs.getAll().entrySet())
        {
            if(e.getValue() instanceof  String)
            {
                dirs.add(mk.make(e.getKey(), URI.create(String.valueOf(e.getValue()))));
            }
        }
        return  dirs;
    }

    public interface Maker<T>
    {
        T make(String name, URI uri);
    }

    private static void initRoots(Roots roots, String resouceName) {
        try(InputStream is = Roots.class.getResourceAsStream(resouceName)) {
            Properties props = new Properties();
            if(is != null)
                props.load(is);
            for(String prop : props.stringPropertyNames())
                roots.add(prop, URI.create(props.getProperty(prop)));
            Log.i(Roots.class.getName(), "Loaded " + props.size() + " from " + resouceName);
        }
        catch (IOException io)
        {
            Log.w(Roots.class.getName(), "Failed to read properties from" + resouceName, io);
        }
    }

}
