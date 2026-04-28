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
        myAgent.setDestination(null); // àfin de l'arreter
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
        
        if (now - this.lastBroadcast <= 4000) {
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
        System.out.println(myAgent.getLocalName() + " - Wumpus position broadcast: " + location);
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
    public void announceBlocked(String location) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setProtocol("AGENT-BLOCKED");
        msg.setSender(myAgent.getAID());
        msg.setContent(location);
        for (String name : agentList) {
            if (!name.equals(myAgent.getLocalName()))
                msg.addReceiver(new AID(name, AID.ISLOCALNAME));
        }
        myAgent.sendMessage(msg);
        System.out.println(myAgent.getLocalName() + " - Announcing BLOCKED at " + location);
    }

    /**
     * Demande de l'aide pour encercler le Wumpus
     */
    public void requestWumpusHelp(String location) {
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.setProtocol("WUMPUS-HELP");
        msg.setSender(myAgent.getAID());
        msg.setContent(location);
        for (String name : agentList) {
            if (!name.equals(myAgent.getLocalName()))
                msg.addReceiver(new AID(name, AID.ISLOCALNAME));
        }
        myAgent.sendMessage(msg);
        System.out.println(myAgent.getLocalName() + " - Requesting help at " + location);
    }
    
    
    // à utiliser
    public void requestCoordinatedBlock(String nodeToBlock, List<AID> targetAgents) {
        
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.setProtocol("BLOCK-REQUEST");
        msg.setContent(nodeToBlock);
        msg.setSender(myAgent.getAID());
        
        for (AID agent : targetAgents) {
            msg.addReceiver(agent);
        }
        
        myAgent.sendMessage(msg);
    }
}