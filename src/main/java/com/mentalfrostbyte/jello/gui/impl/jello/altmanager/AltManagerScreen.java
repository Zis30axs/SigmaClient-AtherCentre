package com.mentalfrostbyte.jello.gui.impl.jello.altmanager;

import com.google.gson.JsonObject;
import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.gui.base.alerts.AlertComponent;
import com.mentalfrostbyte.jello.gui.base.alerts.ComponentType;
import com.mentalfrostbyte.jello.gui.base.animations.Animation;
import com.mentalfrostbyte.jello.gui.base.elements.impl.Alert;
import com.mentalfrostbyte.jello.gui.base.elements.impl.Dropdown;
import com.mentalfrostbyte.jello.gui.base.elements.impl.Text;
import com.mentalfrostbyte.jello.gui.base.elements.impl.TextBlock;
import com.mentalfrostbyte.jello.gui.base.elements.impl.VerticalScrollBar;
import com.mentalfrostbyte.jello.gui.base.elements.impl.altmanager.AccountUI;
import com.mentalfrostbyte.jello.gui.base.elements.impl.altmanager.Head;
import com.mentalfrostbyte.jello.gui.base.elements.impl.altmanager.Info;
import com.mentalfrostbyte.jello.gui.base.elements.impl.button.types.TextButton;
import com.mentalfrostbyte.jello.gui.base.elements.impl.critical.Screen;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.impl.jello.buttons.ScrollableContentPanel;
import com.mentalfrostbyte.jello.gui.impl.jello.buttons.TextField;
import com.mentalfrostbyte.jello.gui.impl.others.AccountSorter;
import com.mentalfrostbyte.jello.managers.AccountManager;
import com.mentalfrostbyte.jello.managers.util.account.microsoft.Account;
import com.mentalfrostbyte.jello.managers.util.account.microsoft.sorting.AccountCompareType;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.client.render.Resources;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.client.render.theme.ColorHelper;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.system.math.SmoothInterpolator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.MainMenuHolder;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;

import java.util.ArrayList;
import java.util.List;

public class AltManagerScreen extends Screen {
    private int field21005;
    private float field21006;
    private boolean field21008 = true;
    private ScrollableContentPanel alts;
    private final ScrollableContentPanel altView;
    private Alert loginDialog;
    private Alert tokenLoginDialog;
    private Alert deleteAlert;
    /** Right-click menu: "View token" / "Delete" for one account. */
    private Alert accountOptions;
    private Text accountOptionsName;
    /** Token viewer opened from the right-click menu. */
    private Alert tokenDialog;
    private Text tokenDialogName;
    private Text tokenDialogMeta;
    private TextBlock tokenDialogValue;
    /**
     * Account the right-click menu (and the dialogs it opens) currently acts on. Held
     * here rather than captured per click so the confirm handlers can be registered
     * once instead of stacking up another copy on every right-click.
     */
    private Account contextAccount;
    private final float field21014 = 0.65F;
    private final float field21015 = 1.0F - this.field21014;
    private final int titleOffset = 30;
    private final Head head;
    private final Info info;
    public AccountManager accountManager = Client.getInstance().accountManager;
    private AccountCompareType accountSortType = AccountCompareType.DateAdded;
    private String accountFilter = "";
    private final TextField searchBox;

    public static AltManagerScreen instance;

