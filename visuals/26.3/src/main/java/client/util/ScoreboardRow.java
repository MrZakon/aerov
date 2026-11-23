package client.util;

import net.minecraft.network.chat.Component;

public record ScoreboardRow(Component name, int score, Component formattedScore, int scoreWidth) {
   public Component getFormattedScore() {
      return this.formattedScore;
   }

   public int getScoreWidth() {
      return this.scoreWidth;
   }

   public int getScore() {
      return this.score;
   }

   public Component getName() {
      return this.name;
   }
}
