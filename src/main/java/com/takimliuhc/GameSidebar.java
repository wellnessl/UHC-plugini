package com.takimliuhc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class GameSidebar {
   private static final int MAX_LINES = 15;
   private static final List<String> DEFAULT_LINES = List.of("&7Süre: &f%elapsed%", "&7Hayatta: &a%alive_players%", "&7Takım: &b%alive_teams%", "&7Kill: &c%kills%", "&7Border: &e%border_size%");
   private final TeamUHC plugin;
   private final Game game;
   private final Map<UUID, PlayerBoard> boards = new HashMap();

   public GameSidebar(TeamUHC plugin, Game game) {
      this.plugin = plugin;
      this.game = game;
   }

   public void updateAll() {
      if (this.game.isActiveMatch() && this.plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
         for(Player player : Bukkit.getOnlinePlayers()) {
            this.update(player);
         }

      } else {
         this.clear();
      }
   }

   public void update(Player player) {
      if (player != null) {
         if (this.game.isActiveMatch() && this.plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
            UUID id = player.getUniqueId();
            PlayerBoard state = (PlayerBoard)this.boards.get(id);
            if (state == null) {
               state = this.createBoard(player);
               this.boards.put(id, state);
            }

            if (player.getScoreboard() != state.scoreboard) {
               player.setScoreboard(state.scoreboard);
            }

            this.syncTeamTags(state.scoreboard);
            List<String> configured = this.plugin.getConfig().getStringList("scoreboard.lines");
            if (configured.isEmpty()) {
               configured = DEFAULT_LINES;
            }

            int count = Math.min(15, configured.size());

            while(state.teams.size() < count) {
               int row = state.teams.size();
               Team team = state.scoreboard.registerNewTeam("sidebar_" + row);
               String entry = ChatColor.values()[row].toString();
               team.addEntry(entry);
               state.teams.add(team);
               state.entries.add(entry);
            }

            while(state.teams.size() > count) {
               int last = state.teams.size() - 1;
               state.scoreboard.resetScores((String)state.entries.remove(last));
               ((Team)state.teams.remove(last)).unregister();
            }

            state.objective.setDisplayName(this.color(this.plugin.getConfig().getString("scoreboard.title", "&a&lTEAM UHC")));

            for(int i = 0; i < count; ++i) {
               String line = this.render((String)configured.get(i), player);
               ((Team)state.teams.get(i)).setPrefix(this.color(line));
               state.objective.getScore((String)state.entries.get(i)).setScore(count - i);
            }

         } else {
            this.clearPlayer(player);
         }
      }
   }

   public void clear() {
      for(Map.Entry<UUID, PlayerBoard> entry : this.boards.entrySet()) {
         Player player = Bukkit.getPlayer((UUID)entry.getKey());
         PlayerBoard state = (PlayerBoard)entry.getValue();
         if (player != null && player.getScoreboard() == state.scoreboard) {
            player.setScoreboard(state.originalScoreboard);
         }
      }

      this.boards.clear();
   }

   private void clearPlayer(Player player) {
      PlayerBoard state = (PlayerBoard)this.boards.remove(player.getUniqueId());
      if (state != null && player.getScoreboard() == state.scoreboard) {
         player.setScoreboard(state.originalScoreboard);
      }

   }

   private PlayerBoard createBoard(Player player) {
      Scoreboard originalScoreboard = player.getScoreboard();
      Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
      Objective objective = scoreboard.registerNewObjective("teamuhc", "dummy", this.color(this.plugin.getConfig().getString("scoreboard.title", "&a&lTEAM UHC")));
      objective.setDisplaySlot(DisplaySlot.SIDEBAR);
      this.syncTeamTags(scoreboard);
      player.setScoreboard(scoreboard);
      return new PlayerBoard(originalScoreboard, scoreboard, objective);
   }

   private void syncTeamTags(Scoreboard scoreboard) {
      Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
      HashSet<String> activeTeams = new HashSet();

      for(Team source : main.getTeams()) {
         if (source.getName().startsWith("uhc_")) {
            activeTeams.add(source.getName());
            Team copy = scoreboard.getTeam(source.getName());
            if (copy == null) {
               copy = scoreboard.registerNewTeam(source.getName());
            }

            copy.setColor(source.getColor());
            copy.setPrefix(source.getPrefix());
            copy.setSuffix(source.getSuffix());
            copy.setAllowFriendlyFire(source.allowFriendlyFire());
            copy.setCanSeeFriendlyInvisibles(source.canSeeFriendlyInvisibles());
            HashSet<String> expectedEntries = new HashSet<String>(source.getEntries());

            for(String entry : new HashSet<String>(copy.getEntries())) {
               if (!expectedEntries.contains(entry)) {
                  copy.removeEntry(entry);
               }
            }

            for(String entry : expectedEntries) {
               if (!copy.hasEntry(entry)) {
                  copy.addEntry(entry);
               }
            }
         }
      }

      for(Team copy : new ArrayList<Team>(scoreboard.getTeams())) {
         if (copy.getName().startsWith("uhc_") && !activeTeams.contains(copy.getName())) {
            copy.unregister();
         }
      }

   }

   private String render(String line, Player player) {
      return line.replace("%elapsed%", this.game.elapsedDisplay()).replace("%alive_players%", String.valueOf(this.game.alivePlayerCount())).replace("%alive_teams%", String.valueOf(this.game.aliveTeamCount())).replace("%kills%", String.valueOf(this.game.killsFor(player.getUniqueId()))).replace("%border_size%", this.game.borderSizeDisplay());
   }

   private String color(String text) {
      return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
   }

   private static final class PlayerBoard {
      private final Scoreboard originalScoreboard;
      private final Scoreboard scoreboard;
      private final Objective objective;
      private final List<Team> teams = new ArrayList();
      private final List<String> entries = new ArrayList();

      private PlayerBoard(Scoreboard originalScoreboard, Scoreboard scoreboard, Objective objective) {
         this.originalScoreboard = originalScoreboard;
         this.scoreboard = scoreboard;
         this.objective = objective;
      }
   }
}
