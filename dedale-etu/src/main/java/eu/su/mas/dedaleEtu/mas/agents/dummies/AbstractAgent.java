package eu.su.mas.dedaleEtu.mas.agents.dummies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Location;
import eu.su.mas.dedale.env.Observation;
import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import eu.su.mas.dedaleEtu.mas.knowledge.CommService;
import eu.su.mas.dedaleEtu.mas.knowledge.ExtendedMapRepresentation;

public abstract class AbstractAgent extends AbstractDedaleAgent {
	
    private static final long serialVersionUID = 1L;
	
	// variables pour connaitre le trajet de l'agent
    protected String destination;
 	protected List<String> chemin; 
	protected String lastPosition = null;
	
	protected boolean exploFinished = false;
    
	// pour la chasse
	private boolean chasse = false; // false : Exploration sinon en chasse
    protected String golemPosition = null;                   
    protected boolean stench;
    protected List<String> ListPosLibre = new ArrayList<>();                                                                                                                                   
    
    // pour la connaissance et la communication
	protected ExtendedMapRepresentation myMap;
    protected CommService commService;
	//protected int timer;
    protected List<String> agentList = new ArrayList<>();
    protected List<String> coalition = new ArrayList<>();
    
    
	
	
	
	public void initMapRepresentation() {
        this.myMap = new ExtendedMapRepresentation(this.getLocalName());
        System.out.println(getLocalName() + " - INIT MAP ");

    }
	
	
	public ExtendedMapRepresentation getMyMap() {
		return this.myMap;
	}
	
	public boolean isChasse() {
		return chasse;
	}
	
	
	public void setChasse(boolean chasse) {
		this.chasse = chasse;
	}
	
	public void initCommService() {
	    this.commService = new CommService(this);
	}
	
	public String getDestination() {
		return destination;
	}
	
	// le chemin devient directement le plus cours vers la destination
	public void setDestination(String node) {
		this.destination = node;
		if (this.destination != null)
			this.setChemin(myMap.getShortestPath(this.getCurrentPosition().getLocationId(),node));
	}
	
	public List<String> getChemin() {
		return chemin;
	}


	public void setChemin(List<String> chemin) {
		this.chemin = chemin;
	}
	
	public boolean isExploFinished() {
		return exploFinished;
	}


	public void setExploFinished(boolean exploFinished) {
		this.exploFinished = exploFinished;
	}


	public CommService getCommService() {
		return this.commService;
	}
	
	public List<String> getAgentList() {
        return Collections.unmodifiableList(agentList);
    }
	
	// ne sert pas car tout les receveurs sont connu à l'avance
	public void addAgentList(String name) { 
		if (!name.equals(this.getLocalName()) && !agentList.contains(name)) {
            agentList.add(name);
            System.out.println(this.getLocalName() + " - New agent discovered: " + name);
        }    
	}
	
	
	public String getLastPosition() {
		return lastPosition;
	}
	
	
	public void setLastPosition(String avantPos) {
		this.lastPosition = avantPos;
	}
	
	public void setGolemPosition(String pos) {
	 	this.golemPosition = pos;            
	}                                        
	                                           
	public String getGolemPosition() {       
	 	return this.golemPosition;           
	}            
	
	public boolean isStench() {
		return stench;
	}


	public void setStench(boolean stench) {
		this.stench = stench;
	}


	public Map<String,String> getGolemsNearby (List<Couple<Location, List<Couple<Observation, String>>>> lobs) {
        Map<String, String> golems = new HashMap<>();
		for (Couple<Location, List<Couple<Observation, String>>> obs : lobs) {
	    	for (Couple<Observation, String> detail : obs.getRight()) {
	    		if (detail.getLeft() == Observation.AGENTNAME && detail.getRight().equals("Wumpus")) {
	    			    String pos = obs.getLeft().getLocationId();
    			    	this.setGolemPosition(pos);
	    			    golems.put(pos,detail.getRight());
	    			}
	    	}
		}
		return golems;
	}
	
	public Map<String, String> getAgentsNearby(List<Couple<Location, List<Couple<Observation, String>>>> lobs) {
        Map<String, String> neighbors = new HashMap<>();
        for (Couple<eu.su.mas.dedale.env.Location, List<Couple<Observation, String>>> obs : lobs) {
            String locationId = obs.getLeft().getLocationId();
            for (Couple<Observation, String> detail : obs.getRight()) {
                if (detail.getLeft() == Observation.AGENTNAME && !"Wumpus".equals(detail.getRight())) {
                    neighbors.put(locationId, detail.getRight());
                }
            }
        }
        return neighbors;
    }
	
	// regarde les environs, update et choisis un nouveau noeud comme destination
    public void updateMap(List<Couple<Location, List<Couple<Observation, String>>>> lobs, Location myPosition){
    	String nextNodeId = null;
        for (Couple<Location, List<Couple<Observation, String>>> c : lobs) {
        	
            Location accessibleNode = c.getLeft();
            boolean isNewNode = this.getMyMap().addNewNode(accessibleNode.getLocationId());
            if (!accessibleNode.getLocationId().equals(myPosition.getLocationId())) {
            	this.getMyMap().addEdge(myPosition.getLocationId(), accessibleNode.getLocationId());
                if (nextNodeId == null && isNewNode) {
                    nextNodeId = accessibleNode.getLocationId();
                }
            }
        }
        if (nextNodeId != null)
        	this.setDestination(nextNodeId);
    }
    
    //@Override
    /*public boolean moveTo(Location nodeId) {
    	this.setLastPosition(getCurrentPosition());
    	this.setMoved(this.moveTo(nodeId));
    	return this.hasMoved();	
    } */
                                                                
	                                                                                                                         
	public void addListPosLibre(List<String> positions) {      
	    for (String p : positions) {                           
	        if (!ListPosLibre.contains(p)) ListPosLibre.add(p);
	    }                                                      
	}                                                          
                                                            
	public List<String> getListPosLibre() {                    
	    return new ArrayList<>(ListPosLibre);                  
	}                                                          
	                                                           
	                                                           
	public void clearListPosLibre() {                          
	    this.ListPosLibre.clear();                             
	} 
}                                                         