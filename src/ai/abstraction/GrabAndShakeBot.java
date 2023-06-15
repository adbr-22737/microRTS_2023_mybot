package ai.abstraction;

import ai.abstraction.cRush.CRush_V2;
import ai.abstraction.pathfinding.AStarPathFinding;
import ai.abstraction.pathfinding.PathFinding;
import ai.core.AI;
import ai.core.AIWithComputationBudget;
import ai.core.ParameterSpecification;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import rts.*;
import rts.units.Unit;
import rts.units.UnitType;
import rts.units.UnitTypeTable;
import util.Pair;
import weka.core.pmml.jaxbbindings.MININGFUNCTION;

import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class GrabAndShakeBot extends AbstractionLayerAI {
    static int DIST_ENEMY_BASE_TO_BE_RUSH = 20;

    // for remembering old maps
    HashMap<PhysicalGameState, GrabAndShakeBotSetting> visitedMaps;

    List<AIWithComputationBudget> bots;
    List<Rectangle> territories;

    protected UnitTypeTable utt;
    boolean needLoading = true;

    // TODO: use player indices to show which territory belongs to which player
    /** 0: free, > 0: occupied, < 0: reserved*/
    int[][] buildable;

    public GrabAndShakeBot(UnitTypeTable a_utt) {
        this(a_utt, new AStarPathFinding());
    }


    public GrabAndShakeBot(UnitTypeTable a_utt, PathFinding a_pf) {
        super(a_pf);
        bots = new ArrayList<>();
        territories = new ArrayList<>();
        visitedMaps = new HashMap<>();
        reset(a_utt);
    }

    public void reset() {
        super.reset();
        needLoading = true;
        for (AIWithComputationBudget bot: bots) {
            bot.reset();
        }
    }

    public void reset(UnitTypeTable a_utt) {
        super.reset();
        utt = a_utt;
        needLoading = true;
        for (AIWithComputationBudget bot: bots) {
            bot.reset(a_utt);
        }
    }


    public AI clone() {
        GrabAndShakeBot b = new GrabAndShakeBot(utt, pf);
        // TODO: clone all fields of this bot into b
        return b;
    }

    private int distance(Unit u1, Unit u2) {
        return distance(u1.getX(), u1.getY(), u2.getX(), u2.getY());
    }
    private int distance(int x1, int y1, int x2, int y2) {
        return Math.abs(x2-x1) + Math.abs(y2-y1);
    }

    @Override
    public void preGameAnalysis(GameState gs, long milliseconds) throws Exception {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        // TODO: evaluate free places to build stuff

        // evaluate territories
        // -> start at bases and do flood fill -> if bases left in open_bases: new territory
        LinkedList<Unit> my_open_bases = new LinkedList<>();
        HashSet<Unit> bases = new HashSet<>();
        int simulate_player = -1;
        for (Unit u: pgs.getUnits()) {
            if (simulate_player == -1)
                simulate_player = u.getPlayer();

            if (u.getType().isStockpile) {
                bases.add(u);
                if (u.getPlayer() == simulate_player) {
                    my_open_bases.push(u);
                }
            }

        }

        int w = pgs.getWidth(), h = pgs.getHeight();
        GrabAndShakeBotSetting setting = new GrabAndShakeBotSetting();

        while (!my_open_bases.isEmpty()) {
            Unit u = my_open_bases.pop();
            // flood fill
            Unit closest_enemy_base = null;

            boolean[] visited = new boolean[w*h];
            visited[u.getY()*w+u.getX()] = true;

            Queue<Pair<Integer, Integer>> queue = new ArrayDeque<>();
            queue.add(new Pair<>(u.getX()-1, u.getY()));
            queue.add(new Pair<>(u.getX()+1, u.getY()));
            queue.add(new Pair<>(u.getX(), u.getY()-1));
            queue.add(new Pair<>(u.getX(), u.getY()+1));

            int smallestX = Integer.MAX_VALUE, smallestY = Integer.MAX_VALUE, biggestX = Integer.MIN_VALUE, biggestY = Integer.MIN_VALUE;

            while (!queue.isEmpty()) {
                Pair<Integer, Integer> pos = queue.poll();
                int x = pos.m_a, y = pos.m_b;
                if (visited[y*w+x] || pgs.getTerrain(x,y) == PhysicalGameState.TERRAIN_WALL)
                    continue;

                if (x < smallestX) {
                    smallestX = x;
                } else if (x > biggestX) {
                    biggestX = x;
                }
                if (y < smallestY) {
                    smallestY = y;
                } else if (y > biggestY) {
                    biggestY = y;
                }

                visited[y*w+x] = true;
                Optional<Unit> optBase = bases.stream().filter((unit) -> unit.getX() == x && unit.getY() == y).findFirst();
                if (optBase.isPresent()) {
                    Unit base = optBase.get();
                    if (base.getPlayer() == simulate_player) {
                        my_open_bases.remove(base);
                    } else if (closest_enemy_base == null) {
                        closest_enemy_base = base;
                    }
                }
                // checks are made, when they get polled
                queue.add(new Pair<>(x-1,y));
                queue.add(new Pair<>(x+1,y));
                queue.add(new Pair<>(x,y-1));
                queue.add(new Pair<>(x,y+1));
            }

            // when flood fill is done -> set territory size
            setting.territories.add(new Rectangle(smallestX, smallestY, biggestX-smallestX, biggestY-smallestY));

            if (closest_enemy_base != null && distance(u, closest_enemy_base) <= DIST_ENEMY_BASE_TO_BE_RUSH) {
                setting.usedBots.add(new WorkerRushPlusPlus(utt));
            } else {
                setting.usedBots.add(new RealGrabAndShakeBot(utt));
            }
        }

        visitedMaps.put(pgs, setting);
    }


    static final char FREE = '_';
    static final char BLCOKED = 'X';
    static final char RESOURCE = 'r';
    static final char BASE = 'b';
    static final char BARRACKS = 'c';
    static final char MELEE = 'm';
    static final char WORKER = 'w';

    @Override
    public void preGameAnalysis(GameState gs, long milliseconds, String readWriteFolder) throws Exception {
        if (readWriteFolder == null) {
            preGameAnalysis(gs, milliseconds);
            return;
        }
        long startTime = System.currentTimeMillis();

        // evaluate which map it is
        PhysicalGameState pgs = gs.getPhysicalGameState();
        int w = pgs.getWidth(), h = pgs.getHeight();
        int size = w*h;
        char[] indices = new char[size];
        int counter = 0;
        outer: for (boolean[] row: pgs.getAllFree()) {
            for (boolean isFree: row) {
                indices[counter++] = isFree ? FREE : BLCOKED;
                // to be save and get no exception
                if (counter >= size)
                    break outer;
            }
        }

        List<Unit> bases = new ArrayList<>();

        for (Unit u: pgs.getUnits()) {
            if (u.getType().isResource) {
                indices[u.getY() * w + u.getX()] = RESOURCE;
            } else if (u.getType().isStockpile) {
                indices[u.getY() * w + u.getX()] = BASE;
                bases.add(u);
            } else if (u.getType().canHarvest) {
                indices[u.getY() * w + u.getX()] = WORKER;
            } else if (u.getType().canAttack) {
                indices[u.getY() * w + u.getX()] = MELEE;
            } else {
                indices[u.getY() * w + u.getX()] = BARRACKS;
            }
        }

        StringBuilder b = new StringBuilder();
        for (char index : indices) {
            b.append(index);
        }
        String fileName = b.append(".gsb").toString();


        File folder = new File(readWriteFolder);
        for (final File entry: Objects.requireNonNull(folder.listFiles())) {
            if (entry.isFile() && entry.getName().equals(fileName)) {
                Scanner scanner = new Scanner(entry);
                GrabAndShakeBotSetting setting = new GrabAndShakeBotSetting();
                setting.parseFromFile(scanner);
                bots = setting.usedBots;
                territories = setting.territories;
                return;
            }
        }
        // else...
        long used = System.currentTimeMillis() - startTime;
        if (used >= milliseconds) {
            // set a bot and return
            this.bots.add(new RealGrabAndShakeBot(utt));
            return;
        }

        File newFile = new File(fileName);
        newFile.createNewFile();
        // TODO: analyse pgs
        // analyses the pgs
        preGameAnalysis(gs, System.currentTimeMillis()-startTime);

        GrabAndShakeBotSetting setting = new GrabAndShakeBotSetting();
        setting.usedBots = bots;
        setting.territories = territories;
        // writing the result of analysis to newFile
        try {
            FileWriter writer = new FileWriter(newFile);
            writer.write(setting.toString());
            writer.close();
        } catch (Exception ignored) {
            // delete the file so we know next time, that we need to re-do the analysis
            newFile.delete();
        }
    }

    void loadFromVisitedMaps(GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        bots.clear();
        territories.clear();

        visitedMaps.forEach((old_pgs, setting) -> {
            if (pgs.equivalents(old_pgs)) {
                bots.addAll(setting.usedBots);
                territories.addAll(setting.territories);
            }
        });

        if (bots.isEmpty()) {
            try {
                preGameAnalysis(gs, -1);
            } catch (Exception ignored) {
                // do one RealGrabAndChangeBot instead of doing nothing
                bots.add(new RealGrabAndShakeBot(utt));
            }
        }
        if (bots.isEmpty())
            bots.add(new RealGrabAndShakeBot(utt));
    }

    public PlayerAction getAction(int player, GameState gs) throws Exception {
        if (needLoading) {
            loadFromVisitedMaps(gs);
            needLoading = false;
        }

        PlayerAction action = new PlayerAction();

        // TODO: multi thread this if num_bots > 1

        for (AIWithComputationBudget bot: bots) {
            PlayerAction pa = bot.getAction(player, gs);
            // add all actions to the overall bot
            for (Pair<Unit, UnitAction> pair: pa.getActions()) {
                action.addUnitAction(pair.m_a, pair.m_b);
            }
        }

        return action;
    }


    @Override
    public List<ParameterSpecification> getParameters()
    {
        List<ParameterSpecification> parameters = new ArrayList<>();

        for (AIWithComputationBudget bot: bots) {
            parameters.addAll(bot.getParameters());
        }

        return parameters;
    }

}
