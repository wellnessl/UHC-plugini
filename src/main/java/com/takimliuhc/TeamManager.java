package com.takimliuhc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class TeamManager {
   private static final Col[] PALETTE;
   private final TeamUHC plugin;
   private final Map<Integer, UTeam> teams = new LinkedHashMap();
   private final Map<UUID, UTeam> byPlayer = new HashMap();
   private final Map<UUID, UTeam> invites = new HashMap();
   private int counter = 0;
   private boolean locked = false;

   public TeamManager(TeamUHC plugin) {
      this.plugin = plugin;
      Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();

      for(Team t : new ArrayList<Team>(sb.getTeams())) {
         if (t.getName().startsWith("uhc_")) {
            t.unregister();
         }
      }

   }

   public Collection<UTeam> all() {
      return new ArrayList(this.teams.values());
   }

   public UTeam of(UUID id) {
      return (UTeam)this.byPlayer.get(id);
   }

   public UTeam of(Player p) {
      return (UTeam)this.byPlayer.get(p.getUniqueId());
   }

   public boolean isLocked() {
      return this.locked;
   }

   public void setLocked(boolean v) {
      this.locked = v;
   }

   public int maxSize() {
      return Math.max(1, this.plugin.getConfig().getInt("teams.size", 2));
   }

   public boolean sameTeam(UUID a, UUID b) {
      UTeam ta = (UTeam)this.byPlayer.get(a);
      return ta != null && ta == this.byPlayer.get(b);
   }

   public UTeam create(Player leader) {
      ++this.counter;
      Col col = PALETTE[(this.counter - 1) % PALETTE.length];
      String name = this.plugin.getConfig().getString("teams.name-format", "Takım %number%").replace("%number%", String.valueOf(this.counter));
      UTeam t = new UTeam(this.counter, name, col);
      t.leader = leader.getUniqueId();
      Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
      String sbName;
      Team old = sb.getTeam(sbName = "uhc_" + this.counter);
      if (old != null) {
         old.unregister();
      }

      Team st = sb.registerNewTeam(sbName);
      st.color(col.color());
      st.setAllowFriendlyFire(this.plugin.getConfig().getBoolean("teams.friendly-fire", false));
      st.setCanSeeFriendlyInvisibles(true);
      t.sb = st;
      this.teams.put(this.counter, t);
      this.addMember(t, leader);
      return t;
   }

   public void addMember(UTeam t, Player p) {
      t.members.put(p.getUniqueId(), p.getName());
      this.byPlayer.put(p.getUniqueId(), t);
      t.sb.addEntry(p.getName());
   }

   private void removeMember(UTeam t, UUID id) {
      String name = (String)t.members.remove(id);
      this.byPlayer.remove(id);
      if (name != null) {
         t.sb.removeEntry(name);
      }

      if (t.members.isEmpty()) {
         this.dissolve(t);
      } else if (id.equals(t.leader)) {
         t.leader = (UUID)t.members.keySet().iterator().next();
      }

   }

   private void dissolve(UTeam t) {
      this.teams.remove(t.number);

      try {
         t.sb.unregister();
      } catch (IllegalStateException var3) {
      }

   }

   public boolean leave(Player p) {
      UTeam t = this.of(p);
      if (t == null) {
         return false;
      } else {
         this.removeMember(t, p.getUniqueId());
         if (this.teams.containsKey(t.number)) {
            for(Player m : t.online()) {
               this.plugin.send(m, "team-left", "%player%", p.getName());
            }
         }

         return true;
      }
   }

   public void removeOffline() {
      for(UTeam t : this.all()) {
         for(UUID id : new ArrayList<UUID>(t.members.keySet())) {
            if (Bukkit.getPlayer(id) == null) {
               this.removeMember(t, id);
            }
         }
      }

   }

   public void invite(Player from, Player to) {
      if (this.locked) {
         this.plugin.send(from, "team-locked");
      } else if (this.of(to) != null) {
         this.plugin.send(from, "team-target-in-team", "%player%", to.getName());
      } else {
         UTeam t = this.of(from);
         if (t == null) {
            t = this.create(from);
            this.plugin.send(from, "team-created", "%team%", t.display());
         }

         if (t.members.size() >= this.maxSize()) {
            this.plugin.send(from, "team-full", "%size%", String.valueOf(this.maxSize()));
         } else {
            this.invites.put(to.getUniqueId(), t);
            this.plugin.send(from, "team-invite-sent", "%player%", to.getName());
            this.plugin.send(to, "team-invite-received", "%player%", from.getName(), "%team%", t.display());
            int timeout = this.plugin.getConfig().getInt("teams.invite-timeout", 30);
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.invites.remove(to.getUniqueId(), t), (long)timeout * 20L);
         }
      }
   }

   public void accept(Player p) {
      if (this.locked) {
         this.plugin.send(p, "team-locked");
      } else {
         UTeam t = (UTeam)this.invites.remove(p.getUniqueId());
         if (t != null && this.teams.containsKey(t.number)) {
            if (this.of(p) != null) {
               this.plugin.send(p, "team-already");
            } else if (t.members.size() >= this.maxSize()) {
               this.plugin.send(p, "team-full", "%size%", String.valueOf(this.maxSize()));
            } else {
               this.addMember(t, p);

               for(Player m : t.online()) {
                  this.plugin.send(m, "team-joined", "%player%", p.getName());
               }

            }
         } else {
            this.plugin.send(p, "team-no-invite");
         }
      }
   }

   public void clearInvites(Player p) {
      this.invites.remove(p.getUniqueId());
   }

   public void teamChat(UTeam t, Player from, String text) {
      String fmt = this.plugin.getConfig().getString("teams.chat.format", "&b[Takım] &f%player%&7: &b%message%");
      Component line = Msg.c(fmt.replace("%player%", from.getName())).replaceText((TextReplacementConfig)TextReplacementConfig.builder().matchLiteral("%message%").replacement(Component.text(text)).build());

      for(Player m : t.online()) {
         m.sendMessage(line);
      }

   }

   public void sendList(Player to) {
      if (this.teams.isEmpty()) {
         this.plugin.send(to, "team-list-empty");
      } else {
         this.plugin.send(to, "team-list-header");

         for(UTeam t : this.teams.values()) {
            to.sendMessage(Msg.c(this.plugin.msg("team-list-line", "%team%", t.display(), "%members%", String.join(", ", t.members.values()))));
         }

      }
   }

   public void clearAll() {
      for(UTeam t : this.all()) {
         this.dissolve(t);
      }

      this.byPlayer.clear();
      this.invites.clear();
      this.counter = 0;
   }

   static {
      PALETTE = new Col[]{new Col("&c", NamedTextColor.RED), new Col("&9", NamedTextColor.BLUE), new Col("&a", NamedTextColor.GREEN), new Col("&e", NamedTextColor.YELLOW), new Col("&d", NamedTextColor.LIGHT_PURPLE), new Col("&b", NamedTextColor.AQUA), new Col("&6", NamedTextColor.GOLD), new Col("&5", NamedTextColor.DARK_PURPLE), new Col("&3", NamedTextColor.DARK_AQUA), new Col("&2", NamedTextColor.DARK_GREEN), new Col("&4", NamedTextColor.DARK_RED), new Col("&7", NamedTextColor.GRAY)};
   }

   public static final class UTeam {
      public final int number;
      public final String name;
      public final Col col;
      public UUID leader;
      public final Map<UUID, String> members = new LinkedHashMap();
      Team sb;

      UTeam(int number, String name, Col col) {
         this.number = number;
         this.name = name;
         this.col = col;
      }

      public String display() {
         String var10000 = this.col.code();
         return var10000 + this.name;
      }

      public List<Player> online() {
         ArrayList<Player> out = new ArrayList();

         for(UUID id : this.members.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
               out.add(p);
            }
         }

         return out;
      }
   }

   public static record Col(String code, NamedTextColor color) {
   }
}
