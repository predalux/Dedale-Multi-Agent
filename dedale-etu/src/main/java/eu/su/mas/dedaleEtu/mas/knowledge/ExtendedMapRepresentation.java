package eu.su.mas.dedaleEtu.mas.knowledge;

import java.util.Iterator;
import java.util.Set;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.graphstream.algorithm.ConnectedComponents;
import org.graphstream.algorithm.Dijkstra;
import org.graphstream.graph.Edge;
import org.graphstream.graph.EdgeRejectedException;
import org.graphstream.graph.ElementNotFoundException;
import org.graphstream.graph.Graph;
import org.graphstream.graph.IdAlreadyInUseException;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.ui.fx_viewer.FxViewPanel;
import org.graphstream.ui.fx_viewer.FxViewer;
import org.graphstream.ui.javafx.FxGraphRenderer;
import org.graphstream.ui.view.Viewer;
import org.graphstream.ui.view.Viewer.CloseFramePolicy;

import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Location;
import eu.su.mas.dedale.env.Observation;

import java.util.*;

public class ExtendedMapRepresentation extends MapRepresentation {
	
	private final Map<String, Long> lastVisitTime = new HashMap<>();


    public ExtendedMapRepresentation(String agentName) {
        super(agentName);
    }

    protected Graph getGraph() {
	    return this.g;
	}
    
    public void markVisited(String nodeId) {
        lastVisitTime.put(nodeId, System.currentTimeMillis());
    }
    
    
    public Node getNode(String idTo) {
	    return this.g.getNode(idTo);
	}
    
    public String getStalestNode(String currentNodeId) {
        return this.g.nodes()
            .map(Node::getId)
            .filter(id -> !id.equals(currentNodeId))
            .min(Comparator.comparingLong(id -> lastVisitTime.getOrDefault(id, 0L)))
            .orElse(null);
    }
    
    public Set<String> getAllNodeIds() {
        return this.g.nodes()
            .map(Node::getId)
            .collect(Collectors.toSet());
    }
    
    
    public String getRandomNode() {
		List<String> closedNodes = getClosedNodes();
		Random rand = new Random();
		return closedNodes.get(rand.nextInt(closedNodes.size()));
	}
    
    public List<String> getClosedNodes(){
		return this.g.nodes()
				.filter(x ->x .getAttribute("ui.class")==MapAttribute.closed.toString()) 
				.map(Node::getId)
				.collect(Collectors.toList());
	}

    
    // Permet de calculer les voisins du noeud donnÃ©e en argument
 	// Le noeud donnée en argument n'est pas dans la liste des voisins
 	public synchronized List<String> getNeighbors(String nodeId) {
 	    Node node = this.g.getNode(nodeId);
 	    if (node == null) {
 	        return new ArrayList<>();
 	    }

 	    return node.leavingEdges().map(e -> e.getOpposite(node).getId()).collect(Collectors.toList());
 	}
 	
 	public boolean isGolemSurrounded(String golemNodeId, List<String> agentPositions) {
 	    List<String> neighbors = getNeighbors(golemNodeId);
 	    if (neighbors.isEmpty()) return false;
 	    // Tous les voisins du golem doivent être occupés par un agent
 	    return agentPositions.containsAll(neighbors);
 	}

 	public List<String> getFreeNeighbors(String golemNodeId, List<String> agentPositions) {
 	    return getNeighbors(golemNodeId).stream()
 	        .filter(n -> !agentPositions.contains(n))
 	        .collect(Collectors.toList());
 	}

    // Un noeud est "fully mapped" si tous ses voisins sont dans le graphe
	// On ne peut pas le savoir directement, mais on peut vérifier
	// qu'il a été marqué closed (donc visité, voisins découverts)
 	public boolean isFullyMapped(String nodeId) {
 	    Node n = g.getNode(nodeId);
 	    if (n == null) return false;
 	    return MapAttribute.closed.toString().equals(n.getAttribute("ui.class"));
 	}
 	
 	
 	
 	// Permet de calculer le plus court chemin entre deux noeuds sans passer par des noeuds bloqués
 	public synchronized List<String> getShortestPathWithBlockedNodes(String idFrom, String idTo, List<String> blockedNodes) {
 	    Node start = g.getNode(idFrom);
 	    Node end = g.getNode(idTo);

 	    if (start == null || end == null) return null;

 	    // S'assurer que idFrom et idTo ne sont pas dans les noeuds bloqués
 	    List<String> effectiveBlocked = blockedNodes.stream()
 	        .filter(n -> !n.equals(idFrom) && !n.equals(idTo))
 	        .collect(Collectors.toList());

 	    Graph tempGraph = new SingleGraph("temp");
 	    g.nodes().forEach(n -> {
 	        if (!effectiveBlocked.contains(n.getId())) {
 	            Node nn = tempGraph.addNode(n.getId());
 	            nn.setAttribute("ui.class", n.getAttribute("ui.class"));
 	        }
 	    });
 	    g.edges().forEach(e -> {
 	        Node src = e.getSourceNode();
 	        Node tgt = e.getTargetNode();
 	        if (!effectiveBlocked.contains(src.getId()) && !effectiveBlocked.contains(tgt.getId())) {
 	            try {
 	                tempGraph.addEdge(e.getId(), src.getId(), tgt.getId());
 	            } catch (Exception ex) {}
 	        }
 	    });

 	    Node tempStart = tempGraph.getNode(idFrom);
 	    Node tempEnd = tempGraph.getNode(idTo);

 	    // Vérification explicite avant Dijkstra
 	    if (tempStart == null || tempEnd == null) return null;

 	    Dijkstra dijkstra = new Dijkstra();
 	    dijkstra.init(tempGraph);
 	    dijkstra.setSource(tempStart);
 	    dijkstra.compute();

 	    List<Node> pathNodes = dijkstra.getPath(tempEnd).getNodePath();
 	    dijkstra.clear();

 	    if (pathNodes == null || pathNodes.isEmpty()) return null;

 	    List<String> path = new ArrayList<>();
 	    for (Node n : pathNodes) path.add(n.getId());
 	    path.remove(0);
 	    return path;
 	}

    
}