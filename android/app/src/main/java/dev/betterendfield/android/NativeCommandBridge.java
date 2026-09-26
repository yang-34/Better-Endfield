package dev.betterendfield.android;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

// Overlay controls cannot call JNI directly: the module's classes are loaded by
// LSPosed's loader, which ART refuses to resolve native methods against
// ("Unsupported class loader"), and the game loader cannot see these classes.
// Both the overlay and the injected library run inside the game process, so the
// panel appends key events to a mailbox file that the native runtime worker
// polls and feeds into the same virtual-key latch the desktop modules read.
final class NativeCommandBridge {
    /** Matches betterendfield::VirtualKeyAction in native/shared/android_compat. */
    static final int KEY_RELEASE = 0;
    static final int KEY_PRESS = 1;
    static final int KEY_PULSE = 2;

    private static volatile File mailbox;

    private NativeCommandBridge() {}

    static void configure(File box) {
        File parent = box.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        mailbox = box;
        System.setProperty("betterendfield.keybox", box.getAbsolutePath());
    }

    static boolean bound() {
        return mailboxPath() != null;
    }

    // The overlay is loaded by the game's class loader while RuntimeBootstrap
    // runs under LSPosed's, so each sees its own copy of the static mailbox.
    // A system property lives in the single boot-loaded System instance, so
    // both copies resolve the same path (System.getenv would miss the runtime
    // Os.setenv because it caches the environment at process start).
    private static String mailboxPath() {
        File local = mailbox;
        if (local != null) return local.getAbsolutePath();
        return System.getProperty("betterendfield.keybox");
    }

    static boolean key(int virtualKey, int action) {
        String path = mailboxPath();
        if (path == null) throw new UnsatisfiedLinkError("key mailbox is not configured");
        return append(path, "K " + virtualKey + " " + action + "\n");
    }

    static void releaseKeys() {
        String path = mailboxPath();
        if (path == null) throw new UnsatisfiedLinkError("key mailbox is not configured");
        append(path, "A\n");
    }

    // The command pump still lives in the native library; the settings screen
    // reaches it through the same mailbox so a runtime configuration change is
    // applied without a reload.
    static boolean submit(String payload) {
        String path = mailboxPath();
        if (path == null) throw new UnsatisfiedLinkError("key mailbox is not configured");
        String single = payload.replace('\n', ' ');
        return append(path, "C " + single + "\n");
    }

    static String status() {
        throw new UnsatisfiedLinkError("status is served through the framework file bridge");
    }

    private static boolean append(String path, String line) {
        try (FileOutputStream stream = new FileOutputStream(path, true)) {
            stream.write(line.getBytes(StandardCharsets.US_ASCII));
            stream.flush();
            return true;
        } catch (IOException failure) {
            throw new UnsatisfiedLinkError("mailbox write failed: " + failure);
        }
    }
}
