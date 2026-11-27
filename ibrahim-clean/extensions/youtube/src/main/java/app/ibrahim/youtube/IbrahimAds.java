package app.ibrahim.youtube;

import android.view.View;
import android.view.ViewGroup;

/** Standalone ad-blocker entry points injected into YouTube's own UI and player. */
public final class IbrahimAds {
    private IbrahimAds() {}

    /** Called from YouTube's player-byte ad layout and scheduling hooks. */
    public static boolean hideVideoAds() {
        return true;
    }

    /** Enables removal of YouTube premium, player and overlay ad surfaces. */
    public static boolean hideGeneralAds() {
        return true;
    }

    /** Hide a view and collapse its layout space when YouTube exposes an ad-only view. */
    public static void hideView(View view) {
        if (view == null) {
            return;
        }
        view.setVisibility(View.GONE);
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null) {
            params.width = 0;
            params.height = 0;
            view.setLayoutParams(params);
        }
    }
}
