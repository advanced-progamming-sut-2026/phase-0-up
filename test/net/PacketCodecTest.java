package net;

import models.leaderboard.LeaderboardEntry;
import net.dto.CardOffer;
import net.dto.ChallengeRejectReason;
import net.dto.EntityFlags;
import net.dto.EntityKind;
import net.dto.EntityState;
import models.game.Faction;
import net.dto.MatchEndReason;
import models.social.ReactionKind;
import net.packets.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The wire format, pinned.
//
// This is the network's answer to CommandBridgeTest, and it exists for the same reason: a mismatch
// here does not throw anywhere useful. A packet class that was never added to PacketType simply cannot
// be sent; a tag that changed spelling simply stops decoding at the other end. Both look like "the
// feature does nothing" from the game, and neither shows up in a stack trace.
//
// Runs entirely in memory. No socket is opened, which is what makes it fast enough to keep in the
// normal `gradlew test` run rather than in some separate integration suite nobody executes.
class PacketCodecTest {

    private final PacketCodec codec = new PacketCodec();

    // One representative instance of every packet in the protocol.
    //
    // An EnumMap keyed by PacketType, deliberately: the "did anyone add a packet and forget this file"
    // check below is then a simple key-set comparison, and it CANNOT be satisfied by accident.
    private static Map<PacketType, Packet> samples() {
        Map<PacketType, Packet> samples = new EnumMap<>(PacketType.class);
        accountSamples(samples);
        lobbySamples(samples);
        matchSamples(samples);
        return samples;
    }

    // Split three ways only because one method listing every packet in the protocol outgrows the
    // length limit. The groups are the protocol's own: proving who you are, finding an opponent, and
    // playing the match.
    private static void accountSamples(Map<PacketType, Packet> samples) {
        samples.put(PacketType.HELLO_REQ, new HelloRequest(Protocol.VERSION, "test"));
        samples.put(PacketType.HELLO_RES, new HelloResponse(true, Protocol.VERSION, null));

        samples.put(PacketType.REGISTER_REQ, new RegisterRequest(
                "amir", "9f86d081", "Amir", "amir@example.com", "MALE", 2, "5e884898"));
        samples.put(PacketType.REGISTER_RES, new RegisterResponse(true, "Welcome!", null));
        samples.put(PacketType.LOGIN_REQ, new LoginRequest("amir", "9f86d081", true));
        samples.put(PacketType.LOGIN_RES, new LoginResponse(true, "Welcome back!", null, "tok-1"));
        samples.put(PacketType.LOGOUT_REQ, new LogoutRequest());
        samples.put(PacketType.ACK, new AckResponse(false, "Nope."));
        samples.put(PacketType.USERNAME_CHECK_REQ, new UsernameCheckRequest("amir"));
        samples.put(PacketType.USERNAME_CHECK_RES, new UsernameCheckResponse("amir", true));
        samples.put(PacketType.RECOVERY_Q_REQ,
                new RecoveryQuestionRequest("amir", "amir@example.com"));
        samples.put(PacketType.RECOVERY_Q_RES, new RecoveryQuestionResponse(true, null, 2));
        samples.put(PacketType.RECOVERY_SUBMIT_REQ,
                new RecoverySubmitRequest("amir", "answerhash", "newhash"));
        samples.put(PacketType.PROFILE_SYNC_REQ, new ProfileSyncRequest(null));
        samples.put(PacketType.PROFILE_SYNC_RES, new ProfileSyncResponse(true, "Saved.", null));
        samples.put(PacketType.PASSWORD_CHANGE_REQ,
                new PasswordChangeRequest("oldhash", "newhash"));
        samples.put(PacketType.RENAME_REQ, new RenameRequest("Parsa"));
        samples.put(PacketType.RENAME_RES, new RenameResponse(true, "Renamed.", null));

    }

