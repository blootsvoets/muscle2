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
package muscle.core.standalone;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import muscle.core.ConduitEntrance;
import muscle.core.ConduitEntranceController;
import muscle.core.ConduitExit;
import muscle.core.ConduitExitController;
import muscle.core.CxADescription;
import muscle.core.kernel.CAController;
import muscle.core.model.Distance;
import muscle.core.model.Observation;
import muscle.core.model.Timestamp;
import muscle.exception.MUSCLERuntimeException;
import muscle.util.data.SerializableData;
import muscle.util.data.SerializableDatatype;

/** A kernel used with a native command.
   *
   * It builds the command line arguments from the parameters debugger, command and args. It keeps in contact
   * with the started command using a TCP IP connection that is controlled by NativeGateway.
   */
public class NativeKernel extends CAController implements NativeController {

	private final static Logger logger = Logger.getLogger(NativeKernel.class.toString());
	private final static String TMPFILE = System.getProperty("muscle.native.tmpfile");
	private final static boolean NATIVE_RECONNECT = System.getProperty("muscle.native.reconnect") == null ? false : Boolean.parseBoolean(System.getProperty("muscle.native.reconnect"));
	private final Object childLock = new Object();
	private SerializableDatatype type;
	private Thread processThread;

	private boolean isDone;
	
	/**
	 * Default serial version UID
	 */
	private static final long serialVersionUID = 1L;
	/*
	@SuppressWarnings("rawtypes")
	protected Map<String, ConduitEntrance> entrances =  new HashMap<String, ConduitEntrance>();
	@SuppressWarnings("rawtypes")
	protected Map<String, ConduitExit> exits = new HashMap<String, ConduitExit>();
	*/
	
	public NativeKernel() {
		super();
		isDone = false;
		type = SerializableDatatype.NULL;
	}
	
	@SuppressWarnings({"unchecked", "rawtypes"})
	@Override
	public synchronized void send(String entranceName, SerializableData data) {
		ConduitEntranceController ec = entrances.get(entranceName);
		ConduitEntrance entrance;
		if (ec == null || (entrance = ec.getEntrance()) == null) {
			throw new MUSCLERuntimeException("Unknown entrance: '" + entranceName + "' in " + getLocalName() + " (valid entrances are " + entrances.keySet() + ")");
		}
		Distance dt = getScale().getDt();
		@SuppressWarnings("unchecked")
		Observation obs = new Observation(data.getValue(), ec.getSITime(), ec.getSITime().add(dt), true);
		
		entrance.send(obs);
	}
	
	@SuppressWarnings("rawtypes")
	@Override
	public synchronized SerializableData receive(String exitName) {
		ConduitExitController ec = exits.get(exitName);
		ConduitExit exit;
		if (ec == null || (exit = ec.getExit()) == null) {
			throw new MUSCLERuntimeException("Unknown conduit exit: '" + exitName + "' in " + getLocalName() + " (valid exits are " + exits.keySet() + ")");
		}
		
		if (exit.hasNext()) {
			Observation obs = exit.receiveObservation();
			Timestamp time = obs.getTimestamp();
			for (ConduitEntranceController cec : this.entrances.values()) {
				if (time.compareTo(cec.getSITime()) > 0) {
					cec.resetTime(time);
				}
			}
			Serializable data = obs.getData();
			SerializableData sdata;
			if (data == null) {
				logger.log(Level.WARNING, "Null values, from exit {0}, are not supported in native code.", exit);
				sdata = SerializableData.valueOf(null, SerializableDatatype.NULL);
			} else if (type.getDataClass() == null || !type.getDataClass().isInstance(data)) {
				sdata = SerializableData.valueOf(data);
				type = sdata.getType();
			} else {
				sdata = SerializableData.valueOf(data, type);
			}
			if (type == SerializableDatatype.JAVA_BYTE_OBJECT) {
				logger.log(Level.WARNING, "Received Java object {0}, from exit {1}, not supported in native code.", new Object[] {data, exit});
			}
			return sdata;
		} else {
			return null;
		}
	}
	
	@SuppressWarnings("rawtypes")
	@Override
	public synchronized boolean hasNext(String exitName) {
		ConduitExitController ec = exits.get(exitName);
		ConduitExit exit;
		if (ec == null || (exit = ec.getExit()) == null) {
			throw new MUSCLERuntimeException("Unknown conduit exit: '" + exitName + "' in " + getLocalName() + " (valid exits are " + exits.keySet() + ")");
		}
		
		return exit.hasNext();
	}

	@Override
	public synchronized String getKernelName() {
		return getLocalName();
	}
	
	@Override
	public synchronized String getProperty(String name) {
		return super.getProperty(name);
	}
	
	@Override
	public synchronized String getProperties() {
		return CxADescription.ONLY.getLegacyProperties();
	}
	
