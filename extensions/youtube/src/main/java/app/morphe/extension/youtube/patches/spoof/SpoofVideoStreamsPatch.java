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

    private static void addAv1Clients(List<ClientType> clients, ClientType preferred) {
        for (ClientType candidate : List.of(
                ClientType.ANDROID_XR_SABR,
                ClientType.ANDROID_XR_DASH,
                ClientType.VISIONOS_1_06_AV1,
                ClientType.VISIONOS_1_03)) {
            if (candidate != preferred && !clients.contains(candidate)) {
                clients.add(candidate);
            }
        }
    }

    private static void addFallbackClient(List<ClientType> clients, ClientType candidate, ClientType preferred) {
        if (candidate != preferred && !clients.contains(candidate)) {
            clients.add(candidate);
        }
    }

    /**
     * Injection point.
     */
    public static void setClientOrderToUse() {
        ClientType selectedClient = Settings.SPOOF_VIDEO_STREAMS_CLIENT_TYPE.get();
        final VideoCodec codec = DisableVideoCodecsPatch.getVideoCodec();

        if (codec == VideoCodec.FORCE_AV1) {
            // Only AV1-capable clients are attempted. Client fallback is allowed, codec fallback is not.
            List<ClientType> av1Clients = new ArrayList<>();
            addAv1Clients(av1Clients, ClientType.ANDROID_XR_EXPERIMENTAL);
            app.morphe.extension.shared.spoof.SpoofVideoStreamsPatch.setClientsToUse(
                    av1Clients, ClientType.ANDROID_XR_EXPERIMENTAL);
            return;
        }

        if (codec == VideoCodec.PREFER_AV1) {
            // Try server profiles known to return AV1 before any ordinary fallback client.
            List<ClientType> clients = new ArrayList<>();
            addAv1Clients(clients, ClientType.ANDROID_XR_EXPERIMENTAL);

            // Preserve the user's selected client and normal fallback order after AV1-capable clients.
            addFallbackClient(clients, selectedClient, ClientType.ANDROID_XR_EXPERIMENTAL);
            addFallbackClient(clients, ClientType.TV_SABR, ClientType.ANDROID_XR_EXPERIMENTAL);
            addFallbackClient(clients, ClientType.ANDROID_CREATOR, ClientType.ANDROID_XR_EXPERIMENTAL);

            app.morphe.extension.shared.spoof.SpoofVideoStreamsPatch.setClientsToUse(
                    clients, ClientType.ANDROID_XR_EXPERIMENTAL);
            return;
        }

        ClientType client = selectedClient;

        // Preserve the existing opt-in AV1 client mapping for Default mode.
        if (Settings.SPOOF_VIDEO_STREAMS_AV1.get() && codec == VideoCodec.DEFAULT) {
            if (client == ClientType.ANDROID_VR_DASH) {
                client = ClientType.ANDROID_XR_DASH;
            } else if (client == ClientType.ANDROID_VR_SABR) {
                client = ClientType.ANDROID_XR_SABR;
            } else if (client == ClientType.ANDROID_VR_EXPERIMENTAL) {
                client = ClientType.ANDROID_XR_EXPERIMENTAL;
            } else if (client == ClientType.VISIONOS_1_02) {
                client = ClientType.VISIONOS_1_03;
            } else if (client == ClientType.VISIONOS_1_06) {
                client = ClientType.VISIONOS_1_06_AV1;
            }
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

