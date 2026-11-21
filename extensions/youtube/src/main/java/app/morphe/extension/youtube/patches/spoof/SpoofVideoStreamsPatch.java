package app.morphe.extension.youtube.patches.spoof;

import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.shared.spoof.ClientType;
import app.morphe.extension.youtube.patches.DisableVideoCodecsPatch;
import app.morphe.extension.youtube.patches.DisableVideoCodecsPatch.VideoCodec;
import app.morphe.extension.youtube.settings.Settings;

@SuppressWarnings("unused")
public class SpoofVideoStreamsPatch {

    public static final class SpoofClientAv1Availability implements Setting.Availability {
        @Override
        public boolean isAvailable() {
            ClientType client = Settings.SPOOF_VIDEO_STREAMS_CLIENT_TYPE.get();
            return Settings.SPOOF_VIDEO_STREAMS_CLIENT_TYPE.isAvailable()
                    && (client == ClientType.ANDROID_VR_DASH || client == ClientType.ANDROID_VR_SABR
                    || client == ClientType.ANDROID_VR_EXPERIMENTAL
                    || client == ClientType.VISIONOS_1_02 || client == ClientType.VISIONOS_1_06);
        }

        @Override
        public List<Setting<?>> getParentSettings() {
            return List.of(Settings.SPOOF_VIDEO_STREAMS_CLIENT_TYPE);
        }
    }

    private static ClientType mapToAv1Client(ClientType selected) {
        if (selected == ClientType.ANDROID_VR_DASH) {
            return ClientType.ANDROID_XR_DASH;
        }
        if (selected == ClientType.ANDROID_VR_SABR) {
            return ClientType.ANDROID_XR_SABR;
        }
        if (selected == ClientType.ANDROID_VR_EXPERIMENTAL) {
            return ClientType.ANDROID_XR_EXPERIMENTAL;
        }
        if (selected == ClientType.VISIONOS_1_02) {
            return ClientType.VISIONOS_1_03;
        }
        if (selected == ClientType.VISIONOS_1_06) {
            return ClientType.VISIONOS_1_06_AV1;
        }
        if (selected == ClientType.TV_SABR
                || selected == ClientType.TV_SIMPLY
                || selected == ClientType.MWEB
                || selected == ClientType.ANDROID_XR_DASH
                || selected == ClientType.ANDROID_XR_SABR
                || selected == ClientType.ANDROID_XR_EXPERIMENTAL
                || selected == ClientType.VISIONOS_1_03
                || selected == ClientType.VISIONOS_1_06_AV1) {
            return selected;
        }
        // ANDROID_CREATOR and other non-AV1 clients do not serve AV1 streams.
        return ClientType.TV_SABR;
    }

    /**
     * Injection point.
     */
    public static void setClientOrderToUse() {
        ClientType client = Settings.SPOOF_VIDEO_STREAMS_CLIENT_TYPE.get();
        final VideoCodec codec = DisableVideoCodecsPatch.getVideoCodec();

        if (codec == VideoCodec.FORCE_AV1) {
            ClientType preferred = mapToAv1Client(client);
            List<ClientType> av1Clients = new ArrayList<>();
            av1Clients.add(ClientType.TV_SABR);
            av1Clients.add(ClientType.VISIONOS_1_06_AV1);
            av1Clients.add(ClientType.ANDROID_XR_EXPERIMENTAL);
            app.morphe.extension.shared.spoof.SpoofVideoStreamsPatch.setClientsToUse(
                    av1Clients, preferred);
            return;
        }

        if (codec == VideoCodec.PREFER_AV1) {
            ClientType preferred = mapToAv1Client(client);
            List<ClientType> fallbackClients = new ArrayList<>();
            fallbackClients.add(ClientType.TV_SABR);
            fallbackClients.add(ClientType.VISIONOS_1_06_AV1);
            if (client != preferred && !fallbackClients.contains(client)) {
                fallbackClients.add(client);
            }
            if (!fallbackClients.contains(ClientType.ANDROID_CREATOR)) {
                fallbackClients.add(ClientType.ANDROID_CREATOR);
            }
            app.morphe.extension.shared.spoof.SpoofVideoStreamsPatch.setClientsToUse(
                    fallbackClients, preferred);
            return;
        }

        // Use the Android XR or visionOS client that has AV1 if user settings allow it.
        // AVC cannot be forced with these clients because they use VP9 and AV1.
        // VP9 blocks AV1, so the AV1 clients are not used.
        if (Settings.SPOOF_VIDEO_STREAMS_AV1.get() && codec == VideoCodec.DEFAULT) {
            client = mapToAv1Client(client);
        }

        List<ClientType> availableClients = List.of(
                ClientType.TV_SABR,
                ClientType.ANDROID_CREATOR
                // If not signed in to Android VR, there may be playback issues.
                // Only use it if the user has selected it.
                // ClientType.ANDROID_VR_DASH
        );
        app.morphe.extension.shared.spoof.SpoofVideoStreamsPatch.setClientsToUse(
                availableClients, client);
    }
}