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
import util.Sampler;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * <p>evaluated predefined bots and realized that Defensive Strategies (LightDefense) work best except on small grids and very large ones, because then they do nothing</p>
 * <br>
 * <b>IDEA</b>:<br>
 * <ul>
 *     <li>use the behaviour of LightDefense, but make the distance troops are allowed to be away from the base variable</li>
 *     <li>use different troops on different grid sizes -> <i>espacially</i> on small grids probably use workers because they are faster to make</li>
 *     <li>don't stop building infrastructure</li>
 *     <ul>
 *         <li>one worker for each ressource <i>in my territory -> <b>evaluate what is mine</b></i></li>
 *         <li>one base for 4 ressources or if ressources are far appart</li>
 *     </ul>
 *     <li>on pre-game evaluation analyse grid and for each persistent territory (seperated by barricades) run a bot -> because these are seperate games</li>
 * </ul>
 */
public class MyBot extends AbstractionLayerAI {

    public final static int BUILD_BASE = 0;
    public final static int BUILD_BARRACKS = 1;
    public final static int HARVEST = 2;
    public final static int TRAIN_WORKER = 3;
    public final static int TRAIN_LIGHT = 4;
    public final static int TRAIN_HEAVY = 5;
    public final static double[] DISTRIBUTION = {1,1,1,1,1,1};

    UnitTypeTable m_utt;
    UnitType workerType;
    UnitType baseType;
    UnitType barracksType;
    UnitType lightType;
    UnitType heavyType;

    // This is the default constructor that microRTS will call:

    public MyBot(UnitTypeTable utt) {
        super(new AStarPathFinding());
        reset(utt);
    }

    // This will be called by microRTS when it wants to create new instances of this bot (e.g., to play multiple games).
    @Override
    public AI clone() {
        return new MyBot(m_utt);
    }

    // This will be called once at the beginning of each new game:
    @Override
    public void reset() {
        super.reset();
    }

    public void reset(UnitTypeTable utt) {
        m_utt = utt;
        workerType = utt.getUnitType("Worker");
        baseType = utt.getUnitType("Base");
        barracksType = utt.getUnitType("Barracks");
        lightType = utt.getUnitType("Light");
        heavyType = utt.getUnitType("Heavy");
    }


    // Called by microRTS at each game cycle.
    // Returns the action the bot wants to execute.
    @Override
    public PlayerAction getAction(int player, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Player p = gs.getPlayer(player);
        PlayerAction pa = new PlayerAction();

        int random_action = HARVEST;
        try {
            random_action = Sampler.weighted(DISTRIBUTION);
        } catch (Exception e) {
            e.printStackTrace();
        }

        switch (random_action) {
            case BUILD_BASE -> {

            }
            case BUILD_BARRACKS -> {

            }
            case HARVEST -> {

            }
            case TRAIN_WORKER -> {

            }
            case TRAIN_HEAVY -> {

            }
            case TRAIN_LIGHT -> {

            }
        }

        pa.fillWithNones(gs, player, 10);
        return pa;
    }

    // This will be called by the microRTS GUI to get the
    // list of parameters that this bot wants exposed
    // in the GUI.
    @Override
    public List<ParameterSpecification> getParameters() {
        List<ParameterSpecification> parameters = new ArrayList<>();
        parameters.add(new ParameterSpecification("PathFinding", PathFinding.class, new AStarPathFinding()));
        return parameters;
    }

    // Copy of LightRush-Methods
    public void baseBehavior(Unit u, Player p, PhysicalGameState pgs) {
        int nworkers = 0;
        for (Unit u2 : pgs.getUnits()) {
            if (u2.getType() == workerType
                    && u2.getPlayer() == p.getID()) {
                nworkers++;
            }
        }
        if (nworkers < 1 && p.getResources() >= workerType.cost) {
            train(u, workerType);
        }
    }

    public void barracksBehavior(Unit u, Player p, PhysicalGameState pgs) {
        if (p.getResources() >= lightType.cost) {
            train(u, lightType);
        }
    }

    public void meleeUnitBehavior(Unit u, Player p, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Unit closestEnemy = null;
        int closestDistance = 0;
        for (Unit u2 : pgs.getUnits()) {
            if (u2.getPlayer() >= 0 && u2.getPlayer() != p.getID()) {
                int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                if (closestEnemy == null || d < closestDistance) {
                    closestEnemy = u2;
                    closestDistance = d;
                }
            }
        }
        if (closestEnemy != null) {
//            System.out.println("LightRushAI.meleeUnitBehavior: " + u + " attacks " + closestEnemy);
            attack(u, closestEnemy);
        }
    }

    public void workersBehavior(List<Unit> workers, Player p, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        int nbases = 0;
        int nbarracks = 0;

        int resourcesUsed = 0;
        List<Unit> freeWorkers = new LinkedList<>(workers);

        if (workers.isEmpty()) {
            return;
        }

        for (Unit u2 : pgs.getUnits()) {
            if (u2.getType() == baseType
                    && u2.getPlayer() == p.getID()) {
                nbases++;
            }
            if (u2.getType() == barracksType
                    && u2.getPlayer() == p.getID()) {
                nbarracks++;
            }
        }

        List<Integer> reservedPositions = new LinkedList<>();
        if (nbases == 0 && !freeWorkers.isEmpty()) {
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

        for(Unit u:stillFreeWorkers) meleeUnitBehavior(u, p, gs);
    }
}
