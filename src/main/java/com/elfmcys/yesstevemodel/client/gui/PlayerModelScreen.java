package com.elfmcys.yesstevemodel.client.gui;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.capability.PlayerCapabilityProvider;
import com.elfmcys.yesstevemodel.client.ClientModelManager;
import com.elfmcys.yesstevemodel.client.StarModels;
import com.elfmcys.yesstevemodel.client.entity.PlayerPreviewEntity;
import com.elfmcys.yesstevemodel.client.gui.button.FlatColorButton;
import com.elfmcys.yesstevemodel.client.gui.button.IconButton;
import com.elfmcys.yesstevemodel.client.gui.button.ModIconButton;
import com.elfmcys.yesstevemodel.client.gui.button.ModelButton;
import com.elfmcys.yesstevemodel.client.gui.button.PackIconButton;
import com.elfmcys.yesstevemodel.client.input.PlayerModelToggleKey;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.util.StringPool;
import com.elfmcys.yesstevemodel.resource.models.AuthorInfo;
import com.elfmcys.yesstevemodel.resource.models.Metadata;
import com.elfmcys.yesstevemodel.resource.models.ModelPackData;
import com.elfmcys.yesstevemodel.util.FileTypeUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.shiroha.mmdskin.ui.ScissorCompat;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.InventoryScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.gui.widget.button.CheckboxButton;
import net.minecraft.client.audio.SimpleSound;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Port of upstream {@code client/gui/PlayerModelScreen} (1.20.1): the model picker - search box,
 * folder navigation, category tabs, model grid with live previews, paper-doll of the local player.
 *
 * <p>Deviations (all server-sync or mod-integration cuts):
 * <ul>
 *   <li>{@code AuthModelsCapability} is cut: offline every local model counts as owned, so the
 *       AUTH category behaves identically to ALL and no model renders locked.</li>
 *   <li>STAR reads {@link StarModels} (client config) instead of the server-synced capability.</li>
 *   <li>{@code ServerConfig.CLIENT_NOT_DISPLAY_MODELS} never applies offline; {@code hiddenModels}
 *       stays empty.</li>
 *   <li>The toolbar's download button ({@code ModScreenEvent}, a third-party-mod hook) and config
 *       gear ({@code ExtraPlayerConfigScreen}, bound to Forge config) are omitted.</li>
 *   <li>{@code renderSyncStatus} is cut (no server sync); the version label is a literal because
 *       there is no {@code ModList} here.</li>
 *   <li>{@code IGuiWidget} registration is cut: models are loaded on demand via
 *       {@link ClientModelManager#ensureAllModelsLoaded()} on open, so the reload-refresh channel
 *       has nothing to push. The breadcrumb loses upstream's folder emoji (font-safe plain
 *       text).</li>
 * </ul>
 */
public class PlayerModelScreen extends Screen {

    private static final String AUTHOR_SEARCH_PREFIX = "@";

    private static final String TAG_SEARCH_PREFIX = "#";

    /** Upstream draws {@code ModList...versionString()}; there is no mod list here. */
    private static final String VERSION_STRING = "2.6.5-backport";

    private final List<Widget> renderList = Lists.newArrayList();

    private final HashSet<String> hiddenModels;

    private final Map<String, ModelPackData> modelPackMap;

    private Map<String, ModelAssembly> filteredModels;

    private Map<String, ModelPackData> filteredPacks;

    private List<String> sortedModelKeys;

    private List<String> sortedPackKeys;

    public int guiLeft;

    public int guiTop;

    private int maxPage;

    private TextFieldWidget searchBox;

    private Category category;

    private static final PlayerPreviewEntity[] previewHolders = new PlayerPreviewEntity[10];

    private static final Object2IntMap<String> pageIndexMap = new Object2IntOpenHashMap<>();

    private static String currentPath = StringPool.EMPTY;

    static {
        for (int i = 0; i < previewHolders.length; i++) {
            previewHolders[i] = new PlayerPreviewEntity();
        }
    }

