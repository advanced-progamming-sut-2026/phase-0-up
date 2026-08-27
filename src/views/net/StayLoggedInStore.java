package views.net;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

// "Stay signed in", for a client whose accounts live on a server.
//
// ## Why this file exists
//
// Locally, staying signed in was a boolean on the account: the roster was on this machine, so the game
// could just look for the account carrying the flag. Once the roster is on the server that stops
// working -- the client has nothing to look at before it has signed in, and signing in is the thing
// it is trying to skip.
//
// So the credential is kept here, on the player's own machine, and replayed as an ordinary login on
// the next launch. No new packet, no session-token protocol, and the resume path is the same code path
// as a normal sign-in -- which means it cannot drift away from it.
//
// ## What is stored, and what that means
//
// The username and the PASSWORD HASH -- never the plaintext, which the client discards immediately
// after hashing. But an unsalted hash that the server accepts as proof of identity is a
// password-equivalent: anyone who can read this file can sign in as this player.
//
// That is not a regression. It is exactly what the previous build did -- users_database.json sat in
// the same directory with the same hash in it, and the stay-logged-in flag beside it. And it is what
// "stay signed in" means anywhere: a credential is being written to disk so it does not have to be
// typed again. Ticking the box is the player choosing that trade, which is why the file is written
// only when they tick it and deleted the moment they do not.
public final class StayLoggedInStore {

    // Beside the other client-side files, and named so it is obvious what it is. Not inside
    // users_database.json: that file is the LOCAL roster, which a networked client no longer has.
    public static final String DEFAULT_FILE = "client_session.json";

    // What was remembered. A record so it is plain data, like everything else that gets serialised.
    public record Remembered(String username, String passwordHash) { }

    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public StayLoggedInStore() {
        this(DEFAULT_FILE);
    }

    public StayLoggedInStore(String filePath) {
        this.file = Path.of(filePath);
    }

    public Remembered read() {
        if (!Files.exists(file)) {
            return null;
        }
        try {
            Remembered remembered = gson.fromJson(
                    Files.readString(file, StandardCharsets.UTF_8), Remembered.class);
            if (remembered == null || remembered.username() == null
                    || remembered.passwordHash() == null) {
                return null;
            }
            return remembered;
        } catch (IOException | RuntimeException e) {
            // A hand-edited or truncated file means "nobody is remembered", not a crash on launch.
            return null;
        }
    }

    public void remember(String username, String passwordHash) {
        if (username == null || passwordHash == null) {
            forget();
            return;
        }
        try {
            Files.writeString(file, gson.toJson(new Remembered(username, passwordHash)),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Not fatal: the player stays signed in for this session and simply has to type it again
            // next time. Worth a line, because "stay signed in does nothing" is otherwise a mystery.
            System.err.println("[net] could not remember this sign-in: " + e.getMessage());
        }
    }

    // Keeps the remembered credential aimed at the right account after a rename. Without this, renaming
    // yourself would silently break auto-login until the next manual sign-in.
    public void rename(String newUsername) {
        Remembered remembered = read();
        if (remembered != null && newUsername != null) {
            remember(newUsername, remembered.passwordHash());
        }
    }

    public void forget() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            System.err.println("[net] could not clear the remembered sign-in: " + e.getMessage());
        }
    }
}
