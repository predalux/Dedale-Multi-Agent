package eu.su.mas.dedaleEtu.mas.behaviours;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dataStructures.serializableGraph.SerializableSimpleGraph;
import eu.su.mas.dedale.env.Location;
import eu.su.mas.dedaleEtu.mas.agents.dummies.AbstractAgent;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation.MapAttribute;
import jade.core.AID;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.UnreadableException;

/**
 * Behaviour de communication pour FSM.
 * S'exécute une fois par cycle FSM pour traiter et réagir aux messages en attente.
 * Gère : découverte initiale, partage de cartes, coordination Wumpus.
 * L'envoie des messages est défini dans MessageUtils
 */
public class CommunicationDripBehaviour extends OneShotBehaviour {
    
    // ========== CONFIGURATION ==========
    private static final int PING_INTERVAL_MS = 5000;           // Ping toutes les 5 secondes
    private static final int PONG_WAIT_MS = 5000;           // Attente en ayant recu un pong
    private static final int MAP_SHARE_COOLDOWN_MS = 3000;      // Cooldown entre partages
    private static final int MAX_MESSAGES_PER_CYCLE = 10;       // Évite le blocage
    
    
    // ========== ATTRIBUTS ==========
    private AbstractAgent myAgent;
    
    // État statique pour persister entre les cycles FSM
    private static Map<String, AgentCommState> commStates = new HashMap<>();
    
    // ========== CLASSE INTERNE POUR L'ÉTAT ==========
    
    /**
     * État de communication d'un agent (persist entre cycles FSM)
     */
    private static class AgentCommState {
        long lastPingTime;
        Map<AID, Long> lastMapSentTime;
        Map<AID, Long> lastMapReceivedTime;
        boolean initialPingSent;
        
        // Statistiques
        int messagesSent;
        int messagesReceived;
        int mapsMerged;
        int wumpusAlertsReceived;
        
        AgentCommState() {
            this.lastPingTime = 0;
            this.lastMapSentTime = new HashMap<>();
            this.lastMapReceivedTime = new HashMap<>();
            this.initialPingSent = false;
            this.messagesSent = 0;
            this.messagesReceived = 0;
            this.mapsMerged = 0;
            this.wumpusAlertsReceived = 0;
        }
    }
    
    // ========== CONSTRUCTEUR ==========
    
    public CommunicationDripBehaviour(AbstractAgent agent) {
        super(agent);
        this.myAgent = agent;
        
        // Initialiser l'état pour cet agent 
        if (!commStates.containsKey(agent.getLocalName())) {
            commStates.put(agent.getLocalName(), new AgentCommState());
        }
    }
    
    // ========== ACTION PRINCIPALE ==========
    
    @Override
    public void action() {
    	
    	Location myPosition = (Location) myAgent.getCurrentPosition();
        if (myPosition == null) return;

        AgentCommState state = getState();
        
        // 2. Envoyer le PING initial (une seule fois au démarrage)
        if (!state.initialPingSent) {
            sendInitialPing(state);
        }
        
        // 3. Traiter TOUS les messages en attente
        processAllMessages(state);
        
        // 4. Envoyer un PING périodique (pour maintenir la communication)
        sendPeriodicPing(state);
    }
    
    // ========== TRAITEMENT DES MESSAGES ==========
    
    /**
     * Traite tous les messages en attente dans la file
     */
    private boolean processAllMessages(AgentCommState state) {
        ACLMessage msg;
        int processedCount = 0;
        
        // Traiter jusqu'à MAX_MESSAGES_PER_CYCLE messages par cycle
        while ((msg = myAgent.receive(null)) != null && processedCount < MAX_MESSAGES_PER_CYCLE) {
            processedCount++;
            state.messagesReceived++;
            handleMessage(msg, state);
        }
        
        if (processedCount > 0) {
            System.out.println(myAgent.getLocalName() + " - Processed " + processedCount + " messages");
        }
        return processedCount > 0;
    }
    
