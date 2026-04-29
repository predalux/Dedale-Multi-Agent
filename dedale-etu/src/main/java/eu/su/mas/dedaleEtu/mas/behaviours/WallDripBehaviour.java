package eu.su.mas.dedaleEtu.mas.behaviours;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
        exitValue = STAY_WALL;
        myAgent.doWait(500);

        Location myPosition = myAgent.getCurrentPosition();
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
                }
            }
        }

        String golemPos = myAgent.getGolemPosition();
        if (golemPos == null) { exitValue = GOLEM_GONE; return; }
        
        // Golem disparu, retour chasse
        if (!golemVisible) {
            System.out.println(myAgent.getLocalName() + " - Golem parti, retour en chasse");
            myAgent.setGolemPosition(null);
            exitValue = GOLEM_GONE;
            return;
        }

        // Voisins du golem connus via la carte
        List<String> golemNeighbors = myAgent.getMyMap().getNeighbors(golemPos);

        // Collecter les positions des agents visibles
        List<String> agentPositions = new ArrayList<>();
        agentPositions.add(myPosition.getLocationId());
        myAgent.getAgentsNearby(lobs).forEach((pos, name) -> agentPositions.add(pos));

        // Cases libres autour du golem selon la carte
        List<String> freeNeighbors = golemNeighbors.stream()
            .filter(n -> !agentPositions.contains(n))
            .collect(Collectors.toList());

        // Vérifier encerclement
        if (freeNeighbors.isEmpty()) {
            System.out.println(myAgent.getLocalName() + " - Golem encerclé !");
            //exitValue = GOLEM_CAUGHT;
            return;
        }

        // Broadcast avec noeuds bloqués pour éviter de passer par le golem
        // Les agents receveurs devront utiliser getShortestPathWithBlockedNodes
        List<String> blocked = new ArrayList<>();
        blocked.add(golemPos); // ne pas passer par le golem
        agentPositions.forEach(p -> {
            if (!p.equals(myPosition.getLocationId())) blocked.add(p); // ni par les cases déjà occupées
        });

        myAgent.getCommService().broadcastFreeNeighbors(golemPos, freeNeighbors,blocked);
        myAgent.getCommService().broadcastWumpusPosition(golemPos);
        
    }

    @Override
    public int onEnd() {
        myAgent.setEtat(exitValue);
        return exitValue;
    }
}