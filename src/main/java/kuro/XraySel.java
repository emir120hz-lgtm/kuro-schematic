package kuro;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** XRay icin secilen bloklar. Katalog BlockSel.ALL ile ayni; secimler Config.xrayBlocks / Config.xrayCustom icinde saklanir. */
public final class XraySel {
   public static final String DEFAULT = "diamond,emerald,gold,iron,coal,redstone,lapis,copper,ancient_debris,quartz";

   private static final Set<String> on = new HashSet<>();

   private XraySel() {
   }

   public static void load() {
      on.clear();
      for (String s : Config.xrayBlocks.split(",")) {
         String k = s.trim();
         if (!k.isEmpty()) {
            on.add(k);
         }
      }

      Xray.markRebuild();
   }

   public static boolean has(String key) {
      return on.contains(key);
   }

   public static void set(String key, boolean v) {
      if (v ? on.add(key) : on.remove(key)) {
         StringBuilder sb = new StringBuilder();
         for (BlockSel.Entry en : BlockSel.ALL) {
            if (on.contains(en.key())) {
               if (sb.length() > 0) {
                  sb.append(',');
               }

               sb.append(en.key());
            }
         }

         Config.xrayBlocks = sb.toString();
         Xray.markRebuild();
      }
   }

   public static List<String> custom() {
      List<String> l = new ArrayList<>();
      for (String s : Config.xrayCustom.split(",")) {
         if (!s.isBlank()) {
            l.add(s.trim());
         }
      }

      return l;
   }

   /** @return null = eklendi, aksi halde hata metni */
   public static String addCustom(String raw) {
      String id = Mods.validBlockId(raw);
      if (id == null) {
         return "Geçersiz blok: " + raw.trim();
      }

      List<String> l = custom();
      if (l.contains(id)) {
         return "Zaten ekli";
      }

      l.add(id);
      Config.xrayCustom = String.join(",", l);
      Xray.markRebuild();
      return null;
   }

   public static void removeCustom(int index) {
      List<String> l = custom();
      if (index >= 0 && index < l.size()) {
         l.remove(index);
         Config.xrayCustom = String.join(",", l);
         Xray.markRebuild();
      }
   }
}