    /**
     * Route un message vers le bon handler
     */
    private void handleMessage(ACLMessage msg, AgentCommState state) {
        AID sender = msg.getSender();
        String protocol = msg.getProtocol();
        
        
        // Router selon le performatif et protocole
        switch (msg.getPerformative()) {
            case ACLMessage.REQUEST:
                handleRequest(msg, sender, protocol, state);
                break;
                
            case ACLMessage.INFORM:
                handleInform(msg, sender, protocol, state);
                break;
                
            case ACLMessage.PROPAGATE:
                handlePropagate(msg, sender, protocol, state);
                break;
                
            default:
                System.out.println(myAgent.getLocalName() + 
                    " - Unknown performative: " + ACLMessage.getPerformative(msg.getPerformative()));
        }
    }
    
    /**
     * Gère les messages de type REQUEST
     */
    private void handleRequest(ACLMessage msg, AID sender, String protocol, AgentCommState state) {
        switch (protocol) {
            case "PING":
                handlePing(sender, state);
                break;
                
            case "REQUEST-MAP":
            	myAgent.getCommService().sendMap(sender);
                break;
                
            case "WUMPUS-HELP":
                handleWumpusHelpRequest(msg, sender);
                break;
                
            case "BLOCK-REQUEST":
                handleBlockRequest(msg, sender);
                break;
                
            default:
                System.out.println(myAgent.getLocalName() + " - Unknown REQUEST: " + protocol);
        }
    }
    
    /**
     * Gère les messages de type INFORM
     */
    private void handleInform(ACLMessage msg, AID sender, String protocol, AgentCommState state) {
        switch (protocol) {
            case "PONG":
                handlePong(sender, state);
                break;
                
            case "SHARE-TOPO":
                handleMapShare(msg, sender, state);
                break;
                
            case "CHASSE-GOLEM":
                handleChasseGolem(msg, sender);
                break;
                
            case "WUMPUS-LOCATION":
                handleWumpusLocation(msg, sender);
                break;
                
            case "AGENT-BLOCKED":
                handleAgentBlocked(msg, sender);
                break;
                
            default:
                System.out.println(myAgent.getLocalName() + " - Unknown INFORM: " + protocol);
        }
    }
    
    /**
     * Gère les messages de type PROPAGATE
     */
    private void handlePropagate(ACLMessage msg, AID sender, String protocol, AgentCommState state) {
        switch (protocol) {
            case "WUMPUS-ALERT":
                String loc = msg.getContent();
                myAgent.setGolemPosition(loc);
                myAgent.getMyMap().addNode(loc, MapAttribute.open);
                System.out.println(myAgent.getLocalName() + " - WUMPUS ALERT at " + loc);
                // Propager aux autres (sauf expéditeur)
                myAgent.getCommService().propagateWumpusAlert(loc, sender);
                break;               
                
            default:
                System.out.println(myAgent.getLocalName() + " - Unknown PROPAGATE: " + protocol);
        }
    }
    
    // ========== HANDLERS SPÉCIFIQUES ==========
    
    /**
     * Traite un PING : répond avec PONG
     */
    private void handlePing(AID sender, AgentCommState state) {
        System.out.println(myAgent.getLocalName() + " - PING from " + sender.getLocalName());
        myAgent.getCommService().sendPong(sender);
    }
    
    /**
     * Traite un PONG : envoie la carte si cooldown respecté
     */
    private void handlePong(AID sender, AgentCommState state) {
        System.out.println(myAgent.getLocalName() + " - PONG from " + sender.getLocalName());
        
        if (shouldShareMapWith(sender, state)) {
        	myAgent.getCommService().sendMap(sender);
        	state.lastMapSentTime.put(sender, System.currentTimeMillis());
        }
        
        myAgent.doWait(PONG_WAIT_MS);
    }
    
    /**
     * Traite la réception d'une carte partagée
     */
    private void handleMapShare(ACLMessage msg, AID sender, AgentCommState state) {
        try {
            SerializableSimpleGraph<String, MapAttribute> receivedGraph =
                (SerializableSimpleGraph<String, MapAttribute>) msg.getContentObject();
            System.out.println(myAgent.getLocalName() + " - MERGED MAP from " + sender.getLocalName());

            myAgent.getMyMap().mergeMap(receivedGraph);
            
            state.lastMapReceivedTime.put(sender, System.currentTimeMillis());
            state.mapsMerged++;
            
            //System.out.println(myAgent.getLocalName() + " - Map merged from " + sender.getLocalName() + " (+" + newNodes + " nodes, total: " + nodesAfter + ")");
            
        } catch (UnreadableException e) {
            System.err.println(myAgent.getLocalName() + " - Error merging map from " + 
                             sender.getLocalName() + ": " + e.getMessage());
        }
    }
    
    
    private void handleChasseGolem(ACLMessage msg, AID sender) {
        if (myAgent.getGolemPosition() == null) {
            String pos = msg.getContent();
            myAgent.setGolemPosition(pos);
            myAgent.getMyMap().addNode(pos, MapAttribute.open);
            System.out.println(myAgent.getLocalName() + " - Golem position received: " + pos);
        }
    }
    
