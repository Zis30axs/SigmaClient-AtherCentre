package com.mentalfrostbyte.jello.gui.impl.classic.altmanager.submenus;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.gui.base.elements.impl.altmanager.Anthropic;
import com.mentalfrostbyte.jello.gui.base.elements.impl.critical.Screen;
import com.mentalfrostbyte.jello.gui.impl.classic.altmanager.ClassicAltScreen;
import com.mentalfrostbyte.jello.gui.base.elements.impl.button.types.AltManagerButton;
import com.mentalfrostbyte.jello.gui.impl.classic.clickgui.buttons.Input;
import com.mentalfrostbyte.jello.managers.AccountManager;
import com.mentalfrostbyte.jello.managers.util.account.microsoft.Account;
import com.mentalfrostbyte.jello.util.client.network.microsoft.MicrosoftLoginUtil;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.client.render.Resources;
import com.mentalfrostbyte.jello.util.client.render.FontSizeAdjust;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddAltScreen extends Screen {
   public Input field21116;
   public Input field21117;
   public AltManagerButton field21118;
   public AltManagerButton tokenLoginButton;
   public AltManagerButton webLoginButton;
   public AltManagerButton field21119;
   public AltManagerButton field21120;
   public AccountManager field21121 = Client.getInstance().accountManager;
   private String field21122 = "§7Idle...";

   public AddAltScreen() {
      super("Alt Manager");
      this.setListening(false);
      int var3 = 400;
      int var4 = 114;
      int var5 = (this.getWidthA() - var3) / 2;
      this.addToList(
            this.field21116 = new Input(this, "username", var5, var4, var3, 45, Input.field20741, "",
                  "Username / E-Mail", ResourceRegistry.DefaultClientFont));
      var4 += 80;
      this.addToList(this.field21117 = new Input(this, "password", var5, var4, var3, 45, Input.field20741, "",
            "Password", ResourceRegistry.DefaultClientFont));
      var4 += 50;
      this.addToList(this.field21118 = new AltManagerButton(this, "login", var5, var4, var3, 40, "Login",
            ClientColors.MID_GREY.getColor()));
      var4 += 50;
      this.addToList(this.tokenLoginButton = new AltManagerButton(this, "token_login", var5, var4, var3, 40,
            "Token Login", ClientColors.MID_GREY.getColor()));
      var4 += 50;
      this.addToList(this.webLoginButton = new AltManagerButton(this, "web_login", var5, var4, var3, 40,
            "Web Login", ClientColors.MID_GREY.getColor()));
      var4 += 50;
      this.addToList(this.field21119 = new AltManagerButton(this, "back", var5, var4, var3, 40, "Back",
            ClientColors.MID_GREY.getColor()));
      var4 += 50;
      this.addToList(this.field21120 = new AltManagerButton(this, "import", var5, var4, var3, 40, "Import user:pass",
            ClientColors.MID_GREY.getColor()));
      this.field21117.setCensorText(true);
      this.field21117.method13147("*");
      this.field21118.onClick((var1, var2) -> {
         this.field21122 = "§bLogging in...";
         new Thread(() -> {
            Account var3x = new Account(this.field21116.getText(), this.field21117.getText());
            if (!this.field21121.updateSelectedEmail(var3x)) {
               this.field21122 = "§cAlt failed!";
            } else {
               this.field21121.updateAccount(var3x);
               boolean premium = Anthropic.isPremiumCached(var3x);
               this.field21122 = "Alt added. (" + var3x.getEmail() + (!premium ? " - offline name" : "") + ")";
            }
         }).start();
      });
      this.tokenLoginButton.onClick((var1, var2) -> {
         this.field21122 = "§bLogging in with token...";
         new Thread(() -> {
            String tokenInput = this.field21116.getText();
            if (tokenInput.startsWith("mctoken:")) {
               tokenInput = tokenInput.substring(8);
            }
            Account var3x = new Account("Token Account", "Token ID", tokenInput);
            if (!this.field21121.updateSelectedEmail(var3x)) {
               this.field21122 = "§cToken failed!";
            } else {
               this.field21121.updateAccount(var3x);
               this.field21122 = "Alt added. (" + var3x.getKnownName() + ")";
            }
         }).start();
      });
      this.webLoginButton.onClick((var1, var2) -> this.startWebLogin());
      this.field21119.onClick((var0, var1) -> Client.getInstance().guiManager.handleScreen(new ClassicAltScreen()));
      this.field21120.onClick((var1, var2) -> {
         String var5x = "";

         try {
            var5x = GLFW.glfwGetClipboardString(Minecraft.getInstance().getMainWindow().getHandle());
         } catch (Exception var7x) {
         }

         if (var5x != "" && var5x.contains(":")) {
            String[] var6x = var5x.split(":");
            if (var6x.length == 2) {
               this.field21116.setText(var6x[0].replace("\n", ""));
               this.field21117.setText(var6x[1].replace("\n", ""));
            }
         }
      });
   }

   /**
    * Starts the Microsoft OAuth browser login (Web login), mirroring the Jello-mode
    * flow: opens the browser, waits for the auth-code callback, exchanges it for a
    * Minecraft session, and persists the account (with its refresh token) so it can
    * be silently re-logged in later.
    */
   private void startWebLogin() {
      this.field21122 = "§bOpening your browser...";
      ExecutorService executor = Executors.newSingleThreadExecutor();
      MicrosoftLoginUtil.acquireMSAuthCodeSession(executor)
            .thenComposeAsync(authCodeSession -> {
               Minecraft.getInstance().execute(() -> this.field21122 = "§bAuthorization received...");
               return MicrosoftLoginUtil.loginWithAuthCodeSession(authCodeSession, executor);
            }, executor)
            .thenAccept(msSession -> Minecraft.getInstance().execute(() -> {
               Session session = msSession.session();
               Account account = this.createAuthenticatedAccount(
                     session.username, session.playerID, session.token, msSession.refreshToken());
               if (!this.field21121.containsAccount(account)) {
                  this.field21121.updateAccount(account);
               }
               this.field21121.saveAlts();
               this.field21122 = "§aAlt added. (" + account.getName() + ")";
            }))
            .exceptionally(error -> {
               Client.getInstance().logger.error("Microsoft web login failed", error);
               Minecraft.getInstance().execute(() -> this.field21122 = "§cWeb login failed!");
               return null;
            })
            .whenComplete((ignored, error) -> executor.shutdown());
   }

   private Account createAuthenticatedAccount(String username, String playerID, String token, String refreshToken) {
      String safeUsername = username != null && !username.trim().isEmpty() ? username : "Unknown name";
      String safePlayerID = playerID != null ? playerID : "";
      Account account = new Account(safeUsername, safePlayerID, token);
      account.setName(safeUsername);
      if (safePlayerID != null && !safePlayerID.trim().isEmpty()) {
         account.setUuid(Account.fixUUID(safePlayerID));
      }
      if (refreshToken != null && !refreshToken.trim().isEmpty()) {
         account.setRefreshToken(refreshToken);
      }
      return account;
   }

   @Override
   public void draw(float partialTicks) {
      RenderUtil.drawImage(0.0F, 0.0F, (float) this.getWidthA(), (float) this.getHeightA(),
            Resources.mainmenubackground);
      RenderUtil.drawRoundedRect(0.0F, 0.0F, (float) this.getWidthA(), (float) this.getHeightA(),
            RenderUtil2.applyAlpha(ClientColors.PALE_RED.getColor(), 0.1F));
      RenderUtil.drawRoundedRect(0.0F, 0.0F, (float) this.getWidthA(), (float) this.getHeightA(),
            RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.95F));
      RenderUtil.drawString(
            ResourceRegistry.DefaultClientFont, (float) (this.getWidthA() / 2), 38.0F, "Add Alt",
            ClientColors.LIGHT_GREYISH_BLUE.getColor(), FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2,
            FontSizeAdjust.field14488);
      RenderUtil.drawString(
            ResourceRegistry.DefaultClientFont,
            (float) (this.getWidthA() / 2),
            58.0F,
            this.field21122,
            ClientColors.LIGHT_GREYISH_BLUE.getColor(),
            FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2,
            FontSizeAdjust.field14488,
            true);
      super.draw(partialTicks);
   }

   @Override
   public void keyPressed(int keyCode) {
      super.keyPressed(keyCode);
      if (keyCode == 256) {
         Client.getInstance().guiManager.handleScreen(new ClassicAltScreen());
      }
   }
}
