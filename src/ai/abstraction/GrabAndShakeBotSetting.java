package ai.abstraction;

import ai.core.AI;
import ai.core.AIWithComputationBudget;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class GrabAndShakeBotSetting {
    List<Rectangle> territories = new ArrayList<>();
    List<AIWithComputationBudget> usedBots = new ArrayList<>();

    public void parseFromFile(Scanner sc) {
        // TODO: parse
    }

    public String toString() {
        // bots
        StringBuilder builder = new StringBuilder();
        builder.append("num_bots ").append(usedBots.size());
        for (AI bot: usedBots) {
            builder.append(' ').append(bot.getClass().getName());
        }
        builder.append('\n');
        // ranges of the bots
        builder.append("ranges");
        for (Rectangle range: territories) {
            String territory = "("+range.x+","+range.y+","+range.width+","+range.height+")";
            builder.append(' ').append(territory);
        }
        builder.append('\n');
        return builder.toString();
    }
}
