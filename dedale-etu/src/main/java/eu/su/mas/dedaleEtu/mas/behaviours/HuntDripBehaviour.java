package eu.su.mas.dedaleEtu.mas.behaviours;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Location;
import eu.su.mas.dedale.env.Observation;
import eu.su.mas.dedale.env.gs.GsLocation;
import eu.su.mas.dedaleEtu.mas.agents.dummies.DrippedAgent;
import jade.core.behaviours.SimpleBehaviour;

import java.io.IOException;
import java.util.Iterator;

import dataStructures.serializableGraph.SerializableSimpleGraph;

import eu.su.mas.dedale.mas.AbstractDedaleAgent;

import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation.MapAttribute;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation;
import eu.su.mas.dedaleEtu.mas.behaviours.ShareMapBehaviour;
import jade.core.AID;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

public class HuntDripBehaviour extends OneShotBehaviour {

    private static final long serialVersionUID = 1L;
    private DrippedAgent myAgent;
    private int exitValue = 0;
    

    public HuntDripBehaviour(DrippedAgent agent) {
        super(agent);
        this.myAgent = agent;
    }

    @Override
    public void action() {
    	
    	// par dÃ©faut on reste en chasse
        exitValue = 1;
        //System.out.println(this.myAgent.getLocalName() +"-----chasse------");
        

        Location myPosition = myAgent.getCurrentPosition();
        if (myPosition == null) return;

        List<Couple<Location, List<Couple<Observation, String>>>> lobs = myAgent.observe();
        
        //System.out.println(this.myAgent.getLocalName() +"-----observation------" + lobs);

        boolean stenchDetected = false;
        myAgent.setStench(false);
        // Normalement ca permet de suivre le Golem si il s'est déplacé, mais je sens que ca va juste créer des erreurs inutiles
        String AnciengolemPos = null;
        
        String golemPos = myAgent.getGolemPosition();
        //myAgent.setGolemPosition(null);
        //String golemPos = null;
        

        List<String> VoisinsLibreStench = new ArrayList<>();
        List<String> VoisinsLibre = new ArrayList<>();

        //  Analyse des observations
        for (Couple<Location, List<Couple<Observation, String>>> obs : lobs) {

            String node = obs.getLeft().getLocationId();
            boolean hasAgent = false;
            boolean hasStench = false;
            //boolean AbsentGolem = true;
            
            

            for (Couple<Observation, String> detail : obs.getRight()) {
            	
            	// Permet de se dÃ©placer Ã  l'ancienne position du golem si cette position n'est pas occupÃ©
            	// ProblÃ¨me : il faudrait s'assurer qu'il n'y ait pas de Wumpus au alentour sinon, on peut potentielement s'Ã©loigner du Golem
            	// Solution : ajouter se dÃ©placer aprÃ¨s avoir vu toutes les cases
            	if(node.equals(golemPos) && (detail.getLeft() != Observation.AGENTNAME))
                {
                	AnciengolemPos = node;
                }

                if (detail.getLeft() == Observation.STENCH) {
                    hasStench = true;
                    stenchDetected = true;
                    myAgent.setStench(true);
                }
                // Pour ne pas que les agents reviennent en arriÃ¨re lorsqu'il voit un golem
                /*
                if(node.equals(golemPos) && (detail.getLeft() != Observation.AGENTNAME || !detail.getRight().equals("Wumpus")))
                {
                	golemPos = null;
                	AbsentGolem = true;
                	myAgent.setGolemPosition(null);
                }
                */

                if (detail.getLeft() == Observation.AGENTNAME) {
                    hasAgent = true;

                    if (detail.getRight().equals("Wumpus")) {
                        golemPos = node;
                        myAgent.setGolemPosition(node);
                        exitValue = 0;
                        myAgent.getCommService().broadcastWumpusPosition(golemPos);

                        //System.out.println(myAgent.getLocalName() + " --- je vais le tueeeeeer --- ");
                        return;
                    }
                }
            }
            
            // Permet de construire sa map mÃªme en chasse
            Location accessibleNode = obs.getLeft();
            boolean isNewNode = myAgent.getMyMap().addNewNode(accessibleNode.getLocationId());
            if (!accessibleNode.getLocationId().equals(myPosition.getLocationId())) {
            	myAgent.getMyMap().addEdge(myPosition.getLocationId(), accessibleNode.getLocationId());
                if (node == null && isNewNode) {
                    node = accessibleNode.getLocationId();
                }
            }
            /*
            if(AbsentGolem)
            {
            	randomMove(lobs);
            	
            }
            */
            //golemPos = null;

            // case libre intÃ©ressante
            if (!hasAgent) {
                
            	VoisinsLibre.add(node);
                if(hasStench)
                {
                	
                	VoisinsLibreStench.add(node);
                }
                
            }
        }
        
        // Si on dÃ©tecte que le golem s'était échappé et la case est libres, alors on le suivre
        if(AnciengolemPos!=null && !moveTo(AnciengolemPos))
        {
        	//System.out.println("!!!!!" + myAgent.getLocalName() + " POURQUOIIIIII, la case Ã©tait libre");
        	//randomMove(VoisinsLibre);
        }

        //  Si on ne sent plus rien alors on termine la chasse
        if (!stenchDetected && golemPos == null) {
            //System.out.println(myAgent.getLocalName() + " perd la trace du golem");
        	
        	// Si on a déjà  fini d'explorer alors on bouge alÃ©atoirement
        	// Dans la chasse si on ne detecte plus de stench et qu'on n'a pas la position du golem
        	// Alors on retourne dans explo. Donc si on ne bouge pas -> on boucle 
        	if(myAgent.isExploFinished())
        	{
        		randomMove(VoisinsLibre);
        		exitValue = 1;
        	    System.out.println("!!!!!" + myAgent.getLocalName() + " pas besoin d'aller en explo");
        		return;
        	}
        	
            myAgent.setGolemPosition(null);
            exitValue = 0;
            return;
        }
        
        //  On se rapproche du golem
        if (golemPos != null) {
        	
        	// Empeche de faire la mÃ©thode 'getShortestPath' avec des noeuds qui n'existe pas encore dans le graphe
        	if(myAgent.getMyMap().getNode(golemPos) == null) {
        	    System.out.println("!!!!!" + myAgent.getLocalName() + " veux se dÃ©placer vers le noeud" + golemPos +" qui n'existe pas dans son graphe");
        	    randomMove(VoisinsLibre);
        	    return;
        	}
        	
            List<String> path = myAgent.getMyMap().getShortestPath(myPosition.getLocationId(), golemPos);
            
            if(path == null || path.isEmpty())
            {
        	    System.out.println("!!!!!" + myAgent.getLocalName() + " n'a trouvÃ© aucun chemin (triste) vers la position " + golemPos + " avec cette lst d'obs : " + lobs + " et ces voisins libres " + VoisinsLibre);
        	    
        	    randomMove(VoisinsLibre);
        	    return;
            }
            
            
            // L'agent reste sur place
            // Si on Ã©tait Ã  cÃ´tÃ© du Golem ou si on est Ã  l'ancienne position du Golem
            if(path !=null && path.size()<=1)
            {
            	exitValue = 2;
        	    //System.out.println("!!!!!" + myAgent.getLocalName() + " condition inutile? pos golem : " + golemPos+ "; avec le chemin : " + path);
            	return;
            }
            
            if (path != null && !path.isEmpty()) {

                String next = path.get(0);

                //System.out.println(myAgent.getLocalName()+ " se rapproche du golem vers " + next + " de chemin " + path);

                if(!moveTo(next))
                {
                	randomMove(VoisinsLibre);
                }
                return;
            }
        }

        // Encerclement
        if (!VoisinsLibre.isEmpty()) {
        	// Enfin, je crois avoir rÃ©solu l'erreur des agents qui tourne en rond (ouaiiiiiiis!!!)
        	Collections.shuffle(VoisinsLibre);
            for (String target : VoisinsLibre) {

                // Ã©vite de revenir en arriÃ¨re
                if (target.equals(myAgent.getLastPosition()) || target.equals(myAgent.getCurrentPosition().getLocationId())) continue;

                if (moveTo(target)) {
                	//System.out.println(myAgent.getLocalName()+ " mon voisin Matimbo est un drÃ´le de voisin... " + target);
                    return;
                }
            }
        }

        

        //  Si on a rien detecter alors on fait de la marche alÃ©atoire
        randomMove(VoisinsLibre);
        //System.out.println(myAgent.getLocalName()+" aucun mouv possible (pas assez de drip)");
    }



