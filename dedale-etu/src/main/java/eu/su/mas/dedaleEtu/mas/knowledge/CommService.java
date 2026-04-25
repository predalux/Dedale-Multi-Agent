package eu.su.mas.dedaleEtu.mas.knowledge;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dataStructures.serializableGraph.SerializableSimpleGraph;
import eu.su.mas.dedaleEtu.mas.agents.dummies.AbstractAgent;
import eu.su.mas.dedaleEtu.mas.behaviours.CommunicationDripBehaviour.AgentCommState;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation.MapAttribute;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.UnreadableException;

public class CommService {

    private AbstractAgent agent;
    private List<String> agentList;

    // ========== CONSTRUCTEUR ==========

    public CommService(AbstractAgent agent) {
        this.agent = agent;
        this.agentList = agent.getAgentList();
    }

    // ========== PING / PONG ==========

    public void sendPing() {
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.setProtocol("PING");
        for (String name : agentList) {
            if (!name.equals(agent.getLocalName()))
                msg.addReceiver(new AID(name, AID.ISLOCALNAME));
        }
        agent.sendMessage(msg);
    }

    public void sendPong(AID receiver) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setProtocol("PONG");
        msg.addReceiver(receiver);
        agent.sendMessage(msg);
    }

    // ========== CARTE ==========

    public void sendMap(AID receiver) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setProtocol("SHARE-TOPO");
        msg.addReceiver(receiver);
        try {
            SerializableSimpleGraph<String, MapAttribute> sg =
                agent.getMyMap().getSerializableGraph();
            msg.setContentObject(sg);
            agent.sendMessage(msg);
        } catch (IOException e) {
            System.err.println(agent.getLocalName() + " - Error sending map: " + e.getMessage());
        }
    }

    // ========== WUMPUS ==========

    /**
     * Envoie la position confirmée du Wumpus à tous les agents
     */
    public void broadcastWumpusPosition(String location) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setProtocol("WUMPUS-LOCATION");
        msg.setContent(location);
        for (String name : agentList) {
            if (!name.equals(agent.getLocalName()))
                msg.addReceiver(new AID(name, AID.ISLOCALNAME));
        }
        agent.sendMessage(msg);
        System.out.println(agent.getLocalName() + " - Wumpus position broadcast: " + location);
    }

    /**
     * Envoie la position du Wumpus à un seul agent (ex: lors d'un PONG)
     */
    public void sendWumpusLocation(String location, AID receiver) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setProtocol("WUMPUS-LOCATION");
        msg.setContent(location);
        msg.addReceiver(receiver);
        agent.sendMessage(msg);
    }

    /**
     * Propage une alerte Wumpus à tous
     */
    public void propagateWumpusAlert(String location, AID originalSender) {
        ACLMessage msg = new ACLMessage(ACLMessage.PROPAGATE);
        msg.setProtocol("WUMPUS-ALERT");
        msg.setContent(location);
        for (String name : agentList) {
            if (!name.equals(agent.getLocalName()) &&
                !name.equals(originalSender.getLocalName()))
                msg.addReceiver(new AID(name, AID.ISLOCALNAME));
        }
        agent.sendMessage(msg);
    }

    /**
     * Annonce que l'agent est bloqué à une position
     */
    public void announceBlocked(String location) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setProtocol("AGENT-BLOCKED");
        msg.setContent(location);
        for (String name : agentList) {
            if (!name.equals(agent.getLocalName()))
                msg.addReceiver(new AID(name, AID.ISLOCALNAME));
        }
        agent.sendMessage(msg);
        System.out.println(agent.getLocalName() + " - Announcing BLOCKED at " + location);
    }

    /**
     * Demande de l'aide pour encercler le Wumpus
     */
    public void requestWumpusHelp(String location) {
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.setProtocol("WUMPUS-HELP");
        msg.setContent(location);
        for (String name : agentList) {
            if (!name.equals(agent.getLocalName()))
                msg.addReceiver(new AID(name, AID.ISLOCALNAME));
        }
        agent.sendMessage(msg);
        System.out.println(agent.getLocalName() + " - Requesting help at " + location);
    }
    
    
    // à utiliser
    public void requestCoordinatedBlock(String nodeToBlock, List<AID> targetAgents) {
        
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.setProtocol("BLOCK-REQUEST");
        msg.setContent(nodeToBlock);
        
        for (AID agent : targetAgents) {
            msg.addReceiver(agent);
        }
        
        agent.sendMessage(msg);
    }
}