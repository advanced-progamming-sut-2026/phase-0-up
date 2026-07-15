package models.templates;

// The kind of a per-level upgrade entry. Mirrors the " " field of each entry in a plant's
// "upgrades" array. The BUFF_* kinds fold into the plant's effective stats; SPECIAL_MECHANIC is
// routed by its specialTag to a plant/ability specific tweak (see UpgradeResolver).
public enum UpgradeType {
    BUFF_ACTION_INTERVAL,
    BUFF_HP,
    BUFF_COST,
    BUFF_DAMAGE,
    BUFF_RECHARGE,
    SPECIAL_MECHANIC
}