    private boolean moveTo(String node) {
    	String AvantPos = myAgent.getCurrentPosition().getLocationId();
        if(myAgent.moveTo(new GsLocation(node)))
        {
        	// Faut croire qu'il y a des erreurs partout
        	if(!AvantPos.equals(myAgent.getCurrentPosition().getLocationId()))
        	{
        		//System.out.println(this.myAgent.getLocalName() + " opÃ©ration rÃ©ussie !!! o7 de " + AvantPos+ " Ã  " + myAgent.getCurrentPosition().getLocationId());
        		myAgent.setLastPosition(AvantPos);
        		/*
            	System.out.println(this.myAgent.getLocalName() +"::::::: observation------ " + myAgent.observe());
            	System.out.println(this.myAgent.getLocalName() +":::::: je suis actuellement en poooooos " + myAgent.getCurrentPosition().getLocationId());
            	System.out.println(myAgent.getLocalName()+" :::Voici les voisins du noeud prÃ©cÃ©dent " + myAgent.getMyMap().getNeighbors(myAgent.getCurrentPosition().getLocationId()));
            	*/
        		return true;
        	}
        	
    		//System.out.println(this.myAgent.getLocalName() + " je sais pas pourquoi, mais apparamment on reste sur place");
    		/*
        	System.out.println(this.myAgent.getLocalName() + "::::::::: COMMENT CA SE FAIT QUE J'AI UNE ERREUR (waaaaaaaaaa!!!) ");
        	System.out.println(this.myAgent.getLocalName() +":::::: j'Ã©tais en pooooooos " + AvantPos);
        	System.out.println(this.myAgent.getLocalName() +":::::: je suis actuellement en poooooos " + myAgent.getCurrentPosition().getLocationId());
        	*/
        	return false;
        }
        return false;
    }



