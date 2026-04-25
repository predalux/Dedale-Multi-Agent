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

    public ExtendedMapRepresentation(String agentName) {
        super(agentName);
    }

    protected Graph getGraph() {
	    return this.g;
	}
    
    
    public Node getNode(String idTo) {
	    return this.g.getNode(idTo);
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
 	
 	
 	
 	// Permet de calculer le plus court chemin entre deux noeuds sans passer par des noeuds bloqués
 	public synchronized List<String> getShortestPathWithBlockedNodes(String idFrom, String idTo, List<String> blockedNodes) {
 	    Node start = g.getNode(idFrom);
 	    Node end = g.getNode(idTo);

 	    if (start == null || end == null) return null;

 	    // CrÃ©er un graphe temporaire filtrÃ©
 	    Graph tempGraph = new SingleGraph("temp");
 	    // Copier les noeuds
 	    g.nodes().forEach(n -> {
 	        if (!blockedNodes.contains(n.getId())) {
 	            Node nn = tempGraph.addNode(n.getId());
 	            nn.setAttribute("ui.class", n.getAttribute("ui.class"));
 	        }
 	    });
 	    // Copier les aretes entre noeuds autorisÃ©s
 	    g.edges().forEach(e -> {
 	        Node src = e.getSourceNode();
 	        Node tgt = e.getTargetNode();
 	        if (!blockedNodes.contains(src.getId()) && !blockedNodes.contains(tgt.getId())) {
 	        	// Ignorer les doublons ou erreurs
 	            try {
 	                tempGraph.addEdge(e.getId(), src.getId(), tgt.getId());
 	            } catch (Exception ex) {
 	                
 	            }
 	        }
 	    });

 	    // Calcul du plus court chemin
 	    Dijkstra dijkstra = new Dijkstra();
 	    dijkstra.init(tempGraph);
 	    dijkstra.setSource(tempGraph.getNode(idFrom));
 	    dijkstra.compute();
 	    List<Node> pathNodes = dijkstra.getPath(tempGraph.getNode(idTo)).getNodePath();
 	    dijkstra.clear();

 	    if (pathNodes == null || pathNodes.isEmpty()) return null;

 	    // Transformer en liste d'IDs et enlever le noeud de départ
 	    List<String> path = new ArrayList<>();
 	    for (Node n : pathNodes) path.add(n.getId());
 	    path.remove(0);
 	    return path;
 	}



    
}