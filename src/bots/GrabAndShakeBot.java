package bots;

import ai.abstraction.AbstractionLayerAI;
import ai.abstraction.WorkerRushPlusPlus;
import ai.abstraction.pathfinding.AStarPathFinding;
import ai.abstraction.pathfinding.PathFinding;
import ai.core.AI;
import ai.core.AIWithComputationBudget;
import ai.core.ParameterSpecification;
import rts.*;
import rts.units.Unit;
import rts.units.UnitTypeTable;
import util.Pair;

import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;

public class GrabAndShakeBot extends AbstractionLayerAI {
    static int DIST_ENEMY_BASE_TO_BE_RUSH = 22;


    List<AIWithComputationBudget> bots;
    List<Rectangle> territories;

    protected UnitTypeTable utt;

    // TODO: use player indices to show which territory belongs to which player
    /** 0: free, > 0: occupied, < 0: reserved*/
    int[][] buildable;

    File currentMapFile;
    GrabAndShakeBotSetting currentSetting;
    int myPlayerIdx = -1;

    public GrabAndShakeBot(UnitTypeTable a_utt) {
        this(a_utt, new AStarPathFinding());
    }


    public GrabAndShakeBot(UnitTypeTable a_utt, PathFinding a_pf) {
        super(a_pf);
        bots = new ArrayList<>();
        territories = new ArrayList<>();
        buildable = new int[1][1];
        reset(a_utt);
    }

    public void reset() {
        super.reset();
        for (AIWithComputationBudget bot: bots) {
            bot.reset();
        }
        currentSetting = null;
        currentMapFile = null;
        myPlayerIdx = -1;
    }

    public void reset(UnitTypeTable a_utt) {
        super.reset();
        utt = a_utt;
        for (AIWithComputationBudget bot: bots) {
            bot.reset(a_utt);
        }
        currentSetting = null;
        currentMapFile = null;
        myPlayerIdx = -1;
    }


    public AI clone() {
        GrabAndShakeBot b = new GrabAndShakeBot(utt, pf);
        b.bots = new ArrayList<>(bots);
        b.territories = new ArrayList<>(territories);
        b.buildable = new int[buildable.length][buildable[0].length];
        for (int i = 0; i < buildable.length; i++) {
            b.buildable[i] = Arrays.copyOf(buildable[i], buildable[i].length);
        }
        return b;
    }

    private int distance(Unit u1, Unit u2) {
        return distance(u1.getX(), u1.getY(), u2.getX(), u2.getY());
    }
    private int distance(int x1, int y1, int x2, int y2) {
        return Math.abs(x2-x1) + Math.abs(y2-y1);
    }

    public void botsTerritoriesFromSetting(GrabAndShakeBotSetting setting) {
        bots.clear();
        bots = setting.usedBots;
        territories.clear();
        territories = setting.territories;
    }

    @Override
    public void preGameAnalysis(GameState gs, long milliseconds) throws Exception {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        // TODO: evaluate free places to build stuff

        // TODO: evaluate strategy not by distance to enemy base, but by simulating building barracks and training first unit vs. WorkerRushPlusPlus (or formula with distance to resources)

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
                if (x < 0 || y < 0 || x >= w || y >= h)
                    continue;
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

        currentSetting = setting;

        this.botsTerritoriesFromSetting(setting);
    }


    static final char FREE = '_';
    static final char BLCOKED = 'X';
    static final char RESOURCE = 'r';
    static final char BASE = 'b';
    static final char BARRACKS = 'c';
    static final char MELEE = 'm';
    static final char WORKER = 'w';

