package eu.su.mas.dedaleEtu.mas.behaviours;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Location;
import eu.su.mas.dedale.env.Observation;
import eu.su.mas.dedale.env.gs.GsLocation;
import eu.su.mas.dedaleEtu.mas.agents.dummies.AbstractAgent;
import jade.core.behaviours.OneShotBehaviour;

public class WallDripBehaviour extends OneShotBehaviour {

    private static final long serialVersionUID = 1L;
    private AbstractAgent myAgent;
    private int exitValue = 0;

    private long wallStartTime = 0;

    private static final long WALL_TIMEOUT_MS = 15000; 		// temps pour que l'agent considère le golem bloquer
    
    public static final int STAY_WALL = 2;    				// rester mur
    public static final int GOLEM_GONE = 1;   				// golem parti, retour chasse

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
        String currentNodeId = myPosition.getLocationId();

        // permet de déplacer l'agent autour du golem pour laisser la place
        String wallTarget = myAgent.getWallTargetNode();
        if (wallTarget != null) {
            //System.out.println(myAgent.getLocalName() + " wallTarget=" + wallTarget + " currentPos=" + currentNodeId);
            
            // arriver la ou on redevient mur
            if (wallTarget.equals(currentNodeId)) {
                myAgent.setWallTargetNode(null);
                //System.out.println(myAgent.getLocalName() + " arrivé en position mur");
                
            } else {
                // Pas encore arrivé, avancer
                List<Couple<Location, List<Couple<Observation, String>>>> lobs = myAgent.observe();
                List<String> blocked = new ArrayList<>();
                String golemPos = myAgent.getGolemPosition();
                if (golemPos != null) blocked.add(golemPos);
                myAgent.getAgentsNearby(lobs).keySet().stream()
                    .filter(p -> !p.equals(currentNodeId))
                    .forEach(blocked::add);

                //System.out.println(myAgent.getLocalName() + " blocked=" + blocked + " wallTarget=" + wallTarget);

                List<String> path = myAgent.getMyMap()
                    .getShortestPathWithBlockedNodes(currentNodeId, wallTarget, blocked);

                //System.out.println(myAgent.getLocalName() + " path=" + path);

                if (path != null && !path.isEmpty()) {
                    myAgent.setChemin(path);
                    myAgent.moveTo(new GsLocation(path.get(0)));
                    return;
                }
                // Chemin impossible, abandon
                //System.out.println(myAgent.getLocalName() + " chemin introuvable vers " + wallTarget + ", abandon");
                myAgent.setWallTargetNode(null);
            }
            
            // devoir demander aux autres murs a bouger egalement (chain block)
        }
            
        List<Couple<Location, List<Couple<Observation, String>>>> lobs = myAgent.observe();

        boolean golemVisible = false;
        String golemPos = myAgent.getGolemPosition();
        if (golemPos == null) { exitValue = GOLEM_GONE; return; }

        // regarde si le golem est toujours visible ou s'il a bougé autour
        for (Couple<Location, List<Couple<Observation, String>>> obs : lobs) {
            for (Couple<Observation, String> detail : obs.getRight()) {
                if (detail.getLeft() == Observation.AGENTNAME && detail.getRight().equals("Wumpus")) {
                    golemVisible = true;
                    String newGolemPos = obs.getLeft().getLocationId();
                    if (golemPos != null && !golemPos.equals(newGolemPos)) {
                        System.out.println("Golem déplacé ! de " + golemPos + " à " + newGolemPos);
                        wallStartTime = System.currentTimeMillis();
                    }
                    myAgent.setGolemPosition(newGolemPos);
                }
            }
        }

        
        // Golem disparu, retour chasse
        if (!golemVisible) {
            System.out.println(myAgent.getLocalName() + " - Golem parti, retour en chasse");
            myAgent.setGolemPosition(null);
            exitValue = GOLEM_GONE;
            return;
        }
        
        long elapsed = System.currentTimeMillis() - wallStartTime;
        // determine si le golem a été coincé
        if (elapsed >= WALL_TIMEOUT_MS) {
            //System.out.println(myAgent.getLocalName() + " - Golem considéré bloqué après 15s");
            //exitValue = GOLEM_CAUGHT;
        	
        	// faire un message disant que c'est bloqué -> permet de repasser les chasseurs perdus en explo
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

        

        // Broadcast avec noeuds bloqués pour éviter de passer par le golem et les autres agents
        List<String> blocked = new ArrayList<>();
        blocked.add(golemPos); 
        agentPositions.forEach(p -> {
            if (!p.equals(myPosition.getLocationId())) blocked.add(p);
        });
        
        if (!freeNeighbors.isEmpty()) {
	
	        //myAgent.getCommService().propagateBroadcastFreeNeighbors(golemPos, freeNeighbors,blocked);
	        
        	myAgent.getCommService().broadcastFreeNeighbors(golemPos, freeNeighbors,blocked);
        }
        myAgent.getCommService().broadcastWumpusPosition(golemPos);
        
    }

    @Override
    public void onStart() {
        wallStartTime = System.currentTimeMillis();
    }
    
    @Override
    public int onEnd() {
        myAgent.setEtat(exitValue);
        return exitValue;
    }
}