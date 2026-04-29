package eu.su.mas.dedaleEtu.mas.agents.dummies;

import java.util.ArrayList;
import java.util.List;

import eu.su.mas.dedale.mas.agent.behaviours.platformManagment.*;
import eu.su.mas.dedaleEtu.mas.behaviours.CommunicationDripBehaviour;
import eu.su.mas.dedaleEtu.mas.behaviours.ExplorationDripBehaviour;
import eu.su.mas.dedaleEtu.mas.behaviours.MovementDripBehaviour;
import eu.su.mas.dedaleEtu.mas.behaviours.WallDripBehaviour;
import eu.su.mas.dedaleEtu.mas.behaviours.HuntDripBehaviour;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.FSMBehaviour;


public class DrippedAgent extends AbstractAgent {

	private static final long serialVersionUID = -6431752665590433727L;
	
	private static final String EXPLO = "EXPLO";
	private static final String COMM = "COMM";
	private static final String HUNT = "HUNT";
	private static final String WALL = "WALL";
	private FSMBehaviour fsm;
	
	
	// rajouter le cas où le golem à bouger (pos = golem pos) on repart en explo.
    // explo doit pouvoir explorer meme quand graphe totalement finito
    // tester la chasse avec un golem qui bouge bruh
    // implementer la propagation de l'annonce
    // coalition
    // determiner comment bien encercler
    // voir pour communiquer lors du deadlock
	
	/**
	 * This method is automatically called when "agent".start() is executed.
	 * Consider that Agent is launched for the first time. 
	 * 			1) set the agent attributes 
	 *	 		2) add the behaviours
	 *          
	 */
	protected void setup(){

		super.setup();
		
		

		this.fsm = new FSMBehaviour(this);	
		
	    // liste des destinataires
		List<String> receivers = new ArrayList<String>();
		this.addAgentList("Drip1");
		this.addAgentList("Drip2");
		this.addAgentList("Drip3");
		this.addAgentList("Drip4");
		this.addAgentList("Drip5");

		// gère envoie des messages
		this.initCommService();
		
		/************************************************
		 * 
		 * ADD the initial behaviours of the Agent here
		 * 
		 * CODE POUR LES TRANSITIONs :
		 * 0 - explo
		 * 1 - chasse
		 * 2 - wall
		 * Com n'est pas associé, chaque behavior cycle sur la comm puis lui meme
		 ************************************************/
		
		fsm.registerFirstState(new ExplorationDripBehaviour(this), EXPLO);
		
		fsm.registerState(new CommunicationDripBehaviour(this), COMM);
		fsm.registerState(new HuntDripBehaviour(this), HUNT);
		fsm.registerState(new WallDripBehaviour(this),WALL);

		// transition EXPLO
		fsm.registerTransition(EXPLO, COMM, 0); // continue explo
		fsm.registerTransition(EXPLO, HUNT, 1); // golem detecter

		// transition COMM (en fonction de l'etat de l'agent
		fsm.registerTransition(COMM,EXPLO, 0);
		fsm.registerTransition(COMM, HUNT, 1);
		fsm.registerTransition(COMM, WALL, 2);

		// transition HUNT
		fsm.registerTransition(HUNT, EXPLO, 0);  // retourne explo
		fsm.registerTransition(HUNT, COMM, 1);   // continuer chasse
		fsm.registerTransition(HUNT, WALL, 2);   // On bloque
		
		// transition WALL
		//fsm.registerTransition(WALL, EXPLO, 0);   // golem perdu
		fsm.registerTransition(WALL, HUNT, 1);   // golem s'échappe
		fsm.registerTransition(WALL, COMM, 2);   // On bloque
		
		/***
		 * MANDATORY TO ALLOW YOUR AGENT TO BE DEPLOYED CORRECTLY
		 */
		List<Behaviour> lb = new ArrayList<Behaviour>();
	    lb.add(fsm);
	    
        // Ajout du behaviour de dÃ©marrage.
	    
	    this.doWait(2000);
		addBehaviour(new StartMyBehaviours(this,lb));
		
		System.out.println("the agent"+this.getLocalName()+ " is started");

	}
	
	/**
	 * This method is automatically called after doDelete()
	 */
	protected void takeDown(){
		super.takeDown();
	}

	protected void beforeMove(){
		super.beforeMove();
		//System.out.println("I migrate");
	}

	protected void afterMove(){
		super.afterMove();
		//System.out.println("I migrated");
	}

	
	
}