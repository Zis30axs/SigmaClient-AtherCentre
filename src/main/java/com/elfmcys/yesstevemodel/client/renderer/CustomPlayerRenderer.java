package com.elfmcys.yesstevemodel.client.renderer;

import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.capability.PlayerCapabilityProvider;
import com.elfmcys.yesstevemodel.client.compat.touhoulittlemaid.TouhouLittleMaidCompat;
import com.elfmcys.yesstevemodel.client.compat.gun.swarfare.SWarfareCompat;
import com.elfmcys.yesstevemodel.client.entity.PlayerPreviewEntity;
import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import com.elfmcys.yesstevemodel.client.renderer.layer.CustomPlayerArmorLayer;
import com.elfmcys.yesstevemodel.client.renderer.layer.CustomPlayerElytraLayer;
import com.elfmcys.yesstevemodel.client.renderer.layer.CustomPlayerHeldItemLayer;
import com.elfmcys.yesstevemodel.client.renderer.layer.CustomPlayerParrotLayer;
import com.elfmcys.yesstevemodel.event.api.SpecialPlayerRenderEvent;
import com.elfmcys.yesstevemodel.geckolib3.geo.GeoReplacedEntityRenderer;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

public class CustomPlayerRenderer extends GeoReplacedEntityRenderer<PlayerEntity, CustomPlayerEntity> {

    private ResourceLocation currentTexture;

    public CustomPlayerRenderer(EntityRendererManager renderManager) {
        super(renderManager);
        addLayerRenderer(new CustomPlayerHeldItemLayer(this));
        addLayerRenderer(new CustomPlayerElytraLayer(this));
        addLayerRenderer(new CustomPlayerParrotLayer(this));
        addLayerRenderer(new CustomPlayerArmorLayer(this));
    }

    public void render(PlayerEntity player, float entityYaw, float partialTick, MatrixStack poseStack, IRenderTypeBuffer bufferSource, int packedLight) {
        PlayerCapability capability;
        if (SWarfareCompat.isPlayerAiming(player) || (capability = player.getCapability(PlayerCapabilityProvider.PLAYER_CAP).orElse(null)) == null) {
            return;
        }
        capability.tickModel();
        SpecialPlayerRenderEvent renderEvent = new SpecialPlayerRenderEvent(player, capability, capability.getModelId());
        this.currentTexture = renderEvent.getTextureLocation();
        renderEntityWithTexture(capability, renderEvent.getTextureLocation(), entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public boolean canRenderName(PlayerEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPlayerEntity clientPlayer = minecraft.player;
        double distSq = this.renderManager.getDistanceToCamera(entity.getPosX(), entity.getPosY(), entity.getPosZ());
        float nameRenderDistance = entity.isSneaking() ? 32.0f : 64.0f;
        if (distSq >= nameRenderDistance * nameRenderDistance || clientPlayer == null) {
            return false;
        }
        boolean visible = !entity.isInvisibleToPlayer(clientPlayer);
        if (entity != clientPlayer) {
            Team team = entity.getTeam();
            Team team2 = clientPlayer.getTeam();
            if (team != null) {
                Team.Visible visibility = team.getNameTagVisibility();
                if (visibility == Team.Visible.ALWAYS) return visible;
                if (visibility == Team.Visible.NEVER) return false;
                if (visibility == Team.Visible.HIDE_FOR_OTHER_TEAMS) return team2 == null ? visible : team.isSameTeam(team2) && (team.getSeeFriendlyInvisiblesEnabled() || visible);
                if (visibility == Team.Visible.HIDE_FOR_OWN_TEAM) return team2 == null ? visible : !team.isSameTeam(team2) && visible;
            }
        }
        return Minecraft.isGuiEnabled() && entity != minecraft.getRenderViewEntity() && visible && !entity.isBeingRidden();
    }

    @NotNull
    public ResourceLocation getTextureLocation(PlayerEntity player) {
        return this.currentTexture == null ? player.getCapability(PlayerCapabilityProvider.PLAYER_CAP).map(PlayerCapability::getTextureLocation).orElse(new ResourceLocation("missingno")) : this.currentTexture;
    }

    public void renderNameTag(PlayerEntity player, ITextComponent displayName, MatrixStack poseStack, IRenderTypeBuffer bufferSource, int packedLight) {
        if (PlayerPreviewEntity.isPreviewPlayer(player)) {
            return;
        }
        double distSq = this.renderManager.getDistanceToCamera(player.getPosX(), player.getPosY(), player.getPosZ());
        poseStack.push();
        if (distSq < 100.0d) {
            Scoreboard scoreboard = player.getWorldScoreboard();
            ScoreObjective displayObjective = scoreboard.getObjectiveInDisplaySlot(2);
            if (displayObjective != null) {
                super.renderNameTag(player, new StringTextComponent(Integer.toString(scoreboard.getOrCreateScore(player.getScoreboardName(), displayObjective).getScorePoints()) + " ").append(displayObjective.getDisplayName()), poseStack, bufferSource, packedLight);
                poseStack.translate(0.0d, 0.25875d, 0.0d);
            }
        }
        super.renderNameTag(player, displayName, poseStack, bufferSource, packedLight);
        poseStack.pop();
    }

    @Override
    public void setupRotations(PlayerEntity player, MatrixStack poseStack, float ageInTicks, float rotationYaw, float partialTicks) {
        super.setupRotations(player, poseStack, ageInTicks, rotationYaw, partialTicks);
        Entity vehicle = player.getRidingEntity();
        if (TouhouLittleMaidCompat.isSimplePlanesEntity(vehicle) || TouhouLittleMaidCompat.isImmersiveAircraftEntity(vehicle)) {
            poseStack.translate(0.0d, 0.5d, 0.0d);
        }
    }
}