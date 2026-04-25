package eu.su.mas.dedaleEtu.mas.behaviours;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import dataStructures.serializableGraph.SerializableSimpleGraph;
import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Location;
import eu.su.mas.dedale.env.Observation;
import eu.su.mas.dedale.env.gs.GsLocation;

import eu.su.mas.dedale.mas.AbstractDedaleAgent;

import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation.MapAttribute;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation;
import eu.su.mas.dedaleEtu.mas.agents.dummies.DrippedAgent;
import eu.su.mas.dedaleEtu.mas.behaviours.ShareMapBehaviour;
import jade.core.AID;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

public class MovementDripBehaviour extends OneShotBehaviour {
    private boolean finished = false;
    private MapRepresentation myMap;
    private DrippedAgent myAgent;

    public MovementDripBehaviour(DrippedAgent agent) {
        super(agent);
        this.myAgent = agent;
        this.myMap = myAgent.getMyMap();
    }

    @Override
    public void action() {
    	
    	if (myAgent.getMyMap() == null) {
            myAgent.initMapRepresentation();
            System.out.println("MOVE EEEEEE");
    	}
        Location myPosition = myAgent.getCurrentPosition();
        if (myPosition != null) {
            String nextNodeId = null;

            // Sélectionner le prochain noeud à atteindre
            if (!myAgent.getMyMap().hasOpenNode()) {
                finished = true;
                return;
            }

            nextNodeId = myAgent.getMyMap().getShortestPathToClosestOpenNode(myPosition.getLocationId()).get(0);
            myAgent.moveTo(new GsLocation(nextNodeId));
        }
    }

}