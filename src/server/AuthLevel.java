package server;

// Whether a packet may be sent by somebody who has not signed in yet.
//
// Every registration names one explicitly -- there is no default. That is deliberate: a default would
// have to be one of the two, and if it were ANONYMOUS a future feature could be exposed to unsigned-in
// callers by simply forgetting to think about it. Making it a required argument forces the question to
// be answered once, at the point where the answer is obvious.
public enum AuthLevel {

    // Sendable before signing in: the handshake, registration, login, and password recovery. Nothing
    // else belongs here -- these four are the only things a stranger has any business asking for.
    ANONYMOUS,

    // Everything else. The gate is in GameServer, not in the handlers, so a handler can read
    // session.user() without a null check and without each one re-deciding what "not signed in" means.
    AUTHENTICATED
}
