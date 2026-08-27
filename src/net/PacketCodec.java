package net;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

// Packet <-> one line of JSON.
//
//   {"t":"LOGIN_REQ","c":17,"d":{"username":"amir","passwordHash":"9f8...","stayLoggedIn":true}}
//
// Newline-delimited JSON over TCP, using the Gson the project already depends on and already persists
// with. No new dependency, and the same "plain data only" discipline the save file runs on carries
// straight over to the wire.
//
// Decoding is two steps, and has to be: Gson cannot be asked for an object until something has decided
// which class to ask for. So the line is parsed to a JsonObject, the "t" tag is looked up in
// PacketType, and only then is the "d" subtree bound to the concrete record.
//
// NOT pretty-printing, unlike DatabaseManager's Gson. Pretty output contains newlines, and a newline
// inside a packet would split it into two unparseable lines -- the framing IS the newline. This is
// also why serializeNulls is off: a null field simply disappears, which for a record means the
// component comes back as null anyway, and every optional field here is written to expect that.
public final class PacketCodec {

    private static final String TAG = "t";
    private static final String CORRELATION = "c";
    private static final String DATA = "d";

    // Gson is documented thread-safe once built, and both the reader and writer threads encode through
    // the same instance, so this is shared rather than per-connection.
    private final Gson gson;

    public PacketCodec() {
        this(new GsonBuilder().create());
    }

    public PacketCodec(Gson gson) {
        this.gson = gson;
    }

    public String encode(Packet packet) {
        return encode(packet, Envelope.NO_CORRELATION);
    }

    // Returns the line WITHOUT its terminating newline; the writer adds that, so a caller cannot
    // accidentally frame a packet twice.
    public String encode(Packet packet, long correlationId) {
        if (packet == null) {
            throw new IllegalArgumentException("cannot encode a null packet");
        }
        PacketType type = PacketType.of(packet);
        if (type == null) {
            // Almost always a new packet class that was never added to PacketType. Naming the class is
            // the difference between a one-line fix and an afternoon.
            throw new IllegalArgumentException(packet.getClass().getName()
                    + " is not registered in PacketType");
        }
        JsonObject envelope = new JsonObject();
        envelope.addProperty(TAG, type.tag());
        if (correlationId != Envelope.NO_CORRELATION) {
            envelope.addProperty(CORRELATION, correlationId);
        }
        envelope.add(DATA, gson.toJsonTree(packet));
        return gson.toJson(envelope);
    }

    public Envelope decode(String line) throws ProtocolException {
        if (line == null || line.isBlank()) {
            throw new ProtocolException("empty line");
        }
        JsonObject envelope;
        try {
            envelope = JsonParser.parseString(line).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            throw new ProtocolException("not a JSON object: " + summarise(line), e);
        }
        if (!envelope.has(TAG)) {
            throw new ProtocolException("packet has no \"" + TAG + "\" tag: " + summarise(line));
        }
        String tag = envelope.get(TAG).getAsString();
        PacketType type = PacketType.byTag(tag);
        if (type == null) {
            // A peer on a newer build, or a typo'd tag. Recoverable: the caller may skip the line and
            // keep the connection, which is why this is checked rather than fatal.
            throw new ProtocolException("unknown packet tag \"" + tag + "\"");
        }
        long correlationId = envelope.has(CORRELATION)
                ? envelope.get(CORRELATION).getAsLong() : Envelope.NO_CORRELATION;
        try {
            Packet payload = gson.fromJson(envelope.get(DATA), type.type());
            if (payload == null) {
                throw new ProtocolException(tag + " has no \"" + DATA + "\" payload");
            }
            return new Envelope(type, correlationId, payload);
        } catch (JsonSyntaxException e) {
            throw new ProtocolException(tag + " payload does not fit "
                    + type.type().getSimpleName(), e);
        }
    }

    // Errors quote the offending line, but a bad MATCH_SNAPSHOT is kilobytes long and would bury the
    // log it is meant to explain.
    private static String summarise(String line) {
        String trimmed = line.strip();
        return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 200) + "...";
    }
}