    public AltManagerScreen() {
        super("Alt Manager");
        instance = this;
        this.setListening(false);
        List<String> sortingOptions = new ArrayList<>();
        sortingOptions.add("Alphabetical");
        sortingOptions.add("Bans");
        sortingOptions.add("Date Added");
        sortingOptions.add("Last Used");
        sortingOptions.add("Use count");
        List<String> servers = new ArrayList<>();
        ServerList serverList = new ServerList(Minecraft.getInstance());
        serverList.loadServerList();
        int serverListSize = serverList.countServers();

        for (int i = 0; i < serverListSize; i++) {
            ServerData server = serverList.getServerData(i);
            if (!servers.contains(server.serverIP)) {
                servers.add(server.serverIP);
            }
        }

        this.getLoginDialog();
        this.tokenLoginAlert();
        this.deleteAltAlert();
        this.accountOptionsAlert();
        this.tokenAlert();
        this.addToList(
                this.alts = new ScrollableContentPanel(
                        this,
                        "alts",
                        0,
                        114,
                        (int) ((float) Minecraft.getInstance().getMainWindow().getWidth() * this.field21014) - 4,
                        Minecraft.getInstance().getMainWindow().getHeight() - 119 - this.titleOffset));
        this.addToList(
                this.altView = new ScrollableContentPanel(
                        this,
                        "altView",
                        (int) ((float) Minecraft.getInstance().getMainWindow().getWidth() * this.field21014),
                        114,
                        (int) ((float) Minecraft.getInstance().getMainWindow().getWidth() * this.field21015)
                                - this.titleOffset,
                        Minecraft.getInstance().getMainWindow().getHeight() - 119 - this.titleOffset));
        this.alts.setListening(false);
        this.altView.setListening(false);
        this.alts.method13515(false);
        this.altView
                .addToList(
                        this.head = new Head(
                                this.altView,
                                "",
                                (int) ((float) Minecraft.getInstance().getMainWindow().getWidth() * this.field21015
                                        - (float) ((int) ((float) Minecraft.getInstance().getMainWindow().getWidth()
                                                * this.field21015)))
                                        / 2
                                        - 10,
                                Minecraft.getInstance().getMainWindow().getHeight() / 12,
                                (int) ((float) Minecraft.getInstance().getMainWindow().getWidth() * this.field21015),
                                350,
                                "8667ba71-b85a-4004-af54-457a9734eed7"));
        this.altView
                .addToList(
                        this.info = new Info(
                                this.altView,
                                "info",
                                (int) ((float) Minecraft.getInstance().getMainWindow().getWidth() * this.field21015
                                        - (float) ((int) ((float) Minecraft.getInstance().getMainWindow().getWidth()
                                                * this.field21015)))
                                        / 2
                                        - 10,
                                this.method13374(),
                                (int) ((float) Minecraft.getInstance().getMainWindow().getWidth() * this.field21015),
                                500));
        Dropdown filterDropdown = new Dropdown(this, "drop",
                (int) ((float) Minecraft.getInstance().getMainWindow().getWidth() * this.field21014) - 220, 44, 200, 32,
                sortingOptions, 0);
        filterDropdown.method13643(servers, 1);
        filterDropdown.method13656(2);
        this.addToList(filterDropdown);
        filterDropdown.onPress(var2 -> {
            switch (filterDropdown.getIndex()) {
                case 0:
                    this.accountSortType = AccountCompareType.Alphabetical;
                    break;
                case 1:
                    this.accountSortType = AccountCompareType.Bans;
                    this.accountFilter = filterDropdown.method13645(1).method13636()
                            .get(filterDropdown.method13645(1).method13640());
                    break;
                case 2:
                    this.accountSortType = AccountCompareType.DateAdded;
                    break;
                case 3:
                    this.accountSortType = AccountCompareType.LastUsed;
                    break;
                case 4:
                    this.accountSortType = AccountCompareType.UseCount;
            }

            this.updateAccountList(false);
        });
        this.addToList(
                this.searchBox = new TextField(
                        this,
                        "textbox",
                        (int) ((float) Minecraft.getInstance().getMainWindow().getWidth() * this.field21014),
                        44,
                        150,
                        32,
                        TextField.field20741,
                        "",
                        "Search...",
                        ResourceRegistry.JelloLightFont18));
        this.searchBox.setFont(ResourceRegistry.JelloLightFont18);
        this.searchBox.addChangeListener(var1 -> this.updateAccountList(false));
        TextButton addButton;
        this.addToList(addButton = new TextButton(this, "btnt", this.getWidthA() - 90, 43, 70, 30,
                ColorHelper.field27961, "Add +", ResourceRegistry.JelloLightFont25));
        this.alts.method13242();
        addButton.onClick((var1, var2) -> {
            if (this.method13369()) {
                this.loginDialog.method13603(!this.loginDialog.isHovered());
            }
        });
    }

