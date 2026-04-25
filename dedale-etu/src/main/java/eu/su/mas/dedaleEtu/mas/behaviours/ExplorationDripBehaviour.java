package eu.su.mas.dedaleEtu.mas.behaviours;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import dataStructures.serializableGraph.SerializableSimpleGraph;
import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Location;
import eu.su.mas.dedale.env.Observation;
import eu.su.mas.dedale.env.gs.GsLocation;

import eu.su.mas.dedale.mas.AbstractDedaleAgent;

import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation.MapAttribute;
import eu.su.mas.dedaleEtu.mas.knowledge.ExtendedMapRepresentation;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation;
import eu.su.mas.dedaleEtu.mas.agents.dummies.AbstractAgent;
import eu.su.mas.dedaleEtu.mas.agents.dummies.DrippedAgent;
import eu.su.mas.dedaleEtu.mas.behaviours.ShareMapBehaviour;
import jade.core.AID;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.util.leap.Map;

public class ExplorationDripBehaviour extends OneShotBehaviour {
    private ExtendedMapRepresentation myMap;
    private AbstractAgent myAgent;

    
    // Codes de retour pour le FSM
    public static final int CONTINUE_EXPLORATION = 0;
    public static final int WUMPUS_DETECTED = 1;
    public static final int EXPLORATION_COMPLETE = 2;
    
    private int exitValue = CONTINUE_EXPLORATION;

    public ExplorationDripBehaviour(AbstractAgent agent) {
        super(agent);
        this.myAgent = agent;
        this.myMap = myAgent.getMyMap();
    }

    @Override
    public void action() {
    	exitValue = 0;
    	myAgent.setGolemPosition(null);
        //myAgent.setStench(false);
        
        Location myPosition = myAgent.getCurrentPosition();
        
        if (myPosition == null) return; // sécurité si agent pas encore déployé

        // 1) Marquer le noeud courant comme fermé
        myAgent.getMyMap().addNode(myPosition.getLocationId(), MapAttribute.closed);

        // 2) Observer et mettre à jour la carte
        List<Couple<Location, List<Couple<Observation, String>>>> lobs = myAgent.observe();
        
        String nextNodeId = myAgent.updateMap(lobs,myPosition);

        java.util.Map<String,String> nearbyAgents = myAgent.getAgentsNearby(lobs);
        
        java.util.Map<String,String> nearbyGolems = myAgent.getGolemsNearby(lobs);
        
        boolean stenchDetected = false;
        for (Couple<Location, List<Couple<Observation, String>>> obs : lobs) {
            for (Couple<Observation, String> detail : obs.getRight()) {
                if (detail.getLeft() == Observation.STENCH) {
                    stenchDetected = true;
                }
            }
        }
        
        // 3) Vérifier si tous les noeuds ont été explorés
        if (!myAgent.getMyMap().hasOpenNode()) {
            System.out.println(myAgent.getLocalName() + " - Exploration successfully done, behaviour removed.");
            exitValue = EXPLORATION_COMPLETE;
            return;
        }

        // 4) Déterminer le prochain noeud à atteindre
        if (nextNodeId == null) {
            // aucun noeud ouvert directement adjacent prendre le plus proche 
            List<String> path = myAgent.getMyMap().getShortestPathToClosestOpenNode(myPosition.getLocationId());
            if (path != null && !path.isEmpty()) {
                nextNodeId = path.get(0);
            } else {
                return;
            }
        }
        
        if (nearbyAgents.containsKey(nextNodeId)) { 
        	System.out.println(myAgent.getLocalName() + " - Recalcul d'un chemin.");
        	String string_pos = myPosition.getLocationId();
        	List<String> path = myAgent.getMyMap().getShortestPathToClosestOpenNode(string_pos);
        	if (path != null && path.size() > 1) {
        		nextNodeId = path.get(0);
        	} else { 
        		while (nextNodeId.equals(myPosition))
        			nextNodeId = (this.myAgent).getMyMap().getRandomNode();
        		
        	}
        }
        
        if (!nearbyGolems.isEmpty() || stenchDetected) {
            // Broadcaster si golem visible
            if (!nearbyGolems.isEmpty()) {
                myAgent.getCommService().broadcastWumpusPosition(nearbyGolems.keySet().iterator().next());
            }
		    System.out.println(myAgent.getLocalName() + " a trouvé le GOLEM ");
            exitValue = WUMPUS_DETECTED; // transition vers chasse
            return;
        }
        		
        		
        myAgent.setLastPosition(myPosition);
        myAgent.doWait(1000);

        // 5) Se déplacer vers le noeud sélectionné
        myAgent.moveTo(new GsLocation(nextNodeId)); // rend un bool
    }
    
    /**
     * Vérifie si l'agent a bougé depuis le dernier cycle
     */
    private void checkIfMoved(String currentPositionId) {
        boolean hasMoved = !currentPositionId.equals(myAgent.getLastPosition());
        myAgent.setMoved(hasMoved);
        
        if (!hasMoved) {
            System.out.println(myAgent.getLocalName() + " - STUCK : Did not move from " + currentPositionId);
        }
    }
    
    @Override
    public int onEnd() {
        // Retourne le code de transition pour le FSM
        return exitValue;
    }
}
