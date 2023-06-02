package ai.abstraction;

/**
 * <p>evaluated predefined bots and realized that Defensive Strategies (LightDefense) work best except on small grids and very large ones, because then they do nothing</p>
 * <br>
 * <b>IDEA</b>:<br>
 * <ul>
 *     <li>use the behaviour of LightDefense, but make the distance troops are allowed to be away from the base variable</li>
 *     <li>when there is a strong front line -> train ranged</li>
 *     <li>use different troops on different grid sizes -> <i>espacially</i> on small grids probably use workers because they are faster to make</li>
 *     <li>don't stop building infrastructure</li>
 *     <ul>
 *         <li>one worker for each ressource <i>in my territory -> <b>evaluate what is mine</b></i></li>
 *         <li>one base for 4 ressources or if ressources are far appart</li>
 *     </ul>
 *     <li>on pre-game evaluation analyse grid and for each persistent territory (seperated by barricades) run a bot -> because these are seperate games</li>
 * </ul>
 */
public class GrabAndShakeBot extends AbstractionLayerAI {


}
