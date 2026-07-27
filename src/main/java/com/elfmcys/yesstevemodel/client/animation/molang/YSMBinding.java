package com.elfmcys.yesstevemodel.client.animation.molang;

import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.client.animation.molang.functions.ysm.*;
import com.elfmcys.yesstevemodel.client.compat.CompatMolangStubs;
import com.elfmcys.yesstevemodel.client.compat.cosmeticarmorreworked.CosmeticArmorHelper;
import com.elfmcys.yesstevemodel.client.compat.touhoulittlemaid.TouhouLittleMaidCompat;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.EntityFrameStateTracker;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.binding.ContextBinding;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.util.StringPool;
import com.elfmcys.yesstevemodel.geckolib3.util.MathInterpolation;
import com.elfmcys.yesstevemodel.util.CameraUtil;
import com.elfmcys.yesstevemodel.util.accessors.ProjectileStateAccessor;
import com.elfmcys.yesstevemodel.util.data.LazySupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Pose;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.AbstractArrowEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.entity.projectile.ProjectileItemEntity;
import net.minecraft.entity.projectile.SpectralArrowEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.Hand;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.world.LightType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.Heightmap;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;

/**
 * Port note (1.20.1 -> 1.16.5):
 * <ul>
 *   <li>Forge registries -> {@code net.minecraft.util.registry.Registry}.</li>
 *   <li>ForgeMod attributes (reach/swim speed/gravity/step height/nametag distance) do not exist in
 *       1.16.5; they report vanilla-equivalent constants (documented per binding below).</li>
 *   <li>Mixin accessors -> direct decompiled-source getters
 *       ({@link AbstractArrowEntity} implements {@link ProjectileStateAccessor},
 *       {@code FishingBobberEntity#isBiting/getHookedIn}, {@code ProjectileItemEntity#invokeGetDefaultItem}).</li>
 *   <li>{@code dump_mods} returns null (no Forge ModList) and Curios item registration is cut.</li>
 *   <li>1.16.5 has no entity freezing, so {@code frozen_ticks} is always 0.</li>
 * </ul>
 */
public class YSMBinding extends ContextBinding {

    public static final LazySupplier<YSMBinding> INSTANCE = new LazySupplier<>(YSMBinding::new);

    /** ParrotEntity variant names; 1.16.5 stores the variant as a raw int (1.19+ has Parrot.Variant). */
    private static final String[] PARROT_VARIANTS = {"red_blue", "blue", "green", "yellow_blue", "gray"};

    /** ForgeMod.BLOCK_REACH default. */
    private static final double DEFAULT_BLOCK_REACH = 4.5D;
    /** ForgeMod.ENTITY_REACH default. */
    private static final double DEFAULT_ENTITY_REACH = 3.0D;
    /** ForgeMod.SWIM_SPEED default. */
    private static final double DEFAULT_SWIM_SPEED = 1.0D;
    /** ForgeMod.ENTITY_GRAVITY default. */
    private static final double DEFAULT_ENTITY_GRAVITY = 0.08D;
    /** ForgeMod.STEP_HEIGHT_ADDITION default. */
    private static final double DEFAULT_STEP_HEIGHT_ADDITION = 0.0D;
    /** ForgeMod.NAMETAG_DISTANCE default. */
    private static final double DEFAULT_NAMETAG_DISTANCE = 64.0D;

