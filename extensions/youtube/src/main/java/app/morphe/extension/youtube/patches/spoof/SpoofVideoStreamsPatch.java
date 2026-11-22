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

        List<ClientType> availableClients = new ArrayList<>(List.of(
                ClientType.TV_SABR,
                ClientType.ANDROID_CREATOR
        ));
        if (!availableClients.contains(requestedClient) && requestedClient != preferredClient) {
            availableClients.add(requestedClient);
        }

        app.morphe.extension.shared.spoof.SpoofVideoStreamsPatch.setClientsToUse(
                availableClients, preferredClient);
    }
}