    public PlayerModelScreen() {
        super(new StringTextComponent("YSM Player Model GUI"));
        this.hiddenModels = new HashSet<>();
        this.filteredModels = Maps.newHashMap();
        this.filteredPacks = Maps.newHashMap();
        this.category = Category.ALL;
        // Upstream's registry is pre-populated at resource reload; here models load on demand.
        // First open pays the load cost, repeat opens hit the cache (failures are sticky).
        ClientModelManager.ensureAllModelsLoaded();
        this.modelPackMap = new Object2ReferenceOpenHashMap<>(ClientModelManager.getModelPackMap());
    }

    public ModelButton createModelButton(int x, int y, boolean locked, PlayerPreviewEntity previewEntity,
                                         ModelAssembly modelAssembly) {
        return new ModelButton(x, y, locked, previewEntity, modelAssembly);
    }

    public PlayerTextureScreen createTextureScreen(PlayerModelScreen parent, String modelId,
                                                   ModelAssembly modelAssembly) {
        return new PlayerTextureScreen(parent, modelId, modelAssembly);
    }

    public ModelInfoScreen createModelInfoScreen(PlayerModelScreen parent, ModelAssembly modelAssembly) {
        return new ModelInfoScreen(parent, modelAssembly);
    }

    private Map<String, ModelAssembly> buildFilteredModelMap() {
        Map<String, ModelAssembly> map = Maps.newHashMap();
        if (StringUtils.isBlank(currentPath)) {
            map.putAll(ClientModelManager.getModelAssemblyMap());
        }
        ClientModelManager.getModelAssemblyMap().forEach((modelId, modelAssembly) -> {
            if (modelId.startsWith(currentPath)) {
                map.put(modelId, modelAssembly);
            }
            String parentDir = FileTypeUtil.splitFileNameAndParentDir(modelId).getRight();
            if (StringUtils.isNotBlank(parentDir)) {
                ensurePackHierarchy(parentDir, this.modelPackMap);
            }
        });
        return map;
    }

    private static void ensurePackHierarchy(String parentDir, Map<String, ModelPackData> map) {
        if (StringUtils.isBlank(parentDir) || !parentDir.contains("/")) {
            return;
        }
        String[] segments = parentDir.split("/");
        StringBuilder path = new StringBuilder();
        for (String segment : segments) {
            if (!segment.isEmpty()) {
                path.append(segment).append("/");
                String current = path.toString();
                map.putIfAbsent(current, new ModelPackData(current, FileTypeUtil.getFinalPathSegment(current),
                        StringPool.EMPTY, null, null));
            }
        }
    }

    private Map<String, ModelPackData> buildFilteredPackMap() {
        if (StringUtils.isBlank(currentPath)) {
            return Maps.newHashMap(this.modelPackMap);
        }
        Map<String, ModelPackData> map = Maps.newHashMap();
        this.modelPackMap.forEach((path, packData) -> {
            if (path.startsWith(currentPath)) {
                map.put(path, packData);
            }
        });
        return map;
    }

    private void refreshModelList() {
        this.filteredModels = Maps.newHashMap();
        this.filteredPacks = Maps.newHashMap();
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        if (this.category == Category.ALL) {
            this.filteredModels = buildFilteredModelMap();
            this.filteredPacks = buildFilteredPackMap();
        }
        if (this.category == Category.AUTH) {
            // Offline: every local model counts as owned, so the AUTH filter degenerates to ALL.
            this.filteredModels.putAll(ClientModelManager.getModelAssemblyMap());
        }
        if (this.category == Category.STAR) {
            ClientModelManager.getModelAssemblyMap().forEach((modelId, modelAssembly) -> {
                if (StarModels.containsModel(modelId)) {
                    this.filteredModels.put(modelId, modelAssembly);
                }
            });
        }
        String needle;
        if (this.searchBox != null) {
            needle = this.searchBox.getText().toLowerCase(Locale.ENGLISH);
        } else {
            needle = StringPool.EMPTY;
        }
        if (StringUtils.isBlank(needle)) {
            this.filteredModels.entrySet().removeIf(entry -> {
                Pair<String, String> split = FileTypeUtil.splitFileNameAndParentDir(entry.getKey());
                return this.hiddenModels.contains(split.getLeft()) || !split.getRight().equals(currentPath);
            });
            this.filteredPacks.entrySet().removeIf(entry -> !isDirectChild(currentPath, entry.getKey()));
        } else {
            this.filteredModels.entrySet().removeIf(entry -> shouldFilterModel(
                    FileTypeUtil.splitFileNameAndParentDir(entry.getKey()).getLeft(), entry.getValue(), needle));
            this.filteredPacks.entrySet().removeIf(entry -> shouldFilterPack(
                    FileTypeUtil.splitFileNameAndParentDir(entry.getKey()).getLeft(), entry.getValue(), needle));
        }
        this.sortedModelKeys = Lists.newArrayList(this.filteredModels.keySet());
        this.sortedModelKeys.sort(String::compareTo);
        this.sortedPackKeys = Lists.newArrayList(this.filteredPacks.keySet());
        this.sortedPackKeys.sort(String::compareTo);
        this.maxPage = ((this.filteredModels.size() + this.filteredPacks.size()) - 1) / 10;
    }

