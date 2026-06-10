package models.entities.collectibles;

import models.entities.Entity;

public abstract class Collectible extends Entity {
    protected long expireTime;
    public void collect(){};
}
