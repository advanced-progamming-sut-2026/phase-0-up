package net;

// A line arrived that is not a packet this build understands: malformed JSON, an unknown type tag, or
// a payload that does not fit its class.
//
// Checked rather than unchecked on purpose. Every caller is a read loop that has to decide between
// "skip this line" and "drop the connection", and a RuntimeException here would let that decision be
// forgotten silently.
public class ProtocolException extends Exception {

    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
