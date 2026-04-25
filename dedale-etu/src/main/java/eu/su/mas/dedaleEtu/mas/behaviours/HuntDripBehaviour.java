package eu.su.mas.dedaleEtu.mas.behaviours;

import java.util.ArrayList;
import java.util.List;

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
    private int exitValue = 0;
    

    public HuntDripBehaviour(DrippedAgent agent) {
        super(agent);
        this.myAgent = agent;
    }

    @Override
    public void action() {
    	
    	// par défaut on reste en chasse
        exitValue = 1; 
        
        //System.out.println(this.myAgent.getLocalName() +"-----chasse------");
        

        Location myPosition = myAgent.getCurrentPosition();
        if (myPosition == null) return;

        List<Couple<Location, List<Couple<Observation, String>>>> lobs = myAgent.observe();
        
        System.out.println(this.myAgent.getLocalName() +"-----observation------" + lobs);

        boolean stenchDetected = false;
        String golemPos = myAgent.getGolemPosition();

        List<String> freeNeighbors = new ArrayList<>();

        //  Analyse des observations
        for (Couple<Location, List<Couple<Observation, String>>> obs : lobs) {

            String node = obs.getLeft().getLocationId();
            boolean hasAgent = false;
            boolean hasStench = false;

            for (Couple<Observation, String> detail : obs.getRight()) {

                if (detail.getLeft() == Observation.STENCH) {
                    hasStench = true;
                    stenchDetected = true;
                }

                if (detail.getLeft() == Observation.AGENTNAME) {
                    hasAgent = true;

                    if (detail.getRight().equals("Wumpus")) {
                        golemPos = node;
                        myAgent.setGolemPosition(node);
                        
                        // Diffuser aux autres agents
                        broadcastWumpusPosition(node);
                        
                        exitValue = 0;
                        return;
                    }
                }
            }
            
            Location accessibleNode = obs.getLeft();
            boolean isNewNode = myAgent.getMyMap().addNewNode(accessibleNode.getLocationId());
            if (!accessibleNode.getLocationId().equals(myPosition.getLocationId())) {
            	myAgent.getMyMap().addEdge(myPosition.getLocationId(), accessibleNode.getLocationId());
                if (node == null && isNewNode) {
                    node = accessibleNode.getLocationId();
                }
            }

            // case libre intéressante
            if (!hasAgent && hasStench) {
                freeNeighbors.add(node);
            }
        }

        //  Si on ne sent plus rien alors on termine la chasse
        if (!stenchDetected && golemPos == null) {
            //System.out.println(myAgent.getLocalName() + " perd la trace du golem");
            myAgent.setGolemPosition(null);
            exitValue = 0;
            return;
        }
        
        //  On se rapproche du golem
        if (golemPos != null) {

            List<String> path = myAgent.getMyMap().getShortestPath(myPosition.getLocationId(), golemPos);
            
            if (path != null && !path.isEmpty()) {
                if (path.size() == 1) { exitValue = 0; } // le golem est en face
                String next = path.get(0);
                moveTo(next);
                return;
            }
        }

        // Encerclement
        if (!freeNeighbors.isEmpty()) {

            // choisir une position libre
            String target = freeNeighbors.get(0);

            //System.out.println(myAgent.getLocalName()+ " se positionne pour encercler en " + target);

            moveTo(target);
            return;
        }

        

        //  Si on a rien detecter alors on fait de la marche aléatoire
        randomMove(lobs);
    }



    private void moveTo(String node) {
    	myAgent.setLastPosition(myAgent.getCurrentPosition());
        if(!myAgent.moveTo(new GsLocation(node)))
        {
        	randomMove(myAgent.observe());
        }
        
    }



    private void randomMove(List<Couple<Location, List<Couple<Observation, String>>>> lobs) {

    	Location lastPos = myAgent.getLastPosition(); // à stocker dans l'agent

        for (Couple<Location, List<Couple<Observation, String>>> obs : lobs) {
            String candidate = obs.getLeft().getLocationId();
            boolean isCurrentPos = candidate.equals(myAgent.getCurrentPosition().getLocationId());
            boolean isLastPos = lastPos != null && candidate.equals(lastPos.getLocationId());

            if (!isCurrentPos && !isLastPos) {
                if (myAgent.moveTo(new GsLocation(candidate))) return;
            }
        }
        // Fallback si tous les voisins sont visités
        for (Couple<Location, List<Couple<Observation, String>>> obs : lobs) {
            String candidate = obs.getLeft().getLocationId();
            if (!candidate.equals(myAgent.getCurrentPosition().getLocationId())) {
                if (myAgent.moveTo(new GsLocation(candidate))) return;
            }
        }
    }
    
    private void broadcastWumpusPosition(String node) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setProtocol("WUMPUS-LOCATION");
        msg.setContent(node);
        myAgent.sendMessage(msg);
    }
    
    // Pour continuer ou terminer la chasse
    @Override
    public int onEnd() {
        return exitValue;
    }

}