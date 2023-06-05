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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * <p>evaluated predefined bots and realized that Defensive Strategies (LightDefense) work best except on small grids and very large ones, because then they do nothing</p>
 * <br>
 * <b>IDEA</b>:<br>
 * <ul>
 *     <li>use the behaviour of RangedDefense, but make the distance troops are allowed to be away from the base variable</li>
 *     <li>ranged are weak -> alternate with heavy</li>
 *     <li>ranged should stay behind heavy & heavy should wait for at least one ranged -> for now: reset relativeDistance if army is dead</li>
 *     <li>use different troops on different grid sizes -> <i>espacially</i> on small grids probably use workers because they are faster to make</li>
 *     <li>try to adapt units and aggressiveness according to enemy -> e.g. on WorkerRush(PlusPlus) on small grids use workers and don't build barracks</li>
 *     <li>don't stop building infrastructure</li>
 *     <ul>
 *         <li>one worker for each ressource <i>in my territory -> <b>evaluate what is mine</b></i></li>
 *         <li>one base for 4 ressources or if ressources are far appart</li>
 *     </ul>
 *     <li>on pre-game evaluation analyse grid and for each persistent territory (seperated by barricades) run a bot -> because these are seperate games</li>
 * </ul>
 */
public class GrabAndShakeBot extends AbstractionLayerAI {

    // later:
    // sections
    // bots

    // RangedDefense
    Random r = new Random();
    protected UnitTypeTable utt;
    UnitType workerType;
    UnitType baseType;
    UnitType barracksType;
    UnitType rangedType;

    // additional
    UnitType heavyType;
    boolean heavyNext = true;

    static float START_REL_DIST_FROM_BASE = 0.15f;
    static float START_ENEMY_DIST = 2.0f;
    static float REL_DIST_MULTIPLIER = 0.001f;
    float relativeDistanceFromBase = START_REL_DIST_FROM_BASE;
    float enemyDistance = START_ENEMY_DIST;
    static int MAX_DIST_RESSOUCES_AWAY_FROM_BASE_TO_TRAIN_WORKERS = 6;


    public GrabAndShakeBot(UnitTypeTable a_utt) {
        this(a_utt, new AStarPathFinding());
    }


    public GrabAndShakeBot(UnitTypeTable a_utt, PathFinding a_pf) {
        super(a_pf);
        reset(a_utt);
    }

    public void reset() {
        super.reset();
        // additional
        heavyNext = true;
        relativeDistanceFromBase = START_REL_DIST_FROM_BASE;
        enemyDistance = START_ENEMY_DIST;
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
        relativeDistanceFromBase = START_REL_DIST_FROM_BASE;
        enemyDistance = START_ENEMY_DIST;
    }


    public AI clone() {
        return new LightDefense(utt, pf);
    }

    @Override
    public void preGameAnalysis(GameState gs, long milliseconds, String readWriteFolder) throws Exception {
        // alle seperaten territorien finden -> gs.getPhysicalGameState().getAllFree()
        // damit auch maps unterscheiden -> array als 0-1-array umwandeln und als key in datei schreiben
        // hinter den Key schreiben: wie viele bereiche/bots nötig, abgrenzung der bereiche (z.b. rect mit smallest x,y und biggest x,y),
        //    #Ressourcen in jedem bereich, #Startressourcen
    }

