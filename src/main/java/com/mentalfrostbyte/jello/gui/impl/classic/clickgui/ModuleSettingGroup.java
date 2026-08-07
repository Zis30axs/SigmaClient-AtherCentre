package com.mentalfrostbyte.jello.gui.impl.classic.clickgui;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.impl.classic.clickgui.panel.ClickGuiPanel;
import com.mentalfrostbyte.jello.gui.impl.classic.clickgui.buttons.Class4345;
import com.mentalfrostbyte.jello.gui.impl.classic.clickgui.buttons.Exit;
import com.mentalfrostbyte.jello.gui.impl.classic.clickgui.panel.CategoryPanel;
import com.mentalfrostbyte.jello.gui.impl.jello.buttons.ScrollableContentPanel;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.client.render.Resources;

public class ModuleSettingGroup extends ClickGuiPanel {
   /** Grid geometry of the module cards; kept as constants so the list panel can be sized from them. */
   private static final int COLUMNS = 3;
   private static final int CELL_WIDTH = 170;
   private static final int CELL_HEIGHT = 80;
   /** Insets of the scrolling viewport inside the panel: below the title, above the description strip. */
   private static final int LIST_X = 36;
   private static final int LIST_Y = 62;
   private static final int LIST_WIDTH = 536;
   private static final int LIST_BOTTOM_INSET = 46;
   /** Offsets of the first card inside the viewport, chosen so the grid keeps its original screen position. */
   private static final int CELL_ORIGIN_X = 4;
   private static final int CELL_ORIGIN_Y = 10;

   public Class4345 field21181;
   public int field21182 = 0;
   private final ScrollableContentPanel moduleList;

   public ModuleSettingGroup(CustomGuiScreen var1, String var2, int var3, int var4, ModuleCategory[] var5) {
      super(var1, var2, var3 - 296, var4 - 346, 592, 692);

      this.addToList(
              this.moduleList = new ScrollableContentPanel(
                      this, "moduleList", LIST_X, LIST_Y, LIST_WIDTH, this.getHeightA() - LIST_Y - LIST_BOTTOM_INSET
              )
      );
      this.moduleList.setListening(false);

      for (Module var9 : Client.getInstance().moduleManager.getModuleMap().values()) {
         if (var9.isAvailableOnClassic()) {
            for (ModuleCategory var13 : var5) {
               if (var9.getCategoryBasedOnMode().equals(var13)) {
                  this.method13485(var9);
               }
            }
         }
      }

      Exit var14;
      this.addToList(var14 = new Exit(this, "exit", this.getWidthA() - 47, 18));
      var14.onClick((var1x, var2x) -> {
         if (this.field21181 == null) {
            ((ClassicClickGui)this.getParent()).method13417();
         } else {
            this.field21181.method13556();
         }
      });
      this.setListening(false);
   }

   private void method13485(Module var1) {
      int var4 = this.field21182 % COLUMNS;
      int var5 = this.field21182 / COLUMNS;
      this.moduleList
              .addToList(
                      new CategoryPanel(
                              this.moduleList,
                              var1.getName(),
                              CELL_ORIGIN_X + CELL_WIDTH * var4,
                              CELL_ORIGIN_Y + CELL_HEIGHT * var5,
                              CELL_WIDTH,
                              CELL_HEIGHT,
                              var1
                      )
              );
      this.field21182++;
   }

   public void method13486(Module var1) {
      this.runThisOnDimensionUpdate(() -> {
         if (this.field21181 == null) {
            this.addToList(this.field21181 = new Class4345(this, "settings", 5, 70, this.getWidthA() - 10, this.getHeightA() - 75, var1));
            this.field21181.setReAddChildren(true);
         }
      });
   }

   @Override
   public void draw(float partialTicks) {
      super.draw(partialTicks);
      // The hovered card's description is drawn by the panel, not the card, so it stays outside the
      // scrolling viewport. Require the cursor to be inside that viewport as well, otherwise a card
      // clipped away by the scissor would still claim the strip.
      if (this.field21181 == null && this.moduleList.method13114(this.getHeightO(), this.getWidthO())) {
         for (CustomGuiScreen var5 : this.moduleList.getButton().getChildren()) {
            if (var5 instanceof CategoryPanel var6 && this.field21149.calcPercent() == 1.0F && var5.method13114(this.getHeightO(), this.getWidthO())) {
				RenderUtil.drawString(Resources.regular17, 20.0F, (float)(this.getHeightA() - 26), var6.module.getDescription(), -14540254);
               RenderUtil.startScissor(5.0F, (float)(this.getHeightA() - 27), 12.0F, 24.0F);
               RenderUtil.drawImage(5.0F, (float)(this.getHeightA() - 27), 24.0F, 24.0F, Resources.xmark);
               RenderUtil.restoreScissor();
               break;
            }
         }
      }
   }

   @Override
   public void updatePanelDimensions(int newHeight, int newWidth) {
      super.updatePanelDimensions(newHeight, newWidth);
      if (this.field21181 != null && this.field21181.method13557()) {
         this.runThisOnDimensionUpdate(() -> {
            this.removeChildren(this.field21181);
            this.field21181 = null;
         });
      }
   }
}