    private void method13360(Account acc, boolean var2) {
        AccountUI accountUI;
        this.alts.addToList(
                accountUI = new AccountUI(
                        this.alts,
                        acc.getEmail(),
                        this.titleOffset,
                        (100 + this.titleOffset / 2) * this.method13370(),
                        this.alts.getWidthA() - this.titleOffset * 2 + 4,
                        100,
                        acc));
        if (!var2) {
            accountUI.field20805 = new Animation(0, 0);
        }

        if (this.accountManager.isCurrentAccount(acc)) {
            accountUI.setAccountListRefreshing(true);
        }

        accountUI.method13247((var2x, var3) -> {
            if (var3 != 0) {
                this.openAccountOptions(accountUI.selectedAccount);
            } else {
                if (this.head.account == accountUI.selectedAccount && accountUI.method13168()) {
                    this.loginToAccount(accountUI);
                } else {
                    this.altView.method13512(0);
                }

                this.head.handleSelectedAccount(accountUI.selectedAccount);
                this.info.handleSelectedAccount(accountUI.selectedAccount);

                for (CustomGuiScreen var7 : this.alts.getChildren()) {
                    if (!(var7 instanceof VerticalScrollBar)) {
                        for (CustomGuiScreen var9 : var7.getChildren()) {
                            ((AccountUI) var9).method13166(false);
                        }
                    }
                }

                accountUI.method13166(true);
            }
        });
        if (Client.getInstance().accountManager.isCurrentAccount(acc)) {
            this.head.handleSelectedAccount(accountUI.selectedAccount);
            this.info.handleSelectedAccount(accountUI.selectedAccount);
            accountUI.method13167(true, true);
        }
    }

    public static void login() {

    }

    public void loginToAccount(AccountUI account) {
        account.setLoadingIndicator(true);
        new Thread(() -> {
            if (!this.accountManager.login(account.selectedAccount)) {
                account.setErrorState(114);
                Client.getInstance().soundManager.play("error");
            } else {
                this.method13368();
                account.setAccountListRefreshing(true);
                Client.getInstance().soundManager.play("connect");
                this.updateAccountList(false);
            }
            account.setLoadingIndicator(false);
        }).start();
    }

    private void getLoginDialog() {
        AlertComponent header = new AlertComponent(ComponentType.HEADER, "Add Alt", 50);
        AlertComponent firstline1 = new AlertComponent(ComponentType.FIRST_LINE, "Login with your minecraft", 15);
        AlertComponent firstline2 = new AlertComponent(ComponentType.FIRST_LINE, "account here!", 25);
        AlertComponent emailInput = new AlertComponent(ComponentType.SECOND_LINE, "Email", 50);
        AlertComponent passwordInput = new AlertComponent(ComponentType.SECOND_LINE, "Password", 50);
        AlertComponent button = new AlertComponent(ComponentType.BUTTON, "Add alt", 50);
        AlertComponent button2 = new AlertComponent(ComponentType.BUTTON, "Cookie login", 50);
        AlertComponent button3 = new AlertComponent(ComponentType.BUTTON, "Web login", 50);
        AlertComponent button4 = new AlertComponent(ComponentType.BUTTON, "Token login", 50);
        AlertComponent button5 = new AlertComponent(ComponentType.BUTTON, "Random login", 50);
        this.addToList(this.loginDialog = new Alert(this, "Add alt dialog", true, "Add Alt", header, firstline1,
                firstline2, emailInput, passwordInput, button, button2, button3, button4, button5));

        this.loginDialog.onPress(element -> {
            if (!this.loginDialog.getInputMap().get("Email").contains(":")) {
                Account account = new Account(this.loginDialog.getInputMap().get("Email"),
                        this.loginDialog.getInputMap().get("Password"));
                if (!this.accountManager.containsAccount(account)) {
                    this.accountManager.updateAccount(account);
                }

                this.updateAccountList(false);
            } else {
                String[] emails = this.loginDialog.getInputMap().get("Email").replace("\r", "\n").replace("\n\n", "\n")
                        .split("\n");

                for (String email : emails) {
                    String[] splitted = email.split(":");
                    if (splitted.length == 2) {
                        Account account = new Account(splitted[0], splitted[1]);
                        if (!this.accountManager.containsAccount(account)) {
                            this.accountManager.updateAccount(account);
                        }
                    }
                }

                this.updateAccountList(false);
            }
        });
        this.loginDialog.setButtonAction(3, () -> {
            this.loginDialog.method13603(false);
            this.tokenLoginDialog.setFocused(true);
            this.tokenLoginDialog.method13603(true);
        });
    }