    private static void lobbySamples(Map<PacketType, Packet> samples) {
        samples.put(PacketType.ONLINE_USERS_REQ, new OnlineUsersRequest());
        samples.put(PacketType.ONLINE_USERS_RES, new OnlineUsersResponse(List.of("amir", "parsa")));
        samples.put(PacketType.CHALLENGE_REQ, new ChallengeRequest("parsa", Faction.ZOMBIES));
        samples.put(PacketType.CHALLENGE_REJECTED,
                new ChallengeRejected("parsa", ChallengeRejectReason.OFFLINE));
        samples.put(PacketType.CHALLENGE_INVITE,
                new ChallengeInvite("ch-1", "amir", Faction.ZOMBIES, 30));
        samples.put(PacketType.CHALLENGE_ANSWER, new ChallengeAnswer("ch-1", true));
        samples.put(PacketType.CHALLENGE_DECLINED, new ChallengeDeclined("parsa", false));
        samples.put(PacketType.QUEUE_JOIN_REQ, new QueueJoinRequest(Faction.PLANTS));
        samples.put(PacketType.QUEUE_LEAVE_REQ, new QueueLeaveRequest());
        samples.put(PacketType.QUEUE_STATUS, new QueueStatus(true, 1, 3));

    }

    private static void matchSamples(Map<PacketType, Packet> samples) {
        samples.put(PacketType.MATCH_START, new MatchStart(
                "m-1", Faction.PLANTS, "parsa", 1200,
                List.of(new CardOffer("ZombieDefault", 50), new CardOffer("ZombieImp", 25)),
                List.of(new CardOffer("Peashooter", 100), new CardOffer("Sunflower", 50)),
                5, 5, 9, 50, 300));
        samples.put(PacketType.GAME_COMMAND,
                new GameCommand("plant plant -t Sunflower -l (0, 2)", 42L));
        samples.put(PacketType.COMMAND_REJECTED,
                new CommandRejected("summon -t ZombieImp -l (7, 2)", "You're on the plant side."));
        samples.put(PacketType.MATCH_SNAPSHOT, new MatchSnapshot(
                42L, 1158, 175, 300, 2,
                new boolean[] {false, false, true, false, false},
                List.of(
                        new EntityState(1, EntityKind.PLANT, "Peashooter", 1.5f, 2f, 300, 300,
                                EntityFlags.NONE),
                        new EntityState(2, EntityKind.ZOMBIE, "ZombieArmor2", 8.25f, 2f, 900, 1290,
                                EntityFlags.ARMOUR_2 | EntityFlags.SUN_PRODUCER),
                        new EntityState(3, EntityKind.PROJECTILE, "PEA", 3.75f, 2.5f, 0, 0,
                                EntityFlags.NONE))));
        samples.put(PacketType.MATCH_EVENT,
                new MatchEvent(true, "A sun-maker drops 25 sun in lane 2."));
        samples.put(PacketType.MATCH_OVER,
                new MatchOver(Faction.PLANTS, MatchEndReason.TIME_UP, 3, 5, 1200L));
        samples.put(PacketType.MATCH_LEAVE_REQ, new MatchLeaveRequest());
        samples.put(PacketType.OPPONENT_DISCONNECTED, new OpponentDisconnected("parsa", 10));

        samples.put(PacketType.REACTION_SEND, new ReactionSend(ReactionKind.EMOJI, 1));
        samples.put(PacketType.REACTION_RELAY,
                new ReactionRelay("amir", ReactionKind.STICKER, 2));

        samples.put(PacketType.LEADERBOARD_REQ, new LeaderboardRequest("MEOW_POINT", false));
        samples.put(PacketType.LEADERBOARD_RES, new LeaderboardResponse(
                List.of(new LeaderboardEntry("amir", 2, 3, 4, 5, 6, 7)), 1));
        samples.put(PacketType.SCORE_SUBMIT_REQ, new ScoreSubmitRequest(1234));
        samples.put(PacketType.SCORE_SUBMIT_RES, new ScoreSubmitResponse(true, 1234, null));
    }

    @Test
    @DisplayName("every registered packet type has a sample -- nobody added one and forgot this file")
    void everyTypeIsCovered() {
        Map<PacketType, Packet> samples = samples();
        for (PacketType type : PacketType.values()) {
            assertTrue(samples.containsKey(type),
                    type + " has no sample in PacketCodecTest -- add one, or the round-trip below "
                            + "silently stops covering it");
        }
    }

    @Test
    @DisplayName("the tag on the wire matches the class it was registered against")
    void tagAndClassAgree() {
        for (Map.Entry<PacketType, Packet> entry : samples().entrySet()) {
            PacketType declared = entry.getKey();
            Packet packet = entry.getValue();
            assertSame(declared, PacketType.of(packet),
                    packet.getClass().getSimpleName() + " is registered against a different type");
            assertSame(declared, PacketType.byTag(declared.tag()),
                    "tag \"" + declared.tag() + "\" does not resolve back to " + declared);
        }
    }

