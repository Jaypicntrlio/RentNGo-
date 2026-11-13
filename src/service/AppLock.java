package service;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.StandardOpenOption;

/**
 * Makes sure only one copy of RentNGo uses a data folder at a time, by holding an OS file lock on
 * data/.rentngo.lock for the whole session. The lock is released when the app exits (or crashes:
 * the operating system drops it when the process ends).
 */
public final class AppLock implements AutoCloseable {

    public static final String LOCK_FILE = ".rentngo.lock";

    private final FileChannel channel;
    private final FileLock lock;

    private AppLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    /**
     * Takes the lock for this data folder. Returns null when another RentNGo (in this or another
     * process) already holds it. Throws IOException if the lock file can't be opened at all.
     */
    public static AppLock tryAcquire(File dataDir) throws IOException {
        dataDir.mkdirs();
        File file = new File(dataDir, LOCK_FILE);
        FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (OverlappingFileLockException e) {
            lock = null; // already held by this same Java process
        } catch (IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
        if (lock == null) {
            channel.close();
            return null;
        }
        return new AppLock(channel, lock);
    }

    /** Releases the lock when the JVM shuts down (normal exit, System.exit or Ctrl+C). */
    public AppLock releaseOnExit() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::close, "RentNGo lock release"));
        return this;
    }

    public boolean isHeld() {
        return lock.isValid();
    }

    /** Releases the lock and closes the lock file; safe to call more than once. */
    @Override
    public void close() {
        try {
            if (lock.isValid()) lock.release();
        } catch (IOException ignored) {
            // closing the channel below releases it anyway
        }
        try {
            channel.close();
        } catch (IOException ignored) {
            // nothing more to do
        }
    }
}