    private void tokenLoginAlert() {
        AlertComponent header = new AlertComponent(ComponentType.HEADER, "Token Login", 50);
        AlertComponent firstLine = new AlertComponent(ComponentType.FIRST_LINE, "Login with your Minecraft", 15);
        AlertComponent secondLine = new AlertComponent(ComponentType.FIRST_LINE, "session token here!", 25);
        AlertComponent tokenInput = new AlertComponent(ComponentType.SECOND_LINE, "Token", 50);
        AlertComponent login = new AlertComponent(ComponentType.BUTTON, "Login", 50);
        AlertComponent back = new AlertComponent(ComponentType.BUTTON, "Back", 50);
        this.addToList(this.tokenLoginDialog = new Alert(this, "Token login dialog", true, "Token Login",
                header, firstLine, secondLine, tokenInput, login, back));

        this.tokenLoginDialog.setButtonAction(0, this::loginWithToken);
        this.tokenLoginDialog.setButtonAction(1, () -> {
            this.tokenLoginDialog.method13603(false);
            this.loginDialog.setFocused(true);
            this.loginDialog.method13603(true);
        });
    }

    private void loginWithToken() {
        String token = this.normalizeToken(this.tokenLoginDialog.getInputMap().get("Token"));
        if (token.isEmpty()) {
            Client.getInstance().soundManager.play("error");
            this.tokenLoginDialog.showLoginError("Token required",
                    "Click here to go back and enter your token.");
            return;
        }

        this.tokenLoginDialog.enterLoadingState("Logging in...",
                "Validating your Minecraft session token.");
        int generation = this.tokenLoginDialog.currentLoginGeneration();
        new Thread(() -> {
            Account account = new Account("Token Account", "Token ID", token);
            boolean loginSucceeded;
            try {
                loginSucceeded = this.accountManager.login(account);
                if (loginSucceeded) {
                    account.setEmail(account.getKnownName());
                    account.setPassword(account.getFormattedUUID());
                    this.accountManager.updateAccount(account);
                    this.accountManager.saveAlts();
                }
            } catch (Exception error) {
                Client.getInstance().logger.error("Token login failed", error);
                loginSucceeded = false;
            }

            boolean success = loginSucceeded;
            Minecraft.getInstance().execute(() -> {
                if (success) {
                    this.updateAccountList(false);
                }
                if (this.tokenLoginDialog.currentLoginGeneration() != generation) {
                    return;
                }

                if (!success) {
                    Client.getInstance().soundManager.play("error");
                    this.tokenLoginDialog.showLoginError("Token login failed",
                            "Click here to go back and check the token.");
                    return;
                }

                this.tokenLoginDialog.method13603(false);
                Client.getInstance().soundManager.play("connect");
            });
        }, "TokenLogin").start();
    }

