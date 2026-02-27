package sxy.baritoneextras.mobavoidance;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;

public enum ThreatType {
    CREEPER(16, 10, Response.FLEE),
    SKELETON(20, 6, Response.SEEK_COVER),
    ZOMBIE(12, 3, Response.ENGAGE),
    SPIDER(12, 4, Response.ENGAGE),
    ENDERMAN(5, 1, Response.IGNORE),
    WITCH(18, 7, Response.FLEE),
    CREAKING(16, 5, Response.FLEE),
    BABY_ZOMBIE(16, 8, Response.FLEE),
    PILLAGER(20, 6, Response.SEEK_COVER),
    VINDICATOR(16, 9, Response.FLEE),
    GENERIC_HOSTILE(12, 5, Response.FLEE);

    public final int avoidanceRadius;
    public final int baseThreatScore;
    public final Response defaultResponse;

    ThreatType(int avoidanceRadius, int baseThreatScore, Response defaultResponse) {
        this.avoidanceRadius = avoidanceRadius;
        this.baseThreatScore = baseThreatScore;
        this.defaultResponse = defaultResponse;
    }

    public static ThreatType classify(Entity entity) {
        if (entity instanceof Creeper) {
            return CREEPER;
        }
        if (entity instanceof Skeleton || entity instanceof Stray || entity instanceof WitherSkeleton) {
            return SKELETON;
        }
        if (entity instanceof Zombie zombie) {
            if (zombie.isBaby()) {
                return BABY_ZOMBIE;
            }
            return ZOMBIE;
        }
        if (entity instanceof Spider) {
            return SPIDER;
        }
        if (entity instanceof EnderMan) {
            return ENDERMAN;
        }
        if (entity instanceof Witch) {
            return WITCH;
        }
        if (entity instanceof Creaking) {
            return CREAKING;
        }
        if (entity instanceof Pillager) {
            return PILLAGER;
        }
        if (entity instanceof Vindicator) {
            return VINDICATOR;
        }
        if (entity instanceof PiglinBrute) {
            return VINDICATOR;
        }
        return GENERIC_HOSTILE;
    }

    public enum Response {
        FLEE,
        ENGAGE,
        SEEK_COVER,
        IGNORE
    }
}