	@Override
	public synchronized String getTmpPath() {
		return super.getTmpPath();
	}
	
	@Override
	public synchronized void isFinished() {
		isDone = true;
		notify();
	}
	
	/**
	 * Adds the command to execute to the given list.
	 * @param command an empty list that will be filled with an input list for a ProcessBuilder
	 * @throws IllegalArgumentException if the "command" property is not set for the current kernel
	 */
	protected void buildCommand(List<String> command) {
		if (hasInstanceProperty("debugger")) {
			command.add(getProperty("debugger"));
		}
		
		if (hasInstanceProperty("command")) {
			command.add(getProperty("command"));
		} else {
			throw new IllegalArgumentException("Missing property: " + getLocalName() + ":command" );
		}
		
		if (hasInstanceProperty("args")) {
			String args[] = getProperty("args").split(" ");
			command.addAll(Arrays.asList(args));
		}
	}
	
	protected void runCommand(String host, String port) throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder();
			
		buildCommand(pb.command());

		pb.environment().put("MUSCLE_GATEWAY_PORT", port);
		pb.environment().put("MUSCLE_GATEWAY_HOST", host);
		pb.environment().put("MUSCLE_GATEWAY_RECONNECT", NATIVE_RECONNECT ? "1" : "0");
		
		getLogger().log(Level.INFO, "Spawning standalone kernel: {0}", pb.command());
		getLogger().log(Level.FINE, "Contact information: {0}", port);	
		
		if (getLogger().isLoggable(Level.FINEST)) {
			for (String envName : pb.environment().keySet()) {
				getLogger().log(Level.FINEST, "Env: {0}={1}", new Object[]{ envName, pb.environment().get(envName)});	
			}
		}
		
		Process child = pb.start();

		StreamRipper stdoutR = new StreamRipper("stdout-reader-"+getLocalName(), System.out, child.getInputStream());
		StreamRipper stderrR = new StreamRipper("stderr-reader-"+getLocalName(), System.err, child.getErrorStream());

		stdoutR.start();
		stderrR.start();

		try {
			synchronized (childLock) {
				processThread = Thread.currentThread();
			}
			int exitCode = child.waitFor();

			if (exitCode == 0) {
				getLogger().log(Level.INFO, "Command {0} finished.", pb.command());
			} else {
				getLogger().log(Level.WARNING, "Command {0} failed with exit code {1}.", new Object[]{pb.command(), exitCode});
			}
		} catch (InterruptedException ex) {
			child.destroy();
		} finally {
			synchronized (childLock) {
				processThread = null;
			}
		}

	}
	
	protected void writeContactInformation(String host, String port) throws InterruptedException, IOException {
		FileWriter fw = new FileWriter(TMPFILE);
		StringBuilder sb = new StringBuilder(24);
		sb.append(host).append(':').append(port);
		fw.append(sb);
		fw.close();
		
		logger.log(Level.FINE, "''{0}'' wrote contact information {1}:{2} for executable to file {3}", new Object[]{getLocalName(), host, port, TMPFILE});

		synchronized (this) {
			while (!isDone) {
				wait();
			}
		}
	}

	@Override
	protected void execute() {
		NativeGateway gateway = null;

		try {
			gateway = NATIVE_RECONNECT ? new NativeReconnectGateway(this) : new NativeGateway(this);
			gateway.start();
			String port = Integer.toString(gateway.getPort());
			String host = gateway.getInetAddress().getHostAddress();
			
			if (TMPFILE == null) {
				this.runCommand(host, port);
			} else {
				this.writeContactInformation(host, port);
			}
		} catch (IOException ex) {
			logger.log(Level.SEVERE, getLocalName() + " could not start native code", ex);
			this.controller.fatalException(ex);
		} catch (InterruptedException ex) {
			logger.log(Level.SEVERE, getLocalName() + " was interrupted and stopped", ex);
			this.controller.fatalException(ex);
		} finally {
			// Make sure the gateway thread quits
			if (gateway != null) {
				gateway.dispose();
			}
		}
	}
	
	/**
	 * Get int value of the log Level {@link java.util.logger.Level}.
	 * 
	 * @return the log level of the ConsoleHandler, if set, otherwise returns Level.ALL.
	 */
	@Override
	public int getLogLevel() {
		String strConsoleLevel = LogManager.getLogManager().getProperty("muscle.util.logging.ConcurrentConsoleHandler.level");
		if (strConsoleLevel == null)
			return 0;
		
		try {
			return Level.parse(strConsoleLevel).intValue();
		} catch (IllegalArgumentException ex) {
			// Just log everything if there is no well-defined log level.
			return 0;
		}
	}
	
	@Override
	public void afterExecute() {
		synchronized (childLock) {
			if (processThread != null) {
				processThread.interrupt();
			}
		}
	}
	
	@Override
	public void fatalException(Throwable thr) {
		this.controller.fatalException(thr);
	}
}