    private String normalizeToken(String token) {
        if (token == null) {
            return "";
        }

        String normalized = token.trim();
        if (normalized.regionMatches(true, 0, "mctoken:", 0, "mctoken:".length())) {
            normalized = normalized.substring("mctoken:".length());
        }

        return normalized.replace("\r", "").replace("\n", "").trim();
    }

    private void deleteAltAlert() {
        AlertComponent title = new AlertComponent(ComponentType.HEADER, "Delete?", 50);
        AlertComponent firstLine = new AlertComponent(ComponentType.FIRST_LINE, "Are you sure you want", 15);
        AlertComponent secondLine = new AlertComponent(ComponentType.FIRST_LINE, "to delete this alt?", 40);
        AlertComponent button = new AlertComponent(ComponentType.BUTTON, "Delete", 50);
        this.addToList(
                this.deleteAlert = new Alert(this, "delete", true, "Delete", title, firstLine, secondLine, button));
        this.deleteAlert.onPress(element -> {
            if (this.contextAccount == null) {
                return;
            }

            this.accountManager.removeAccountDirectly(this.contextAccount);
            this.contextAccount = null;
            this.info.handleSelectedAccount(null);
            this.head.handleSelectedAccount(null);
            this.updateAccountList(false);
        });
    }

    /**
     * Right-click menu for a single account. Sits in front of the destructive action so
     * a right-click is no longer a straight shot at the delete confirmation.
     */
    private void accountOptionsAlert() {
        AlertComponent title = new AlertComponent(ComponentType.HEADER, "Account", 50);
        AlertComponent name = new AlertComponent(ComponentType.FIRST_LINE, "", 30);
        AlertComponent viewToken = new AlertComponent(ComponentType.BUTTON, "View token", 50);
        AlertComponent delete = new AlertComponent(ComponentType.BUTTON, "Delete", 50);
        this.addToList(
                this.accountOptions = new Alert(this, "options", true, "Account", title, name, viewToken, delete));
        this.accountOptionsName = this.accountOptions.getTextRows().get(1);

        this.accountOptions.setButtonAction(0, () -> {
            this.accountOptions.method13603(false);
            this.openTokenDialog();
        });
        this.accountOptions.setButtonAction(1, () -> {
            this.accountOptions.method13603(false);
            this.deleteAlert.setFocused(true);
            this.deleteAlert.method13603(true);
        });
    }

    /**
     * Token viewer. The value is masked until revealed and wider than the standard
     * dialog because a Minecraft session token runs to several hundred characters;
     * "Copy token" is the escape hatch when it is longer than the block can show.
     */
    private void tokenAlert() {
        AlertComponent title = new AlertComponent(ComponentType.HEADER, "Token", 50);
        AlertComponent name = new AlertComponent(ComponentType.FIRST_LINE, "", 15);
        AlertComponent meta = new AlertComponent(ComponentType.FIRST_LINE, "", 25);
        AlertComponent value = new AlertComponent(ComponentType.TEXT_BLOCK, "", 140);
        AlertComponent copy = new AlertComponent(ComponentType.BUTTON, "Copy token", 50);
        AlertComponent reveal = new AlertComponent(ComponentType.BUTTON, "Show token", 50);
        this.addToList(
                this.tokenDialog = new Alert(this, "token", true, "Token", 420, title, name, meta, value, copy, reveal));
        this.tokenDialogName = this.tokenDialog.getTextRows().get(1);
        this.tokenDialogMeta = this.tokenDialog.getTextRows().get(2);
        this.tokenDialogValue = this.tokenDialog.getTextBlocks().get(0);

        this.tokenDialog.setButtonAction(0, () -> {
            String token = this.contextToken();
            if (token.isEmpty()) {
                Client.getInstance().soundManager.play("error");
                return;
            }

            Minecraft.getInstance().keyboardListener.setClipboardString(token);
            this.tokenDialogMeta.setText("Copied to clipboard");
        });
        this.tokenDialog.setButtonAction(1, () -> {
            if (this.contextToken().isEmpty()) {
                Client.getInstance().soundManager.play("error");
                return;
            }

            boolean reveals = this.tokenDialogValue.isMasked();
            this.tokenDialogValue.setMasked(!reveals);
            this.tokenDialog.buttons.get(1).setText(!reveals ? "Show token" : "Hide token");
        });
    }

