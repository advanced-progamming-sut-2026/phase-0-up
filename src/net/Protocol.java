package net;

// Constants both ends have to agree on.
public final class Protocol {

    private Protocol() { }

    // Bumped whenever a packet changes shape in a way an older build would misread. HelloRequest is
    // checked against this BEFORE authentication, so a version skew is reported as a version skew
    // rather than surfacing later as an unexplained "unknown packet type" mid-match.
    public static final int VERSION = 1;

    public static final int DEFAULT_PORT = 7777;

    // -Dpvz.server=host:port. Read by the client at start-up; the server takes -Dpvz.port.
    public static final String SERVER_PROPERTY = "pvz.server";
    public static final String PORT_PROPERTY = "pvz.port";

    // A silent connection is a dead connection. The reader side drops a peer that has said nothing for
    // this long; the writer side sends a heartbeat at a third of it so a healthy but idle connection
    // (a player sitting in the lobby) is never mistaken for a dead one.
    public static final int READ_TIMEOUT_MS = 15_000;
    public static final int HEARTBEAT_MS = 5_000;

    // How long a graceful close waits for the outbound queue to drain before pulling the socket down.
    // Bounded rather than unbounded: the peer this is trying to reach may be exactly the one that has
    // stopped reading, and a close that can hang forever is worse than a refusal that goes unheard.
    public static final int CLOSE_FLUSH_MS = 2_000;

    // How long a challenge invite stays open before it is withdrawn. A challenge that never expires
    // pins the challenger in a waiting state they cannot leave.
    public static final int CHALLENGE_TIMEOUT_SECONDS = 30;

    // How long a match waits for a dropped player before awarding the win to the one still connected.
    public static final int DISCONNECT_GRACE_SECONDS = 10;
}
