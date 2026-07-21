package models.news;

// Category/source of a news entry. This is the seam that keeps the News model open for later phases:
// network messages pushed from the server and status/graphic updates from other players slot in as
// their own types, so nothing that already produces or renders news has to change to support them.
public enum NewsType {
    SYSTEM,            // generic system announcement
    PLANT_UNLOCK,      // a plant was unlocked
    ZOMBIE_DISCOVERY,  // a new zombie was encountered in a level
    MINIGAME_UNLOCK,   // a new mini-game level became available
    LEVEL_UNLOCK,      // a new campaign level became available
    NETWORK,           // future phase: a message pushed from the network / server
    PLAYER_UPDATE      // future phase: a status or graphic update from another player
}