    private boolean isDirectChild(String parent, String path) {
        if (parent.equals(path)) {
            return false;
        }
        if (StringUtils.isNotBlank(parent)) {
            if (!path.startsWith(parent)) {
                return false;
            }
            String remainder = path.substring(parent.length());
            int slash = remainder.indexOf('/');
            return slash == remainder.length() - 1 && remainder.lastIndexOf('/') == slash;
        }
        int slash = path.indexOf('/');
        return slash == path.length() - 1 && path.lastIndexOf('/') == slash;
    }

    private boolean shouldFilterPack(String name, ModelPackData packData, String needle) {
        if (StringUtils.isBlank(needle)) {
            return false;
        }
        if (needle.startsWith(TAG_SEARCH_PREFIX)) {
            needle = needle.substring(TAG_SEARCH_PREFIX.length());
        }
        if (name.toLowerCase(Locale.ENGLISH).contains(needle)) {
            return false;
        }
        if (packData.getTranslations() != null) {
            if (ModelMetadataPresenter.getLocalizedString(packData, "name", packData.getName())
                    .toLowerCase(Locale.ENGLISH).contains(needle)) {
                return false;
            }
            String description = packData.getDescription();
            return description == null || !ModelMetadataPresenter.getLocalizedString(packData, "description",
                    description).toLowerCase(Locale.ENGLISH).contains(needle);
        }
        return true;
    }

    private boolean shouldFilterModel(String name, ModelAssembly modelAssembly, String needle) {
        if (this.hiddenModels.contains(name)) {
            return true;
        }
        if (StringUtils.isBlank(needle)) {
            return false;
        }
        if (needle.startsWith(TAG_SEARCH_PREFIX)) {
            return true;
        }
        if (needle.startsWith(AUTHOR_SEARCH_PREFIX)) {
            String authorNeedle = needle.substring(AUTHOR_SEARCH_PREFIX.length());
            Metadata metadata = modelAssembly.getModelData().getExtraInfo();
            if (metadata != null) {
                return matchesAuthorSearch(modelAssembly, authorNeedle, metadata);
            }
            return true;
        }
        if (name.toLowerCase(Locale.ENGLISH).contains(needle)) {
            return false;
        }
        Metadata metadata = modelAssembly.getModelData().getExtraInfo();
        if (metadata != null) {
            if (ModelMetadataPresenter.getLocalizedModelString(modelAssembly, "metadata.name", metadata.getName())
                    .toLowerCase(Locale.ENGLISH).contains(needle)
                    || ModelMetadataPresenter.getLocalizedModelString(modelAssembly, "metadata.tips",
                    metadata.getTips()).toLowerCase(Locale.ENGLISH).contains(needle)) {
                return false;
            }
            return matchesAuthorSearch(modelAssembly, needle, metadata);
        }
        return true;
    }

    public String getParentPath(String path) {
        if (path == null || path.isEmpty()) {
            return StringPool.EMPTY;
        }
        String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int lastSlash = trimmed.lastIndexOf('/');
        if (lastSlash < 0) {
            return StringPool.EMPTY;
        }
        return trimmed.substring(0, lastSlash + 1);
    }

