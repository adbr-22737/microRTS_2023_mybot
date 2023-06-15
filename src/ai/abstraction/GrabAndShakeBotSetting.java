package ai.abstraction;

import ai.core.AI;
import ai.core.AIWithComputationBudget;
import rts.PhysicalGameState;
import rts.units.UnitTypeTable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class GrabAndShakeBotSetting {
    List<Rectangle> territories = new ArrayList<>();
    List<AIWithComputationBudget> usedBots = new ArrayList<>();

    public void parseFromFile(Scanner sc, UnitTypeTable utt, PhysicalGameState pgs) {
        usedBots.clear();
        territories.clear();

        while (sc.hasNextLine()) {
            String line = sc.nextLine();
            if (line.isBlank())
                continue;

            String[] words = line.split(" ");
            int nBots = -1;
            int nTerritories = -1;
            switch (words[0]) {
                case "num_bots" -> {
                    try {
                        nBots = Integer.parseInt(words[1]);
                    } catch (Exception ignored) {
                        nBots = 0;
                    }
                    if (nBots < 1) {
                        usedBots.clear();
                        territories.clear();
                        usedBots.add(new RealGrabAndShakeBot(utt));
                        territories.add(new Rectangle(0,0,pgs.getWidth(),pgs.getHeight()));
                        return;
                    } else if (nTerritories >= 0) {
                        nBots = Math.min(nBots, nTerritories);
                    }
                    for (int i = 2; i < 2+nBots; ++i) {
                        String fullClassName = words[i];
                        if (fullClassName.equals(RealGrabAndShakeBot.class.getName()))
                            usedBots.add(new RealGrabAndShakeBot(utt));
                        else if (fullClassName.equals(WorkerRushPlusPlus.class.getName()))
                            usedBots.add(new WorkerRushPlusPlus(utt));
                        else
                            usedBots.add(new RealGrabAndShakeBot(utt));
                    }
                }
                case "ranges" -> {
                    try {
                        nTerritories = Integer.parseInt(words[1]);
                    } catch (Exception ignored) {
                        nTerritories = 0;
                    }
                    if (nTerritories < 1) {
                        usedBots.clear();
                        territories.clear();
                        usedBots.add(new RealGrabAndShakeBot(utt));
                        territories.add(new Rectangle(0,0,pgs.getWidth(),pgs.getHeight()));
                        return;
                    } else if (nBots >= 0) {
                        nTerritories = Math.min(nBots, nTerritories);
                    }
                    for (int i = 2; i < 2+nTerritories; ++i) {
                        String[] coords = words[i].split(",");
                        try {
                            int x = Integer.parseInt(coords[0]);
                            int y = Integer.parseInt(coords[1]);
                            int w = Integer.parseInt(coords[2]);
                            int h = Integer.parseInt(coords[3]);
                            territories.add(new Rectangle(x,y,w,h));
                        } catch (Exception ignored) {
                            usedBots.clear();
                            territories.clear();
                            usedBots.add(new RealGrabAndShakeBot(utt));
                            territories.add(new Rectangle(0,0,pgs.getWidth(),pgs.getHeight()));
                            return;
                        }
                    }
                }
            }
        }
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
        builder.append("ranges ").append(territories.size());
        for (Rectangle range: territories) {
            String territory = range.x+","+range.y+","+range.width+","+range.height;
            builder.append(' ').append(territory);
        }
        builder.append('\n');
        return builder.toString();
    }
}