    @Test
    @DisplayName("every packet survives encode -> decode unchanged")
    void everyPacketRoundTrips() throws Exception {
        for (Map.Entry<PacketType, Packet> entry : samples().entrySet()) {
            PacketType type = entry.getKey();
            Packet original = entry.getValue();

            String line = codec.encode(original, 17L);
            // The framing IS the newline, so a packet that serialises to more than one line would be
            // split into two unparseable halves at the other end. This is why PacketCodec must not
            // pretty-print, and the assertion that keeps it that way.
            assertFalse(line.contains("\n"), type + " encoded to more than one line");

            Envelope envelope = codec.decode(line);
            assertEquals(type, envelope.type(), type + " decoded as the wrong type");
            assertEquals(17L, envelope.correlationId(), type + " lost its correlation id");
            assertNotNull(envelope.payload(), type + " decoded to a null payload");
            assertEquals(original.getClass(), envelope.payload().getClass());

            // Records give value equality for free, which is most of why every packet is one. Two
            // packets cannot use it, for different reasons, and both are compared component by
            // component instead of being quietly asserted as "not equal":
            //
            //   MatchSnapshot        carries a boolean[], and array equality is identity.
            //   LeaderboardResponse  carries LeaderboardEntry, a plain class with no equals(). It is
            //                        borrowed from the model deliberately -- it is already the exact
            //                        immutable plain-scalar row this packet needs -- and giving a
            //                        model class an equals() purely to satisfy a test would be the
            //                        tail wagging the dog.
            if (original instanceof MatchSnapshot expected) {
                assertSnapshotEquals(expected, (MatchSnapshot) envelope.payload());
            } else if (original instanceof LeaderboardResponse expected) {
                assertLeaderboardEquals(expected, (LeaderboardResponse) envelope.payload());
            } else {
                assertEquals(original, envelope.payload(), type + " changed across the wire");
            }
        }
    }

    // LeaderboardEntry has all-final fields and no no-arg constructor, so Gson allocates it without
    // running one. This is what proves that actually works -- every column has to arrive, or the
    // leaderboard silently renders a board of zeroes.
    private static void assertLeaderboardEquals(LeaderboardResponse expected,
                                                LeaderboardResponse actual) {
        assertEquals(expected.yourRank(), actual.yourRank());
        assertEquals(expected.rows().size(), actual.rows().size());
        for (int i = 0; i < expected.rows().size(); i++) {
            LeaderboardEntry want = expected.rows().get(i);
            LeaderboardEntry got = actual.rows().get(i);
            assertAll("row " + i,
                    () -> assertEquals(want.getUsername(), got.getUsername()),
                    () -> assertEquals(want.getLastChapter(), got.getLastChapter()),
                    () -> assertEquals(want.getLastLevel(), got.getLastLevel()),
                    () -> assertEquals(want.getMinigamesCompleted(), got.getMinigamesCompleted()),
                    () -> assertEquals(want.getDailyQuests(), got.getDailyQuests()),
                    () -> assertEquals(want.getNonDailyQuests(), got.getNonDailyQuests()),
                    () -> assertEquals(want.getBestMeowPoint(), got.getBestMeowPoint()),
                    // Derived, not stored -- so this also proves the fields it is derived FROM landed.
                    () -> assertEquals(want.getStageLabel(), got.getStageLabel()));
        }
    }

    private static void assertSnapshotEquals(MatchSnapshot expected, MatchSnapshot actual) {
        assertAll(
                () -> assertEquals(expected.tick(), actual.tick()),
                () -> assertEquals(expected.ticksRemaining(), actual.ticksRemaining()),
                () -> assertEquals(expected.sunPlants(), actual.sunPlants()),
                () -> assertEquals(expected.sunZombies(), actual.sunZombies()),
                () -> assertEquals(expected.plantFood(), actual.plantFood()),
                () -> org.junit.jupiter.api.Assertions.assertArrayEquals(
                        expected.brainEaten(), actual.brainEaten()),
                () -> assertEquals(expected.entities(), actual.entities()));
    }