    private void openAccountOptions(Account account) {
        if (account == null) {
            return;
        }

        this.contextAccount = account;
        this.accountOptionsName.setText(account.getName());
        this.accountOptions.setFocused(true);
        this.accountOptions.method13603(true);
    }

    private void openTokenDialog() {
        if (this.contextAccount == null) {
            return;
        }

        String token = this.contextToken();
        boolean hasToken = !token.isEmpty();
        this.tokenDialogName.setText(this.contextAccount.getName());
        this.tokenDialogMeta.setText(this.describeToken(this.contextAccount, token));
        // Start masked so a token is never on screen by accident; the message shown for
        // a token-less account is not a secret, so it is never masked.
        this.tokenDialogValue.setMasked(hasToken);
        this.tokenDialogValue.setContent(hasToken ? token : "Nothing stored yet - log in once with this account.");
        this.tokenDialog.buttons.get(1).setText("Show token");
        this.tokenDialog.setFocused(true);
        this.tokenDialog.method13603(true);
    }

    private String contextToken() {
        if (this.contextAccount == null || this.contextAccount.getToken() == null) {
            return "";
        }

        return this.contextAccount.getToken();
    }

    private String describeToken(Account account, String token) {
        if (token.isEmpty()) {
            return "No session token";
        }

        return token.length() + " characters" + (account.hasRefreshToken() ? " - refresh token stored" : "");
    }

    @Override
    public void draw(float partialTicks) {
        this.drawBackground();
        RenderUtil.method11465(
                (int) ((float) Minecraft.getInstance().getMainWindow().getWidth() * this.field21014),
                114,
                (int) ((float) Minecraft.getInstance().getMainWindow().getWidth() * this.field21015) - this.titleOffset,
                Minecraft.getInstance().getMainWindow().getHeight() - 119 - this.titleOffset,
                ClientColors.LIGHT_GREYISH_BLUE.getColor());
        this.method13367();
        this.drawTitle();
        super.draw(partialTicks);
    }

