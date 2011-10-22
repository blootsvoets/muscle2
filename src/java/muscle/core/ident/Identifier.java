/*
 * 
 */
package muscle.core.ident;

import java.io.Serializable;

/**
 * @author jborgdo1
 */
public interface Identifier extends Comparable<Identifier>, Serializable {
	public String getName();
	public IDType getType();
	public boolean isResolved();
	public void unResolve();
	public Location getLocation();
	public boolean identifies(Identifiable ident);
}
