package net.dto;

// Why a direct challenge could not be delivered. The spec asks for "an appropriate error" when the
// username is invalid or the user is offline; these are the cases worth telling them apart for.
public enum ChallengeRejectReason {
    NO_SUCH_USER,
    OFFLINE,
    IN_MATCH,
    SELF,
    ALREADY_PENDING
}