    private void drawTitle() {
        int xPos = this.xA + this.titleOffset;
        int yPos = this.yA + this.titleOffset;
        int color = RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.8F);
        RenderUtil.drawString(ResourceRegistry.JelloLightFont40, (float) xPos, (float) yPos, "Jello", color);
        RenderUtil.drawString(ResourceRegistry.JelloLightFont25, (float) (xPos + 87), (float) (yPos + 15),
                "Alt Manager", color);
    }

    private void method13367() {
        float var3 = 1.0F;

        for (CustomGuiScreen var5 : this.alts.getChildren()) {
            if (!(var5 instanceof VerticalScrollBar)) {
                for (CustomGuiScreen var7 : var5.getChildren()) {
                    if (var7 instanceof AccountUI accountUI) {
                        if (var7.getYA() <= Minecraft.getInstance().getMainWindow().getHeight()
                                && this.alts.method13513() == 0) {
                            if (var3 > 0.2F) {
                                accountUI.field20805.changeDirection(Animation.Direction.FORWARDS);
                            }

                            float var9 = SmoothInterpolator.interpolate(accountUI.field20805.calcPercent(), 0.51, 0.82,
                                    0.0, 0.99);
                            accountUI.method13284((int) (-((1.0F - var9) * (float) (var7.getWidthA() + 30))));
                            var3 = accountUI.field20805.calcPercent();
                        } else {
                            accountUI.method13284(0);
                            accountUI.field20805.changeDirection(Animation.Direction.FORWARDS);
                        }
                    }
                }
            }
        }
    }

    private void method13368() {
        for (CustomGuiScreen screen : this.alts.getChildren()) {
            if (!(screen instanceof VerticalScrollBar)) {
                for (CustomGuiScreen child : screen.getChildren()) {
                    AccountUI accountUI = (AccountUI) child;
                    accountUI.setAccountListRefreshing(false);
                }
            }
        }
    }

    private boolean method13369() {
        for (CustomGuiScreen var5 : this.alts.getChildren()) {
            if (!(var5 instanceof VerticalScrollBar)) {
                for (CustomGuiScreen var7 : var5.getChildren()) {
                    if (var7.method13280() != 0 && var7.getXA() > this.widthA) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private int method13370() {
        int var3 = 0;

        for (CustomGuiScreen var5 : this.alts.getChildren()) {
            if (!(var5 instanceof VerticalScrollBar)) {
                for (CustomGuiScreen ignored : var5.getChildren()) {
                    var3++;
                }
            }
        }

        return var3;
    }

    private void drawBackground() {
        int var3 = this.getHeightO() * -1;
        float var4 = (float) this.getWidthO() / (float) this.getWidthA() * -114.0F;
        if (this.field21008) {
            this.field21006 = (float) ((int) var4);
            this.field21005 = var3;
            this.field21008 = false;
        }

        float var5 = var4 - this.field21006;
        float var6 = (float) (var3 - this.field21005);
        RenderUtil.drawImage((float) this.field21005, this.field21006, (float) (this.getWidthA() * 2),
                (float) (this.getHeightA() + 114), Resources.panoramaPNG);
        float var7 = 0.5F;
        if (var4 != this.field21006) {
            this.field21006 += var5 * var7;
        }

        if (var3 != this.field21005) {
            this.field21005 = (int) ((float) this.field21005 + var6 * var7);
        }

        RenderUtil.drawRoundedRect(0.0F, 0.0F, (float) this.getWidthA(), (float) this.getHeightA(),
                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.95F));
    }

    @Override
    public void keyPressed(int keyCode) {
        super.keyPressed(keyCode);
        if (keyCode == 256) {
            Minecraft.getInstance().displayGuiScreen(new MainMenuHolder());
        }
    }

    @Override
    public JsonObject toConfigWithExtra(JsonObject config) {
        this.accountManager.saveAlts();
        return config;
    }

    @Override
    public void loadConfig(JsonObject config) {
        for (CustomGuiScreen var5 : this.alts.getChildren()) {
            if (!(var5 instanceof VerticalScrollBar)) {
                for (CustomGuiScreen var7 : var5.getChildren()) {
                    this.alts.method13234(var7);
                }
            }
        }

        this.updateAccountList(true);
    }

    public void updateAccountList(boolean forceRefresh) {
        List<Account> var5 = AccountSorter.sortByInputAltAccounts(this.accountManager.getAccounts(),
                this.accountSortType, this.accountFilter, this.searchBox.getText());
        this.runThisOnDimensionUpdate(new AccountListUpdater(this, this, var5, forceRefresh));
    }

    public int method13374() {
        return Minecraft.getInstance().getMainWindow().getHeight() / 12 + 280
                + Minecraft.getInstance().getMainWindow().getHeight() / 12;
    }

    // $VF: synthetic method
    public static ScrollableContentPanel method13382(AltManagerScreen instance) {
        return instance.alts;
    }

    // $VF: synthetic method
    public static ScrollableContentPanel method13383(AltManagerScreen instance, ScrollableContentPanel var1) {
        return instance.alts = var1;
    }

    // $VF: synthetic method
    public static float method13384(AltManagerScreen instance) {
        return instance.field21014;
    }

    // $VF: synthetic method
    public static int getTitleOffset(AltManagerScreen instance) {
        return instance.titleOffset;
    }

    // $VF: synthetic method
    public static void method13386(AltManagerScreen instance, Account var1, boolean var2) {
        instance.method13360(var1, var2);
    }
}
