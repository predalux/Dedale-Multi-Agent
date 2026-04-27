package eu.su.mas.dedaleEtu.mas.knowledge;

import org.graphstream.graph.Node;
import java.util.*;

public class GolemAnalyzer {

    // Composante de fuite

    public static Set<String> computeEscapeComponent(
            ExtendedMapRepresentation map,
            String golemPos,
            Set<String> occupiedNodes) {

        if (occupiedNodes.contains(golemPos)) return Collections.emptySet();

        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(golemPos);

        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            if (!visited.add(nodeId)) continue;

            // Utilise getNeighbors() de ExtendedMapRepresentation
            map.getNeighbors(nodeId).stream()
                .filter(id -> !visited.contains(id) && !occupiedNodes.contains(id))
                .forEach(queue::add);
        }
        return visited;
    }

    // Points d'articulation — Tarjan itératif
    // Un point d'articulation est une zone sensible d'un "composant" du graphe (notemment les couleurs)

    public static Set<String> findArticulationPoints(
            ExtendedMapRepresentation map,
            Set<String> occupiedNodes) {

        // Noeuds actifs = tous les noeuds connus - occupés
        Set<String> activeNodes = new HashSet<>();
        map.getGraph().nodes()
            .map(Node::getId)
            .filter(id -> !occupiedNodes.contains(id))
            .forEach(activeNodes::add);

        Map<String, Integer> disc   = new HashMap<>();
        Map<String, Integer> low    = new HashMap<>();
        Map<String, String>  parent = new HashMap<>();
        Set<String>          ap     = new HashSet<>();
        int[] timer = {0};

        for (String start : activeNodes) {
            if (!disc.containsKey(start)) {
                tarjanDFS(start, map, activeNodes, disc, low, parent, ap, timer);
            }
        }
        return ap;
    }

    private record Frame(String node, Iterator<String> neighbors, int[] childCount) {}

    private static void tarjanDFS(
            String start,
            ExtendedMapRepresentation map,
            Set<String> activeNodes,
            Map<String, Integer> disc,
            Map<String, Integer> low,
            Map<String, String>  parent,
            Set<String>          ap,
            int[]                timer) {

        Deque<Frame> stack = new ArrayDeque<>();

        disc.put(start, timer[0]);
        low.put(start,  timer[0]);
        timer[0]++;
        stack.push(new Frame(start, activeNeighbors(map, start, activeNodes), new int[]{0}));

        while (!stack.isEmpty()) {
            Frame frame = stack.peek();
            String u = frame.node();

            if (frame.neighbors().hasNext()) {
                String v = frame.neighbors().next();

                if (!disc.containsKey(v)) {
                    frame.childCount()[0]++;
                    parent.put(v, u);
                    disc.put(v, timer[0]);
                    low.put(v,  timer[0]);
                    timer[0]++;
                    stack.push(new Frame(v, activeNeighbors(map, v, activeNodes), new int[]{0}));

                } else if (!v.equals(parent.getOrDefault(u, null))) {
                    low.merge(u, disc.get(v), Math::min);
                }

            } else {
                stack.pop();
                if (!stack.isEmpty()) {
                    String p = stack.peek().node();
                    low.merge(p, low.get(u), Math::min);

                    boolean isRoot = !parent.containsKey(p);
                    if ( isRoot && stack.peek().childCount()[0] > 1) ap.add(p);
                    if (!isRoot && low.get(u) >= disc.get(p))        ap.add(p);
                }
            }
        }
    }
    
    private static Iterator<String> activeNeighbors(
            ExtendedMapRepresentation map,
            String nodeId,
            Set<String> activeNodes) {

        return map.getNeighbors(nodeId).stream()
                  .filter(activeNodes::contains)
                  .iterator();
    }

    // Résultat

    public record GolemSituation(
        Set<String> escapeZone,
        boolean     isTrapped,
        Set<String> articulationPoints,
        Set<String> priorityTargets
    ) {}

    // à utiliser pour le hunt

    public static GolemSituation analyze(
            ExtendedMapRepresentation map,
            String                    golemPos,
            Set<String>               occupiedNodes) {

        Set<String> escapeZone = computeEscapeComponent(map, golemPos, occupiedNodes);
        boolean     trapped    = escapeZone.isEmpty();

        Set<String> apAll = findArticulationPoints(map, occupiedNodes);

        Set<String> priorityTargets = new HashSet<>(apAll);
        priorityTargets.retainAll(escapeZone);

        return new GolemSituation(escapeZone, trapped, apAll, priorityTargets);
    }

    // noeud le plus proche parmi un ensemble de cibles

    public static Optional<String> closestTarget(
            ExtendedMapRepresentation map,
            String myPosition,
            Set<String> targets) {

        return targets.stream()
            .filter(t -> map.getShortestPath(myPosition, t) != null)
            .min(Comparator.comparingInt(t -> map.getShortestPath(myPosition, t).size()));
    }
}