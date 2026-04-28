package eu.su.mas.dedaleEtu.mas.behaviours;

import java.io.IOException;
import java.util.List;
import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Location;
import eu.su.mas.dedale.env.Observation;
import eu.su.mas.dedaleEtu.mas.agents.dummies.AbstractAgent;
import jade.core.behaviours.OneShotBehaviour;

public class WallDripBehaviour extends OneShotBehaviour {

    private static final long serialVersionUID = 1L;
    private AbstractAgent myAgent;
    private int exitValue = 0;

    public static final int STAY_WALL = 2;    // rester mur
    public static final int GOLEM_GONE = 1;   // golem parti, retour chasse
    //public static final int GOLEM_CAUGHT = 3; // golem coincé, fin ?

    public WallDripBehaviour(AbstractAgent agent) {
        super(agent);
        this.myAgent = agent;
    }

    @Override
    public void action() {
    	//System.out.println("mode mur ");
        exitValue = STAY_WALL;
        myAgent.doWait(500);

        Location myPosition = (Location) myAgent.getCurrentPosition(); // pourquoi on a besoin d'un cast ici mais pas dans explo rfdnjsghdi
        if (myPosition == null) return;

        List<Couple<Location, List<Couple<Observation, String>>>> lobs = myAgent.observe();

        boolean stenchDetected = false;
        boolean golemVisible = false;

        for (Couple<Location, List<Couple<Observation, String>>> obs : lobs) {
            for (Couple<Observation, String> detail : obs.getRight()) {
                if (detail.getLeft() == Observation.STENCH) {
                    stenchDetected = true;
                }
                if (detail.getLeft() == Observation.AGENTNAME 
                        && detail.getRight().equals("Wumpus")) {
                    golemVisible = true;
                    myAgent.setGolemPosition(obs.getLeft().getLocationId());
                    myAgent.getCommService().broadcastWumpusPosition(
                        obs.getLeft().getLocationId()
                    );
                }
            }
        }

        // Broadcast périodique même si pas visible, pour coordonner les autres
        if (myAgent.getGolemPosition() != null) {
            myAgent.getCommService().broadcastWumpusPosition(myAgent.getGolemPosition());
        }

        // Golem disparu, retour en chasse
        if (!stenchDetected && !golemVisible) {
            System.out.println(myAgent.getLocalName() + " - Golem parti, retour en chasse");
            myAgent.setGolemPosition(null);
            exitValue = GOLEM_GONE;
        }
    }

    @Override
    public int onEnd() {
        myAgent.setEtat(exitValue);
        return exitValue;
    }
}