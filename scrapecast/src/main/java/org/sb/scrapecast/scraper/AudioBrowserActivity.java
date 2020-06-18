package org.sb.scrapecast.scraper;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;

import org.sb.scrapecast.MainActivity;
import org.sb.scrapecast.R;
import org.sb.scrapecast.player.CastPreference;
import org.sb.scrapecast.queue.QueueListViewActivity;

public class AudioBrowserActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_browser);
        if(getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        menu.findItem(R.id.action_settings).setVisible(false);
        CastButtonFactory.setUpMediaRouteButton(getApplicationContext(),
                menu, R.id.media_route_menu_item);
        menu.findItem(R.id.action_home).setVisible(true);
        menu.findItem(R.id.action_exit).setVisible(true);

        CastContext mCastContext = CastContext.getSharedInstance(this);
        CastSession mCastSession = mCastContext.getSessionManager().getCurrentCastSession();
        if(mCastSession == null || !mCastSession.isConnected())
            menu.findItem(R.id.action_show_queue).setVisible(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        switch(item.getItemId()) {
            case R.id.action_show_queue: {
                intent = new Intent(AudioBrowserActivity.this, QueueListViewActivity.class);
                startActivity(intent);
                break;
            }
            case R.id.action_home: {
                intent = new Intent(AudioBrowserActivity.this, MainActivity.class);
                startActivity(intent);
                break;
            }
            case R.id.action_exit: {
                intent = new Intent(AudioBrowserActivity.this, MainActivity.class);
                startActivity(intent);
                break;
            }
            case android.R.id.home:
                finish();
                break;
            default:
                return false;
        }
        return true;
    }
}
