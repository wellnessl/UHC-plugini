package com.takimliuhc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class Commands implements CommandExecutor, TabCompleter {
   private final TeamUHC plugin;

   public Commands(TeamUHC plugin) {
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
      switch (cmd.getName().toLowerCase(Locale.ROOT)) {
         case "uhc" -> this.uhc(s, a);
         case "team" -> this.team(s, a);
         case "tc" -> this.tc(s, a);
      }

      return true;
   }

   private void uhc(CommandSender s, String[] a) {
      if (a.length == 0) {
         this.plugin.send(s, "uhc-usage");
      } else {
         switch (a[0].toLowerCase(Locale.ROOT)) {
            case "start":
               this.plugin.game().start(s);
               break;
            case "stop":
               this.plugin.game().stop(s);
               break;
            case "pvp":
            case "skip":
               if (!this.plugin.game().isRunning()) {
                  this.plugin.send(s, "not-running");
               } else if (this.plugin.game().isPvpOn()) {
                  this.sendConfigured(s, "pvp-already", "&ePvP zaten açık.");
               } else {
                  this.plugin.game().skipToPvpTime();
                  this.sendConfigured(s, "pvp-manual-enabled", "&aPvP zamanına kadar oyun süresi simüle edildi.");
               }
               break;
            case "reload":
               this.plugin.reload();
               this.plugin.send(s, "reloaded");
               break;
            case "revive":
               if (a.length < 2) {
                  this.plugin.send(s, "uhc-usage");
                  return;
               }

               Player t = Bukkit.getPlayerExact(a[1]);
               if (t == null) {
                  this.plugin.send(s, "player-not-found");
                  return;
               }

               this.plugin.game().revive(s, t);
               break;
            default:
               this.plugin.send(s, "uhc-usage");
         }

      }
   }

   private void sendConfigured(CommandSender sender, String key, String fallback) {
      String prefix = this.plugin.getConfig().getString("prefix", "");
      String message = this.plugin.getConfig().getString("messages." + key, fallback);
      sender.sendMessage(Msg.c(prefix + message));
   }

   private void team(CommandSender s, String[] a) {
      if (!(s instanceof Player p)) {
         this.plugin.send(s, "only-players");
      } else {
         TeamManager tm = this.plugin.teams();
         if (a.length == 0) {
            this.plugin.send(p, "team-usage");
         } else {
            switch (a[0].toLowerCase(Locale.ROOT)) {
               case "create":
                  if (tm.isLocked()) {
                     this.plugin.send(p, "team-locked");
                     return;
                  }

                  if (tm.of(p) != null) {
                     this.plugin.send(p, "team-already");
                     return;
                  }

                  TeamManager.UTeam t = tm.create(p);
                  this.plugin.send(p, "team-created", "%team%", t.display());
                  break;
               case "invite":
                  if (a.length < 2) {
                     this.plugin.send(p, "team-usage");
                     return;
                  }

                  Player target = Bukkit.getPlayerExact(a[1]);
                  if (target == null) {
                     this.plugin.send(p, "player-not-found");
                     return;
                  }

                  if (target.equals(p)) {
                     this.plugin.send(p, "team-self-invite");
                     return;
                  }

                  tm.invite(p, target);
                  break;
               case "accept":
                  tm.accept(p);
                  break;
               case "leave":
                  if (tm.isLocked()) {
                     this.plugin.send(p, "team-locked");
                     return;
                  }

                  if (tm.leave(p)) {
                     this.plugin.send(p, "team-left-self");
                  } else {
                     this.plugin.send(p, "team-none");
                  }
                  break;
               case "list":
                  tm.sendList(p);
                  break;
               default:
                  this.plugin.send(p, "team-usage");
            }

         }
      }
   }

   private void tc(CommandSender s, String[] a) {
      if (!(s instanceof Player p)) {
         this.plugin.send(s, "only-players");
      } else {
         TeamManager.UTeam t = this.plugin.teams().of(p);
         if (t == null) {
            this.plugin.send(p, "team-none");
         } else if (a.length == 0) {
            this.plugin.send(p, "tc-usage");
         } else {
            this.plugin.teams().teamChat(t, p, String.join(" ", a));
         }
      }
   }

   public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
      ArrayList<String> out = new ArrayList();
      String name = cmd.getName().toLowerCase(Locale.ROOT);
      if (name.equals("uhc")) {
         if (a.length == 1) {
            out.addAll(List.of("start", "stop", "skip", "pvp", "reload", "revive"));
         } else if (a.length == 2 && a[0].equalsIgnoreCase("revive")) {
            this.addPlayers(out);
         }
      } else if (name.equals("team")) {
         if (a.length == 1) {
            out.addAll(List.of("create", "invite", "accept", "leave", "list"));
         } else if (a.length == 2 && a[0].equalsIgnoreCase("invite")) {
            this.addPlayers(out);
         }
      }

      String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
      out.removeIf((x) -> !x.toLowerCase(Locale.ROOT).startsWith(last));
      return out;
   }

   private void addPlayers(List<String> out) {
      for(Player p : Bukkit.getOnlinePlayers()) {
         out.add(p.getName());
      }

   }
}