    public PlayerAction getAction(int player, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Player p = gs.getPlayer(player);

        // extend the range troops are allowed to be away from base
        relativeDistanceFromBase = Math.min(1.0f, relativeDistanceFromBase + relativeDistanceFromBase * REL_DIST_MULTIPLIER);
        enemyDistance = Math.min(Math.max(pgs.getHeight(), pgs.getWidth()), enemyDistance + 5*REL_DIST_MULTIPLIER);

        int nbases = 0, nbarracks = 0, nworkers = 0;
        for (Unit u2 : pgs.getUnits()) {
            if (u2.getPlayer() == p.getID()) {
                if (u2.getType() == baseType)
                    ++nbases;
                if (u2.getType() == barracksType)
                    ++nbarracks;
                if (u2.getType() == workerType)
                    ++nworkers;
            }
        }
        // get number of ressources near my base
        int nressources = 0;

        // behavior of bases:
        for (Unit u : pgs.getUnits()) {
            if (u.getType() == baseType
                    && u.getPlayer() == player
                    && gs.getActionAssignment(u) == null) {
                int ressourcesNearBase = evalRessourcesNearBase(u, p, pgs);
                nressources += ressourcesNearBase;
                baseBehavior(u, p, pgs, ressourcesNearBase, nworkers);
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
        // behavior of melee units:
        for (Unit u : pgs.getUnits()) {
            if (u.getType().canAttack && !u.getType().canHarvest
                    && u.getPlayer() == player) {
                ++ntroups;
                if (gs.getActionAssignment(u) == null)
                    meleeUnitBehavior(u, p, pgs);
            }
        }

        // reset relativeDistance if army is dead
        if (ntroups <= 1) {
            relativeDistanceFromBase = START_REL_DIST_FROM_BASE;
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

    public int evalRessourcesNearBase(Unit u, Player p, PhysicalGameState pgs) {
        int maxDistFromBase = MAX_DIST_RESSOUCES_AWAY_FROM_BASE_TO_TRAIN_WORKERS;
        int n_ressources = 0;

        for (Unit u2: pgs.getUnits()) {
            if (u2.getType().isResource) {
                int dist = (Math.abs(u.getX()-u2.getX()) + Math.abs(u.getY()-u2.getY()));
                if (dist <= maxDistFromBase)
                    ++n_ressources;
            }
        }

        return n_ressources;
    }

    public void baseBehavior(Unit u, Player p, PhysicalGameState pgs, int nressources, int nworkers) {
        if (nworkers < nressources && p.getResources() >= workerType.cost) {
            train(u, workerType);
        }
    }

    public void barracksBehavior(Unit u, Player p, PhysicalGameState pgs) {
        if (p.getResources() >= rangedType.cost) {
            if (heavyNext)
                train(u, heavyType);
            else
                train(u, rangedType);

            heavyNext = !heavyNext;
        }
    }

    public void meleeUnitBehavior(Unit u, Player p, PhysicalGameState pgs) {
        Unit closestEnemy = null;
        int closestDistance = 0;
        int distToMyBase = 0;
        int baseX = pgs.getWidth()/2, baseY = pgs.getHeight()/2;
        for (Unit u2 : pgs.getUnits()) {
            if (u2.getPlayer() >= 0 && u2.getPlayer() != p.getID()) {
                int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                if (closestEnemy == null || d < closestDistance) {
                    closestEnemy = u2;
                    closestDistance = d;
                }
            }
            else if(u2.getPlayer()==p.getID() && u2.getType() == baseType)
            {
                baseX = u2.getX();
                baseY = u2.getY();
                distToMyBase = Math.abs(baseX - u.getX()) + Math.abs(baseY - u.getY());
            }
        }
        int averageSize = (pgs.getWidth()+pgs.getHeight())/2;

        // closestDistance < enemyDistance: attack attacking enemies / distToMyBase < (averageSize*relativeDistanceFromBase): walk towards enemy until you are to far away from base
        if (closestEnemy!=null && (closestDistance < enemyDistance || distToMyBase < (averageSize*relativeDistanceFromBase))) {
            attack(u,closestEnemy);
        }
        // returning from a battle
        else if (distToMyBase > (averageSize*relativeDistanceFromBase)) {
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
        if (4*nbases < nworkers && !freeWorkers.isEmpty()) {
            // build a base:
            if (p.getResources() >= baseType.cost + resourcesUsed) {
                Unit u = freeWorkers.remove(0);
                buildIfNotAlreadyBuilding(u,baseType,u.getX(),u.getY(),reservedPositions,p,pgs);
                resourcesUsed += baseType.cost;
            }
        }

        if (nbarracks == 0) {
            // build a barracks:
            if (p.getResources() >= barracksType.cost + resourcesUsed && !freeWorkers.isEmpty()) {
                Unit u = freeWorkers.remove(0);
                buildIfNotAlreadyBuilding(u,barracksType,u.getX(),u.getY(),reservedPositions,p,pgs);
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
                        if (h_aa.base!=closestBase) harvest(u, null, closestBase);
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
                        if (h_aa.target != closestResource || h_aa.base!=closestBase) harvest(u, closestResource, closestBase);
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
