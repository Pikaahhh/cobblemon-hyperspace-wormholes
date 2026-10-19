package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.api.entity.EntitySideDelegate;
import com.cobblemon.mod.common.api.scheduling.SchedulingTracker;
import com.cobblemon.mod.common.client.render.MatrixWrapper;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * Pose state for a floating ball on the client, so Cobblemon's Poke Ball model can animate it like any other posable
 * thing.
 */
public class BallPose extends PosableState implements EntitySideDelegate<FloatingBall> {
    private static final List<String> LOCATORS = List.of("root", "middle", "target", "special", "physical", "head");

    private final FloatingBall ball;
    private final SchedulingTracker scheduling = new SchedulingTracker();

    public BallPose(FloatingBall ball) {
        this.ball = ball;
        for (String locator : LOCATORS) getLocatorStates().put(locator, new MatrixWrapper());
        follow();
    }

    @Override
    public Entity getEntity() {
        return ball;
    }

    @Override
    public SchedulingTracker getSchedulingTracker() {
        return scheduling;
    }

    @Override
    public void updatePartialTicks(float partialTicks) {
    }

    @Override
    public void tick(FloatingBall entity) {
        follow();
    }

    private void follow() {
        updateLocatorPosition(ball.position().add(0, ball.size() / 2, 0));
    }
}
