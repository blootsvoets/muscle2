/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package muscle.client.instance;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import muscle.client.LocalManager;
import muscle.client.communication.PortFactory;
import muscle.core.*;
import muscle.core.kernel.InstanceControllerListener;
import muscle.exception.MUSCLEConduitExhaustedException;
import muscle.exception.MUSCLEDatatypeException;
import muscle.id.InstanceClass;
import muscle.id.ResolverFactory;
import muscle.util.concurrency.NamedRunnable;

/**
 *
 * @author Joris Borgdorff
 */
public class ThreadedInstanceController extends AbstractInstanceController implements NamedRunnable {
	private final static Logger logger = Logger.getLogger(ThreadedInstanceController.class.getName());
	
	public ThreadedInstanceController(InstanceClass instanceClass, InstanceControllerListener listener, ResolverFactory rf, PortFactory portFactory) {
		super(instanceClass, listener, rf, portFactory);
	}
	
	@Override
	public void run() {		
		logger.log(Level.INFO, "{0}: connecting...", getName());
		
		if (!init()) {
			return;
		}
		
		try {
			instance.beforeExecute();

			if (!register()) {
				logger.log(Level.SEVERE, "Could not register {0}; it may already have been registered. {0} was halted.", getName());
				if (!this.isDisposed()) {
					this.disposeNoDeregister();
				}
				return;
			}
			instance.connectPortals();
			propagate();

			// log info about this controller
			if (logger.isLoggable(Level.INFO)) {
				logger.log(Level.INFO, instance.infoText());
			}

			beforeExecute();
			logger.log(Level.INFO, "{0}: executing", getName());
			try {
				instance.start();
			} catch (MUSCLEConduitExhaustedException ex) {
				logger.log(Level.SEVERE, getName() + " was prematurely halted, by trying to receive a message from a stopped submodel.", ex);
				LocalManager.getInstance().shutdown(6);
			} catch (MUSCLEDatatypeException ex) {
				logger.log(Level.SEVERE, getName() + " communicated a wrong data type. Check the coupling.", ex);
				LocalManager.getInstance().shutdown(7);
			} catch (Exception ex) {
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				ex.printStackTrace(pw);
				try {
					pw.close(); sw.close();
				} catch (IOException ex1) {
					Logger.getLogger(ThreadedInstanceController.class.getName()).log(Level.SEVERE, null, ex1);
				}
				logger.log(Level.SEVERE, "{0} was halted due to an error.\n====TRACE====\n{1}==END TRACE==", new Object[]{getName(), sw});
				LocalManager.getInstance().shutdown(8);
			}
			try {
				for (ConduitEntranceController ec : entrances) {
					if (!ec.waitUntilEmpty()) {
						logger.log(Level.WARNING, "After executing {0}, waiting for conduit {1}  was ended prematurely", new Object[]{getName(), ec.getLocalName()});
					}
				}
			} catch (InterruptedException ex) {
				logger.log(Level.SEVERE, "After executing " + getName() + ", waiting for conduit was interrupted", ex);
			}
			afterExecute();
			logger.log(Level.INFO, "{0}: finished", getName());
			dispose();
		} catch (OutOfMemoryError er) {
			logger.log(Level.SEVERE, "Instance " + getName() + " is out of memory. Try increasing with, e.g., heap size -H 1g..4g, or stack size -D -Xss512k.", er);
			LocalManager.getInstance().shutdown(2);
		}
	}

	@Override
	public NamedRunnable getRunner() {
		return this;
	}
	
	protected void disposeNoDeregister() {
		super.disposeNoDeregister();
		listener.isFinished(this);
	}
}
