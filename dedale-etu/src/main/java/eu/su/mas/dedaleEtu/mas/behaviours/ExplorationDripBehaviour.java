package eu.su.mas.dedaleEtu.mas.behaviours;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;

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
    
	private AbstractAgent myAgent;
    
    // codes de retour pour le FSM
    public static final int CONTINUE_EXPLORATION = 0; // explore
    public static final int WUMPUS_DETECTED = 1;	  // chasse
    
    //public static final int EXPLORATION_COMPLETE = 2; // directement intégrer dans l'exploration
    
    private int exitValue = CONTINUE_EXPLORATION;

    public ExplorationDripBehaviour(AbstractAgent agent) {
        super(agent);
        this.myAgent = agent;
    }

    @Override
    public void action() {
    	exitValue = 0;
    	myAgent.setGolemPosition(null);
        //myAgent.setStench(false);
        myAgent.doWait(1000);
        
        // Initialiser la carte si nécessaire
        if (myAgent.getMyMap() == null) {
            myAgent.initMapRepresentation();
        }

        
        Location myPosition = myAgent.getCurrentPosition();
        
        if (myPosition == null) return; // sécurité si agent pas encore déployé

        // 1) Marquer le noeud courant comme fermé
        myAgent.getMyMap().addNode(myPosition.getLocationId(), MapAttribute.closed);

        // 2) Observer et mettre à jour la carte
        List<Couple<Location, List<Couple<Observation, String>>>> lobs = myAgent.observe();
        
        // update map definie la destination
        myAgent.updateMap(lobs,myPosition);

        String nextNodeId = null;
        
        if (myAgent.getChemin() != null && !myAgent.getChemin().isEmpty()) {
        	 nextNodeId = myAgent.getChemin().get(0);
        }
        		
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
        if (!myAgent.getMyMap().hasOpenNode() && !myAgent.isExploFinished()) {
            System.out.println(myAgent.getLocalName() + " - Exploration successfully done, now patrols.");
            myAgent.setExploFinished(true);
        }

        /// 4) Déterminer le prochain noeud à atteindre
        if (nextNodeId == null || myAgent.getDestination() == null || myAgent.getDestination().equals(myPosition.getLocationId())) {

            if (!myAgent.isExploFinished()) {
                // Mode exploration : aller vers le nœud ouvert le plus proche
                List<String> path = myAgent.getMyMap().getShortestPathToClosestOpenNode(myPosition.getLocationId());
                if (path != null && !path.isEmpty()) {
                    myAgent.setChemin(path);
                    myAgent.setDestination(path.get(path.size() - 1));
                    nextNodeId = path.get(0);
                } else {
                    // Plus de nœuds ouverts, exploration finie
                    myAgent.setExploFinished(true);
                }
            }
            
            if (myAgent.isExploFinished()) {
                // Mode patrouille : aller vers le noeud le plus vieux
                String stalest = myAgent.getMyMap().getStalestNode(myPosition.getLocationId());
                if (stalest != null) {
                    List<String> path = myAgent.getMyMap().getShortestPath(myPosition.getLocationId(), stalest);
                    if (path != null && !path.isEmpty()) {
                        myAgent.setChemin(path);
                        myAgent.setDestination(stalest);
                        nextNodeId = path.get(0);
                    }
                }
            }
        }
        
        // eviter de se bloquer avec un autre agent
        if (nearbyAgents.containsKey(nextNodeId)) { 
        	System.out.println(myAgent.getLocalName() + " - Recalcul d'un chemin.");
    	    System.out.println(myAgent.getLocalName() + " next Node : "+ nextNodeId);
        	String string_pos = myPosition.getLocationId();
        	List<String> reserves = new ArrayList<>();
        	reserves.add(nextNodeId);
        	List<String> path = myAgent.getMyMap().getShortestPathWithBlockedNodes(string_pos,myAgent.getDestination(),reserves);
        	if (path != null && path.size() >= 1) {
        	    myAgent.setChemin(path);
        		nextNodeId = path.get(0);
        	} else { 
        		List<String> openNodes = myAgent.getMyMap().getOpenNodes();
        		if (!openNodes.isEmpty()) {
        		    nextNodeId = openNodes.get(new Random().nextInt(openNodes.size()));
        		}
        	} 
        	
        	// v2 avec modif chemin
        	/*
        	
        	List<String> chemin = myAgent.getMyMap().getShortestPathWithBlockedNodes(myPosition.getLocationId(), myAgent.getDestination(), reserves);
        	if (chemin != null && !chemin.isEmpty()) {
        	    myAgent.setChemin(chemin);
        	    nextNodeId = chemin.get(0);
        	} else {
        	    System.out.println("Pas de chemin trouvé !");   
        	} */
        }
        
        // si on trouve un golem ou de la stench
        if (!nearbyGolems.isEmpty() || stenchDetected) {
            // Broadcaster si golem visible
            if (!nearbyGolems.isEmpty()) {
                myAgent.getCommService().broadcastWumpusPosition(nearbyGolems.keySet().iterator().next());
    		    System.out.println(myAgent.getLocalName() + " a trouvé le GOLEM ");
            }
            exitValue = WUMPUS_DETECTED; // transition vers chasse
            return;
        }
        		
        		
        myAgent.setLastPosition(myPosition.getLocationId());

        // 5) Se déplacer vers le noeud sélectionné
        if (myAgent.moveTo(new GsLocation(nextNodeId))) {
            myAgent.getMyMap().markVisited(nextNodeId);
            List<String> chemin = myAgent.getChemin();
            if (chemin != null && !chemin.isEmpty()) {
                chemin.remove(0);
                myAgent.setChemin(chemin);
            }
        }    
    }
    
    
    @Override
    public int onEnd() {
        // Retourne le code de transition pour le FSM
        myAgent.setEtat(exitValue);
        return exitValue;
    }
}
