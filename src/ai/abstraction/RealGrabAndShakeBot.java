package ai.abstraction;

import ai.abstraction.pathfinding.AStarPathFinding;
import ai.abstraction.pathfinding.PathFinding;
import ai.core.AI;
import ai.core.ParameterSpecification;
import rts.GameState;
import rts.PhysicalGameState;
import rts.Player;
import rts.PlayerAction;
import rts.units.Unit;
import rts.units.UnitType;
import rts.units.UnitTypeTable;
import util.Pair;

import java.util.*;

public class RealGrabAndShakeBot extends AbstractionLayerAI {

    // RangedDefense
    Random r = new Random();
    protected UnitTypeTable utt;
    UnitType workerType;
    UnitType baseType;
    UnitType barracksType;
    UnitType rangedType;
    UnitType heavyType;
    boolean heavyNext = true;

    static float START_REL_DIST_FROM_TERRITORY = 0.15f;
    static float START_ENEMY_DIST = 3.0f;
    static float REL_DIST_MULTIPLIER = 0.001f;
    float relativeDistanceFromBase = START_REL_DIST_FROM_TERRITORY;
    float enemyDistance = START_ENEMY_DIST;
    static int MAX_DIST_RESOUCES_AWAY_FROM_BASE_TO_TRAIN_WORKERS = 6;
    // TODO: use player indices to show which territory belongs to which player
    /** 0: free, > 0: occupied, < 0: reserved*/
    int[][] buildable;

    // TODO: evaluate if the enemy is attacking (use territories, distances, walk-behaviour of troups, ...)

    public RealGrabAndShakeBot(UnitTypeTable a_utt) {
        this(a_utt, new AStarPathFinding());
    }


    public RealGrabAndShakeBot(UnitTypeTable a_utt, PathFinding a_pf) {
        super(a_pf);
        reset(a_utt);
    }

    public void reset() {
        super.reset();
        // additional
        heavyNext = true;
        relativeDistanceFromBase = START_REL_DIST_FROM_TERRITORY;
        enemyDistance = START_ENEMY_DIST;
        buildable = null;
    }

    public void reset(UnitTypeTable a_utt)
    {
        utt = a_utt;
        workerType = utt.getUnitType("Worker");
        baseType = utt.getUnitType("Base");
        barracksType = utt.getUnitType("Barracks");
        rangedType = utt.getUnitType("Ranged");

        heavyType = utt.getUnitType("Heavy");
        heavyNext = true;
        relativeDistanceFromBase = START_REL_DIST_FROM_TERRITORY;
        enemyDistance = START_ENEMY_DIST;
        buildable = null;
    }


    public AI clone() {
        return new RealGrabAndShakeBot(utt, pf);
    }

    @Override
    public void preGameAnalysis(GameState gs, long milliseconds, String readWriteFolder) throws Exception {
        // evaluate free places to build stuff
        PhysicalGameState pgs = gs.getPhysicalGameState();
        findBuildablePositions(pgs);
    }

