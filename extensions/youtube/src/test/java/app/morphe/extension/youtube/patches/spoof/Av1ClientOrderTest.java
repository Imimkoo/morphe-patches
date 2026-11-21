package app.morphe.extension.youtube.patches.spoof;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import app.morphe.extension.shared.spoof.ClientType;
import java.util.List;
import org.junit.Test;

public class Av1ClientOrderTest {
    @Test public void allKnownAv1ClientsAreInTheForceOrder() {
        List<ClientType> order = SpoofVideoStreamsPatch.av1ClientsInOrder(ClientType.ANDROID_CREATOR);
        assertTrue(order.contains(ClientType.VISIONOS_1_06_AV1));
        assertTrue(order.contains(ClientType.VISIONOS_1_03));
        assertTrue(order.contains(ClientType.TV_SABR));
        assertTrue(order.contains(ClientType.TV_SIMPLY));
        assertTrue(order.contains(ClientType.ANDROID_XR_EXPERIMENTAL));
        assertTrue(order.contains(ClientType.ANDROID_XR_DASH));
        assertTrue(order.contains(ClientType.ANDROID_XR_SABR));
        assertFalse(order.contains(ClientType.ANDROID_VR_DASH));
        assertFalse(order.contains(ClientType.ANDROID_VR_SABR));
        assertFalse(order.contains(ClientType.ANDROID_VR_EXPERIMENTAL));
        assertFalse(order.contains(ClientType.ANDROID_CREATOR));
        assertEquals(ClientType.VISIONOS_1_06_AV1, order.get(0));
    }

    @Test public void selectedAv1ClientIsTriedFirstWithoutDroppingOtherAv1Clients() {
        List<ClientType> order = SpoofVideoStreamsPatch.av1ClientsInOrder(ClientType.TV_SABR);
        assertEquals(ClientType.TV_SABR, order.get(0));
        assertTrue(order.contains(ClientType.VISIONOS_1_06_AV1));
        assertTrue(order.contains(ClientType.ANDROID_XR_EXPERIMENTAL));
    }
}