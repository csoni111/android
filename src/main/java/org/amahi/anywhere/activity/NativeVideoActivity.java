/*
 * Copyright (c) 2014 Amahi
 *
 * This file is part of Amahi.
 *
 * Amahi is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Amahi is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Amahi. If not, see <http ://www.gnu.org/licenses/>.
 */

package org.amahi.anywhere.activity;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.VideoView;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;

import org.amahi.anywhere.AmahiApplication;
import org.amahi.anywhere.R;
import org.amahi.anywhere.server.client.ServerClient;
import org.amahi.anywhere.server.model.ServerFile;
import org.amahi.anywhere.server.model.ServerShare;
import org.amahi.anywhere.util.FullScreenHelper;
import org.amahi.anywhere.util.Intents;
import org.amahi.anywhere.view.MediaControls;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

/**
 * Native Video Player activity. Shows video, supports basic operations such as pausing, resuming.
 * The playback itself is done via {@link org.amahi.anywhere.service}.
 * Backed up by {@link android.media.MediaPlayer}.
 */
public class NativeVideoActivity extends AppCompatActivity implements
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener {

    private static final Set<String> SUPPORTED_FORMATS;
    private static final String VIDEO_POSITION = "video_position";

    static {
        SUPPORTED_FORMATS = new HashSet<>(Arrays.asList(
                "video/3gp",
                "video/mp4",
                "video/ts",
                "video/webm",
                "video/x-matroska"
        ));
    }

    public static boolean supports(String mime_type) {
        return SUPPORTED_FORMATS.contains(mime_type);
    }

    @Inject
    ServerClient serverClient;

    private MediaControls videoControls;
    private VideoView videoView;

    private CastContext mCastContext;
	private CastSession mCastSession;
	private PlaybackLocation mLocation;
	private SessionManagerListener<CastSession> mSessionManagerListener;

	public enum PlaybackLocation {
		LOCAL,
		REMOTE
	}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_native_video);

        // NOTE-cpg: used for debugging - to visually display when the native player is being used
        // Toast.makeText(this, "NATIVE PLAYER", Toast.LENGTH_SHORT).show();

        setUpInjections();

        setUpHomeNavigation();

        setUpCast();

        setUpVideo();

        setUpFullScreen();
    }

    private void setUpInjections() {
        AmahiApplication.from(this).inject(this);
    }

    private void setUpHomeNavigation() {
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setIcon(R.drawable.ic_launcher);
    }

    private void setUpCast() {
        mCastContext = CastContext.getSharedInstance(this);
    }

    private void setUpVideo() {
        setUpVideoTitle();
        setUpVideoView();
    }

    private ServerShare getVideoShare() {
        return getIntent().getParcelableExtra(Intents.Extras.SERVER_SHARE);
    }

    private ServerFile getVideoFile() {
        return getIntent().getParcelableExtra(Intents.Extras.SERVER_FILE);
    }

    private Uri getVideoUri() {
        return serverClient.getFileUri(getVideoShare(), getVideoFile());
    }

    private FrameLayout getVideoMainFrame() {
        return (FrameLayout) findViewById(R.id.video_main_frame);
    }

    private View getControlsContainer() {
        return findViewById(R.id.container_controls);
    }

    private FrameLayout getVideoSurfaceFrame() {
        return (FrameLayout) findViewById(R.id.video_surface_frame);
    }

    private ProgressBar getProgressBar() {
        return (ProgressBar) findViewById(android.R.id.progress);
    }

    private void setUpVideoView() {
        setUpVideoControls();
        videoView = (VideoView) findViewById(R.id.video_view);
        videoView.setOnPreparedListener(this);
        videoView.setOnCompletionListener(this);
        videoView.setVideoURI(getVideoUri());
        videoView.setMediaController(videoControls);
        videoView.start();
    }

    private boolean areVideoControlsAvailable() {
        return videoControls != null;
    }

    private void setUpVideoControls() {
        if (!areVideoControlsAvailable()) {
            videoControls = new MediaControls(this);
        }
    }

    private void setUpVideoTitle() {
        getSupportActionBar().setTitle(getVideoFile().getName());
    }

    private void setUpFullScreen() {
        final FullScreenHelper fullScreen = new FullScreenHelper(getSupportActionBar(), getVideoMainFrame());
        fullScreen.init();
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.action_bar_native_player, menu);
		CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), menu,
				R.id.media_route_menu_item);
		return true;
	}

	@Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case android.R.id.home:
                finish();
                return true;

            default:
                return super.onOptionsItemSelected(menuItem);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

    }

    @Override
    public void onPause() {
        super.onPause();

        videoControls.hide();

        if (!isChangingConfigurations()) {
            videoView.pause();
        }

        if (isFinishing()) {
            videoView.stopPlayback();
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        getProgressBar().setVisibility(View.GONE);
        getVideoMainFrame().setVisibility(View.VISIBLE);
        videoControls.setAnchorView(getControlsContainer());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(VIDEO_POSITION, videoView.getCurrentPosition());
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        int videoPosition = savedInstanceState.getInt(VIDEO_POSITION, 0);
        if (videoPosition > 0) {
            videoView.seekTo(videoPosition);
        }
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        finish();
    }

	private void setupCastListener() {
		mSessionManagerListener = new SessionManagerListener<CastSession>() {

			@Override
			public void onSessionEnded(CastSession session, int error) {
				onApplicationDisconnected();
			}

			@Override
			public void onSessionResumed(CastSession session, boolean wasSuspended) {
				onApplicationConnected(session);
			}

			@Override
			public void onSessionResumeFailed(CastSession session, int error) {
				onApplicationDisconnected();
			}

			@Override
			public void onSessionStarted(CastSession session, String sessionId) {
				onApplicationConnected(session);
			}

			@Override
			public void onSessionStartFailed(CastSession session, int error) {
				onApplicationDisconnected();
			}

			@Override
			public void onSessionStarting(CastSession session) {}

			@Override
			public void onSessionEnding(CastSession session) {}

			@Override
			public void onSessionResuming(CastSession session, String sessionId) {}

			@Override
			public void onSessionSuspended(CastSession session, int reason) {}

			private void onApplicationConnected(CastSession castSession) {
				mCastSession = castSession;
				if (videoView.isPlaying()) {
					videoView.pause();
					loadRemoteMedia(videoView.getCurrentPosition(), true);
					finish();
					return;
				} else {
					updatePlaybackLocation(PlaybackLocation.REMOTE);
				}
//				updatePlayButton(mPlaybackState);
				supportInvalidateOptionsMenu();
			}

			private void onApplicationDisconnected() {
				updatePlaybackLocation(PlaybackLocation.LOCAL);
				mLocation = PlaybackLocation.LOCAL;
//				updatePlayButton(mPlaybackState);
				supportInvalidateOptionsMenu();
			}
		};
	}

	private void updatePlaybackLocation(PlaybackLocation location) {
		mLocation = location;
	}

	private void loadRemoteMedia(int position, boolean autoPlay) {
		if (mCastSession == null) {
			return;
		}
		final RemoteMediaClient remoteMediaClient = mCastSession.getRemoteMediaClient();
		if (remoteMediaClient == null) {
			return;
		}
		remoteMediaClient.addListener(new RemoteMediaClient.Listener() {
			@Override
			public void onStatusUpdated() {
//				Intent intent = new Intent(NativeVideoActivity.this, ExpandedControlsActivity.class);
//				startActivity(intent);
				remoteMediaClient.removeListener(this);
			}

			@Override
			public void onMetadataUpdated() {
			}

			@Override
			public void onQueueStatusUpdated() {
			}

			@Override
			public void onPreloadStatusUpdated() {
			}

			@Override
			public void onSendingRemoteMediaRequest() {
			}

			@Override
			public void onAdBreakStatusUpdated() {

			}
		});
		remoteMediaClient.load(buildMediaInfo(), autoPlay, position);
	}

	private MediaInfo buildMediaInfo() {
		MediaMetadata movieMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);

//		movieMetadata.putString(MediaMetadata.KEY_SUBTITLE, getVideoFile().getSize());
		movieMetadata.putString(MediaMetadata.KEY_TITLE, getVideoFile().getName());
//		movieMetadata.addImage(new WebImage(Uri.parse(mSelectedMedia.getImage(0))));
//		movieMetadata.addImage(new WebImage(Uri.parse(mSelectedMedia.getImage(1))));

		return new MediaInfo.Builder(getVideoUri().toString())
				.setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
				.setContentType(getVideoFile().getMime())
				.setMetadata(movieMetadata)
				.setStreamDuration(videoView.getDuration())
				.build();
	}
}
