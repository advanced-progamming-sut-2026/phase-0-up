package server;

import net.Envelope;

// What the server does with one kind of packet.
//
// Registered against a PacketType in GameServer, so dispatch is a map lookup rather than a growing
// chain of if-instanceof. Each feature area registers its own -- accounts in T3.3, the lobby in T3.5,
// match traffic in T3.7 -- and none of them has to know the others exist.
//
// Runs on the sender's READER THREAD. Two rules follow, and neither is optional:
//
//   * A handler must never touch a match's GameSession. It queues the work for that match's runner
//     thread, which is the only thread permitted to mutate the model. Everything in the game -- every
//     entity list, every Row, the sun bank -- is written assuming a single thread.
//   * A handler must not block for long. The reader thread it occupies is the only one delivering that
//     client's packets, so a slow handler stops delivering everything else that player sends,
//     including their next command.
@FunctionalInterface
public interface PacketHandler {
    void handle(ClientSession session, Envelope envelope);
}
