package com.takimliuhc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class Msg {
   private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

   private Msg() {
   }

   public static Component c(String s) {
      return LEGACY.deserialize(s == null ? "" : s);
   }

   public static Component item(String s) {
      return c(s).decoration(TextDecoration.ITALIC, false);
   }
}
