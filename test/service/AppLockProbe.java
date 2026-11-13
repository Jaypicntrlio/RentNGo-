package service;

import java.io.File;

/** Helper for AppLockTest: run in its own process, tries to lock a data folder and prints "locked" or "busy". */
public class AppLockProbe {
    public static void main(String[] args) throws Exception {
        try (AppLock lock = AppLock.tryAcquire(new File(args[0]))) {
            System.out.println(lock != null ? "locked" : "busy");
        }
    }
}