    /**
     * Traite une localisation Wumpus confirmée
     */
    private void handleWumpusLocation(ACLMessage msg, AID sender) {
    	String pos = msg.getContent();
        myAgent.setGolemPosition(pos);
        myAgent.getMyMap().addNode(pos, MapAttribute.open);
        System.out.println(myAgent.getLocalName() + " - Wumpus confirmed at " + pos);
    }
    
    /**
     * Traite une demande d'aide pour bloquer un Wumpus
     */
    private void handleWumpusHelpRequest(ACLMessage msg, AID sender) {
        String wumpusLocation = msg.getContent();
        
        System.out.println(myAgent.getLocalName() + 
                         " - Help requested to block Wumpus at " + wumpusLocation);
        
        // Déclencher un changement d'état ou stocker l'info pour le FSM
        // myAgent.setWumpusTarget(wumpusLocation);
    }
    
    /**
     * Traite une demande de blocage coordonné
     */
    private void handleBlockRequest(ACLMessage msg, AID sender) {
        String nodeToBlock = msg.getContent();
        
        System.out.println(myAgent.getLocalName() + 
                         " - Block request at " + nodeToBlock + 
                         " from " + sender.getLocalName());
        
        // Logique de coordination pour bloquer un nœud ensemble
    }
    
    /**
     * Traite l'annonce qu'un agent est bloqué par un Wumpus
     */
    private void handleAgentBlocked(ACLMessage msg, AID sender) {
        String blockLocation = msg.getContent();
        
        System.out.println(myAgent.getLocalName() + 
                         " - " + sender.getLocalName() + 
                         " is BLOCKED at " + blockLocation);
        
        // éviter cette zone
        // myAgent.getMyMap().markOccuped(blockLocation) or something idk yet;
    }
    
    // ========== ENVOI DE MESSAGES ==========
    
    /**
     * Envoie le PING initial au démarrage (une seule fois)
     */
    private void sendInitialPing(AgentCommState state) {
    	myAgent.getCommService().sendPing();
        state.initialPingSent = true;
        state.lastPingTime = System.currentTimeMillis();
    }
    
    /**
     * Envoie un PING périodique
     */
    private void sendPeriodicPing(AgentCommState state) {
        long now = System.currentTimeMillis();
        
        if (now - state.lastPingTime >= PING_INTERVAL_MS) {
        	myAgent.getCommService().sendPing();
            //System.out.println("pinged");
            state.lastPingTime = now;
        }
    }
        
    /**
     * Détermine si on doit partager notre carte avec un agent
     */
    private boolean shouldShareMapWith(AID agent, AgentCommState state) {
        Long lastSent = state.lastMapSentTime.get(agent);
        
        if (lastSent == null) {
            return true; // Jamais envoyé
        }
        
        long timeSinceLastShare = System.currentTimeMillis() - lastSent;
        return timeSinceLastShare >= MAP_SHARE_COOLDOWN_MS;
    }
    
    /**
     * Récupère l'état de communication pour cet agent
     */
    private AgentCommState getState() {
        return commStates.get(myAgent.getLocalName());
    }
    
    /**
     * Affiche les statistiques
     */
    public void printStatistics() {
        AgentCommState state = getState();
        
        System.out.println("=== " + myAgent.getLocalName() + " - Communication Stats ===");
        System.out.println("Messages sent: " + state.messagesSent);
        System.out.println("Messages received: " + state.messagesReceived);
        System.out.println("Maps merged: " + state.mapsMerged);
        System.out.println("Wumpus alerts received: " + state.wumpusAlertsReceived);
        System.out.println("================================================");
    }
    
    /**
     * Nettoie l'état statique (utile pour les tests)
     */
    public static void resetAllStates() {
        commStates.clear();
    }
    
    @Override
    public int onEnd() {
        // Passer en chasse si golem connu ou stench détectée        
        return myAgent.getEtat();
    }
}