    public static String compressString(String s) {
        if (s.length() < 2)
            return s;
        char[] arr = s.toCharArray();
        StringBuilder b = new StringBuilder();
        int occurrences = 1;
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] == arr[i-1]) {
                occurrences++;
            } else {
                b.append(arr[i-1]);
                if (occurrences > 1) {
                    b.append(occurrences);
                }
                occurrences = 1;
            }
        }

        b.append(arr[arr.length-1]);
        if (occurrences > 1) {
            b.append(occurrences);
        }

        return b.toString();
    }

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
        String fileName = compressString(b.append(".gsb").toString());

        File folder = new File(readWriteFolder);
        for (final File entry: Objects.requireNonNull(folder.listFiles())) {
            if (entry.isFile() && entry.getName().equals(fileName)) {
                Scanner scanner = new Scanner(entry);
                GrabAndShakeBotSetting setting = new GrabAndShakeBotSetting();
                setting.parseFromFile(scanner, utt, pgs);
                botsTerritoriesFromSetting(setting);
                currentSetting = setting;
                currentMapFile = entry;
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

        File newFile = new File(readWriteFolder+"/"+fileName);
        try {
            newFile.createNewFile();
        } catch (Exception ignored) {
            System.out.println("GrabAndShakeBot: Exception occured, when creating a new file.");
            preGameAnalysis(gs, System.currentTimeMillis()-startTime);
            return;
        }
        // analyses the pgs
        preGameAnalysis(gs, System.currentTimeMillis()-startTime);
        // after that the fields of this are set properly
        GrabAndShakeBotSetting setting = new GrabAndShakeBotSetting();
        setting.fromGrabAndShakeBot(this);
        currentSetting = setting;
        currentMapFile = newFile;
        // writing the result of analysis to newFile
        try {
            FileWriter writer = new FileWriter(newFile);
            writer.write(setting.toString());
            writer.close();
        } catch (Exception ignored) {
            // delete the file so we know next time, that we need to re-do the analysis
            newFile.delete();
            currentMapFile = null;
        }
    }

    public PlayerAction getAction(int player, GameState gs) throws Exception {
        myPlayerIdx = player;

        PlayerAction action = new PlayerAction();

        // TODO: multi thread this if num_bots > 1
        int size = bots.size();
        PhysicalGameState[] pgss = new PhysicalGameState[size];
        GameState[] gss = new GameState[size];

        if (size > 1) {
            // territories needed
            PhysicalGameState pgs = gs.getPhysicalGameState();

            for (int i = 0; i < size; i++) {
                Rectangle range = territories.get(i);
                // PhysicalGameState
                pgss[i] = new PhysicalGameState(range.width, range.height);
                // keeping players and units without cloning them
                for (Player p: pgs.getPlayers()) {
                    pgss[i].addPlayer(p);
                }
                for (Unit u: pgs.getUnitsInRectangle(range.x, range.y, range.width, range.height)) {
                    pgss[i].addUnit(u);
                }
                for (int x = range.x; x < range.x+range.width; ++x) {
                    for (int y = range.y; y < range.y+range.height; ++y) {
                        int t = pgs.getTerrain(x,y);
                        int setX = x-range.width, setY = y-range.height;
                        pgss[i].setTerrain(setX, setY, t);
                    }
                }
                // GameState -> vectorObservation is not important -> not necessary to clone something for that
                gss[i] = new GameState(pgss[i],utt);
                // copy all unitActions (we can get the reference with this function and manipulate it)
                Map<Unit, UnitActionAssignment> unitActions = gss[i].getUnitActions();
                for (Map.Entry<Unit, UnitActionAssignment> entry: gs.getUnitActions().entrySet()) {
                    Unit u = entry.getKey();
                    if (range.contains(u.getX(), u.getY())) {
                        unitActions.put(u, entry.getValue());
                    }
                }
            }
        } else {
            gss[0] = gs;
        }

        if (size > 0) {
            for (int i = 0; i < size; ++i) {
                PlayerAction pa = bots.get(i).getAction(player, gss[i]);

                // add all actions to the overall bot
                for (Pair<Unit, UnitAction> pair : pa.getActions()) {
                    action.addUnitAction(pair.m_a, pair.m_b);
                }

                // add all actionAssignments to the real GameState (we can manipulate the real gamestate because we get the same reference)
                gs.getUnitActions().putAll(gss[i].getUnitActions());
            }
        }

        return action;
    }

    @Override
    public void gameOver(int winner) {
        // adapt behaviour for a map
        if (currentSetting == null || currentMapFile == null) {
            return;
        }

        // WIN or TIE or no playerIdx set -> do nothing
        if (winner < 0 || myPlayerIdx < 0 || winner == myPlayerIdx) {
            return;
        }

        // LOST -> change strategy
        int size = currentSetting.usedBots.size();
        for (int i = 0; i < size; i++) {
            if (currentSetting.usedBots.get(i) instanceof RealGrabAndShakeBot) {
                currentSetting.usedBots.set(i, new WorkerRushPlusPlus(utt));
            } else {
                currentSetting.usedBots.set(i, new RealGrabAndShakeBot(utt));
            }
        }

        // save
        try {
            // override
            FileWriter writer = new FileWriter(currentMapFile, false);
            writer.write(currentSetting.toString());
            writer.close();
        } catch (Exception ignored) {
            // delete the file so we know next time, that we need to re-do the analysis
            currentMapFile.delete();
        }

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