    private boolean matchesAuthorSearch(ModelAssembly modelAssembly, String needle, Metadata metadata) {
        int index = 0;
        for (AuthorInfo author : metadata.getAuthors()) {
            if (ModelMetadataPresenter.getLocalizedModelString(modelAssembly,
                    String.format("metadata.authors.%d.name", index), author.getName())
                    .toLowerCase(Locale.ENGLISH).contains(needle)) {
                return false;
            }
            index++;
        }
        return true;
    }

    @Override
    protected void init() {
        clearWidgets();
        refreshModelList();
        if (getCurrentPage() > this.maxPage) {
            resetCurrentPage();
        }
        this.guiLeft = (this.width - 420) / 2;
        this.guiTop = (this.height - 235) / 2;
        String previousText = StringPool.EMPTY;
        boolean wasFocused = false;
        if (this.searchBox != null) {
            previousText = this.searchBox.getText();
            wasFocused = this.searchBox.isFocused();
        }
        this.searchBox = new TextFieldWidget(this.font, this.guiLeft + 144, this.guiTop + 6, 140, 16,
                new StringTextComponent("YSM Search Box"));
        this.searchBox.setText(previousText);
        this.searchBox.setTextColor(15986656);
        this.searchBox.setFocused2(wasFocused);
        this.searchBox.setCursorPositionEnd();
        addListener(this.searchBox);
        addRenderableWidget(new IconButton(this.guiLeft + 5, this.guiTop + 5, 20, 20, 80, 16, button -> {
            if (this.minecraft.player != null) {
                this.minecraft.player.getCapability(PlayerCapabilityProvider.PLAYER_CAP).ifPresent(cap -> {
                    ModelAssembly modelAssembly = cap.getModelAssembly();
                    if (modelAssembly != null && modelAssembly.getModelData().getExtraInfo() != null) {
                        this.minecraft.displayGuiScreen(createModelInfoScreen(this, modelAssembly));
                    }
                });
            }
        })).setTooltipText("gui.yes_steve_model.model.info");
        addRenderableWidget(new IconButton(this.guiLeft + 28, this.guiTop + 5, 79, 20, 32, 16, button -> {
            if (this.minecraft.player != null) {
                this.minecraft.player.getCapability(PlayerCapabilityProvider.PLAYER_CAP).ifPresent(cap -> {
                    if (cap.getModelAssembly() != null) {
                        this.minecraft.displayGuiScreen(
                                createTextureScreen(this, cap.getModelId(), cap.getModelAssembly()));
                    }
                });
            }
        }).setTooltipText("gui.yes_steve_model.model.texture"));
        addRenderableWidget(new ModIconButton(this.guiLeft + 110, this.guiTop + 5));
        if (StringUtils.isNotBlank(currentPath)) {
            addRenderableWidget(new IconButton(this.guiLeft + 110, this.guiTop + 27, 20, 20, 0, 32, button ->
                    navigateUp()).setTooltipText("gui.back"));
        }
        addRenderableWidget(new CheckboxButton(this.guiLeft + 5, this.guiTop - 22, 20, 20,
                new TranslationTextComponent("gui.yes_steve_model.show_model_id_first"),
                GeneralConfig.isShowModelIdFirst(), true) {
            @Override
            public void onPress() {
                super.onPress();
                GeneralConfig.setShowModelIdFirst(this.isChecked());
            }
        });
        addRenderableWidget(new IconButton(this.guiLeft + 328, this.guiTop + 5, 18, 18, 32, 0, button -> {
            if (this.category != Category.ALL) {
                this.category = Category.ALL;
                resetCurrentPage();
                init();
            }
        }).setTooltipText("gui.yes_steve_model.all_models"));
        addRenderableWidget(new IconButton(this.guiLeft + 308, this.guiTop + 5, 18, 18, 48, 0, button -> {
            if (this.category != Category.AUTH) {
                this.category = Category.AUTH;
                resetCurrentPage();
                init();
            }
        }).setTooltipText("gui.yes_steve_model.auth_models"));
        addRenderableWidget(new IconButton(this.guiLeft + 288, this.guiTop + 5, 18, 18, 0, 0, button -> {
            if (this.category != Category.STAR) {
                this.category = Category.STAR;
                resetCurrentPage();
                init();
            }
        }).setTooltipText("gui.yes_steve_model.star_models"));
        addRenderableWidget(new IconButton(this.guiLeft + 357, this.guiTop + 5, 18, 18, 80, 0, button ->
                this.minecraft.displayGuiScreen(new OpenModelFolderScreen(this)))
                .setTooltipText("gui.yes_steve_model.open_model_folder.open"));
        addRenderableWidget(new FlatColorButton(this.guiLeft + 198, this.guiTop + 215, 52, 14,
                new TranslationTextComponent("gui.yes_steve_model.pre_page"), button -> {
            int page = getCurrentPage();
            if (page > 0) {
                setCurrentPage(page - 1);
                init();
            }
        }));
        addRenderableWidget(new FlatColorButton(this.guiLeft + 308, this.guiTop + 215, 52, 14,
                new TranslationTextComponent("gui.yes_steve_model.next_page"), button -> {
            int page = getCurrentPage();
            if (page < this.maxPage) {
                setCurrentPage(page + 1);
                init();
            }
        }));
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        for (int cell = 0; cell < 10; cell++) {
            int index = cell + (getCurrentPage() * 10);
            int x = this.guiLeft + 143 + (55 * (cell % 5));
            int y = this.guiTop + 28 + (93 * (cell / 5));
            if (index < this.sortedPackKeys.size()) {
                String path = this.sortedPackKeys.get(index);
                getPackData(path).ifPresent(packData ->
                        addRenderableWidget(new PackIconButton(x, y, 52, 90, packData, button -> {
                            currentPath = path;
                            resetCurrentPage();
                            init();
                        })));
            }
            int modelIndex = index - this.sortedPackKeys.size();
            if (0 <= modelIndex && modelIndex < this.sortedModelKeys.size()) {
                String modelId = this.sortedModelKeys.get(modelIndex);
                PlayerPreviewEntity previewEntity = previewHolders[cell];
                previewEntity.resetModel();
                ModelAssembly modelAssembly = this.filteredModels.get(modelId);
                if (modelAssembly == null) {
                    continue;
                }
                previewEntity.initModelWithTexture(modelId,
                        modelAssembly.getAnimationBundle().getDefaultTextureName());
                previewEntity.getAnimationStateMachine().setCurrentAnimation(
                        modelAssembly.getModelData().getModelProperties().getPreviewAnimation());
                // Locked flag is always false offline (no auth capability); kept for the ctor shape.
                addRenderableWidget(createModelButton(x, y, false, previewEntity, modelAssembly));
            }
        }
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(matrixStack);
        this.fillGradient(matrixStack, this.guiLeft, this.guiTop, this.guiLeft + 135, this.guiTop + 235,
                -14540254, -14540254);
        this.fillGradient(matrixStack, this.guiLeft + 138, this.guiTop, this.guiLeft + 420, this.guiTop + 235,
                -14540254, -14540254);
        this.fillGradient(matrixStack, this.guiLeft + 351, this.guiTop + 7, this.guiLeft + 352, this.guiTop + 21,
                -790560, -790560);
        this.searchBox.render(matrixStack, mouseX, mouseY, partialTicks);
        renderModelPreview(matrixStack, mouseX, mouseY, this.minecraft.getRenderPartialTicks());
        if (this.searchBox.getText().isEmpty() && !this.searchBox.isFocused()) {
            this.font.func_243248_b(matrixStack,
                    new TranslationTextComponent("gui.yes_steve_model.search").mergeStyle(TextFormatting.ITALIC),
                    (float) (this.guiLeft + 148), (float) (this.guiTop + 10), 7829367);
        }
        String page = String.format("%d/%d", getCurrentPage() + 1, this.maxPage + 1);
        this.font.drawStringWithShadow(matrixStack, page,
                this.guiLeft + 138 + ((282 - this.font.getStringWidth(page)) / 2.0F),
                (float) ((this.guiTop + 223) - (9 / 2)), 15986656);
        matrixStack.push();
        matrixStack.translate(0.0F, 0.0F, 1000.0F);
        this.font.drawStringWithShadow(matrixStack, VERSION_STRING, (float) (this.guiLeft + 2),
                (float) (this.guiTop + 226), TextFormatting.DARK_GRAY.getColor());
        matrixStack.pop();
        if (StringUtils.isNotBlank(currentPath)) {
            int lineIndex = 0;
            // Upstream prefixes a folder emoji; plain text here (the vanilla font cannot draw it).
            List<IReorderingProcessor> lines = this.font.trimStringToWidth(
                    new StringTextComponent(currentPath).mergeStyle(TextFormatting.GRAY), 270);
            for (IReorderingProcessor line : lines) {
                this.font.func_238407_a_(matrixStack, line, (float) (this.guiLeft + 142),
                        (float) (this.guiTop + ((-(lines.size() - lineIndex)) * 10) - 2), 15986656);
                lineIndex++;
            }
        }
        for (Widget widget : this.renderList) {
            widget.render(matrixStack, mouseX, mouseY, partialTicks);
        }
        for (Widget widget : this.renderList) {
            if (widget instanceof FlatColorButton) {
                ((FlatColorButton) widget).renderTooltip(matrixStack, this, mouseX, mouseY);
            }
        }
        for (Widget widget : this.renderList) {
            if (widget instanceof ModelButton) {
                ((ModelButton) widget).renderTooltip(matrixStack, this, mouseX, mouseY);
            }
        }
        for (Widget widget : this.renderList) {
            if (widget instanceof PackIconButton) {
                ((PackIconButton) widget).renderDescription(matrixStack, this, mouseX, mouseY);
            }
        }
        if (this.searchBox.isHovered()) {
            ITextComponent tip = new TranslationTextComponent("gui.yes_steve_model.search.tip")
                    .mergeStyle(TextFormatting.GRAY);
            this.renderTooltip(matrixStack, this.font.trimStringToWidth(tip, 320), mouseX, mouseY);
        }
    }

