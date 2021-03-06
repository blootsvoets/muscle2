/*
* Copyright 2008, 2009 Complex Automata Simulation Technique (COAST) consortium
* Copyright 2010-2013 Multiscale Applications on European e-Infrastructures (MAPPER) project
*
* GNU Lesser General Public License
* 
* This file is part of MUSCLE (Multiscale Coupling Library and Environment).
* 
* MUSCLE is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
* 
* MUSCLE is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU Lesser General Public License for more details.
* 
* You should have received a copy of the GNU Lesser General Public License
* along with MUSCLE.  If not, see <http://www.gnu.org/licenses/>.
*/
/*
 * 
 */
package muscle.manager;

import muscle.util.serialization.Protocol;
import muscle.util.serialization.ProtocolSerializer;

/**
 *  Operation codes of the SimulationManagerProtocol.
 * 
 * These operation codes should be used by both client and server. The protocol is as follows: a client requests
 * an operation. This operation number that will be executed is repeated by the server, which then executes it, and then returns a boolean for success. The operations are the following
 * <dl>
 *	<dd>LOCATE</dd>
 *	<dt>For clients to find other clients. Client sends name of ID it wants to locate. SimulationManager tries to locate given ID, and returns
 *              a boolean for success. If it succeeds, this is followed by the TcpLocation of the found ID. This is a
 *              <em>blocking</em> call.</dt>
 *      <dd>REGISTER</dd>
 *      <dt>For clients to register themselves. Client sends the name of its ID, and then its TcpLocation. SimulationManager tries to add the ID; it
 *             will return a false boolean code if it already existed, or true if it was not entered yet.</dt>
 *      <dd>PROPAGATE</dd>
 *      <dt>Indicates to the server that a client is ready to accept data transactions. Only succeeds if there was
 *				a successful registration. The client only sends an ID, the SimulationManager returns a boolean for success.</dt>
 *      <dd>DEREGISTER</dd>
 *     <dt>For clients to deregister themselves. Client sends the name of its ID. SimulationManager tries to remove this ID, and return a boolean
 *             for success.</dt>
 *      <dd>UNSUPPORTED</dd>
 *      <dt>If an operation code is sent that the server does not understand, it will respond with UNSUPPORTED.</dt>
 * </dl>
 * 
 * @author Joris Borgdorff
 */
public enum SimulationManagerProtocol implements Protocol {
	LOCATE(0), REGISTER(1), PROPAGATE(2), DEREGISTER(3),
	UNSUPPORTED(4), WILL_ACTIVATE(5), CLOSE(-1), ERROR(-2), MAGIC_NUMBER(2391029), MAGIC_NUMBER_KEEP_ALIVE(291934),
	MANAGER_LOCATION(6);
	
	public final static ProtocolSerializer<SimulationManagerProtocol> handler = new ProtocolSerializer<SimulationManagerProtocol>(values());

	public final int num;
	SimulationManagerProtocol(int n) { num = n; }
	@Override
	public int intValue() { return num; }
}
