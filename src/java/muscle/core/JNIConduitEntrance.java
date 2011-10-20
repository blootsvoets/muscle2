/*
Copyright 2008,2009 Complex Automata Simulation Technique (COAST) consortium

GNU Lesser General Public License

This file is part of MUSCLE (Multiscale Coupling Library and Environment).

    MUSCLE is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    MUSCLE is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with MUSCLE.  If not, see <http://www.gnu.org/licenses/>.
*/

package muscle.core;



import muscle.core.kernel.RawKernel;
import utilities.jni.JNIMethod;
import javatool.ArraysTool;
import utilities.Transmutable;


/**
entrance which can directly be called from native code<br>
C for conduit type, R for raw jni type
@author Jan Hegewald
*/
public class JNIConduitEntrance<R,C extends java.io.Serializable> extends ConduitEntrance<C> {

	private Class<R> jniClass;
	private Transmutable<R,C> transmuter;


	//
	public JNIConduitEntrance(Transmutable<R,C> newTransmuter, Class<R> newJNIClass, PortalID newPortalID, RawKernel newOwnerAgent, int newRate, DataTemplate newDataTemplate, EntranceDependency ... newDependencies) {
		super(newPortalID, newOwnerAgent, newRate, newDataTemplate, newDependencies);
		transmuter = newTransmuter;
		jniClass = newJNIClass;
	}


	//
	public JNIMethod toJavaJNIMethod() {
		return new JNIMethod(this, "toJava", ArraysTool.asArray(Object.class), ArraysTool.asArray(jniClass), null, null);
	}


	//
	public void toJava(R rawData) {
		
		C data = transmuter.transmute(rawData);
		send(data);
	}
}
