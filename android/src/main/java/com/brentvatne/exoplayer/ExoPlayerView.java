package com.brentvatne.exoplayer;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.media3.common.AdViewProvider;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.Cue;
import androidx.media3.common.util.Assertions;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.SubtitleView;

import android.util.TypedValue;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.media3.ui.CaptionStyleCompat;
import android.graphics.Color;

import com.brentvatne.common.api.ResizeMode;
import com.brentvatne.common.api.SubtitleStyle;
import com.brentvatne.common.api.ViewType;
import com.brentvatne.common.toolbox.DebugLog;
import com.google.common.collect.ImmutableList;

import java.util.List;

@SuppressLint("ViewConstructor")
public final class ExoPlayerView extends FrameLayout implements AdViewProvider {
    private final static String TAG = "ExoPlayerView";
    private View surfaceView;
    private final View shutterView;
    private final SubtitleView subtitleLayout;
    private final AspectRatioFrameLayout layout;
    private final ComponentListener componentListener;
    private ExoPlayer player;
    private final Context context;
    private final ViewGroup.LayoutParams layoutParams;
    private final FrameLayout adOverlayFrameLayout;

    private @ViewType.ViewType int viewType = ViewType.VIEW_TYPE_SURFACE;
    private boolean hideShutterView = false;

    private SubtitleStyle localStyle = new SubtitleStyle();

    public ExoPlayerView(Context context) {
        super(context, null, 0);

        this.context = context;

        layoutParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);

        componentListener = new ComponentListener();

        FrameLayout.LayoutParams aspectRatioParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        aspectRatioParams.gravity = Gravity.CENTER;
        layout = new AspectRatioFrameLayout(context);
        layout.setLayoutParams(aspectRatioParams);

        shutterView = new View(getContext());
        shutterView.setLayoutParams(layoutParams);
        shutterView.setBackgroundColor(ContextCompat.getColor(context, android.R.color.black));

        subtitleLayout = new SubtitleView(context);
        subtitleLayout.setLayoutParams(layoutParams);
        subtitleLayout.setUserDefaultStyle();
        subtitleLayout.setUserDefaultTextSize();

        updateSurfaceView(viewType);

        adOverlayFrameLayout = new FrameLayout(context);

        layout.addView(shutterView, 1, layoutParams);
        if (localStyle.getSubtitlesFollowVideo()) {
            layout.addView(subtitleLayout, layoutParams);
            layout.addView(adOverlayFrameLayout, layoutParams);
        }

