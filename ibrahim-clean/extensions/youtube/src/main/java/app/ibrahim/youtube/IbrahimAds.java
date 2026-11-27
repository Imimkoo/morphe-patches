package app.ibrahim.youtube;

/** Minimal extension entry points for the standalone video-ad blocker patch. */
public final class IbrahimAds {
    private IbrahimAds() {}

    /** Called from YouTube's player-byte ad layout and scheduling hooks. */
    public static boolean hideVideoAds() {
        return true;
    }
}