    @Test
    @DisplayName("a null field survives as null -- verify-only recovery depends on it")
    void nullFieldsSurvive() throws Exception {
        // Gson drops nulls rather than writing them, so the component simply comes back null on the
        // other side. That is relied on: a RecoverySubmitRequest with no newPasswordHash means "check
        // the answer, change nothing", which is how the terminal reports a wrong answer before asking
        // for a password. If a null ever round-tripped as something else, the verify step would
        // silently become a password reset.
        RecoverySubmitRequest verifyOnly = new RecoverySubmitRequest("amir", "answerhash", null);
        assertTrue(verifyOnly.isVerifyOnly());

        RecoverySubmitRequest back =
                (RecoverySubmitRequest) codec.decode(codec.encode(verifyOnly)).payload();
        assertNull(back.newPasswordHash());
        assertTrue(back.isVerifyOnly());
    }

    @Test
    @DisplayName("a correlation-free packet decodes as a push, not as a reply")
    void pushesCarryNoCorrelation() throws Exception {
        Envelope envelope = codec.decode(codec.encode(new QueueStatus(true, 1, 2)));
        assertEquals(Envelope.NO_CORRELATION, envelope.correlationId());
        assertFalse(envelope.isReply());
    }

    @Test
    @DisplayName("float positions survive, because rounding them would break diagonals")
    void fractionalPositionsSurvive() throws Exception {
        // A Rotobaga's diagonal shot sits half a lane between rows, and a falling sun sits between them
        // vertically. If either were rounded on the wire there would be nothing between the lanes for
        // EntityInterpolator to interpolate, and the diagonals would cross the board sideways -- the
        // exact bug EntityInterpolator documents against getY().
        MatchSnapshot snapshot = new MatchSnapshot(1L, 10, 0, 0, 0, new boolean[] {false},
                List.of(new EntityState(9, EntityKind.PROJECTILE, "PEA", 3.75f, 2.5f, 0, 0, 0)));
        MatchSnapshot back = (MatchSnapshot) codec.decode(codec.encode(snapshot)).payload();
        assertEquals(3.75f, back.entities().get(0).x());
        assertEquals(2.5f, back.entities().get(0).y());
    }

    @Test
    @DisplayName("entity flags are a bitset, and every flag is distinct")
    void flagsAreDistinct() {
        int[] flags = {EntityFlags.FROZEN, EntityFlags.EATING, EntityFlags.DYING,
                EntityFlags.BOOSTED, EntityFlags.SUN_PRODUCER, EntityFlags.ARMOUR_1,
                EntityFlags.ARMOUR_2, EntityFlags.ARMOUR_3, EntityFlags.FALLING};
        int combined = 0;
        for (int flag : flags) {
            assertEquals(0, combined & flag, "flag " + flag + " overlaps an earlier one");
            combined |= flag;
        }
        assertTrue(EntityFlags.has(combined, EntityFlags.ARMOUR_2));
        assertFalse(EntityFlags.has(EntityFlags.NONE, EntityFlags.ARMOUR_2));
        assertEquals(EntityFlags.NONE,
                EntityFlags.with(EntityFlags.FROZEN, EntityFlags.FROZEN, false));
    }

    // ---- refusals -------------------------------------------------------------------------------
    //
    // These three are why ProtocolException is CHECKED. Each one is recoverable in a different way, and
    // a caller that has to catch it is a caller that had to decide which.

    @Test
    @DisplayName("an unknown tag is refused without taking the connection down")
    void unknownTagIsRefused() {
        ProtocolException thrown = assertThrows(ProtocolException.class,
                () -> codec.decode("{\"t\":\"FROM_THE_FUTURE\",\"d\":{}}"));
        assertTrue(thrown.getMessage().contains("FROM_THE_FUTURE"),
                "the message must name the tag, or nobody can tell which build sent it");
    }

    @Test
    @DisplayName("malformed JSON is refused")
    void malformedJsonIsRefused() {
        assertThrows(ProtocolException.class, () -> codec.decode("{not json at all"));
        assertThrows(ProtocolException.class, () -> codec.decode("   "));
    }

    @Test
    @DisplayName("a packet class missing from PacketType fails loudly at encode")
    void unregisteredPacketFailsLoudly() {
        record Unregistered(String x) implements Packet { }
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> codec.encode(new Unregistered("hi")));
        assertTrue(thrown.getMessage().contains("PacketType"));
    }
}
