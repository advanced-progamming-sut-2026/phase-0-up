package net;

// Marker for anything that may cross the wire.
//
// The rule this interface exists to carry: a Packet holds PLAIN DATA and nothing else -- no live
// GameSession, no Plant, no Zombie, no Random, no LibGDX type. It is the same discipline
// DatabaseManager already applies to the save file ("only ever holds plain-data UserRecords, never
// live domain objects"), extended to the network, and for the same reason: the moment a live game
// object is reachable from a packet, serialising one drags an unbounded object graph behind it.
//
// Every implementation is a record. That is not decoration -- records are immutable, so a packet
// handed to the writer thread cannot be mutated by the thread that built it, and Gson 2.10+ supports
// them natively with no adapter.
public interface Packet {
}
