package net;

// What a Connection hands each decoded packet to.
//
// Called on the connection's READER THREAD, never on the caller's. Two consequences that every
// implementation has to respect:
//
//   * On the server, a listener must not touch a match's GameSession directly -- it queues the work
//     for that match's runner thread, which is the only thread allowed to mutate the model.
//   * On the client, a listener must not touch Scene2D or any renderer directly -- it posts through
//     Gdx.app.postRunnable, or the change lands mid-frame on the wrong thread.
@FunctionalInterface
public interface PacketListener {
    void onPacket(Envelope envelope);
}
