package com.elfmcys.yesstevemodel.resource;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.audio.AudioCodec;
import com.elfmcys.yesstevemodel.audio.AudioTrackData;
import com.elfmcys.yesstevemodel.client.ClientModelInfo;
import com.elfmcys.yesstevemodel.client.compat.oculus.ShadersTextureType;
import com.elfmcys.yesstevemodel.client.gui.custom.AbstractConfig;
import com.elfmcys.yesstevemodel.client.gui.custom.ExtraAnimationButtons;
import com.elfmcys.yesstevemodel.client.gui.custom.configs.CheckboxConfig;
import com.elfmcys.yesstevemodel.client.gui.custom.configs.RadioConfig;
import com.elfmcys.yesstevemodel.client.gui.custom.configs.RangeConfig;
import com.elfmcys.yesstevemodel.client.model.MainModelData;
import com.elfmcys.yesstevemodel.client.texture.OuterFileTexture;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.Animation;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.AnimationController;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.value.IValue;
import com.elfmcys.yesstevemodel.geckolib3.file.AnimationControllerFile;
import com.elfmcys.yesstevemodel.geckolib3.file.AnimationFile;
import com.elfmcys.yesstevemodel.geckolib3.file.ModelExtraResourcesFile;
import com.elfmcys.yesstevemodel.geckolib3.file.ProjectileModelFiles;
import com.elfmcys.yesstevemodel.geckolib3.file.VehicleModelFiles;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.elfmcys.yesstevemodel.geckolib3.resource.GeckoLibCache;
import com.elfmcys.yesstevemodel.model.format.ServerModelInfo;
import com.elfmcys.yesstevemodel.resource.models.AuthorInfo;
import com.elfmcys.yesstevemodel.resource.models.GeometryDescription;
import com.elfmcys.yesstevemodel.resource.models.MainModelInfo;
import com.elfmcys.yesstevemodel.resource.models.Metadata;
import com.elfmcys.yesstevemodel.resource.models.ModelProperties;
import com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel;
import com.elfmcys.yesstevemodel.util.data.OrderedStringMap;
import com.elfmcys.yesstevemodel.util.data.StringMapPair;
import com.elfmcys.yesstevemodel.util.data.StringPair;
import org.gagravarr.ogg.OggFile;
import org.gagravarr.ogg.OggPacketReader;
import org.gagravarr.opus.OpusFile;
import org.gagravarr.vorbis.VorbisFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reduced 1.16.5 port of OpenYSM 2.6.5 {@code YSMClientMapper.buildParsedBundle}.
 *
 * <p>Turns a decrypted/parsed {@link RawYsmModel} into the {@link ClientModelInfo}
 * consumed by {@code ModelAssemblyFactory.buildAssembly}. Geometry and animation
 * baking are delegated to {@link YSMGeometryBaker} / {@link YSMAnimationBaker}.
 *
 * <p>Deferred (kept self-contained so this compiles without later waves):
 * <ul>
 *   <li>W6 GUI: {@code guiForeground}/{@code guiBackground}/{@code backgroundImages} textures -
 *       empty for now; {@code buildModelInfo} metadata is complete.</li>
 *   <li>Sub-entity (projectile/vehicle) models - empty arrays for now.</li>
 *   <li>JPEG/WebP/AVIF texture decoders (rip.ysm.imagestream) - see {@link #toPng}.</li>
 * </ul>
 */
public final class YSMModelMapper {

    private YSMModelMapper() {
    }

    public static ClientModelInfo buildParsedBundle(RawYsmModel raw, String modelId) {
        Map<String, OuterFileTexture> mainTextures = new LinkedHashMap<>();
        for (RawYsmModel.RawTexture rt : raw.mainEntity.textures.values()) {
            byte[] processedData = toPng(rt.data, rt.imageFormat, rt.width, rt.height);
            OuterFileTexture tex = new OuterFileTexture(processedData);
            Map<ShadersTextureType, OuterFileTexture> suffixTextures = new LinkedHashMap<>();
            for (RawYsmModel.RawTexture.SubTexture sub : rt.subTextures) {
                if (sub.data == null) continue;
                byte[] processedSubData = toPng(sub.data, sub.imageFormat, sub.width, sub.height);
                if (sub.specularType == 1) {
                    suffixTextures.put(ShadersTextureType.NORMAL, new OuterFileTexture(processedSubData));
                } else if (sub.specularType == 2) {
                    suffixTextures.put(ShadersTextureType.SPECULAR, new OuterFileTexture(processedSubData));
                }
            }
            tex.setSuffixTextures(suffixTextures);
            mainTextures.put(rt.name, tex);
        }

        // Author avatar textures, keyed by the image name the metadata screen looks up.
        Map<String, OuterFileTexture> avatarTextures = new LinkedHashMap<>();
        for (RawYsmModel.RawMetadata.Author author : raw.metadata.authors) {
            if (author.avatarImage == null) continue;
            byte[] processedAvatarData = toPng(author.avatarImage.data, author.avatarImage.format,
                    author.avatarImage.width, author.avatarImage.height);
            avatarTextures.put(author.avatarImage.name, new OuterFileTexture(processedAvatarData));
        }
        OrderedStringMap<String, OuterFileTexture> textureMap = buildTextureMap(mainTextures);

        GeometryDescription context = raw.mainEntity.mainModel != null
                ? YSMGeometryBaker.buildContext(raw.mainEntity.mainModel)
                : new GeometryDescription("", 64, 64, 0, 0, new double[0]);
        int textureCount = Math.max(1, raw.mainEntity.textures.size());
        GeoModel mainMesh = YSMGeometryBaker.buildMesh(raw.mainEntity.mainModel, context, textureCount);
        GeoModel armMesh = raw.mainEntity.armModel != null
                ? YSMGeometryBaker.buildMesh(raw.mainEntity.armModel, context, textureCount)
                : mainMesh;
        GeoModel[] meshes = new GeoModel[]{mainMesh, armMesh};

        boolean mergeMultilineExpr = raw.properties.mergeMultilineExpr;
        Map<String, AnimationFile> animations = new LinkedHashMap<>();
        for (Map.Entry<String, RawYsmModel.RawAnimationFile> entry : raw.mainEntity.animationFiles.entrySet()) {
            animations.put(entry.getKey(), new AnimationFile(YSMAnimationBaker.buildAnimations(entry.getValue(), mergeMultilineExpr)));
        }

        List<AnimationControllerFile> controllersList = new ArrayList<>();
        Map<String, AnimationController> controllerMap = YSMAnimationBaker.buildControllers(raw.mainEntity.animationControllers, mergeMultilineExpr);
        if (!controllerMap.isEmpty()) {
            controllersList.add(new AnimationControllerFile(controllerMap));
        }

        MainModelData mainModelData = new MainModelData(meshes, animations, controllersList.toArray(new AnimationControllerFile[0]), textureMap);
        ServerModelInfo modelInfo = buildModelInfo(raw);
        ModelExtraResourcesFile extraResources = buildExtraResources(raw);
        ProjectileModelFiles[] extraItemModels = buildExtraItemModels(raw, context, mergeMultilineExpr);
        VehicleModelFiles[] extraEntityModels = buildExtraEntityModels(raw, context, mergeMultilineExpr);
        // W6: gui extra textures deferred.
        Map<String, OuterFileTexture> extraTextures = new LinkedHashMap<>();

        return new ClientModelInfo(mainModelData, extraItemModels, extraEntityModels, extraResources, modelInfo, avatarTextures, extraTextures);
    }

    /**
     * Port of {@code YSMClientMapper.buildModelInfo}. Pure data assembly - the deserializers
     * already populate every field used here.
     */
    private static ServerModelInfo buildModelInfo(RawYsmModel raw) {
        RawYsmModel.RawMetadata rm = raw.metadata;
        List<AuthorInfo> authors = new ArrayList<>();
        for (RawYsmModel.RawMetadata.Author a : rm.authors) {
            authors.add(new AuthorInfo(a.name, a.role, new OrderedStringMap<>(a.contacts), a.comment));
        }
        Metadata extraInfo = new Metadata(rm.name, rm.tips,
                new StringPair(rm.licenseType, rm.licenseDescription),
                authors.toArray(new AuthorInfo[0]),
                new OrderedStringMap<>(rm.links));

        RawYsmModel.RawProperties rp = raw.properties;
        List<StringMapPair> classifyList = new ArrayList<>();
        for (RawYsmModel.ExtraAnimationClassify rCls : rp.extraAnimationClassifies) {
            classifyList.add(new StringMapPair(rCls.id, new OrderedStringMap<>(rCls.extras)));
        }

        List<ExtraAnimationButtons> buttonsList = new ArrayList<>();
        for (RawYsmModel.ExtraAnimationButton rBtn : rp.extraAnimationButtons) {
            List<AbstractConfig> metaList = new ArrayList<>();
            for (RawYsmModel.ConfigForm form : rBtn.forms) {
                if (CheckboxConfig.TYPE.equals(form.type)) {
                    metaList.add(new CheckboxConfig(form.title, form.description, form.defaultValue));
                } else if (RadioConfig.TYPE.equals(form.type)) {
                    metaList.add(new RadioConfig(form.title, form.description, form.defaultValue,
                            new OrderedStringMap<>(form.labels)));
                } else if (RangeConfig.TYPE.equals(form.type)) {
                    metaList.add(new RangeConfig(form.title, form.description, form.defaultValue,
                            form.step, form.min, form.max));
                }
            }
            buttonsList.add(new ExtraAnimationButtons(rBtn.id, rBtn.name, rBtn.description,
                    metaList.toArray(new AbstractConfig[0])));
        }
        ModelProperties properties = new ModelProperties(rp.heightScale, rp.widthScale, rp.defaultTexture,
                rp.previewAnimation, new OrderedStringMap<>(rp.extraAnimations),
                buttonsList.toArray(new ExtraAnimationButtons[0]),
                classifyList.toArray(new StringMapPair[0]),
                rp.isFree, rp.renderLayersFirst, rp.disablePreviewRotation);

        int bones = 0;
        int cubes = 0;
        int faces = 0;
        if (raw.mainEntity.mainModel != null) {
            bones = raw.mainEntity.mainModel.bones.size();
            for (RawYsmModel.RawBone bone : raw.mainEntity.mainModel.bones) {
                cubes += bone.cubes.size();
                for (RawYsmModel.RawCube cube : bone.cubes) {
                    faces += cube.faces.size();
                }
            }
        }
        MainModelInfo stats = new MainModelInfo(bones, cubes, faces);

        RawYsmModel.RawFooter footer = raw.footer;
        return new ServerModelInfo(extraInfo, properties, stats, footer.version,
                rp.sha256 != null ? rp.sha256 : "",
                footer.extra, footer.time, footer.rand);
    }

    private static ProjectileModelFiles[] buildExtraItemModels(RawYsmModel raw, GeometryDescription context, boolean mergeMultilineExpr) {
        List<ProjectileModelFiles> list = new ArrayList<>();
        for (RawYsmModel.RawSubEntity sub : raw.projectiles.values()) {
            list.add(buildSubEntityHolder(sub, context, 1, mergeMultilineExpr));
        }
        return list.toArray(new ProjectileModelFiles[0]);
    }

    private static VehicleModelFiles[] buildExtraEntityModels(RawYsmModel raw, GeometryDescription context, boolean mergeMultilineExpr) {
        List<VehicleModelFiles> list = new ArrayList<>();
        for (RawYsmModel.RawSubEntity sub : raw.vehicles.values()) {
            list.add(buildSubEntityWrapper(sub, context, 1, mergeMultilineExpr));
        }
        return list.toArray(new VehicleModelFiles[0]);
    }

    private static ProjectileModelFiles buildSubEntityHolder(RawYsmModel.RawSubEntity sub, GeometryDescription context, int textureCount, boolean mergeMultilineExpr) {
        GeoModel mesh = YSMGeometryBaker.buildMesh(sub.model, context, textureCount);
        Map<String, Animation> allAnimations = new LinkedHashMap<>();
        for (RawYsmModel.RawAnimationFile animFile : sub.animationFiles.values()) {
            allAnimations.putAll(YSMAnimationBaker.buildAnimations(animFile, mergeMultilineExpr));
        }
        AnimationFile combinedAnim = new AnimationFile(allAnimations);
        AnimationControllerFile controllers = new AnimationControllerFile(new LinkedHashMap<String, AnimationController>());
        OuterFileTexture texture = firstSubTexture(sub);
        String[] matchIds = sub.matchIds != null ? sub.matchIds : new String[]{sub.identifier};
        return new ProjectileModelFiles(matchIds, mesh, combinedAnim, controllers, texture);
    }

    private static VehicleModelFiles buildSubEntityWrapper(RawYsmModel.RawSubEntity sub, GeometryDescription context, int textureCount, boolean mergeMultilineExpr) {
        GeoModel mesh = YSMGeometryBaker.buildMesh(sub.model, context, textureCount);
        Map<String, Animation> allAnimations = new LinkedHashMap<>();
        for (RawYsmModel.RawAnimationFile animFile : sub.animationFiles.values()) {
            allAnimations.putAll(YSMAnimationBaker.buildAnimations(animFile, mergeMultilineExpr));
        }
        AnimationFile combinedAnim = new AnimationFile(allAnimations);
        AnimationControllerFile controllers = new AnimationControllerFile(new LinkedHashMap<String, AnimationController>());
        OuterFileTexture texture = firstSubTexture(sub);
        String[] matchIds = sub.matchIds != null ? sub.matchIds : new String[]{sub.identifier};
        return new VehicleModelFiles(matchIds, mesh, combinedAnim, controllers, texture);
    }

    private static OuterFileTexture firstSubTexture(RawYsmModel.RawSubEntity sub) {
        if (sub.textures.isEmpty()) return null;
        RawYsmModel.RawTexture rt = sub.textures.values().iterator().next();
        return new OuterFileTexture(toPng(rt.data, rt.imageFormat, rt.width, rt.height));
    }

    private static ModelExtraResourcesFile buildExtraResources(RawYsmModel raw) {
        Map<String, AudioTrackData> sounds = new LinkedHashMap<>();
        for (Map.Entry<String, RawYsmModel.RawDataFile> entry : raw.soundFiles.entrySet()) {
            byte[] data = entry.getValue().data;
            AudioTrackData track = parseAudioTrackData(data);
            if (track != null) sounds.put(entry.getKey(), track);
        }

        Map<String, IValue> functions = new LinkedHashMap<>();
        for (Map.Entry<String, RawYsmModel.RawDataFile> entry : raw.functionFiles.entrySet()) {
            byte[] data = entry.getValue().data;
            if (data == null) continue;
            String molangScript = new String(data, StandardCharsets.UTF_8);
            try {
                functions.put(entry.getKey(), GeckoLibCache.getMolangParser().parseExpression(molangScript, true));
            } catch (Exception ignored) {
            }
        }

        Map<String, Map<String, String>> translations = new LinkedHashMap<>();
        for (Map.Entry<String, RawYsmModel.RawLanguageFile> entry : raw.languageFiles.entrySet()) {
            translations.put(entry.getKey(), entry.getValue().data);
        }

        return new ModelExtraResourcesFile(sounds, functions, translations);
    }

    /**
     * Upstream {@code YSMClientMapper#parseAudioTrackData} (verbatim): sniffs the OGG codec from
     * the header ({@code OpusHead}), reads the sample rate from the codec info, and takes the
     * duration from the last packet's granule position. The raw bytes are stored in a direct
     * buffer (the Opus decoder requires direct input). Malformed tracks are dropped (null).
     */
    private static AudioTrackData parseAudioTrackData(byte[] oggData) {
        if (oggData == null || oggData.length < 8) return null;
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(oggData);
            OggFile oggFile = new OggFile(bais);
            String header = new String(oggData, 0, Math.min(oggData.length, 100), StandardCharsets.US_ASCII);
            boolean isOpus = header.contains("OpusHead");

            AudioCodec codec = isOpus ? AudioCodec.OPUS : AudioCodec.VORBIS;
            int sampleRate;
            if (isOpus) {
                OpusFile opus = new OpusFile(oggFile);
                sampleRate = (int) opus.getInfo().getRate();
            } else {
                VorbisFile vorbis = new VorbisFile(oggFile);
                sampleRate = (int) vorbis.getInfo().getRate();
            }

            OggPacketReader reader = oggFile.getPacketReader();
            long durationSamples = 0;
            var packet = reader.getNextPacket();
            while (packet != null) {
                long granule = packet.getGranulePosition();
                if (granule > 0) durationSamples = granule;
                packet = reader.getNextPacket();
            }

            ByteBuffer directBuf = ByteBuffer.allocateDirect(oggData.length);
            directBuf.put(oggData);
            directBuf.flip();

            return new AudioTrackData(directBuf, codec.ordinal(), sampleRate, durationSamples);
        } catch (Exception e) {
            return null;
        }
    }

    public static OrderedStringMap<String, OuterFileTexture> buildTextureMap(Map<String, OuterFileTexture> textures) {
        if (textures.isEmpty()) {
            return new OrderedStringMap<>(new String[0], new OuterFileTexture[0]);
        }
        String[] keys = textures.keySet().toArray(new String[0]);
        OuterFileTexture[] values = textures.values().toArray(new OuterFileTexture[0]);
        return new OrderedStringMap<>(keys, values);
    }

    private static byte[] toPng(byte[] data, int imageFormat, int width, int height) {
        if (data == null || data.length == 0 || imageFormat == 2) {
            return data;
        }
        try {
            BufferedImage img = null;
            if (imageFormat == -1) {
                if (width > 0 && height > 0 && data.length >= width * height * 4) {
                    img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                    int[] pixels = new int[width * height];
                    for (int i = 0; i < pixels.length; i++) {
                        int r = data[i * 4] & 0xFF;
                        int g = data[i * 4 + 1] & 0xFF;
                        int b = data[i * 4 + 2] & 0xFF;
                        int a = data[i * 4 + 3] & 0xFF;
                        pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
                    }
                    img.setRGB(0, 0, width, height, pixels, 0, width);
                }
            } else {
                // imageFormat 0/1 (PNG/BMP) and 3/4 (JPEG/WebP) all go through ImageIO. WebP needs
                // the TwelveMonkeys plugin, which only registers after an explicit plugin scan.
                ensureImageIoPluginsScanned();
                img = ImageIO.read(new ByteArrayInputStream(data));
            }
            if (img != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, "png", baos);
                return baos.toByteArray();
            }
            // Format 5 (AVIF) has no decoder on this classpath; the texture will fail to upload.
            YesSteveModel.LOGGER.warn("[YSM] No image decoder for texture format {} ({} bytes); leaving raw",
                    imageFormat, data.length);
        } catch (Exception e) {
            YesSteveModel.LOGGER.warn("[YSM] Failed to decode texture (format {}, {} bytes)", imageFormat, data.length, e);
        }
        return data;
    }

    private static volatile boolean imageIoPluginsScanned;

    private static synchronized void ensureImageIoPluginsScanned() {
        if (!imageIoPluginsScanned) {
            ImageIO.scanForPlugins();
            imageIoPluginsScanned = true;
        }
    }
}