    /** Upstream {@code renderModelPreview}: the local player's paper-doll plus selected model name. */
    public void renderModelPreview(MatrixStack matrixStack, int mouseX, int mouseY, float partialTick) {
        PlayerEntity player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ScissorCompat.enable(this.guiLeft + 5, this.guiTop + 29, this.guiLeft + 130, this.guiTop + 200);
        matrixStack.push();
        matrixStack.translate(0.0F, 0.0F, 100.0F);
        InventoryScreen.drawEntityOnScreen(this.guiLeft + 67, this.guiTop + 190, 70,
                (float) ((this.guiLeft + 67) - mouseX), (float) (((this.guiTop + 180) - 95) - mouseY), player);
        matrixStack.pop();
        ScissorCompat.disable();
        player.getCapability(PlayerCapabilityProvider.PLAYER_CAP).ifPresent(cap -> {
            String display = ClientModelManager.getModelContext(cap.getModelId()).map(modelAssembly -> {
                Metadata metadata = modelAssembly.getModelData().getExtraInfo();
                if (metadata != null) {
                    return ModelMetadataPresenter.getLocalizedModelString(modelAssembly, "metadata.name",
                            metadata.getName());
                }
                return StringPool.EMPTY;
            }).filter(StringUtils::isNoneBlank).orElse(FileTypeUtil.getNameWithoutArchiveExtension(cap.getModelId()));
            List<IReorderingProcessor> lines = this.font.trimStringToWidth(new StringTextComponent(display), 125);
            int y = this.guiTop + 205;
            for (IReorderingProcessor line : lines) {
                this.font.func_238407_a_(matrixStack, line,
                        (float) (this.guiLeft + ((135 - this.font.getStringWidth(line)) / 2)), (float) y, 15986656);
                y += 10;
            }
        });
    }

