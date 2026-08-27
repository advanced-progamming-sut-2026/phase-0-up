package net;

// A decoded line: what kind of packet it is, which request it answers, and the payload itself.
//
// `correlationId` is what lets a blocking client call (RemoteBackend.findUser, say) match a response
// to the request it sent, on a connection where unsolicited server pushes -- snapshots, challenge
// invites, reaction relays -- are arriving on the same socket at the same time. A pushed packet
// carries NO_CORRELATION.
public record Envelope(PacketType type, long correlationId, Packet payload) {

    public static final long NO_CORRELATION = 0L;

    public boolean isReply() {
        return correlationId != NO_CORRELATION;
    }

    // Convenience for a handler that has already switched on type() and knows what it is holding.
    @SuppressWarnings("unchecked")
    public <T extends Packet> T as(Class<T> expected) {
        if (!expected.isInstance(payload)) {
            throw new IllegalStateException("envelope carries " + payload.getClass().getSimpleName()
                    + ", not " + expected.getSimpleName());
        }
        return (T) payload;
    }
}