        addViewInLayout(layout, 0, aspectRatioParams);
        if (!localStyle.getSubtitlesFollowVideo()) {
            addViewInLayout(subtitleLayout, 1, layoutParams);
        }
    }

    private void clearVideoView() {
        if (surfaceView instanceof TextureView) {
            player.clearVideoTextureView((TextureView) surfaceView);
        } else if (surfaceView instanceof SurfaceView) {
            player.clearVideoSurfaceView((SurfaceView) surfaceView);
        }
    }

    private void setVideoView() {
        if (surfaceView instanceof TextureView) {
            player.setVideoTextureView((TextureView) surfaceView);
        } else if (surfaceView instanceof SurfaceView) {
            player.setVideoSurfaceView((SurfaceView) surfaceView);
        }
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public void setSubtitleStyle(SubtitleStyle style) {
        // ensure we reset subtitle style before reapplying it
        subtitleLayout.setUserDefaultStyle();
        subtitleLayout.setUserDefaultTextSize();
        CaptionStyleCompat captionStyle = new CaptionStyleCompat(
                Color.WHITE,    // Subtitle text color
                Color.TRANSPARENT,    // Background color (change this to any color)
                Color.TRANSPARENT, // Window color (transparent to remove window box)
                CaptionStyleCompat.EDGE_TYPE_OUTLINE, // Edge type for better visibility
                Color.BLACK,    // Edge color
                null            // Custom Typeface (null for default)
            );
        subtitleLayout.setStyle(captionStyle);

        if (style.getFontSize() > 0) {
            subtitleLayout.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, style.getFontSize());
        }
        subtitleLayout.setPadding(style.getPaddingLeft(), style.getPaddingTop(), style.getPaddingRight(), style.getPaddingBottom());
        if (style.getOpacity() != 0) {
            subtitleLayout.setAlpha(style.getOpacity());
            subtitleLayout.setVisibility(View.VISIBLE);
        } else {
            subtitleLayout.setVisibility(View.GONE);
        }
        if (localStyle.getSubtitlesFollowVideo() != style.getSubtitlesFollowVideo()) {
            // No need to manipulate layout if value didn't change
            if (style.getSubtitlesFollowVideo()) {
                removeViewInLayout(subtitleLayout);
                layout.addView(subtitleLayout, layoutParams);
            } else {
                layout.removeViewInLayout(subtitleLayout);
                addViewInLayout(subtitleLayout, 1, layoutParams, false);
            }
            requestLayout();
        }
        localStyle = style;
    }

    public void setShutterColor(Integer color) {
        shutterView.setBackgroundColor(color);
    }

    public void updateSurfaceView(@ViewType.ViewType int viewType) {
        this.viewType = viewType;
        boolean viewNeedRefresh = false;
        if (viewType == ViewType.VIEW_TYPE_SURFACE || viewType == ViewType.VIEW_TYPE_SURFACE_SECURE) {
            if (!(surfaceView instanceof SurfaceView)) {
                surfaceView = new SurfaceView(context);
                viewNeedRefresh = true;
            }
            ((SurfaceView)surfaceView).setSecure(viewType == ViewType.VIEW_TYPE_SURFACE_SECURE);
        } else if (viewType == ViewType.VIEW_TYPE_TEXTURE) {
            if (!(surfaceView instanceof TextureView)) {
                surfaceView = new TextureView(context);
                viewNeedRefresh = true;
            }
            // Support opacity properly:
            ((TextureView) surfaceView).setOpaque(false);
        } else {
            DebugLog.wtf(TAG, "wtf is this texture " + viewType);
        }
        if (viewNeedRefresh) {
            surfaceView.setLayoutParams(layoutParams);

            if (layout.getChildAt(0) != null) {
                layout.removeViewAt(0);
            }
            layout.addView(surfaceView, 0, layoutParams);

            if (this.player != null) {
                setVideoView();
            }
        }
    }

    private void hideShutterView() {
        shutterView.setVisibility(INVISIBLE);
        surfaceView.setAlpha(1);
    }

    private void showShutterView() {
        shutterView.setVisibility(VISIBLE);
        surfaceView.setAlpha(0);
    }

    private void updateShutterViewVisibility() {
        if (this.hideShutterView) {
            hideShutterView();
        } else {
            showShutterView();
        }
    }

    @Override
    public void requestLayout() {
        super.requestLayout();
        post(measureAndLayout);
    }

    // AdsLoader.AdViewProvider implementation.

    @Override
    public ViewGroup getAdViewGroup() {
        return Assertions.checkNotNull(adOverlayFrameLayout, "exo_ad_overlay must be present for ad playback");
    }

    /**
     * Set the {@link ExoPlayer} to use. The {@link ExoPlayer#addListener} method of the
     * player will be called and previous
     * assignments are overridden.
     *
     * @param player The {@link ExoPlayer} to use.
     */
    public void setPlayer(ExoPlayer player) {
        if (this.player == player) {
            return;
        }
        if (this.player != null) {
            this.player.removeListener(componentListener);
            clearVideoView();
        }
        this.player = player;

        updateShutterViewVisibility();

        if (player != null) {
            setVideoView();
            player.addListener(componentListener);
        }
    }

    /**
     * Sets the resize mode which can be of value {@link ResizeMode.Mode}
     *
     * @param resizeMode The resize mode.
     */
    public void setResizeMode(@ResizeMode.Mode int resizeMode) {
        if (layout != null && layout.getResizeMode() != resizeMode) {
            layout.setResizeMode(resizeMode);
            post(measureAndLayout);
        }
    }

    public void setHideShutterView(boolean hideShutterView) {
        this.hideShutterView = hideShutterView;
        updateShutterViewVisibility();
    }

    private final Runnable measureAndLayout = () -> {
        measure(
                MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY));
        layout(getLeft(), getTop(), getRight(), getBottom());
    };

    private void updateForCurrentTrackSelections(Tracks tracks) {
        if (tracks == null) {
            return;
        }
        ImmutableList<Tracks.Group> groups = tracks.getGroups();
        for (Tracks.Group group: groups) {
            if (group.getType() == C.TRACK_TYPE_VIDEO && group.length > 0) {
                // get the first track of the group to identify aspect ratio
                Format format = group.getTrackFormat(0);

                // There are weird cases when video height and width did not change with rotation so we need change aspect ration to fix it
                switch (format.rotationDegrees) {
                    // update aspect ratio !
                    case 90:
                    case 270:
                        layout.setVideoAspectRatio(format.width == 0 ? 1 : (format.height * format.pixelWidthHeightRatio) / format.width);
                    default:
                        layout.setVideoAspectRatio(format.height == 0 ? 1 : (format.width * format.pixelWidthHeightRatio) / format.height);
                }
                return;
            }
        }
        // no video tracks, in that case refresh shutterView visibility
        shutterView.setVisibility(this.hideShutterView ? View.INVISIBLE : View.VISIBLE);
    }

    public void invalidateAspectRatio() {
        // Resetting aspect ratio will force layout refresh on next video size changed
        layout.invalidateAspectRatio();
    }

    private final class ComponentListener implements Player.Listener {

        @Override
        public void onCues(@NonNull List<Cue> cues) {
            subtitleLayout.setCues(cues);
        }

        @Override
        public void onVideoSizeChanged(VideoSize videoSize) {
            boolean isInitialRatio = layout.getVideoAspectRatio() == 0;
            if (videoSize.height == 0 || videoSize.width == 0) {
                // When changing video track we receive an ghost state with height / width = 0
                // No need to resize the view in that case
                return;
            }
            layout.setVideoAspectRatio((videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height);

            // React native workaround for measuring and layout on initial load.
            if (isInitialRatio) {
                post(measureAndLayout);
            }
        }

        @Override
        public void onRenderedFirstFrame() {
            hideShutterView();
        }

        @Override
        public void onTracksChanged(@NonNull Tracks tracks) {
            updateForCurrentTrackSelections(tracks);
        }
    }
}