    void findBuildablePositions(PhysicalGameState pgs) {
        int w = pgs.getWidth(), h = pgs.getHeight();
        // terrain
        buildable = new int[w][h];
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                buildable[i][j] = pgs.getTerrain(i,j);
            }
        }
        // units
        for (Unit u: pgs.getUnits()) {
            if (!u.getType().canMove) {
                setImmoveablePosAndMarkAround(u.getX(), u.getY(), w,h);
            }
        }
    }

    void setImmoveablePosAndMarkAround(int x, int y, int w, int h) {
        for (int i = Math.max(0, x - 2); i < Math.min(x + 2, w); i++) {
            for (int j = Math.max(0, y - 2); j < Math.min(y + 2, h); j++) {
                if (Math.abs(i - x) + Math.abs(j - y) > 2)
                    continue;

                buildable[i][j] = 1;
            }
        }
    }
    // TODO: overthink reservation -> do this differently or make reservations happen (when building is done -> observer that indicates that a worker has finished its work)
    void reservePosAndMarkAround(int x, int y, int w, int h) {
        for (int i = Math.max(0, x - 2); i < Math.min(x + 2, w); i++) {
            for (int j = Math.max(0, y - 2); j < Math.min(y + 2, h); j++) {
                if (Math.abs(i - x) + Math.abs(j - y) > 2)
                    continue;

                buildable[i][j] = -1;
            }
        }
    }

    /**
     * reserves the found position<br>
     * HELPER FUNCTION
     * @param a_x x
     * @param a_y y
     * @return the nearest possible building position that isn't reserved yet or null if nothing found
     */
    Pair<Integer, Integer> useBuildablePositionAround(int a_x, int a_y) {
        int w = buildable.length, h = buildable[0].length;
        int x = Math.max(0, Math.min(a_x, w));
        int y = Math.max(0, Math.min(a_y, h));

        Queue<Pair<Integer, Integer>> queue = new ArrayDeque<>();
        queue.add(new Pair<>(x,y));
        Set<Pair<Integer, Integer>> visited = new HashSet<>();

        while (!queue.isEmpty()) {
            Pair<Integer, Integer> p = queue.poll();
            int i = p.m_a, j = p.m_b;
            if (buildable[i][j] == 0) {
                // reserve
                reservePosAndMarkAround(i,j,w,h);
                return p;
            }

            visited.add(p);

            if (i-1 >= 0) {
                Pair<Integer,Integer> newp = new Pair<>(i-1,j);
                if (!visited.contains(newp))
                    queue.add(newp);
            }
            if (j-1 >= 0) {
                Pair<Integer,Integer> newp = new Pair<>(i,j-1);
                if (!visited.contains(newp))
                    queue.add(newp);
            }
            if (i+1 < w) {
                Pair<Integer,Integer> newp = new Pair<>(i+1,j);
                if (!visited.contains(newp))
                    queue.add(newp);
            }
            if (j+1 < h) {
                Pair<Integer,Integer> newp = new Pair<>(i,j+1);
                if (!visited.contains(newp))
                    queue.add(newp);
            }
        }

        return null;
    }

    @Override
    public boolean buildIfNotAlreadyBuilding(Unit u, UnitType t, int x, int y, List<Integer> reservedPositions, Player p, PhysicalGameState pgs) {
        AbstractAction action = getAbstractAction(u);
        // can't access type for some reason -> doing trick over creating new Build
        Build tmpBuild = new Build(u,t,x,y,pf);
        if (tmpBuild.equals(action))
            return false;

        Pair<Integer, Integer> pair = useBuildablePositionAround(x,y);
        // will be reset in next iteration anyway because buildable is evaluated every iteration
        //setImmoveablePosAndMarkAround(pair.m_a, pair.m_b, pgs.getWidth(), pgs.getHeight());

        if (pair == null)
            return false;

        build(u, t, pair.m_a, pair.m_b);
        return true;
    }

    public PlayerAction getAction(int player, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();

        // TODO: don't do this everytime
//        if (buildable == null)
        findBuildablePositions(pgs);

        Player p = gs.getPlayer(player);

        // extend the range troops are allowed to be away from base
        relativeDistanceFromBase = Math.min(1.0f, relativeDistanceFromBase + relativeDistanceFromBase * REL_DIST_MULTIPLIER);
        enemyDistance = Math.min(Math.max(pgs.getHeight(), pgs.getWidth()), enemyDistance + 5*REL_DIST_MULTIPLIER);

        int nbases = 0, nbarracks = 0, nworkers = 0, nressources = 0;
        for (Unit u2 : pgs.getUnits()) {
            if (u2.getPlayer() == player) {
                if (u2.getType() == baseType) {
                    ++nbases;
                }
                if (u2.getType() == barracksType)
                    ++nbarracks;
                if (u2.getType().canHarvest)
                    ++nworkers;
            }
        }

        // behavior of bases:
        for (Unit u : pgs.getUnits()) {
            if (u.getType() == baseType
                    && u.getPlayer() == player
                    && gs.getActionAssignment(u) == null) {
                Pair<Integer, Integer> r_sD = evalRessourcesNearBase(u, p, pgs);
                nressources += r_sD.m_a;
                baseBehavior(u, p, pgs, r_sD, nworkers);
            }
        }

        // behavior of barracks:
        for (Unit u : pgs.getUnits()) {
            if (u.getType() == barracksType
                    && u.getPlayer() == player
                    && gs.getActionAssignment(u) == null) {
                barracksBehavior(u, p, pgs);
            }
        }

        int ntroups = 0;
        int enemyTroups = 0;
        // behavior of melee units:
        for (Unit u : pgs.getUnits()) {
            if (u.getType().canAttack && !u.getType().canHarvest) {
                if (u.getPlayer() == player) {
                    ++ntroups;
                    if (gs.getActionAssignment(u) == null)
                        meleeUnitBehavior(u, p, pgs);
                } else {
                    ++enemyTroups;
                }
            }
        }

        // reset relativeDistance if army is weaker than enemy army
        if (ntroups < enemyTroups) {
            relativeDistanceFromBase = START_REL_DIST_FROM_TERRITORY;
            enemyDistance = START_ENEMY_DIST;
        }

        // behavior of workers:
        List<Unit> workers = new LinkedList<>();
        for (Unit u : pgs.getUnits()) {
            if (u.getType().canHarvest
                    && u.getPlayer() == player) {
                workers.add(u);
            }
        }
        workersBehavior(workers, p, pgs, nworkers, nbases, nbarracks);

        // This method simply takes all the unit actions executed so far, and packages them into a PlayerAction
        return translateActions(player, gs);
    }

    public Pair<Integer, Integer> evalRessourcesNearBase(Unit u, Player p, PhysicalGameState pgs) {
//        int maxDistFromBase = MAX_DIST_RESOUCES_AWAY_FROM_BASE_TO_TRAIN_WORKERS;
        int smallestDist = Integer.MAX_VALUE;
        int n_ressources = 0;

        for (Unit u2: pgs.getUnits()) {
            if (u2.getType().isResource) {
                int dist = (Math.abs(u.getX()-u2.getX()) + Math.abs(u.getY()-u2.getY()));
                if (dist < smallestDist){
                    smallestDist = dist;
                }
                ++n_ressources;
            }
        }
        return new Pair<>(n_ressources, smallestDist);
//        return Math.max(1, n_ressources);
    }

    public void baseBehavior(Unit u, Player p, PhysicalGameState pgs, Pair<Integer, Integer> r_sD, int nworkers) {
        // TODO: use more workers if harvesting is with a long distance and less when it is with small distance
        int nressources = r_sD.m_a;
        int smallestDist = r_sD.m_b;
        if ((nworkers < (smallestDist/4) || nworkers < 2) && p.getResources() >= workerType.cost) {
            train(u, workerType);
        }
    }

    public void barracksBehavior(Unit u, Player p, PhysicalGameState pgs) {
        if (p.getResources() >= Math.max(heavyType.cost, rangedType.cost)) {
            if (heavyNext)
                train(u, heavyType);
            else
                train(u, rangedType);

            heavyNext = !heavyNext;
        }

    }

    private Set<Unit> getNeighbouringMeleesOf(Unit u, Player p, PhysicalGameState pgs) {
        Queue<Unit> openUnits = new ArrayDeque<>();
        openUnits.add(u);
        Set<Unit> visitedUnits = new HashSet<>();

        final int squareSize = 5;
        final int off = squareSize/2;

        while (!openUnits.isEmpty()) {
            Unit unit = openUnits.poll();
            visitedUnits.add(unit);
            for (Unit u2: pgs.getUnitsInRectangle(unit.getX()-off, unit.getY()-off, squareSize,squareSize)) {
                if (u2 != unit && u2.getPlayer() == p.getID() && !visitedUnits.contains(u2)) {
                    openUnits.add(u2);
                }
            }
        }

        return visitedUnits;
    }

    public void meleeUnitBehavior(Unit u, Player p, PhysicalGameState pgs) {
        Unit closestEnemy = null;
        int closestDistance = 0;
        int distToMyBase = Integer.MAX_VALUE;
        int baseX = pgs.getWidth()/2, baseY = pgs.getHeight()/2;
        for (Unit u2 : pgs.getUnits()) {
            if (u2.getPlayer() >= 0 && u2.getPlayer() != p.getID()) {
                int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                if (closestEnemy == null || d < closestDistance) {
                    closestEnemy = u2;
                    closestDistance = d;
                }
            }
            // distance away from my barracks and bases
            else if(u2.getPlayer()==p.getID() && !u2.getType().produces.isEmpty())
            {
                baseX = u2.getX();
                baseY = u2.getY();
                int d = Math.abs(baseX - u.getX()) + Math.abs(baseY - u.getY());
                if (d < distToMyBase)
                    distToMyBase = d;
            }
        }
        int averageSize = (pgs.getWidth()+pgs.getHeight())/2;

        // TODO: change increasing of front line -> is to unordered when attacked once -> use Map to save which unit is in attack mode

        // closestDistance < enemyDistance: attack attacking enemies / distToMyBase < (averageSize*relativeDistanceFromBase): walk towards enemy until you are to far away from base
        float maxDistAway = u.getType()==heavyType ? averageSize*relativeDistanceFromBase+1 : averageSize*relativeDistanceFromBase;
        // defend against enemy
        /*if (closestEnemy!=null && (closestDistance < enemyDistance)) {
            for (Unit unit: getNeighbouringMeleesOf(u, p, pgs)) {
                attack(unit, closestEnemy);
            }
        }
        // go forward
        else if (closestEnemy!=null && distToMyBase < maxDistAway) {
            attack(u, closestEnemy);
        }*/
        if (closestEnemy != null && (distToMyBase < maxDistAway || closestDistance < enemyDistance)) {
            attack(u, closestEnemy);
        }
        // TODO: return from battle (look at CRush for example)
        else if (distToMyBase > maxDistAway) {
            move(u, baseX, baseY);
        }
        else
        {
            attack(u, null);
        }
    }

    public void workersBehavior(List<Unit> workers, Player p, PhysicalGameState pgs, int nworkers, int nbases, int nbarracks) {
        if (workers.isEmpty()) {
            return;
        }

        int resourcesUsed = 0;
        List<Unit> freeWorkers = new LinkedList<>(workers);

        List<Integer> reservedPositions = new LinkedList<>();
        // a base can be surrounded by maximum 4 workers
        if ((nbases < 2 || 4*nbases < nworkers) && !freeWorkers.isEmpty()) {
            // build a base:
            if (p.getResources() >= baseType.cost + resourcesUsed) {
                Unit u = freeWorkers.remove(0);
                buildIfNotAlreadyBuilding(u,baseType,u.getX(), u.getY(),reservedPositions,p,pgs);
                resourcesUsed += baseType.cost;
            }
        }

        if (nbarracks == 0 || p.getResources() >= barracksType.cost + resourcesUsed + heavyType.cost + rangedType.cost) {
            // build a barracks:
            if (p.getResources() >= barracksType.cost + resourcesUsed && !freeWorkers.isEmpty()) {
                Unit u = freeWorkers.remove(0);
                buildIfNotAlreadyBuilding(u,barracksType,u.getX(), u.getY(),reservedPositions,p,pgs);
                resourcesUsed += barracksType.cost;
            }
        }

        // harvest with all the free workers:
        List<Unit> stillFreeWorkers = new LinkedList<>();
        for (Unit u : freeWorkers) {
            Unit closestBase = null;
            Unit closestResource = null;
            int closestDistance = 0;
            for (Unit u2 : pgs.getUnits()) {
                if (u2.getType().isResource) {
                    int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                    if (closestResource == null || d < closestDistance) {
                        closestResource = u2;
                        closestDistance = d;
                    }
                }
            }
            closestDistance = 0;
            for (Unit u2 : pgs.getUnits()) {
                if (u2.getType().isStockpile && u2.getPlayer()==p.getID()) {
                    int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                    if (closestBase == null || d < closestDistance) {
                        closestBase = u2;
                        closestDistance = d;
                    }
                }
            }
            boolean workerStillFree = true;
            if (u.getResources() > 0) {
                if (closestBase!=null) {
                    AbstractAction aa = getAbstractAction(u);
                    if (aa instanceof Harvest) {
                        Harvest h_aa = (Harvest)aa;
                        if (h_aa.getBase()!=closestBase) harvest(u, null, closestBase);
                    } else {
                        harvest(u, null, closestBase);
                    }
                    workerStillFree = false;
                }
            } else {
                if (closestResource!=null && closestBase!=null) {
                    AbstractAction aa = getAbstractAction(u);
                    if (aa instanceof Harvest) {
                        Harvest h_aa = (Harvest)aa;
                        if (h_aa.getTarget() != closestResource || h_aa.getBase()!=closestBase) harvest(u, closestResource, closestBase);
                    } else {
                        harvest(u, closestResource, closestBase);
                    }
                    workerStillFree = false;
                }
            }

            if (workerStillFree) stillFreeWorkers.add(u);
        }

        for(Unit u:stillFreeWorkers) meleeUnitBehavior(u, p, pgs);
    }


    @Override
    public List<ParameterSpecification> getParameters()
    {
        List<ParameterSpecification> parameters = new ArrayList<>();

        parameters.add(new ParameterSpecification("PathFinding", PathFinding.class, new AStarPathFinding()));

        return parameters;
    }

}
