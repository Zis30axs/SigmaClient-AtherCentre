package net.minecraft.entity;

/**
 * Minimal runnable pure-function checks for the bubble-column velocity math in
 * {@link ModernMovementPhysics}. Deliberately has no JUnit dependency and does
 * not start Minecraft.
 *
 * <p>Run after {@code mvn -q compile}:
 * <pre>
 * java -cp target/classes;target/test-classes net.minecraft.entity.BubbleColumnVelocityTest
 * </pre>
 */
public final class BubbleColumnVelocityTest {
    private static int checks;
    private static int failures;

    private BubbleColumnVelocityTest() {
    }

    public static void main(String[] args) {
        soulSandUpwards();
        magmaDownwards();
        clamps();
        singleApplicationMatchesVanilla();
        statelessness();

        System.out.println();
        if (failures == 0) {
            System.out.println("BubbleColumnVelocityTest PASSED (" + checks + " checks)");
        } else {
            System.out.println("BubbleColumnVelocityTest FAILED: " + failures + "/" + checks);
            System.exit(1);
        }
    }

    private static void soulSandUpwards() {
        // soul sand bubbles: drag=false, velocity increases
        check("soul sand submerged: 0.3 -> min(0.7, 0.3+0.06)",
                eq(ModernMovementPhysics.computeBubbleColumnY(0.3, false, false), 0.36D));
        check("soul sand air above: 0.3 -> min(1.8, 0.3+0.1)",
                eq(ModernMovementPhysics.computeBubbleColumnY(0.3, false, true), 0.4D));
        check("soul sand submerged: 0.0 -> 0.06",
                eq(ModernMovementPhysics.computeBubbleColumnY(0.0, false, false), 0.06D));
        check("soul sand air above: 0.0 -> 0.1",
                eq(ModernMovementPhysics.computeBubbleColumnY(0.0, false, true), 0.1D));
    }

    private static void magmaDownwards() {
        // magma bubbles: drag=true, velocity decreases
        check("magma submerged: 0.3 -> max(-0.3, 0.3-0.03)",
                eq(ModernMovementPhysics.computeBubbleColumnY(0.3, true, false), 0.27D));
        check("magma air above: 0.3 -> max(-0.9, 0.3-0.03)",
                eq(ModernMovementPhysics.computeBubbleColumnY(0.3, true, true), 0.27D));
        check("magma submerged: 0.0 -> -0.03",
                eq(ModernMovementPhysics.computeBubbleColumnY(0.0, true, false), -0.03D));
        check("magma air above: 0.0 -> -0.03",
                eq(ModernMovementPhysics.computeBubbleColumnY(0.0, true, true), -0.03D));
    }

    private static void clamps() {
        check("submerged upward clamp at 0.7",
                eq(ModernMovementPhysics.computeBubbleColumnY(10.0, false, false), 0.7D));
        check("air-above upward clamp at 1.8",
                eq(ModernMovementPhysics.computeBubbleColumnY(10.0, false, true), 1.8D));
        check("submerged downward clamp at -0.3",
                eq(ModernMovementPhysics.computeBubbleColumnY(-10.0, true, false), -0.3D));
        check("air-above downward clamp at -0.9",
                eq(ModernMovementPhysics.computeBubbleColumnY(-10.0, true, true), -0.9D));
    }

    private static void singleApplicationMatchesVanilla() {
        // 1.21.2+ applies the effect exactly once to the final movement vector:
        // the 0.3 swim-hop candidate becomes 0.36 for a submerged soul-sand
        // column. Applying it again would give 0.42 - the double-application
        // bug the modern branch must avoid.
        double once = ModernMovementPhysics.computeBubbleColumnY(0.3, false, false);
        double twice = ModernMovementPhysics.computeBubbleColumnY(once, false, false);
        check("single application yields 0.36", eq(once, 0.36D));
        check("second application would differ (0.42), proving once-per-tick matters",
                !eq(once, twice) && eq(twice, 0.42D));
    }

    private static void statelessness() {
        // No static per-player/per-connection state: interleaved computations
        // for two independent "players" (upward vs downward columns) must not
        // affect each other.
        double soulSand = ModernMovementPhysics.computeBubbleColumnY(0.3, false, false);
        double magma = ModernMovementPhysics.computeBubbleColumnY(0.3, true, false);
        double soulSandAgain = ModernMovementPhysics.computeBubbleColumnY(0.3, false, false);
        check("repeated call returns identical result", eq(soulSand, soulSandAgain));
        check("independent computations do not share state",
                eq(soulSand, 0.36D) && eq(magma, 0.27D));
    }

    private static boolean eq(double a, double b) {
        return Math.abs(a - b) < 1.0E-9D;
    }

    private static void check(String name, boolean ok) {
        checks++;

        if (!ok) {
            failures++;
            System.out.println("FAIL: " + name);
        }
    }
}