    private YSMBinding() {
        function("dump_equipped_item", new DumpEquippedItem());
        function("dump_relative_block", new DumpRelativeBlock());
        var("dump_mods", YSMBinding::dumpMods);
        entityVar("dump_effects", YSMBinding::dumpEffects);
        entityVar("dump_biome", YSMBinding::dumpBiome);
        function("mod_version", new ModVersion());
        function("equipped_enchantment_level", new EquippedEnchantmentLevel());
        function("effect_level", new EffectLevel());

        function("relative_block_name", new RelativeBlockName());
        function("relative_block_name_any", new RelativeBlockNameAny());

        function("bone_rot", new BoneRotation());
        function("bone_pos", new BonePosition());
        function("bone_scale", new BoneScale());
        function("bone_pivot_abs", new BonePivotAbs());

        var("head_yaw", ctx -> ctx.data().netHeadYaw);
        var("head_pitch", ctx -> ctx.data().headPitch);

        var("weather", ctx -> getWeather(ctx.level()));
        var("dimension_name", ctx -> ctx.level().getDimensionKey().getLocation().toString());
        var("fps", ctx -> Minecraft.getFps());
        var("time_delta", ctx -> ctx.geoInstance().getPositionTracker().getTimeDelta() / 20.0f);
        entityVar("ground_speed2", YSMBinding::getGroundSpeed2);

        entityVar("input_vertical", MathInterpolation::getYawInterpolation);
        entityVar("input_horizontal", MathInterpolation::getPitchInterpolation);

        entityVar("person_view", CameraUtil::getCameraType);
        entityVar("rendering_in_paperdoll", ctx -> ModelPreviewRenderer.isExtraPlayer());
        entityVar("rendering_in_inventory", CameraUtil::isThirdPerson);
        entityVar("block_light", ctx -> ctx.level().getLightFor(LightType.BLOCK, ctx.entity().getPosition()));
        entityVar("sky_light", ctx -> ctx.level().getLightFor(LightType.SKY, ctx.entity().getPosition()));
        entityVar("is_passenger", ctx -> ctx.entity().isPassenger());
        entityVar("is_sleep", ctx -> ctx.entity().getPose() == Pose.SLEEPING);
        entityVar("is_sneak", ctx -> ctx.entity().isOnGround() && ctx.entity().getPose() == Pose.CROUCHING);
        entityVar("biome_category", ctx -> getBiomeCategory(ctx.entity()));
        entityVar("is_open_air", ctx -> isOpenAir(ctx.entity()));
        entityVar("eye_in_water", ctx -> ctx.entity().canSwim());
        // 1.16.5 has no powder-snow freezing.
        entityVar("frozen_ticks", ctx -> 0);
        entityVar("air_supply", ctx -> ctx.entity().getAir());
        entityVar("delta_movement_length", ctx -> ctx.entity().getMotion().length());
        livingEntityVar("has_helmet", ctx -> hasEquipment(ctx.entity(), EquipmentSlotType.HEAD));
        livingEntityVar("has_chest_plate", ctx -> hasEquipment(ctx.entity(), EquipmentSlotType.CHEST));
        livingEntityVar("has_leggings", ctx -> hasEquipment(ctx.entity(), EquipmentSlotType.LEGS));
        livingEntityVar("has_boots", ctx -> hasEquipment(ctx.entity(), EquipmentSlotType.FEET));
        livingEntityVar("has_mainhand", ctx -> hasEquipment(ctx.entity(), EquipmentSlotType.MAINHAND));
        livingEntityVar("has_offhand", ctx -> hasEquipment(ctx.entity(), EquipmentSlotType.OFFHAND));
        livingEntityVar("has_elytra", ctx -> !CosmeticArmorHelper.getElytraItem(ctx.entity()).isEmpty());
        livingEntityVar("is_riptide", ctx -> ctx.entity().isSpinAttacking());
        livingEntityVar("armor_value", ctx -> ctx.entity().getTotalArmorValue());
        livingEntityVar("hurt_time", ctx -> ctx.entity().hurtTime);
        livingEntityVar("is_close_eyes", ctx -> isCloseEyes(ctx.animationEvent(), ctx.entity()));
        livingEntityVar("on_ladder", ctx -> ctx.entity().isOnLadder());
        livingEntityVar("ladder_facing", new LadderFacing());
        livingEntityVar("arrow_count", ctx -> ctx.entity().getArrowCountInEntity());
        livingEntityVar("stinger_count", ctx -> ctx.entity().getBeeStingCount());
        livingEntityVar("entity_type", YSMBinding::getEntityTypeName);
        livingEntityVar("is_player", ctx -> "player".equals(getEntityTypeName(ctx)));
        livingEntityVar("is_maid", ctx -> "maid".equals(getEntityTypeName(ctx)));
        livingEntityVar("food_level", YSMBinding::getFoodLevel);

        livingEntityVar("xxa", YSMBinding::getXxa);
        livingEntityVar("yya", YSMBinding::getYya);
        livingEntityVar("zza", YSMBinding::getZza);

        livingEntityVar("mainhand_charged_crossbow", ctx -> isChargedCrossbow(ctx, Hand.MAIN_HAND));
        livingEntityVar("offhand_charged_crossbow", ctx -> isChargedCrossbow(ctx, Hand.OFF_HAND));

        livingEntityVar("is_fishing", YSMBinding::isFishing);
        livingEntityVar("swinging", ctx -> ctx.entity().isSwingInProgress);
        livingEntityVar("swing_time", ctx -> ctx.entity().swingProgressInt);
        livingEntityVar("swinging_arm", ctx -> ctx.entity().swingingHand == Hand.MAIN_HAND ? 0 : 1);
        livingEntityVar("attack_time", ctx -> ctx.entity().getSwingProgress(ctx.animationEvent().getFrameTime()));
        playerEntityVar("texture_name", new TextureName());
        playerEntityVar("first_person_mod_hide", new FirstPersonModHide());

        playerEntityVar("has_left_shoulder_parrot", ctx -> hasShoulderParrot(ctx.entity(), true));
        playerEntityVar("has_right_shoulder_parrot", ctx -> hasShoulderParrot(ctx.entity(), false));

        playerEntityVar("left_shoulder_parrot_variant", ctx -> getShoulderParrotVariant(ctx.entity(), true));
        playerEntityVar("right_shoulder_parrot_variant", ctx -> getShoulderParrotVariant(ctx.entity(), false));

        playerEntityVar("attack_damage", ctx -> ctx.entity().getAttributeValue(Attributes.ATTACK_DAMAGE));
        playerEntityVar("attack_speed", ctx -> ctx.entity().getAttributeValue(Attributes.ATTACK_SPEED));
        playerEntityVar("attack_knockback", ctx -> ctx.entity().getAttributeValue(Attributes.ATTACK_KNOCKBACK));

        playerEntityVar("movement_speed", ctx -> ctx.entity().getAttributeValue(Attributes.MOVEMENT_SPEED));
        playerEntityVar("knockback_resistance", ctx -> ctx.entity().getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        playerEntityVar("luck", ctx -> ctx.entity().getAttributeValue(Attributes.LUCK));
        // No ForgeMod attributes in 1.16.5: report the vanilla-equivalent constants.
        playerEntityVar("block_reach", ctx -> ctx.entity().abilities.isCreativeMode ? 5.0D : DEFAULT_BLOCK_REACH);
        playerEntityVar("entity_reach", ctx -> DEFAULT_ENTITY_REACH);
        playerEntityVar("swim_speed", ctx -> DEFAULT_SWIM_SPEED);
        playerEntityVar("entity_gravity", ctx -> DEFAULT_ENTITY_GRAVITY);
        playerEntityVar("step_height_addition", ctx -> DEFAULT_STEP_HEIGHT_ADDITION);
        playerEntityVar("nametag_distance", ctx -> DEFAULT_NAMETAG_DISTANCE);
        playerEntityVar("in_shield_block_cooldown", YSMBinding::isInShieldBlockCooldown);

        clientPlayerEntityVar("elytra_rot_x", ctx -> Math.toDegrees(ctx.entity().rotateElytraX));
        clientPlayerEntityVar("elytra_rot_y", ctx -> Math.toDegrees(ctx.entity().rotateElytraY));
        clientPlayerEntityVar("elytra_rot_z", ctx -> Math.toDegrees(ctx.entity().rotateElytraZ));

        localPlayerEntityVar("hit_target_id", YSMBinding::getHitTargetId);
        localPlayerEntityVar("hit_target_type", YSMBinding::getHitTargetType);

        function("first_order", new FirstOrderFunction());
        function("second_order", new SecondOrderFunction());

        function("particle", new Particle(false));
        function("abs_particle", new Particle(true));

        function("perlin_noise", new PerlinNoise());

        function("play_sound", new SoundFunction.PlaySoundFunction());
        function("stop_sound", new SoundFunction.StopSoundFunction());
        function("stop_all_sounds", new SoundFunction.StopAllSoundsFunction());

        function("keyboard", new InputKeyDetectionFunction.Keyboard());
        function("mouse", new InputKeyDetectionFunction.Mouse());
        function(MolangEventDispatcher.SYNC, new Sync());
        function(MolangEventDispatcher.DEFER, new Defer());
        projectileEntityVar("projectile_owner", ctx -> ctx.createChild(ctx.entity().func_234616_v_()));
        throwableProjectileEntityVar("throwable_item", YSMBinding::getThrowableItemId);
        fishHookEntityVar("hooked_in", YSMBinding::getHookedEntityType);
        fishHookEntityVar("is_biting", ctx -> ctx.entity().isBiting());
        abstractArrowEntityVar("on_ground_time", ctx -> ((ProjectileStateAccessor) ctx.entity()).getInGroundTime());
        abstractArrowEntityVar("in_ground", ctx -> ((ProjectileStateAccessor) ctx.entity()).isInGround());
        abstractArrowEntityVar("is_spectral_arrow", ctx -> ctx.entity() instanceof SpectralArrowEntity);
        abstractArrowEntityVar("shoot_item_id", ctx -> ((ProjectileStateAccessor) ctx.entity()).getOwnerItemId());
        // Upstream: CuriosCompat.registerCuriosItems(this). Curios itself is cut, but the symbols
        // must still resolve (see CompatMolangStubs) - these are upstream's mod-absent values.
        CompatMolangStubs.registerCurios(this);
    }

    private static String getHitTargetId(IContext<ClientPlayerEntity> context) {
        RayTraceResult hitResult = Minecraft.getInstance().objectMouseOver;
        if (hitResult instanceof BlockRayTraceResult) {
            BlockRayTraceResult blockHitResult = (BlockRayTraceResult) hitResult;
            ClientWorld clientLevel = Minecraft.getInstance().world;
            if (blockHitResult.getType() == RayTraceResult.Type.MISS || clientLevel == null) {
                return StringPool.EMPTY;
            }
            ResourceLocation key = Registry.BLOCK.getKey(clientLevel.getBlockState(blockHitResult.getPos()).getBlock());
            if (key != null) {
                return key.toString();
            }
            return StringPool.EMPTY;
        }
        if (hitResult instanceof EntityRayTraceResult) {
            ResourceLocation key2 = Registry.ENTITY_TYPE.getKey(((EntityRayTraceResult) hitResult).getEntity().getType());
            if (key2 != null) {
                return key2.toString();
            }
            return StringPool.EMPTY;
        }
        return StringPool.EMPTY;
    }

    private static String getHitTargetType(IContext<ClientPlayerEntity> context) {
        RayTraceResult hitResult = Minecraft.getInstance().objectMouseOver;
        if (hitResult == null) {
            return StringPool.EMPTY;
        }
        // Upstream falls through both switch branches and always returns EMPTY; kept verbatim.
        return StringPool.EMPTY;
    }

    private static String getHookedEntityType(IContext<FishingBobberEntity> context) {
        Entity entity = context.entity().getHookedIn();
        if (entity != null) {
            ResourceLocation key = Registry.ENTITY_TYPE.getKey(entity.getType());
            if (key != null) {
                return key.toString();
            }
        }
        return StringPool.EMPTY;
    }

    private static String getThrowableItemId(IContext<ProjectileItemEntity> context) {
        ProjectileItemEntity throwableItemProjectile = context.entity();
        if (throwableItemProjectile != null) {
            ResourceLocation key = Registry.ITEM.getKey(throwableItemProjectile.invokeGetDefaultItem());
            if (key != null) {
                return key.toString();
            }
        }
        return StringPool.EMPTY;
    }

    private static float getGroundSpeed2(IContext<Entity> context) {
        EntityFrameStateTracker<?> tracker = context.geoInstance().getPositionTracker();
        Vector3d delta = tracker.getPositionDelta();
        return (20.0f * MathHelper.sqrt((float) ((delta.x * delta.x) + (delta.z * delta.z)))) / tracker.getTimeDelta();
    }

    private static float getXxa(IContext<LivingEntity> context) {
        AnimatableEntity<?> animatable = context.geoInstance();
        if (animatable instanceof PlayerCapability) {
            PlayerCapability playerCapability = (PlayerCapability) animatable;
            if (!playerCapability.isLocalPlayerModel()) {
                return playerCapability.getPositionTracker().getStrafeInput();
            }
        }
        return context.entity().moveStrafing;
    }

    private static float getYya(IContext<LivingEntity> context) {
        AnimatableEntity<?> animatable = context.geoInstance();
        if (animatable instanceof PlayerCapability) {
            PlayerCapability playerCapability = (PlayerCapability) animatable;
            if (!playerCapability.isLocalPlayerModel()) {
                return playerCapability.getPositionTracker().getVerticalInput();
            }
        }
        return context.entity().moveVertical;
    }

    private static float getZza(IContext<LivingEntity> context) {
        AnimatableEntity<?> animatable = context.geoInstance();
        if (animatable instanceof PlayerCapability) {
            PlayerCapability playerCapability = (PlayerCapability) animatable;
            if (!playerCapability.isLocalPlayerModel()) {
                return playerCapability.getPositionTracker().getForwardInput();
            }
        }
        return context.entity().moveForward;
    }

    private static boolean isInShieldBlockCooldown(IContext<PlayerEntity> context) {
        AnimatableEntity<?> animatable = context.geoInstance();
        if (animatable instanceof PlayerCapability) {
            return ((PlayerCapability) animatable).getPositionTracker().isShieldBlocking();
        }
        return false;
    }

    private static boolean isFishing(IContext<LivingEntity> context) {
        LivingEntity livingEntity = context.entity();
        if (livingEntity instanceof PlayerEntity) {
            return ((PlayerEntity) livingEntity).fishingBobber != null;
        }
        return TouhouLittleMaidCompat.isMaidSitting(livingEntity);
    }

    private static boolean isChargedCrossbow(IContext<LivingEntity> context, Hand hand) {
        ItemStack itemInHand = context.entity().getHeldItem(hand);
        return itemInHand.getItem() == Items.CROSSBOW && CrossbowItem.isCharged(itemInHand);
    }

    private static String getEntityTypeName(IContext<LivingEntity> context) {
        LivingEntity livingEntity = context.entity();
        if (livingEntity instanceof PlayerEntity) {
            return "player";
        }
        ResourceLocation key = Registry.ENTITY_TYPE.getKey(livingEntity.getType());
        if (key == null) {
            return StringPool.EMPTY;
        }
        if ("touhou_little_maid".equals(key.getNamespace()) && "maid".equals(key.getPath())) {
            return "maid";
        }
        return key.toString();
    }

    private static Object getFoodLevel(IContext<LivingEntity> context) {
        AnimatableEntity<?> animatable = context.geoInstance();
        if (animatable instanceof PlayerCapability) {
            PlayerCapability playerCapability = (PlayerCapability) animatable;
            if (!playerCapability.isLocalPlayerModel()) {
                return Integer.valueOf(playerCapability.getPositionTracker().getFoodLevel());
            }
        }
        LivingEntity livingEntity = context.entity();
        if (livingEntity instanceof PlayerEntity) {
            return Integer.valueOf(((PlayerEntity) livingEntity).getFoodStats().getFoodLevel());
        }
        return 20;
    }

    private static boolean isCloseEyes(AnimationEvent<?> event, LivingEntity livingEntity) {
        float f = (event.getCurrentTick() + (Math.abs(livingEntity.getUniqueID().getLeastSignificantBits()) % 10)) % 90.0f;
        return livingEntity.isSleeping() || (f > 85.0f && f < 90.0f);
    }

    private static boolean hasEquipment(LivingEntity livingEntity, EquipmentSlotType slot) {
        return !CosmeticArmorHelper.getArmorItem(livingEntity, slot).isEmpty();
    }

    private static int getWeather(ClientWorld clientLevel) {
        if (clientLevel.isThundering()) {
            return 2;
        }
        if (clientLevel.isRaining()) {
            return 1;
        }
        return 0;
    }

    @Deprecated
    private static String getBiomeCategory(Entity entity) {
        return null;
    }

    private static Object dumpMods(IContext<?> context) {
        // No Forge ModList in this runtime; upstream lists every installed mod here.
        return null;
    }

    private static Object dumpEffects(IContext<Entity> context) {
        Collection<EffectInstance> activeEffects;
        if (!context.isDebugMode()) {
            return null;
        }
        if (context.entity() instanceof net.minecraft.entity.projectile.ArrowEntity) {
            activeEffects = ((net.minecraft.entity.projectile.ArrowEntity) context.entity()).getCustomPotionEffects();
        } else if (context.entity() instanceof LivingEntity) {
            activeEffects = ((LivingEntity) context.entity()).getActivePotionEffects();
        } else {
            return null;
        }
        for (EffectInstance effectInstance : activeEffects) {
            ResourceLocation key = Registry.EFFECTS.getKey(effectInstance.getPotion());
            context.logWarningComponent(new StringTextComponent("Effect: display ")
                    .append(copyOnClickText(effectInstance.getPotion().getDisplayName().getStringTruncated(99)))
                    .append(new StringTextComponent("  name ")
                            .append(copyOnClickText(key == null ? StringPool.EMPTY : key.toString())))
                    .appendString("  lv=")
                    .appendString(String.valueOf(effectInstance.getAmplifier() + 1)));
        }
        return null;
    }

    private static Object dumpBiome(IContext<Entity> context) {
        if (!context.isDebugMode()) {
            return null;
        }
        Entity entity = context.entity();
        Biome biome = entity.world.getBiome(entity.getPosition());
        // 1.16.5 biomes carry no tags; only the registry name is dumpable.
        Optional<ResourceLocation> key = context.level().func_241828_r().getRegistry(Registry.BIOME_KEY)
                .getOptionalKey(biome).map(RegistryKey::getLocation);
        key.ifPresent(resourceLocation -> context.logWarningComponent(
                new StringTextComponent("Name ").append(copyOnClickText(resourceLocation.toString()))));
        return null;
    }

    /** 1.16.5 has no ComponentUtils.copyOnClickText; same style/click/hover combination rebuilt by hand. */
    private static ITextComponent copyOnClickText(String text) {
        return new StringTextComponent(text).setStyle(Style.EMPTY
                .setInsertion(text)
                .setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, text))
                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new StringTextComponent(text))));
    }

    private static boolean isOpenAir(Entity entity) {
        BlockPos blockPos = entity.getPosition();
        return entity.world.canBlockSeeSky(blockPos)
                && entity.world.getHeight(Heightmap.Type.MOTION_BLOCKING, blockPos).getY() <= blockPos.getY();
    }

    public static String getShoulderParrotVariant(PlayerEntity player, boolean left) {
        CompoundNBT shoulderEntity = left ? player.getLeftShoulderEntity() : player.getRightShoulderEntity();
        return EntityType.byKey(shoulderEntity.getString("id"))
                .filter(entityType -> entityType == EntityType.PARROT)
                .map(entityType -> parrotVariantName(shoulderEntity.getInt("Variant")))
                .orElse("empty");
    }

    private static String parrotVariantName(int variant) {
        return PARROT_VARIANTS[MathHelper.clamp(variant, 0, PARROT_VARIANTS.length - 1)].toLowerCase(Locale.ENGLISH);
    }

    private static boolean hasShoulderParrot(PlayerEntity player, boolean left) {
        return !(left ? player.getLeftShoulderEntity() : player.getRightShoulderEntity()).isEmpty();
    }
}