    private boolean randomMove(List<String> lobs) {
    	Collections.shuffle(lobs);
    	String AvantPos = myAgent.getCurrentPosition().getLocationId();
    	
        for (String candidate : lobs) {

            // AJOUTER UNE CONDITION POUR QUE ON NE REVIENNE PAS VERS UNE CASE DEJA EMPRUNTE
        	// changement de plan, dans des couloirs Ã©troits, l'agent reste bloquÃ© si il ne peux pas revenir en arriÃ¨re
            //if (!candidate.equals(myAgent.getCurrentPosition().getLocationId()) && !candidate.equals(myAgent.getlastPosition())) {
        	if (!candidate.equals(myAgent.getCurrentPosition().getLocationId())) {

                if (myAgent.moveTo(new GsLocation(candidate))) {
                	myAgent.setLastPosition(AvantPos);
            	    //System.out.println("!!!!!" + myAgent.getLocalName() + " randomMoOoOoOoOoOove");
                    return true;
                }
            }
        }
        
        return false;
    }
    
    // Pour continuer ou terminer la chasse
    @Override
    public int onEnd() {
    	/*if (exitValue == 0)
    	{
    		System.out.println(myAgent.getLocalName() + " --- de chasse Ã  explo --- ");
    	}
    	if (exitValue == 2)
    	{
    		System.out.println(myAgent.getLocalName() + " --- de chasse Ã  communication --- ");
    	}
    	if (exitValue == 1)
    	{
    		System.out.println(myAgent.getLocalName() + " --- de chasse Ã  chasse --- ");
    	}
    	*/
        return exitValue;
    }

}