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
                    && isAv1CapableClient(client);
        }

        @Override
        public List<Setting<?>> getParentSettings() {
            return List.of(Settings.SPOOF_VIDEO_STREAMS_CLIENT_TYPE);
        }
    }

    private static boolean isAv1CapableClient(ClientType client) {
        return client == ClientType.ANDROID_XR_SABR
                || client == ClientType.ANDROID_XR_DASH
                || client == ClientType.ANDROID_XR_EXPERIMENTAL
                || client == ClientType.VISIONOS_1_03
                || client == ClientType.VISIONOS_1_06_AV1
                || client == ClientType.TV_SABR
                || client == ClientType.TV_SABR_WII_U
                || client == ClientType.TV_SIMPLY;
    }

    static List<ClientType> av1ClientsInOrder(ClientType selected) {
        List<ClientType> clients = new ArrayList<>();
        // Prefer clients that do not require OAuth2 sign-in or a JS/PoToken setup first.
        ClientType[] order = {
                ClientType.VISIONOS_1_06_AV1,
                ClientType.VISIONOS_1_03,
                ClientType.TV_SABR,
                ClientType.TV_SIMPLY,
                ClientType.ANDROID_XR_EXPERIMENTAL,
                ClientType.ANDROID_XR_DASH,
                ClientType.ANDROID_XR_SABR
        };
        if (isAv1CapableClient(selected)) clients.add(selected);
        for (ClientType candidate : order) {
            if (candidate != selected && !clients.contains(candidate)) clients.add(candidate);
        }
        return clients;
    }

    /**
     * Injection point.
     */
    public static void setClientOrderToUse() {
        ClientType selected = Settings.SPOOF_VIDEO_STREAMS_CLIENT_TYPE.get();
        final VideoCodec codec = DisableVideoCodecsPatch.getVideoCodec();

        if (codec == VideoCodec.FORCE_AV1) {
            app.morphe.extension.shared.spoof.requests.StreamingDataRequest.setAdaptiveFormatValidator(
                    (formats, client) -> formats.stream().anyMatch(format -> format.getMimeType() != null
                            && format.getMimeType().toLowerCase(java.util.Locale.ROOT).contains("av01")));
            List<ClientType> av1Clients = av1ClientsInOrder(selected);
            ClientType preferred = av1Clients.remove(0);
            // No non-AV1 clients are included: AV1-only mode never falls back to VP9/AVC.
            app.morphe.extension.shared.spoof.SpoofVideoStreamsPatch.setClientsToUse(av1Clients, preferred);
            return;
        }

        if (codec == VideoCodec.PREFER_AV1) {
            app.morphe.extension.shared.spoof.requests.StreamingDataRequest.setAdaptiveFormatValidator(
                    (formats, client) -> !isAv1CapableClient(client) || formats.stream().anyMatch(format ->
                            format.getMimeType() != null
                                    && format.getMimeType().toLowerCase(java.util.Locale.ROOT).contains("av01")));
            List<ClientType> av1Clients = av1ClientsInOrder(selected);
            ClientType preferred = av1Clients.remove(0);
            List<ClientType> fallbackClients = new ArrayList<>(av1Clients);
            if (!isAv1CapableClient(selected)) fallbackClients.add(selected);
            // Keep established non-AV1 fallbacks after every AV1-capable client.
            for (ClientType fallback : List.of(ClientType.MWEB, ClientType.ANDROID_CREATOR)) {
                if (fallback != preferred && !fallbackClients.contains(fallback)) fallbackClients.add(fallback);
            }
            app.morphe.extension.shared.spoof.SpoofVideoStreamsPatch.setClientsToUse(fallbackClients, preferred);
            return;
        }

        app.morphe.extension.shared.spoof.requests.StreamingDataRequest.setAdaptiveFormatValidator(null);

        // Preserve the existing opt-in AV1 client mapping for Default mode.
        ClientType client = selected;
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

        List<ClientType> availableClients = List.of(ClientType.TV_SABR, ClientType.ANDROID_CREATOR);
        app.morphe.extension.shared.spoof.SpoofVideoStreamsPatch.setClientsToUse(availableClients, client);
    }
}