    @Override
    public void init(Minecraft minecraft, int width, int height) {
        String previousText = this.searchBox == null ? StringPool.EMPTY : this.searchBox.getText();
        super.init(minecraft, width, height);
        this.searchBox.setText(previousText);
    }

    @Override
    public void tick() {
        this.searchBox.tick();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.searchBox.mouseClicked(mouseX, mouseY, button)) {
            setListener(this.searchBox);
            return true;
        }
        if (this.searchBox.isFocused()) {
            this.searchBox.setFocused2(false);
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (!handled && button == 1 && StringUtils.isNotBlank(currentPath)) {
            playClickSound();
            navigateUp();
            handled = true;
        }
        return handled;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.searchBox == null) {
            return false;
        }
        String previousText = this.searchBox.getText();
        if (this.searchBox.charTyped(codePoint, modifiers)) {
            if (!Objects.equals(previousText, this.searchBox.getText())) {
                resetCurrentPage();
                init();
                return true;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keysym, int scancode, int modifiers) {
        if (handleToggleKey(keysym, scancode)) {
            return true;
        }
        // Upstream: InputConstants.getKey(...).getNumericKeyValue() — swallow digit keys so they
        // never reach the screen (they are the extra-animation hotkeys upstream). GLFW KEY_0..9 is
        // 48..57, keypad 0..9 is 320..329.
        boolean numeric = (keysym >= 48 && keysym <= 57) || (keysym >= 320 && keysym <= 329);
        String previousText = this.searchBox.getText();
        if (numeric) {
            return true;
        }
        if (!this.searchBox.keyPressed(keysym, scancode, modifiers)) {
            return (this.searchBox.isFocused() && this.searchBox.getVisible() && keysym != 256)
                    || super.keyPressed(keysym, scancode, modifiers);
        }
        if (!Objects.equals(previousText, this.searchBox.getText())) {
            resetCurrentPage();
            init();
            return true;
        }
        return true;
    }

    private boolean handleToggleKey(int keysym, int scancode) {
        if (PlayerModelToggleKey.isToggleKey(keysym, scancode) && !this.searchBox.isFocused()) {
            closeScreen();
            return true;
        }
        return false;
    }

    /** Upstream {@code insertText} (IME commit path). */
    public void insertText(String text, boolean overwrite) {
        if (overwrite) {
            this.searchBox.setText(text);
        } else {
            this.searchBox.writeText(text);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.minecraft == null) {
            return false;
        }
        if (delta != 0.0D && isInModelArea(mouseX, mouseY)) {
            return handleScrollPage(delta);
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private boolean isInModelArea(double x, double y) {
        return x >= (this.guiLeft + 143) && x < (this.guiLeft + 430) && y >= (this.guiTop + 25)
                && y < (this.guiTop + 235);
    }

    private void navigateUp() {
        String parent = getParentPath(currentPath);
        if (!currentPath.equals(parent)) {
            String previous = currentPath;
            currentPath = parent;
            pageIndexMap.removeInt(previous);
            init();
        }
    }

    private boolean handleScrollPage(double delta) {
        int page = getCurrentPage();
        if (delta > 0.0D && page > 0) {
            setCurrentPage(page - 1);
            playClickSound();
            init();
        }
        if (delta < 0.0D && page < this.maxPage) {
            setCurrentPage(page + 1);
            playClickSound();
            init();
            return true;
        }
        return true;
    }

    private void playClickSound() {
        this.minecraft.getSoundHandler().play(SimpleSound.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    public int getCurrentPage() {
        return pageIndexMap.getOrDefault(currentPath, 0);
    }

    public void setCurrentPage(int page) {
        pageIndexMap.put(currentPath, page);
    }

    public void resetCurrentPage() {
        pageIndexMap.put(currentPath, 0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Optional<ModelPackData> getPackData(String path) {
        return Optional.ofNullable(this.modelPackMap.get(path));
    }

    private void clearWidgets() {
        this.renderList.clear();
        this.children.clear();
    }

    private <T extends Widget> T addRenderableWidget(T widget) {
        this.renderList.add(widget);
        addListener(widget);
        return widget;
    }

    private enum Category {
        ALL,
        AUTH,
        STAR
    }
}
