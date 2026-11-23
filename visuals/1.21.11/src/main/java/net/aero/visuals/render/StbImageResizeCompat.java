package net.aero.visuals.render;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.lwjgl.stb.STBImageResize;

/** Keeps Minecraft's resize call compatible with the original and updated LWJGL runtimes. */
public final class StbImageResizeCompat {
    private static final int STBIR_4CHANNEL = 5;
    private static final MethodType LEGACY_TYPE = MethodType.methodType(int.class,
            long.class, int.class, int.class, int.class, long.class, int.class, int.class, int.class, int.class);
    private static final Resizer RESIZER = findResizer();

    private StbImageResizeCompat() {}

    private record Resizer(MethodHandle handle, boolean modern) {}

    private static Resizer findResizer() {
        var lookup = MethodHandles.publicLookup();
        try {
            return new Resizer(lookup.findStatic(STBImageResize.class, "nstbir_resize_uint8", LEGACY_TYPE), false);
        } catch (NoSuchMethodException unavailable) {
            try {
                var modernType = LEGACY_TYPE.changeReturnType(long.class);
                return new Resizer(lookup.findStatic(STBImageResize.class, "nstbir_resize_uint8_linear", modernType), true);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("LWJGL has no compatible STB image resizer", failure);
            }
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot access the LWJGL STB image resizer", failure);
        }
    }

    public static int resize(long input, int inputWidth, int inputHeight, int inputStride,
                             long output, int outputWidth, int outputHeight, int outputStride, int channels) {
        var handle = RESIZER.handle();
        try {
            if (RESIZER.modern()) {
                // Four independent channels preserve the legacy unweighted alpha behavior.
                int layout = channels == 4 ? STBIR_4CHANNEL : channels;
                long result = (long) handle.invokeExact(input, inputWidth, inputHeight, inputStride,
                        output, outputWidth, outputHeight, outputStride, layout);
                return result == 0L ? 0 : 1;
            }
            return (int) handle.invokeExact(input, inputWidth, inputHeight, inputStride,
                    output, outputWidth, outputHeight, outputStride, channels);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Could not resize a Minecraft image with STB", failure);
        }
    }
}
