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
        List<Couple<Location, List<Couple<Observation, String>>>> lobs = myAgent.observe();

        boolean stenchDetected = false;
        boolean golemVisible = false;
        String golemPos = myAgent.getGolemPosition(); // la variable n'est pas détecté ici ?


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
                    golemPos = node;
                    myAgent.setGolemPosition(node);
                    myAgent.getCommService().broadcastWumpusPosition(golemPos);
                } else if (detail.getLeft() == Observation.AGENTNAME) {
                    hasAgent = true;
                }
            }

            // Mise à jour carte
            boolean isNewNode = myAgent.getMyMap().addNewNode(node);
            if (!node.equals(currentNodeId)) {
                myAgent.getMyMap().addEdge(currentNodeId, node);
            }

            if (!hasAgent) {
                voisinsLibres.add(node);
                if (hasStench) voisinsStench.add(node);
            }
        }
        
        // Priorité : aller occuper un noeud libre autour du golem si assigné par message
        String wallTarget = myAgent.getWallTargetNode();
        if (wallTarget != null) {
            if (wallTarget.equals(currentNodeId)) {
                // on devient mur
                myAgent.setWallTargetNode(null);
                exitValue = 2;
                return;
            }
            List<String> path = myAgent.getMyMap().getShortestPath(currentNodeId, wallTarget);
            if (path != null && !path.isEmpty()) {
                if (!moveTo(path.get(0))) randomMove(voisinsLibres);
                return;
            }
            // Chemin introuvable → annuler l'assignation
            myAgent.setWallTargetNode(null);
        }

        // Golem visible et adjacent : on reste sur place pour le bloquer
        if (golemVisible) {
            List<String> path = myAgent.getMyMap().getShortestPath(currentNodeId, golemPos);
            if (path != null && path.size() == 1) {
                exitValue = 2; // on se met en mode wall
                return;
            }
        }

        // Golem n'est plus visible sur ce tick
        if (!golemVisible && golemPos != null) {
            // Vérifier si le golem était sur un nœud adjacent visible
        	String golemPos1 = myAgent.getGolemPosition();
            boolean golemNodeVisible = lobs.stream()
                .anyMatch(obs -> obs.getLeft().getLocationId().equals(golemPos1));

            if (golemNodeVisible) {
                // On voit la case mais le golem n'y est plus, il a bougé
                System.out.println(myAgent.getLocalName() + " - Golem a bougé depuis " + golemPos);
                myAgent.setGolemPosition(null);
                golemPos = null;
            }
            // Si la case n'est pas visible, on garde la dernière position connue
        }

        // Plus aucune trace → retour exploration
        if (!stenchDetected && golemPos == null) {
            if (myAgent.isExploFinished()) {
                randomMove(voisinsLibres);
                exitValue = WUMPUS_LOST;
                return;
            }
            myAgent.setGolemPosition(null);
            exitValue = 0;
            return;
        }

        // Se rapprocher du golem si position connue
        if (golemPos != null) {
            if (myAgent.getMyMap().getNode(golemPos) == null) {
                System.out.println(myAgent.getLocalName() + " - Nœud golem inconnu : " + golemPos);
                randomMove(voisinsStench.isEmpty() ? voisinsLibres : voisinsStench);
                return;
            }

            List<String> path = myAgent.getMyMap().getShortestPath(currentNodeId, golemPos);

            if (path == null || path.isEmpty()) {
                randomMove(voisinsLibres);
                return;
            }

            // Adjacent au golem : rester sur place et signaler
            if (path.size() == 1 && !golemVisible) {
                // Le golem devrait être là mais n'est plus visible → il a bougé
                myAgent.setGolemPosition(null);
                golemPos = null;
                randomMove(voisinsStench.isEmpty() ? voisinsLibres : voisinsStench);
                return;
            }

            String next = path.get(0);
            if (!moveTo(next)) {
                randomMove(voisinsLibres);
            }
            return;
        }

        // Pas de position connue mais stench : se diriger vers les cases avec stench
        if (!voisinsStench.isEmpty()) {
            Collections.shuffle(voisinsStench);
            for (String target : voisinsStench) {
                if (!target.equals(myAgent.getLastPosition()) && moveTo(target)) return;
            }
        }

        randomMove(voisinsLibres);
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
    	String AvantPos = myAgent.getCurrentPosition().getLocationId();
    	
        for (String candidate : lobs) {

        	if (!candidate.equals(myAgent.getCurrentPosition().getLocationId())) {

                if (myAgent.moveTo(new GsLocation(candidate))) {
                	myAgent.setLastPosition(AvantPos);
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