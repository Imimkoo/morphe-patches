package app.morphe.extension.youtube.patches.spoof;

import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.shared.spoof.ClientType;
import app.morphe.extension.youtube.settings.Settings;
import app.morphe.extension.youtube.videoplayer.IbrahimVideoControlButton;

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

    /** Injection point: Ibrahim selects an AV1-capable client first and retains the chosen client as fallback. */
    public static void setClientOrderToUse() {
        ClientType requestedClient = Settings.SPOOF_VIDEO_STREAMS_CLIENT_TYPE.get();
        ClientType preferredClient = IbrahimVideoControlButton.getPreferredAv1Client(requestedClient);

        List<ClientType> availableClients = new ArrayList<>();
        // Add a JavaScript-independent AV1 fallback only when AV1 preference is enabled.
        // In Default mode, preserve Morphe's original client ordering.
        if (preferredClient != requestedClient && preferredClient != ClientType.VISIONOS_1_06_AV1) {
            availableClients.add(ClientType.VISIONOS_1_06_AV1);
        }
        availableClients.add(ClientType.TV_SABR);
        availableClients.add(ClientType.ANDROID_CREATOR);
        if (!availableClients.contains(requestedClient) && requestedClient != preferredClient) {
            availableClients.add(requestedClient);
        }

        app.morphe.extension.shared.spoof.SpoofVideoStreamsPatch.setClientsToUse(
                availableClients, preferredClient);
    }
}
