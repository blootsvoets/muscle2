/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package muscle.core.conduit.filter;

import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import muscle.client.LocalManager;
import muscle.core.kernel.InstanceController;
import muscle.util.concurrency.SafeTriggeredThread;
import muscle.util.data.SingleProducerConsumerBlockingQueue;

/**
 *
 * @author Joris Borgdorff
 */
public class ThreadedFilterHead<F> extends SafeTriggeredThread implements QueueProducer<F> {
	private QueueConsumer<F> consumer;
	private final BlockingQueue<F> outgoingQueue;
	private final InstanceController controller;
	
	public ThreadedFilterHead(QueueConsumer<F> consumer, InstanceController localController) {
		super("FilterHead");
		this.outgoingQueue = new SingleProducerConsumerBlockingQueue<F>();
		this.setQueueConsumer(consumer);
		this.controller = localController;
	}
	
	public void put(F data) {
		try {
			outgoingQueue.put(data);
			apply();
		} catch (InterruptedException ex) {
			Logger.getLogger(ThreadedFilterHead.class.getName()).log(Level.SEVERE, null, ex);
		}
	}
	
	private void apply() {
		this.trigger();
	}
	
	protected void execute() {
		this.consumer.apply();
	}

	public final void setQueueConsumer(QueueConsumer<F> qc) {
		if (this.consumer != null) {
			this.consumer.setIncomingQueue(null);
		}
		this.consumer = qc;
		qc.setIncomingQueue(outgoingQueue);
		qc.apply();
	}

	protected void handleInterruption(InterruptedException ex) {
		Logger.getLogger(ThreadedFilterHead.class.getName()).log(Level.SEVERE, "Could not apply filter", ex);
	}

	@Override
	protected void handleException(Throwable ex) {
		Logger.getLogger(ThreadedFilterHead.class.getName()).log(Level.SEVERE, "Could not apply filter.", ex);
		this.controller.fatalException(ex);
	}
}
