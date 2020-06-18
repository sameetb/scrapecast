package org.sb.scrapecast.scraper;

import java.net.URI;
import java.util.List;

/**
 * Created by sam on 27-12-2017.
 */
public class PlayList extends Dir {
	final List<URI> imageUri;
    public PlayList(String title, URI uri, List<URI> imageUri) {
        super(title, uri);
		this.imageUri = imageUri;
    }
}
