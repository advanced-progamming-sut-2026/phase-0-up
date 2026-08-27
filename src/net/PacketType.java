package net;

import net.packets.*;

import java.util.HashMap;
import java.util.Map;

// The wire vocabulary: every packet type, its tag, and the class that carries it.
//
// This exists because JSON is not self-describing about types. A line says {"t":"LOGIN_REQ",...} and
// something has to turn that tag into a class before Gson can be asked for anything -- Gson's own
// RuntimeTypeAdapterFactory would do it, but that lives in gson-extras, which is not a dependency and
// is not worth becoming one for a lookup table.
//
// The tag is written out as a STRING LITERAL rather than derived from name() or from the class name.
// Both of those tie the wire format to a Java identifier, so renaming a class or an enum constant --
// something a refactor does silently -- would change the protocol and break every older client with no
// compiler error anywhere. The literal is the contract; the enum constant is just how this build spells
// it.
//
// PacketTypeTest asserts that no two entries share a tag or a class, and that every type round-trips.
// A collision here does not throw at start-up: it silently makes one of the two packets undecodable,
// which is the same class of silent failure CommandBridgeTest exists to catch on the command strings.
public enum PacketType {

    // ---- handshake ----
    HELLO_REQ("HELLO_REQ", HelloRequest.class),
    HELLO_RES("HELLO_RES", HelloResponse.class),

    // ---- accounts ----
    REGISTER_REQ("REGISTER_REQ", RegisterRequest.class),
    REGISTER_RES("REGISTER_RES", RegisterResponse.class),
    LOGIN_REQ("LOGIN_REQ", LoginRequest.class),
    LOGIN_RES("LOGIN_RES", LoginResponse.class),
    LOGOUT_REQ("LOGOUT_REQ", LogoutRequest.class),
    ACK("ACK", AckResponse.class),
    USERNAME_CHECK_REQ("USERNAME_CHECK_REQ", UsernameCheckRequest.class),
    USERNAME_CHECK_RES("USERNAME_CHECK_RES", UsernameCheckResponse.class),
    RECOVERY_Q_REQ("RECOVERY_Q_REQ", RecoveryQuestionRequest.class),
    RECOVERY_Q_RES("RECOVERY_Q_RES", RecoveryQuestionResponse.class),
    RECOVERY_SUBMIT_REQ("RECOVERY_SUBMIT_REQ", RecoverySubmitRequest.class),
    PROFILE_SYNC_REQ("PROFILE_SYNC_REQ", ProfileSyncRequest.class),
    PROFILE_SYNC_RES("PROFILE_SYNC_RES", ProfileSyncResponse.class),
    PASSWORD_CHANGE_REQ("PASSWORD_CHANGE_REQ", PasswordChangeRequest.class),
    RENAME_REQ("RENAME_REQ", RenameRequest.class),
    RENAME_RES("RENAME_RES", RenameResponse.class),

    // ---- lobby ----
    ONLINE_USERS_REQ("ONLINE_USERS_REQ", OnlineUsersRequest.class),
    ONLINE_USERS_RES("ONLINE_USERS_RES", OnlineUsersResponse.class),
    CHALLENGE_REQ("CHALLENGE_REQ", ChallengeRequest.class),
    CHALLENGE_REJECTED("CHALLENGE_REJECTED", ChallengeRejected.class),
    CHALLENGE_INVITE("CHALLENGE_INVITE", ChallengeInvite.class),
    CHALLENGE_ANSWER("CHALLENGE_ANSWER", ChallengeAnswer.class),
    CHALLENGE_DECLINED("CHALLENGE_DECLINED", ChallengeDeclined.class),
    QUEUE_JOIN_REQ("QUEUE_JOIN_REQ", QueueJoinRequest.class),
    QUEUE_LEAVE_REQ("QUEUE_LEAVE_REQ", QueueLeaveRequest.class),
    QUEUE_STATUS("QUEUE_STATUS", QueueStatus.class),

    // ---- match ----
    MATCH_START("MATCH_START", MatchStart.class),
    GAME_COMMAND("GAME_COMMAND", GameCommand.class),
    COMMAND_REJECTED("COMMAND_REJECTED", CommandRejected.class),
    MATCH_SNAPSHOT("MATCH_SNAPSHOT", MatchSnapshot.class),
    MATCH_EVENT("MATCH_EVENT", MatchEvent.class),
    MATCH_OVER("MATCH_OVER", MatchOver.class),
    MATCH_LEAVE_REQ("MATCH_LEAVE_REQ", MatchLeaveRequest.class),
    OPPONENT_DISCONNECTED("OPPONENT_DISCONNECTED", OpponentDisconnected.class),

    // ---- social ----
    REACTION_SEND("REACTION_SEND", ReactionSend.class),
    REACTION_RELAY("REACTION_RELAY", ReactionRelay.class),

    // ---- leaderboard & scoring ----
    LEADERBOARD_REQ("LEADERBOARD_REQ", LeaderboardRequest.class),
    LEADERBOARD_RES("LEADERBOARD_RES", LeaderboardResponse.class),
    SCORE_SUBMIT_REQ("SCORE_SUBMIT_REQ", ScoreSubmitRequest.class),
    SCORE_SUBMIT_RES("SCORE_SUBMIT_RES", ScoreSubmitResponse.class);

    private final String tag;
    private final Class<? extends Packet> type;

    PacketType(String tag, Class<? extends Packet> type) {
        this.tag = tag;
        this.type = type;
    }

    public String tag() {
        return tag;
    }

    public Class<? extends Packet> type() {
        return type;
    }

    // Built once, eagerly, in a holder so the enum's own constructors have all finished running. A
    // static initialiser inside the enum body would run at the same time and see a half-built values()
    // array on some paths.
    private static final class Index {
        private static final Map<String, PacketType> BY_TAG = new HashMap<>();
        private static final Map<Class<?>, PacketType> BY_CLASS = new HashMap<>();

        static {
            for (PacketType packetType : values()) {
                PacketType clashingTag = BY_TAG.put(packetType.tag, packetType);
                PacketType clashingClass = BY_CLASS.put(packetType.type, packetType);
                // Fail loudly at class-load rather than silently making one of the two undecodable.
                if (clashingTag != null) {
                    throw new IllegalStateException("duplicate packet tag \"" + packetType.tag
                            + "\" on " + packetType + " and " + clashingTag);
                }
                if (clashingClass != null) {
                    throw new IllegalStateException("duplicate packet class "
                            + packetType.type.getName() + " on " + packetType + " and "
                            + clashingClass);
                }
            }
        }
    }

    public static PacketType byTag(String tag) {
        return tag == null ? null : Index.BY_TAG.get(tag);
    }

    public static PacketType of(Packet packet) {
        return packet == null ? null : Index.BY_CLASS.get(packet.getClass());
    }
}
