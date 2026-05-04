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

    private long wallStartTime = 0;

    private static final long WALL_TIMEOUT_MS = 15000; 		// temps pour que l'agent considère le golem bloquer
    private static final boolean AFFICHAGE = false;         // Affiche le traitement des messages par les agents (ca peut faire beaucoup)

    public static final int STAY_WALL = 2;    				// rester mur
    public static final int GOLEM_GONE = 1;   				// golem parti, retour chasse

    private int exitValue;

    public WallDripBehaviour(AbstractAgent agent) {
        super(agent);
        this.myAgent = agent;
    }

    @Override
    public void action() {
    	
        exitValue = STAY_WALL;
        

        Location myPosition = myAgent.getCurrentPosition();
        if (myPosition == null) return;
        String currentNodeId = myPosition.getLocationId();
        
        

        // permet de déplacer l'agent autour du golem pour laisser la place
        String wallTarget = myAgent.getWallTargetNode();
        if (wallTarget != null) {
            
            // arriver la ou on redevient mur
            if (wallTarget.equals(currentNodeId)) {
                myAgent.setWallTargetNode(null);
                if (AFFICHAGE)
                	System.out.println(myAgent.getLocalName() + " arrivé en position mur");
                
            } else {
                // Pas encore arrivé, avancer
            	if (myAgent.getChemin()!= null && !myAgent.getChemin().isEmpty()) {
	                myAgent.moveTo(new GsLocation(myAgent.getChemin().get(0)));
	                myAgent.getChemin().remove(0);
	                myAgent.doWait(800);
	                return;
	                }
            	}
            }
            
        // devoir demander aux autres murs a bouger egalement (chain block)
       
            
        List<Couple<Location, List<Couple<Observation, String>>>> lobs = myAgent.observe();

        boolean golemVisible = false;
        String golemPos = myAgent.getGolemPosition();
        if (golemPos == null) { exitValue = GOLEM_GONE; return; }

        // regarde si le golem est toujours visible ou s'il a bougé autour
        for (Couple<Location, List<Couple<Observation, String>>> obs : lobs) {
            for (Couple<Observation, String> detail : obs.getRight()) {
            	if (detail.getLeft() == Observation.AGENTNAME && AbstractAgent.isGolem(detail.getRight())) {
                    golemVisible = true;
                    String newGolemPos = obs.getLeft().getLocationId();
                    if (golemPos != null && !golemPos.equals(newGolemPos)) {
                        if (AFFICHAGE)
                        	System.out.println("Golem déplacé ! de " + golemPos + " à " + newGolemPos);
                        wallStartTime = System.currentTimeMillis();
                    }
                    myAgent.setGolemPosition(newGolemPos);
                }
            }
        }

        
        // Golem disparu, retour chasse
        if (!golemVisible) {
            if (AFFICHAGE)
            	System.out.println(myAgent.getLocalName() + " - Golem parti, retour en chasse");
            myAgent.setGolemPosition(null);
            exitValue = GOLEM_GONE;
            return;
        }
        
        long elapsed = System.currentTimeMillis() - wallStartTime;
        
        // determine si le golem a été coincé
        if (elapsed >= WALL_TIMEOUT_MS) {
            myAgent.getCommService().broadcastWumpusCaught(golemPos);
        	
        	// faire un message disant que c'est bloqué -> permet de repasser les chasseurs perdus en explo DONE
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
        boolean golemCurrentlyVisible = lobs.stream()
            .anyMatch(obs -> obs.getLeft().getLocationId().equals(golemPos) &&
                obs.getRight().stream()
                .anyMatch(d -> d.getLeft() == Observation.AGENTNAME && AbstractAgent.isGolem(d.getRight())));

        if (golemCurrentlyVisible) blocked.add(golemPos);

        agentPositions.forEach(p -> {
            if (!p.equals(myPosition.getLocationId())) blocked.add(p);
        });
        	
	        //myAgent.getCommService().propagateBroadcastFreeNeighbors(golemPos, freeNeighbors,blocked);
	        
        myAgent.getCommService().broadcastFreeNeighbors(golemPos, freeNeighbors,blocked);
        
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