package net.aero.visuals.alt;

import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

import static java.nio.channels.FileChannel.MapMode.READ_ONLY;
import static java.nio.channels.FileChannel.MapMode.READ_WRITE;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

final class MultiAccountFeed {
    static final int MAX_DIMENSION = 8192;
    static final long MAX_PIXEL_BYTES = 4L * 3840 * 2160;
    private static final int HEADER = 24;
    private static final VarHandle SEQUENCE = MethodHandles.byteBufferViewVarHandle(long[].class, ByteOrder.nativeOrder());
    private static final int INPUT_BYTES = 32;

    private MultiAccountFeed() {}

    record Input(int type, int first, int second, int modifiers, double x, double y) {
        static final int KEY = 1, CHARACTER = 2, MOVE = 3, BUTTON = 4, SCROLL = 5, RESET = 6;
    }

    static final class Frames implements AutoCloseable {
        private final Path path;
        private final boolean write;
        private MappedByteBuffer memory;
        private long sequence;

        private Frames(Path path, boolean write) {
            this.path = path;
            this.write = write;
        }

        static Frames writer(Path directory) {
            return new Frames(directory.resolve("view-rgba.bin"), true);
        }

        static Frames reader(Path directory) {
            return new Frames(directory.resolve("view-rgba.bin"), false);
        }

        private boolean map(long size) throws IOException {
            if (memory != null && memory.capacity() >= size) return true;
            try (var channel = write ? FileChannel.open(path, CREATE, READ, WRITE) : FileChannel.open(path, READ)) {
                if (!write && channel.size() < size) return false;
                MappedByteBuffer next = channel.map(write ? READ_WRITE : READ_ONLY, 0,
                        Math.max(size, Math.min(channel.size(), MAX_PIXEL_BYTES + HEADER)));
                next.order(ByteOrder.nativeOrder());
                close();
                memory = next;
                return true;
            } catch (java.nio.file.NoSuchFileException missing) {
                return false;
            }
        }

        @Override public void close() {
            if (memory == null) return;
            MappingCleaner.release(memory);
            memory = null;
        }

        private static long frameSize(int width, int height) {
            if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION) return 0;
            long size = HEADER + (long) width * height * Integer.BYTES;
            return size <= MAX_PIXEL_BYTES + HEADER ? size : 0;
        }

        void publish(int viewId, int width, int height, long pixels, boolean locked) throws IOException {
            long size = frameSize(width, height);
            if (size == 0 || !map(size)) return;
            long open = sequence + 1;
            SEQUENCE.setVolatile(memory, 0, open);
            memory.putInt(8, viewId);
            memory.putInt(12, width);
            memory.putInt(16, height);
            memory.putInt(20, locked ? 1 : 0);
            // Keep the GPU row order and alpha in transit. The view shader handles both.
            MemoryUtil.memCopy(pixels, MemoryUtil.memAddress(memory) + HEADER, size - HEADER);
            sequence = open + 1;
            SEQUENCE.setVolatile(memory, 0, sequence);
        }

        Frame latest(int viewId) throws IOException {
            if (!map(HEADER)) return null;
            long current = (long) SEQUENCE.getVolatile(memory, 0);
            if (current == 0 || current == sequence || (current & 1) != 0 || memory.getInt(8) != viewId) return null;
            int width = memory.getInt(12);
            int height = memory.getInt(16);
            boolean locked = memory.getInt(20) != 0;
            long size = frameSize(width, height);
            VarHandle.loadLoadFence();
            if (size == 0 || current != (long) SEQUENCE.getVolatile(memory, 0) || !map(size)) return null;
            return new Frame(current, width, height, locked, size - HEADER);
        }

