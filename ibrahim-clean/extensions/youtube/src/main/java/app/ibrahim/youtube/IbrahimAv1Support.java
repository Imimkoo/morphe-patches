package app.ibrahim.youtube;

import java.lang.reflect.Method;

/**
 * Runtime capability check for the dav1d implementation already shipped in
 * YouTube 21.30.216. This does not request AV1 streams or change codec priorities.
 */
public final class IbrahimAv1Support {
    private IbrahimAv1Support() {}

    public static boolean isBundledDav1dAvailable() {
        try {
            ClassLoader loader = IbrahimAv1Support.class.getClassLoader();
            Class<?> library = Class.forName(
                    "androidx.media3.decoder.av1.Dav1dLibrary",
                    false,
                    loader
            );
            Method availability = library.getDeclaredMethod("a");
            availability.setAccessible(true);
            Object result = availability.invoke(null);
            if (!Boolean.TRUE.equals(result)) {
                return false;
            }

            // Confirm the implementation classes are present before opening the gate.
            Class.forName("androidx.media3.decoder.av1.Dav1dDecoder", false, loader);
            Class.forName("androidx.media3.decoder.av1.Libdav1dVideoRenderer", false, loader);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
