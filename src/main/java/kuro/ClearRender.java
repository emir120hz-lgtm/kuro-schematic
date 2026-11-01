package kuro;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * ClearRender: secilen dolgu bloklarini (netherrack, deepslate, blackstone ...) hic cizmez.
 * Sunucu tarafli anti-xray'in gonderdigi sahte/karisik dolgu bloklarinin arkasini gormeni saglar.
 * Not: gercek bloklari geri getirmez, sadece goruntuyu temizler. Chunk occlusion culling de kapatilir.
 */
public final class ClearRender {
   /** {id, etiket} */
   public static final String[][] ALL = {
      {"stone", "Stone"}, {"deepslate", "Deepslate"}, {"cobbled_deepslate", "Cobbled Deepslate"}, {"tuff", "Tuff"},
      {"netherrack", "Netherrack"}, {"blackstone", "Blackstone"}, {"basalt", "Basalt"}, {"smooth_basalt", "Smooth Basalt"},
      {"andesite", "Andesite"}, {"diorite", "Diorite"}, {"granite", "Granite"}, {"dirt", "Dirt"},
      {"gravel", "Gravel"}, {"end_stone", "End Stone"}
   };

   public static final String DEFAULT = "stone,deepslate,cobbled_deepslate,tuff,netherrack,blackstone,basalt,smooth_basalt";

   /** Mixin (chunk iscisi threadleri) okur. */
   public static volatile boolean active;

   private static volatile Set<Block> hidden = Set.of();

   private ClearRender() {
   }

   public static boolean has(String id) {
      for (String s : Config.clearBlocks.split(",")) {
         if (s.trim().equals(id)) {
            return true;
         }
      }

      return false;
   }

   public static void set(String id, boolean on) {
      if (has(id) == on) {
         return;
      }

      StringBuilder sb = new StringBuilder();
      for (String s : Config.clearBlocks.split(",")) {
         String t = s.trim();
         if (!t.isEmpty() && !t.equals(id)) {
            sb.append(t).append(',');
         }
      }

      if (on) {
         sb.append(id).append(',');
      }

      Config.clearBlocks = sb.toString();
      Config.save();
      rebuild();
      if (active) {
         reload();
      }
   }

   public static void rebuild() {
      Set<Block> n = new HashSet<>();
      for (String s : Config.clearBlocks.split(",")) {
         String t = s.trim();
         if (!t.isEmpty()) {
            Identifier id = Identifier.tryParse("minecraft:" + t);
            if (id != null && Registries.BLOCK.containsId(id)) {
               n.add(Registries.BLOCK.get(id));
            }
         }
      }

      hidden = n;
   }

   public static boolean isHidden(Block b) {
      return hidden.contains(b);
   }

   public static void setActive(boolean v) {
      if (active == v) {
         return;
      }

      active = v;
      rebuild();
      reload();
   }

   private static void reload() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.worldRenderer != null && mc.world != null) {
         mc.worldRenderer.reload();
      }
   }
}
