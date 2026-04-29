package eu.su.mas.dedaleEtu.mas.behaviours;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Location;
import eu.su.mas.dedale.env.Observation;
import eu.su.mas.dedale.env.gs.GsLocation;
import eu.su.mas.dedaleEtu.mas.agents.dummies.AbstractAgent;
import eu.su.mas.dedaleEtu.mas.agents.dummies.DrippedAgent;
import jade.core.behaviours.SimpleBehaviour;

import java.io.IOException;
import java.util.Iterator;

import dataStructures.serializableGraph.SerializableSimpleGraph;

import eu.su.mas.dedale.mas.AbstractDedaleAgent;

import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation.MapAttribute;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation;
import eu.su.mas.dedaleEtu.mas.behaviours.ShareMapBehaviour;
import jade.core.AID;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

public class HuntDripBehaviour extends OneShotBehaviour {

    private static final long serialVersionUID = 1L;
    private AbstractAgent myAgent;
    
    public static final int CONTINUE_CHASSE = 1;      // Comm puis chasse
    public static final int WUMPUS_LOST     = 0;	  // Explo
    public static final int WUMPUS_DETECTED = 2;	  // Wall
    
    private int exitValue = 0;
    

    public HuntDripBehaviour(AbstractAgent agent) {
        super(agent);
        this.myAgent = agent;
    }

    @Override
    public void action() {
        exitValue = CONTINUE_CHASSE;
        myAgent.doWait(500);

        Location myPosition = myAgent.getCurrentPosition();
        if (myPosition == null) return;

        String currentNodeId = myPosition.getLocationId();
        myAgent.getMyMap().markVisited(currentNodeId);
        List<Couple<Location, List<Couple<Observation, String>>>> lobs = myAgent.observe();

        boolean stenchDetected = false;
        boolean golemVisible = false;
        String tmpgolemPos;

        List<String> voisinsLibres = new ArrayList<>();
        List<String> voisinsStench = new ArrayList<>();

        // Analyse des observations
        for (Couple<Location, List<Couple<Observation, String>>> obs : lobs) {
            String node = obs.getLeft().getLocationId();
            boolean hasAgent = false;
            boolean hasStench = false;

            for (Couple<Observation, String> detail : obs.getRight()) {
                if (detail.getLeft() == Observation.STENCH) {
                    hasStench = true;
                    stenchDetected = true;
                    myAgent.setStench(true);
                }
                if (detail.getLeft() == Observation.AGENTNAME && detail.getRight().equals("Wumpus")) {
                    hasAgent = true;
                    golemVisible = true;
                    tmpgolemPos = node;
                    myAgent.setGolemPosition(node);
                    myAgent.getCommService().broadcastWumpusPosition(tmpgolemPos);
                } else if (detail.getLeft() == Observation.AGENTNAME) {
                    hasAgent = true;
                }
            }

            if (!node.equals(currentNodeId)) {
                myAgent.getMyMap().addEdge(currentNodeId, node);
            }

            if (!hasAgent) {
                voisinsLibres.add(node);
                if (hasStench) voisinsStench.add(node);
            }
        }
        
        String golemPos = myAgent.getGolemPosition();


        // 1) Golem visible et adjacent → devenir mur
        if (golemVisible && golemPos != null) {
             exitValue = WUMPUS_DETECTED;
             return;
        }

        // 2) WallTarget assigné par message → s'y rendre
        String wallTarget = myAgent.getWallTargetNode();
        if (wallTarget != null) {
            if (wallTarget.equals(currentNodeId)) {
                myAgent.setWallTargetNode(null);
                exitValue = WUMPUS_DETECTED;
                return;
            }
            // Recalculer le chemin en évitant le golem
            List<String> blocked = new ArrayList<>();
            if (golemPos != null) blocked.add(golemPos);
            List<String> path = myAgent.getMyMap()
                .getShortestPathWithBlockedNodes(currentNodeId, wallTarget, blocked);
            if (path != null && !path.isEmpty()) {
                myAgent.setChemin(path);
                moveTo(path.get(0));
                return;
            }
            // Chemin introuvable → annuler
            myAgent.setWallTargetNode(null);
        }

        // 3) Plus aucune trace → retour exploration
        if (!stenchDetected && golemPos == null) {
            myAgent.setGolemPosition(null);
            exitValue = WUMPUS_LOST;
            return;
        }

        // 4) Position golem connue → recalculer et se rapprocher
        if (golemPos != null) {
            if (myAgent.getMyMap().getNode(golemPos) == null) {
                System.out.println(myAgent.getLocalName() + " - Nœud golem inconnu : " + golemPos);
                // Se diriger vers le stench le plus proche
                if (!voisinsStench.isEmpty()) {
                    moveTo(voisinsStench.get(0));
                }
                return;
            }

            // Vérifier si le golem a bougé : case visible mais golem absent + stench
            String golemPosSnapshot = golemPos;
            boolean golemCaseVisible = lobs.stream()
                .anyMatch(obs -> obs.getLeft().getLocationId().equals(golemPosSnapshot));
            if (golemCaseVisible && !golemVisible && !stenchDetected) {
                // Case visible, pas de golem, pas de stench → golem parti
                System.out.println(myAgent.getLocalName() + " - Golem a bougé depuis " + golemPos);
                myAgent.setGolemPosition(null);
                exitValue = WUMPUS_LOST;
                return;
            }

            // Recalculer le chemin vers le golem à chaque tick
            List<String> path = myAgent.getMyMap().getShortestPath(currentNodeId, golemPos);

            if (path == null || path.isEmpty()) {
                System.out.println(myAgent.getLocalName() + " - Pas de chemin vers golem");
                return;
            }

            myAgent.setChemin(path);
            moveTo(path.get(0));
            return;
        }

        // 5) Stench sans position connue → se diriger vers le stench
        if (!voisinsStench.isEmpty()) {
            // Préférer un nœud stench non visité récemment
            String target = voisinsStench.stream()
                .filter(n -> !n.equals(myAgent.getLastPosition()))
                .findFirst()
                .orElse(voisinsStench.get(0));
            moveTo(target);
        }
    }



    private boolean moveTo(String node) {
    	String AvantPos = myAgent.getCurrentPosition().getLocationId();
        if(myAgent.moveTo(new GsLocation(node)))
        {
        	// Faut croire qu'il y a des erreurs partout
        	if(!AvantPos.equals(myAgent.getCurrentPosition().getLocationId()))
        	{
        		myAgent.setLastPosition(AvantPos);
        		return true;
        	}
        	return false;
        }
        return false;
    }



    private boolean randomMove(List<String> lobs) {
    	Collections.shuffle(lobs);    	
        for (String candidate : lobs) {

        	if (!candidate.equals(myAgent.getCurrentPosition().getLocationId())) {

                if (myAgent.moveTo(new GsLocation(candidate))) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    // Pour continuer ou terminer la chasse
    @Override
    public int onEnd() {
        myAgent.setEtat(exitValue);
        return exitValue;
    }

}