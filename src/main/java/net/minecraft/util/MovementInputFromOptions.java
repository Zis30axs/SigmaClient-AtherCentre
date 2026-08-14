package net.minecraft.util;

import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveInput;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveButton;
import com.mentalfrostbyte.jello.gui.base.JelloPortal;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.GameSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.entity.ModernMovementPhysics;
import net.minecraft.entity.SneakMovementDebug;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.math.MathHelper;
import team.sdhq.eventBus.EventBus;

public class MovementInputFromOptions extends MovementInput {
    private final GameSettings gameSettings;

    public MovementInputFromOptions(GameSettings gameSettings) {
        this.gameSettings = gameSettings;
    }

    /**
     * 1.19+ scales the sneaking slowdown with the Swift Sneak enchantment:
     * clamp(0.3 + 0.15 * level, 0, 1). Older targets always use 0.3.
     */
    private static float getSneakSlowdownFactor() {
        if (JelloPortal.getVersion().olderThan(ProtocolVersion.v1_19)) {
            return 0.3F;
        }

        ClientPlayerEntity player = Minecraft.getInstance().player;

        if (player == null) {
            return 0.3F;
        }

        return MathHelper.clamp(0.3F + 0.15F * (float) getSwiftSneakLevel(player), 0.0F, 1.0F);
    }

    private static int getSwiftSneakLevel(ClientPlayerEntity player) {
        ItemStack leggings = player.inventory.armorItemInSlot(1);
        CompoundNBT tag = leggings.getTag();

        if (tag == null) {
            return 0;
        }

        // Swift Sneak is unknown to the 1.16 registry, so ViaBackwards moves it out of
        // "Enchantments" into a prefixed backup list - scan every *Enchantments key
        for (String key : tag.keySet()) {
            if (!key.endsWith("Enchantments")) {
                continue;
            }

            INBT nbt = tag.get(key);

            if (!(nbt instanceof ListNBT)) {
                continue;
            }

            ListNBT enchantments = (ListNBT) nbt;

            for (int i = 0; i < enchantments.size(); ++i) {
                CompoundNBT enchantment = enchantments.getCompound(i);

                if (enchantment.getString("id").endsWith("swift_sneak")) {
                    return enchantment.getShort("lvl");
                }
            }
        }

        return 0;
    }

    public void tickMovement(boolean forcedDown) {
        if (ModernMovementPhysics.shouldUseGrimVanillaMovement()) {
            tickVanillaMovement(forcedDown);
            return;
        }

        moveForward = 0.0f;
        moveStrafe = 0.0f;

        final EventMoveButton eventMoveButton = new EventMoveButton(
                this.gameSettings.keyBindForward.isKeyDown(),
                this.gameSettings.keyBindBack.isKeyDown(),
                this.gameSettings.keyBindLeft.isKeyDown(),
                this.gameSettings.keyBindRight.isKeyDown(),
                this.gameSettings.keyBindJump.isKeyDown(),
                this.gameSettings.keyBindSneak.isKeyDown()
        );
        EventBus.call(eventMoveButton);

        this.forwardKeyDown = eventMoveButton.forward;
        this.backKeyDown = eventMoveButton.back;
        this.leftKeyDown = eventMoveButton.left;
        this.rightKeyDown = eventMoveButton.right;

        if (eventMoveButton.forward) {
            ++this.moveForward;
        }

        if (eventMoveButton.back) {
            --this.moveForward;
        }

        if (eventMoveButton.left) {
            ++this.moveStrafe;
        }

        if (eventMoveButton.right) {
            --this.moveStrafe;
        }

        this.jump = eventMoveButton.jump;
        this.sneaking = eventMoveButton.sneak;
        SneakMovementDebug.captureRawInput(this.moveForward, this.moveStrafe);

        final EventMoveInput eventMoveInput = new EventMoveInput(this.moveForward, this.moveStrafe, this.jump, this.sneaking, getSneakSlowdownFactor());
        EventBus.call(eventMoveInput);

        this.moveStrafe = eventMoveInput.strafe;
        this.moveForward = eventMoveInput.forward;

        this.jump = eventMoveInput.jumping;
        this.sneaking = eventMoveInput.sneaking;

        // ViaFP MixinKeyboardInput (high-to-low): strip Vec2.normalized() on <=1.21.4.
        // Upgrade path (low-to-high): on 1.21.5+ keep modern KeyboardInput normalize
        // BEFORE sneak/item slowdown. Skipping it leaves raw (1,1)*0.3 into square
        // compensation and makes sneak-diagonal ~sqrt(2) too fast (Grim Simulation).
        ModernMovementPhysics.normalizeRaw1_21_5MovementInput(this);
        SneakMovementDebug.captureNormalizedInput(this.moveForward, this.moveStrafe);

        if (shouldApplySneakSlowdown(forcedDown, this.sneaking)) {
            this.moveStrafe *= eventMoveInput.sneakFactor;
            this.moveForward *= eventMoveInput.sneakFactor;
        }
        SneakMovementDebug.captureSneakInput(this.moveForward, this.moveStrafe);
    }

    /**
     * <=1.14.4 applies the sneak slowdown from the live sneak key of this tick,
     * only 1.15+ uses the (one tick delayed) crouching pose.
     */
    private static boolean shouldApplySneakSlowdown(boolean forcedDown, boolean liveSneaking) {
        if (JelloPortal.getVersion().olderThanOrEqualTo(ProtocolVersion.v1_13_2)) {
            return liveSneaking;
        }

        if (JelloPortal.getVersion().olderThanOrEqualTo(ProtocolVersion.v1_14_4)) {
            return liveSneaking || forcedDown;
        }

        return forcedDown;
    }

    private void tickVanillaMovement(boolean forcedDown) {
        this.moveForward = 0.0F;
        this.moveStrafe = 0.0F;
        this.forwardKeyDown = this.gameSettings.keyBindForward.isKeyDown();
        this.backKeyDown = this.gameSettings.keyBindBack.isKeyDown();
        this.leftKeyDown = this.gameSettings.keyBindLeft.isKeyDown();
        this.rightKeyDown = this.gameSettings.keyBindRight.isKeyDown();

        if (this.forwardKeyDown) {
            ++this.moveForward;
        }

        if (this.backKeyDown) {
            --this.moveForward;
        }

        if (this.leftKeyDown) {
            ++this.moveStrafe;
        }

        if (this.rightKeyDown) {
            --this.moveStrafe;
        }

        this.jump = this.gameSettings.keyBindJump.isKeyDown();
        this.sneaking = this.gameSettings.keyBindSneak.isKeyDown();
        SneakMovementDebug.captureRawInput(this.moveForward, this.moveStrafe);

        // Same 1.21.5+ KeyboardInput diagonal normalize as tickMovement (before sneak).
        ModernMovementPhysics.normalizeRaw1_21_5MovementInput(this);
        SneakMovementDebug.captureNormalizedInput(this.moveForward, this.moveStrafe);

        if (shouldApplySneakSlowdown(forcedDown, this.sneaking)) {
            float sneakFactor = getSneakSlowdownFactor();
            this.moveStrafe *= sneakFactor;
            this.moveForward *= sneakFactor;
        }
        SneakMovementDebug.captureSneakInput(this.moveForward, this.moveStrafe);
    }
}
