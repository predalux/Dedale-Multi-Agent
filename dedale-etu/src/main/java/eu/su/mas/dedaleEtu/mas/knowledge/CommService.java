package eu.su.mas.dedaleEtu.mas.knowledge;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dataStructures.serializableGraph.SerializableSimpleGraph;
import eu.su.mas.dedaleEtu.mas.agents.dummies.AbstractAgent;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation.MapAttribute;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.UnreadableException;

public class CommService {

    private AbstractAgent myAgent;
    private List<String> agentList;
    
    private static final int BROADCAST_COOLDOWN_MS = 4000;      // cd entre broadcast du wumpus
    private static final int PONG_WAIT_MS = 1000;           	// Attente en ayant enovyé un pong
    private static final boolean AFFICHAGE = false;        		// Affiche le traitement des messages par les agents (ca peut faire beaucoup)


    long lastBroadcast;

    // ========== CONSTRUCTEUR ==========

    public CommService(AbstractAgent myAgent) {
        this.myAgent = myAgent;
        this.agentList = myAgent.getAgentList();
    }

    // ========== PING / PONG ==========

    public void sendPing() {
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.setProtocol("PING");
        msg.setSender(myAgent.getAID());
        for (String name : agentList) {
            if (!name.equals(myAgent.getLocalName()))
                msg.addReceiver(new AID(name, AID.ISLOCALNAME));
        }
        myAgent.sendMessage(msg);
    }

    public void sendPong(AID receiver) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setProtocol("PONG");
        msg.setSender(myAgent.getAID());
        msg.addReceiver(receiver);
        myAgent.sendMessage(msg);
        if (myAgent.getEtat()==0)
        	myAgent.doWait(PONG_WAIT_MS);
    }

    public void sendMap(AID receiver) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setProtocol("SHARE-TOPO");
        msg.setSender(myAgent.getAID());
        msg.addReceiver(receiver);
        try {
            SerializableSimpleGraph<String, MapAttribute> sg =
            		myAgent.getMyMap().getSerializableGraph();
            msg.setContentObject(sg);
            myAgent.sendMessage(msg);
        } catch (IOException e) {
            System.err.println(myAgent.getLocalName() + " - Error sending map: " + e.getMessage());
        }
    }

    // ========== WUMPUS ==========

    /**
     * Envoie la position confirmée du Wumpus à tous les agents
     */
    public void broadcastWumpusPosition(String location) {
    	long now = System.currentTimeMillis();
        
        if (now - this.lastBroadcast <= BROADCAST_COOLDOWN_MS) {
        	return;
        }
        this.lastBroadcast = now;
        
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setProtocol("WUMPUS-LOCATION");
        msg.setSender(myAgent.getAID());
        msg.setContent(location);
        for (String name : agentList) {
            if (!name.equals(myAgent.getLocalName()))
                msg.addReceiver(new AID(name, AID.ISLOCALNAME));
        }
        myAgent.sendMessage(msg);
        if (AFFICHAGE)
        	System.out.println(myAgent.getLocalName() + " - Wumpus position broadcast: " + location);
    }
    
    /**
     * Envoie les noeuds "libres" autour d'un noeud
     */
    public void broadcastFreeNeighbors(String golemPos, List<String> freeNeighbors, List<String> blockedNodes) {
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.setProtocol("WALL-NEEDED");
        msg.setSender(myAgent.getAID());
        // Format : "golemPos|free1,free2|blocked1,blocked2"
        String free = String.join(",", freeNeighbors);
        String blocked = String.join(",", blockedNodes);
        msg.setContent(golemPos + "|" + free + "|" + blocked);
        for (String name : agentList) {
            if (!name.equals(myAgent.getLocalName()))
                msg.addReceiver(new AID(name, AID.ISLOCALNAME));
        }
        myAgent.sendMessage(msg);
    }
    
    /**
     * Envoie les noeuds "libres" autour d'un noeud
     */
    public void propagateBroadcastFreeNeighbors(String golemPos, List<String> freeNeighbors, List<String> blockedNodes) {
        ACLMessage msg = new ACLMessage(ACLMessage.PROPAGATE);
        msg.setProtocol("WALL-NEEDED");
        msg.setSender(myAgent.getAID());
        // Format : "golemPos|free1,free2|blocked1,blocked2"
        String free = String.join(",", freeNeighbors);
        String blocked = String.join(",", blockedNodes);
        msg.setContent(golemPos + "|" + free + "|" + blocked);
        for (String name : agentList) {
            if (!name.equals(myAgent.getLocalName()))
                msg.addReceiver(new AID(name, AID.ISLOCALNAME));
        }
        myAgent.sendMessage(msg);
    }
    
    /**
     * Envoie la position du Wumpus à un seul agent
     */
    public void sendWumpusLocation(String location, AID receiver) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setProtocol("WUMPUS-LOCATION");
        msg.setSender(myAgent.getAID());
        msg.setContent(location);
        msg.addReceiver(receiver);
        myAgent.sendMessage(msg);
    }

    /**
     * Propage une alerte Wumpus à tous
     */
    public void propagateWumpusAlert(String location, AID originalSender) {
        ACLMessage msg = new ACLMessage(ACLMessage.PROPAGATE);
        msg.setProtocol("WUMPUS-ALERT");
        msg.setSender(myAgent.getAID());
        msg.setContent(location);
        for (String name : agentList) {
            if (!name.equals(myAgent.getLocalName()) &&
                !name.equals(originalSender.getLocalName()))
                msg.addReceiver(new AID(name, AID.ISLOCALNAME));
        }
        myAgent.sendMessage(msg);
    }

    /**
     * Annonce que l'agent est bloqué à une position
     */
    public void requestMoveAside(String agentName, String targetWall) {
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.setProtocol("MOVE-ASIDE");
        msg.setSender(myAgent.getAID());
        msg.setContent(targetWall);
        msg.addReceiver(new AID(agentName, AID.ISLOCALNAME));
        myAgent.sendMessage(msg);
    }
}