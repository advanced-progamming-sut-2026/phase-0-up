package net.packets;

import net.Packet;

// A command the server would not run, and why. `reason` is prose from the model where there is any
// (the refusals in GameSession.plant and IZombieMode.summonZombie already explain themselves in the
// game's voice), and the faction-whitelist wording otherwise.
public record CommandRejected(String text, String reason) implements Packet {
}
