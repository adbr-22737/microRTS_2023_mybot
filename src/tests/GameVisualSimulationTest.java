 /*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tests;

 import ai.RandomBiasedAI;
 import ai.abstraction.*;
 import ai.abstraction.cRush.CRush_V2;
 import ai.abstraction.pathfinding.BFSPathFinding;
 import ai.core.AI;
 import ai.mcts.believestatemcts.BS1_NaiveMCTS;
 import ai.mcts.believestatemcts.BS2_NaiveMCTS;
 import ai.mcts.believestatemcts.BS3_NaiveMCTS;
 import ai.mcts.informedmcts.InformedNaiveMCTS;
 import ai.montecarlo.MonteCarlo;
 import ai.puppet.PuppetSearchMCTS;
 import gui.PhysicalGameStatePanel;
 import rts.GameState;
 import rts.PhysicalGameState;
 import rts.PlayerAction;
 import rts.units.UnitTypeTable;

 import javax.swing.*;

 /**
 *
 * @author santi
 */
public class GameVisualSimulationTest {
    public static void main(String[] args) throws Exception {
        UnitTypeTable utt = new UnitTypeTable();
//        PhysicalGameState pgs = PhysicalGameState.load("maps/BWDistantResources32x32.xml", utt);
//        PhysicalGameState pgs = PhysicalGameState.load("maps/barricades24x24.xml", utt);
//        PhysicalGameState pgs = PhysicalGameState.load("maps/EightBasesWorkers16x12.xml", utt);
//        PhysicalGameState pgs = PhysicalGameState.load("maps/16x16/basesWorkers16x16.xml", utt);
//        PhysicalGameState pgs = PhysicalGameState.load("maps/24x24/basesWorkers24x24.xml", utt);
//        PhysicalGameState pgs = PhysicalGameState.load("maps/bases8x8.xml", utt);
//        PhysicalGameState pgs = PhysicalGameState.load("maps/letMeOut.xml", utt);
        PhysicalGameState pgs = PhysicalGameState.load("maps/itsNotSafe.xml", utt);
//        PhysicalGameState pgs = MapGenerator.basesWorkers8x8Obstacle();

        GameState gs = new GameState(pgs, utt);
        int MAXCYCLES = 5000;
        int PERIOD = 20;
        boolean gameover = false;
        
//        AI ai1 = new WorkerRush(utt);
        AI ai1 = new WorkerRushPlusPlus(utt);
//        AI ai1 = new LightDefense(utt);
//        AI ai1 = new RandomBiasedAI();
//        AI ai1 = new MonteCarlo(utt);
//        AI ai1 = new LightRush(utt);
//        AI ai1 = new CRush_V2(utt);
        AI ai2 = new GrabAndShakeBot(utt);

        ai1.preGameAnalysis(gs, 1000);
        ai2.preGameAnalysis(gs, 1000);

        JFrame w = PhysicalGameStatePanel.newVisualizer(gs,640,640,false,PhysicalGameStatePanel.COLORSCHEME_BLACK);
//        JFrame w = PhysicalGameStatePanel.newVisualizer(gs,640,640,false,PhysicalGameStatePanel.COLORSCHEME_WHITE);

        long nextTimeToUpdate = System.currentTimeMillis() + PERIOD;
        do{
            if (System.currentTimeMillis()>=nextTimeToUpdate) {
                PlayerAction pa1 = ai1.getAction(0, gs);
                PlayerAction pa2 = ai2.getAction(1, gs);
                gs.issueSafe(pa1);
                gs.issueSafe(pa2);

                // simulate:
                gameover = gs.cycle();
                w.repaint();
                nextTimeToUpdate+=PERIOD;
            } else {
                try {
                    Thread.sleep(1);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }while(!gameover && gs.getTime()<MAXCYCLES);
        ai1.gameOver(gs.winner());
        ai2.gameOver(gs.winner());
        
        System.out.println("Game Over");
    }    
}
