package service;

import java.nio.file.Path;

/** Test helper: opens a data folder with an admin logged in, since the services check who is calling. */
final class Stores {

    private Stores() {}

    /** A DataStore on this folder with the demo admin logged in (if the accounts could be loaded). */
    static DataStore asAdmin(Path dataDir) {
        DataStore store = new DataStore(dataDir.toFile(), null);
        try {
            store.getAuthService().login("admin", "admin123");
        } catch (Exception ignored) {
            // e.g. a test with a damaged accounts file: carry on with nobody logged in
        }
        return store;
    }
}