        boolean read(Frame frame, long destination) {
            if (frame.sequence != (long) SEQUENCE.getVolatile(memory, 0)) return false;
            MemoryUtil.memCopy(MemoryUtil.memAddress(memory) + HEADER, destination, frame.bytes);
            VarHandle.loadLoadFence();
            if (frame.sequence != (long) SEQUENCE.getVolatile(memory, 0)) return false;
            sequence = frame.sequence;
            return true;
        }
    }

    private static final class MappingCleaner {
        private static final java.util.function.Consumer<ByteBuffer> CLEANER = create();

        private static java.util.function.Consumer<ByteBuffer> create() {
            try {
                Class<?> type = Class.forName("sun.misc.Unsafe");
                var field = type.getDeclaredField("theUnsafe");
                field.setAccessible(true);
                Object unsafe = field.get(null);
                var clean = type.getMethod("invokeCleaner", ByteBuffer.class);
                return buffer -> {
                    try { clean.invoke(unsafe, buffer); }
                    catch (ReflectiveOperationException ignored) { }
                };
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                return buffer -> { };
            }
        }

        static void release(ByteBuffer buffer) { CLEANER.accept(buffer); }
    }

    record Frame(long sequence, int width, int height, boolean locked, long bytes) {}

    static final class Inputs implements AutoCloseable {


        private static final int CAPACITY = 4096;
        private static final int HEADER_BYTES = 2 * Long.BYTES;
        private final FileChannel file;
        private final ByteBuffer buffer = ByteBuffer.allocate(INPUT_BYTES);
        private final ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES);

        Inputs(Path directory, int viewId, boolean write) throws IOException {
            file = FileChannel.open(fileFor(directory, viewId), CREATE, READ, WRITE);
        }

        void send(Input input) throws IOException {
            try (var lock = file.lock()) {
                readHeader();
                long head = header.getLong(0), tail = header.getLong(Long.BYTES);
                if (input.type() == Input.MOVE && tail > head) {
                    Input previous = readAt(tail - 1);
                    if (previous.type() == Input.MOVE && previous.first() == input.first()) {
                        if (input.first() == 1) {
                            input = new Input(input.type(), input.first(), input.second(), input.modifiers(),
                                    previous.x() + input.x(), previous.y() + input.y());
                        }
                        writeAt(tail - 1, input);
                        return;
                    }
                }
                if (tail - head >= CAPACITY) throw new IOException("Background account input queue is full");
                writeAt(tail, input);
                writeHeader(head, tail + 1);
            }
        }

        Input read() throws IOException {
            try (var lock = file.tryLock()) {
                if (lock == null) return null;
                readHeader();
                long head = header.getLong(0), tail = header.getLong(Long.BYTES);
                if (head == tail) return null;
                Input input = readAt(head);
                writeHeader(head + 1, tail);
                return input;
            }
        }

        private void readHeader() throws IOException {
            header.clear();
            if (file.size() == 0) {
                header.putLong(0).putLong(0);
                header.flip();
            } else {
                readFully(header, 0);
            }
        }

        private void writeHeader(long head, long tail) throws IOException {
            header.clear();
            header.putLong(head).putLong(tail).flip();
            writeFully(header, 0);
        }

        private static long position(long sequence) {
            return HEADER_BYTES + (sequence % CAPACITY) * INPUT_BYTES;
        }

        private Input readAt(long sequence) throws IOException {
            buffer.clear();
            readFully(buffer, position(sequence));
            return new Input(buffer.getInt(), buffer.getInt(), buffer.getInt(), buffer.getInt(), buffer.getDouble(), buffer.getDouble());
        }

        private void writeAt(long sequence, Input input) throws IOException {
            buffer.clear();
            buffer.putInt(input.type()).putInt(input.first()).putInt(input.second()).putInt(input.modifiers())
                    .putDouble(input.x()).putDouble(input.y()).flip();
            writeFully(buffer, position(sequence));
        }

        private void readFully(ByteBuffer data, long position) throws IOException {
            while (data.hasRemaining()) {
                if (file.read(data, position + data.position()) <= 0) throw new IOException("Incomplete account input");
            }
            data.flip();
        }

        private void writeFully(ByteBuffer data, long position) throws IOException {
            while (data.hasRemaining()) file.write(data, position + data.position());
        }

        static Path fileFor(Path directory, int viewId) {
            return directory.resolve("input-" + viewId + ".bin");
        }

        @Override public void close() throws IOException { file.close(); }
    }
}
