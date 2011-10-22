/*
 * 
 */
package muscle.core.ident;

import jade.core.AID;

/**
 * @author Joris Borgdorff
 */
public interface JadeIdentifier extends Identifier {
	public AID getAID();
	public void resolve(AID aid, Location loc);
}
