package eu.su.mas.dedaleEtu.mas.agents.dummies;

import java.util.ArrayList;
import java.util.List;

import eu.su.mas.dedale.mas.agent.behaviours.platformManagment.*;
import eu.su.mas.dedaleEtu.mas.behaviours.CommunicationDripBehaviour;
import eu.su.mas.dedaleEtu.mas.behaviours.ExplorationDripBehaviour;
import eu.su.mas.dedaleEtu.mas.behaviours.MovementDripBehaviour;
import eu.su.mas.dedaleEtu.mas.behaviours.HuntDripBehaviour;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.FSMBehaviour;


public class DrippedAgent extends AbstractAgent {

	private static final long serialVersionUID = -6431752665590433727L;
	
	private static final String EXPLO = "EXPLO";
	private static final String COMM = "COMM";
	private static final String HUNT = "HUNT";
	private FSMBehaviour fsm;

	
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
		receivers.add("Drip1");
		receivers.add("Drip2");
		//receivers.add("Drip3");
		//receivers.add("Drip4");
		//receivers.add("Drip5");
		
		// gère envoie des messages
		this.initCommService();
		
		/************************************************
		 * 
		 * ADD the initial behaviours of the Agent here
		 * 
		 ************************************************/
		
		fsm.registerFirstState(new CommunicationDripBehaviour(this,receivers), EXPLO);
		fsm.registerState(new ExplorationDripBehaviour(this), COMM);
		//fsm.registerState(new MovementDripBehaviour(this), C);
		fsm.registerState(new HuntDripBehaviour(this), HUNT);

		fsm.registerTransition (EXPLO,COMM, 0);//Default
		fsm.registerTransition (COMM,EXPLO, 0);//Default
		//fsm.registerDefaultTransition (C,A);//Default
		
		// La dÃ©tection de stench dÃ©clenche la chasse
		fsm.registerTransition(EXPLO, HUNT, 1);
		fsm.registerTransition(COMM, HUNT, 1);

		// Comportement de la chasse
		fsm.registerTransition(HUNT, HUNT, 1);   // continuer chasse
		fsm.registerTransition(HUNT, EXPLO, 0);   // fin chasse
		fsm.registerTransition(HUNT, COMM, 2);   // continuer chasse
		
		/***
		 * MANDATORY TO ALLOW YOUR AGENT TO BE DEPLOYED CORRECTLY
		 */
		List<Behaviour> lb = new ArrayList<Behaviour>();
	    lb.add(fsm);
	    
        // Ajout du behaviour de dÃ©marrage.
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