package eu.su.mas.dedaleEtu.mas.behaviours;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
        
    private int exitValue;

    public ExplorationDripBehaviour(AbstractAgent agent) {
        super(agent);
        this.myAgent = agent;
    }

    @Override
    public void action() {
    	exitValue = CONTINUE_EXPLORATION;
    	myAgent.setGolemPosition(null);
        //myAgent.setStench(false);
        myAgent.doWait(800);
        
        // Initialiser la carte si nécessaire
        if (myAgent.getMyMap() == null) {
            myAgent.initMapRepresentation();
        }

        
        Location myPosition = myAgent.getCurrentPosition();
        
        if (myPosition == null) return; // sécurité si agent pas encore déployé
        if (myPosition.getLocationId().equals(myAgent.getDestination())) myAgent.setDestination(null);

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
        if (!myAgent.getMyMap().hasOpenNode() && !myAgent.isExploFinished()) {
            System.out.println(myAgent.getLocalName() + " - Exploration successfully done, now patrols.");
            myAgent.setExploFinished(true);
        }

        /// 4) Déterminer le prochain noeud à atteindre
        if (myAgent.getDestination() == null || myAgent.getDestination().equals(myPosition.getLocationId())) {

            if (!myAgent.isExploFinished()) {
                // Mode exploration : aller vers le noeud ouvert le plus proche
                List<String> path = myAgent.getMyMap().getShortestPathToClosestOpenNode(myPosition.getLocationId());
                if (path != null && !path.isEmpty()) {
                    myAgent.setDestination(path.get(path.size() - 1));
                    myAgent.setChemin(path);
                    nextNodeId = path.get(0);}
            }
            
            if (myAgent.isExploFinished()) {
                // Mode patrouille : aller vers le noeud le plus vieux
            	if ( myAgent.getDestination() == null ) {
	                String stalest = myAgent.getMyMap().getStalestNode(myPosition.getLocationId());
	                if (stalest != null) {
	                    List<String> path = myAgent.getMyMap().getShortestPath(myPosition.getLocationId(), stalest);
	                    if (path != null && !path.isEmpty()) {
	                        myAgent.setDestination(stalest);
	                        myAgent.setChemin(path);
	                        nextNodeId = path.get(0);
	                    }
	                }
            	}
            }
        }
        
        
        // eviter de se bloquer avec un autre agent
        if (nearbyAgents.containsKey(nextNodeId)) { 
            System.out.println(myAgent.getLocalName() + " - Recalcul d'un chemin.");
            
            List<String> reserves = new ArrayList<>(nearbyAgents.keySet());

            // chemin où l'on esquive les agents proches
            List<String> path = myAgent.getMyMap()
                .getShortestPathWithBlockedNodes(myPosition.getLocationId(), myAgent.getDestination(), reserves);
            
            if (path != null && !path.isEmpty()) {
                //myAgent.setDestination(path.get(path.size() - 1)); 
                myAgent.setChemin(path);
                nextNodeId = path.get(0);
            } else {
                // Pas de chemin alternatif, choisir une nouvelle destination
            	List<String> openNodes = myAgent.getMyMap().getOpenNodes();
                Collections.shuffle(openNodes);
                
                for (String candidate : openNodes) {
                    List<String> altPath = myAgent.getMyMap()
                        .getShortestPathWithBlockedNodes(myPosition.getLocationId(), candidate, reserves);
                    if (altPath != null && !altPath.isEmpty()) {
                        myAgent.setDestination(candidate);
                        myAgent.setChemin(altPath);
                        nextNodeId = altPath.get(0);
                        break;
                    }
                }
            }
        }
        
        
        // Broadcaster si golem visible
        if (!nearbyGolems.isEmpty()) {
        	myAgent.setIgnoringStench(false);
            myAgent.getCommService().broadcastWumpusPosition(nearbyGolems.keySet().iterator().next());
		    //System.out.println(myAgent.getLocalName() + " a trouvé le GOLEM ");
            exitValue = WUMPUS_DETECTED;
            return;
        }
        
        if (stenchDetected && !myAgent.isIgnoringStench()) {
            boolean stenchFromAgent = lobs.stream()
                .filter(obs -> obs.getRight().stream()
                    .anyMatch(d -> d.getLeft() == Observation.STENCH))
                .allMatch(obs -> nearbyAgents.containsKey(obs.getLeft().getLocationId()));

            if (!stenchFromAgent) {
                exitValue = WUMPUS_DETECTED;
                return;
            }
            // sinon on ignore, c'est juste un mur
        }
    
        
        
        // ignore l'odeur (cas où il sort de la chasse)
        if (myAgent.isIgnoringStench()) {
            // Blacklister les noeuds avec stench
            List<String> stenchNodes = lobs.stream()
                .filter(obs -> obs.getRight().stream()
                    .anyMatch(d -> d.getLeft() == Observation.STENCH))
                .map(obs -> obs.getLeft().getLocationId())
                .collect(java.util.stream.Collectors.toList());

            // Forcer recalcul si destination dans zone stench
            if (stenchNodes.contains(myAgent.getDestination())) {
                myAgent.setDestination(null);
                myAgent.setChemin(null);
            }

            if (myAgent.getDestination() == null) {
                List<String> path = myAgent.getMyMap()
                    .getShortestPathToClosestOpenNode(myPosition.getLocationId());
                if (path != null && !path.isEmpty()) {
                    myAgent.setDestination(path.get(path.size() - 1));
                    myAgent.setChemin(path);
                    nextNodeId = path.get(0);
                }
            }

            if (!stenchDetected) {
                myAgent.setIgnoringStench(false);
            }
        }
        
        
        // si l'agent n'a pas réussi a bouger en exploration (rencontre autre agent), alors random
        /*
        if (myAgent.getLastPosition() != null && myAgent.getLastPosition().equals(myPosition.getLocationId())) {
            List<String> voisins = new ArrayList<>(nearbyAgents.keySet());
                        myAgent.getMyMap().getNeighbors(myPosition.getLocationId()).stream()
                .filter(n -> !nearbyAgents.containsKey(n))
                .forEach(voisins::add);
            myAgent.randomMove(voisins);
            return;
        }*/ 
        		
        		
        myAgent.setLastPosition(myPosition.getLocationId());

        // 5) Se déplacer vers le noeud sélectionné
        if (!myAgent.moveTo(new GsLocation(nextNodeId))) {
            // moveTo a échoué : noeud bloqué, on abandonne cette destination
            myAgent.setDestination(null);
            myAgent.setChemin(null);
            
            // Tenter un move aléatoire parmi les voisins libres
            List<String> voisins = myAgent.getMyMap().getNeighbors(myPosition.getLocationId());
            List<String> voisinsLibres = voisins.stream()
                .filter(n -> !nearbyAgents.containsKey(n))
                .filter(n -> !nearbyGolems.containsKey(n))
                .collect(java.util.stream.Collectors.toList());
            
            myAgent.randomMove(voisinsLibres);
        } else {
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
