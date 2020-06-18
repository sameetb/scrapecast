package org.sb.scrapecast.scraper;

import java.net.URI;

/**
 * Created by sam on 24-12-2017.
 */
public class Dir {
    final String title;
    final URI uri;

    public Dir(String title, URI uri) {
        this.title = title.endsWith("/") ? title.substring(0, title.length()-1):title;
        this.uri = uri;
    }
}
