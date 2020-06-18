package org.sb.scrapecast;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.MediaRouteButton;
import android.support.v7.widget.PopupMenu;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.CastState;
import com.google.android.gms.cast.framework.CastStateListener;
import com.google.android.gms.cast.framework.IntroductoryOverlay;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;

import org.sb.scrapecast.player.CastPreference;
import org.sb.scrapecast.queue.QueueListViewActivity;

import java.net.URI;
import java.net.URISyntaxException;

public class MainActivity extends AppCompatActivity {

    private MediaRouteButton mMediaRouteButton;

    private MenuItem mediaRouteMenuItem;
    private MenuItem mQueueMenuItem;

    private CastContext mCastContext;

    private CastSession mCastSession;
    private SessionManager mSessionManager;

    private IntroductoryOverlay mIntroductoryOverlay;
    private CastStateListener mCastStateListener;

    private final SessionManagerListener<CastSession> mSessionManagerListener = new SessionManagerListener<CastSession>() {
        @Override
        public void onSessionStarting(CastSession session) {

        }

        @Override
        public void onSessionStarted(CastSession session, String sessionId) {
            mCastSession = session;
            invalidateOptionsMenu();
        }

        @Override
        public void onSessionStartFailed(CastSession session, int i) {

        }

        @Override
        public void onSessionEnding(CastSession session) {

        }

        @Override
        public void onSessionResumed(CastSession session, boolean wasSuspended) {
            mCastSession = session;
            invalidateOptionsMenu();
        }

        @Override
        public void onSessionResumeFailed(CastSession session, int i) {

        }

        @Override
        public void onSessionSuspended(CastSession session, int i) {

        }

        @Override
        public void onSessionEnded(CastSession session, int error) {
            if (session == mCastSession) {
                mCastSession = null;
            }
            invalidateOptionsMenu();
        }

        @Override
        public void onSessionResuming(CastSession session, String s) {

        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FloatingActionButton fabAddRootDir = (FloatingActionButton) findViewById(R.id.fabAddRootDir);
        fabAddRootDir.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
               showPopupAddSite(view);
            }
        });

        mCastStateListener = new CastStateListener() {
            @Override
            public void onCastStateChanged(int newState) {
                if (newState != CastState.NO_DEVICES_AVAILABLE) {
                    showIntroductoryOverlay();
                }
            }
        };

        mSessionManager = CastContext.getSharedInstance(this).getSessionManager();

        mMediaRouteButton = (MediaRouteButton) findViewById(R.id.media_route_button);
        CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), mMediaRouteButton);

        mCastContext = CastContext.getSharedInstance(this);

        if(Roots.get(Roots.Name.ROOTS) == null) {
            Roots.init(Roots.Name.ROOTS, getSharedPreferences(getLocalClassName() + ".Roots", MODE_PRIVATE));
        }
        if(Roots.get(Roots.Name.STREAMS) == null) {
            Roots.init(Roots.Name.STREAMS, getSharedPreferences(getLocalClassName() + ".Streams", MODE_PRIVATE));
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        mediaRouteMenuItem = CastButtonFactory.setUpMediaRouteButton(getApplicationContext(),
                menu,
                R.id.media_route_menu_item);
        mQueueMenuItem = menu.findItem(R.id.action_show_queue);
        if (mQueueMenuItem != null) {
            mQueueMenuItem.setVisible(
                    (mCastSession != null) && mCastSession.isConnected());
        }
        showIntroductoryOverlay();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        if (item.getItemId() == R.id.action_settings) {
            intent = new Intent(MainActivity.this, CastPreference.class);
            startActivity(intent);
        } else if (item.getItemId() == R.id.action_show_queue) {
            intent = new Intent(MainActivity.this, QueueListViewActivity.class);
            startActivity(intent);
        }
        return true;
    }

    @Override
    protected void onResume() {
        mCastContext.addCastStateListener(mCastStateListener);
        mCastSession = mSessionManager.getCurrentCastSession();
        mSessionManager.addSessionManagerListener(mSessionManagerListener, CastSession.class);
        if (mCastSession == null) {
            mCastSession = CastContext.getSharedInstance(this).getSessionManager()
                    .getCurrentCastSession();
        }
        if (mQueueMenuItem != null) {
            mQueueMenuItem.setVisible(
                    (mCastSession != null) && mCastSession.isConnected());
        }
        super.onResume();
    }
    @Override
    protected void onPause() {
        super.onPause();
        mCastContext.removeCastStateListener(mCastStateListener);
        mSessionManager.removeSessionManagerListener(mSessionManagerListener, CastSession.class);
        mCastSession = null;
    }

    private void showIntroductoryOverlay() {
        if (mIntroductoryOverlay != null) {
            mIntroductoryOverlay.remove();
        }
        if ((mediaRouteMenuItem != null) && mediaRouteMenuItem.isVisible()) {
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    mIntroductoryOverlay = new IntroductoryOverlay.Builder(
                            MainActivity.this, mediaRouteMenuItem)
                            .setTitleText(R.string.introducing_cast)
                            .setOverlayColor(R.color.colorPrimary)
                            .setSingleTime()
                            .setOnOverlayDismissedListener(
                                    new IntroductoryOverlay.OnOverlayDismissedListener() {
                                        @Override
                                        public void onOverlayDismissed() {
                                            mIntroductoryOverlay = null;
                                        }
                                    })
                            .build();
                    mIntroductoryOverlay.show();
                }
            });
        }
    }

    private void showPopupAddSite(View view)
    {
        PopupMenu popup = new PopupMenu(this, view);
        popup.getMenuInflater().inflate(R.menu.popup_add_site, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {

            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.action_add_root_folder:
                        popupAddRoot(true);
                        break;
                    case R.id.action_add_streaming_url:
                        popupAddRoot(false);
                        break;
                    default:
                        return false;
                }
                return true;
            }
        });
        popup.show();
    }
    private void popupAddRoot(final boolean site)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Provide a URL to " + (site ? "scrape" : "stream"));
        View viewInflated = LayoutInflater.from(this).inflate(R.layout.add_root, null);
        final EditText inputTitle = (EditText) viewInflated.findViewById(R.id.inputTitle);
        final EditText inputUri = (EditText) viewInflated.findViewById(R.id.inputUri);
        builder.setView(viewInflated);

        // Set up the buttons
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                try {
                    String title = inputTitle.getText().toString();
                    String uriStr = inputUri.getText().toString();
                    if(uriStr.length() > 0 && !uriStr.endsWith("/")) uriStr += '/';
                    if (title.length() != 0)
                        addSite(title, new URI(uriStr), site);
                    else
                        Utils.showErrorDialog(MainActivity.this, "Please provide a non empty title");
                } catch (URISyntaxException ue) {
                    Utils.showToast(MainActivity.this, "Not a valid URL: " + inputUri.getText().toString());
                    Utils.showErrorDialog(MainActivity.this, "Please provide a valid URL");
                }

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

    private void addSite(String title, URI uri, boolean site) {
        Log.v(getPackageName(), "Adding " + (site ? "root" : "streaming") +  " URL " + uri);
        Roots.get(site ? Roots.Name.ROOTS : Roots.Name.STREAMS).add(title, uri);

        Log.d(getPackageName(), "Refreshing activity");
        finish();

        startActivity(getIntent());
    }

    @Override
    public void onBackPressed() {
        Log.d(getPackageName(), "Byeee");
		super.onBackPressed();
		moveTaskToBack(true);
    }
}
