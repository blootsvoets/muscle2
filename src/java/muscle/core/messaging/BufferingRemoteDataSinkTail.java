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

package muscle.core.messaging;

import java.util.concurrent.ConcurrentLinkedQueue;
import muscle.core.messaging.jade.DataMessage;


/**
@author Jan Hegewald
*/
public class BufferingRemoteDataSinkTail<E extends java.io.Serializable> extends muscle.core.messaging.BasicDataSink<E> implements muscle.core.messaging.RemoteDataSinkTail<E> {
	

	// we may add the messages to this queue from different threads, so this queue must be thread safe (or we must handle the synchronization ourself)
	private ConcurrentLinkedQueue<E> messageQueue = new ConcurrentLinkedQueue<E>();
   private SinkObserver<DataMessage<?>> sinkObserver;


	//
	public BufferingRemoteDataSinkTail(String newID) {
		super(newID);
	}
	
	
	//
	public void put(E item) {

		messageQueue.add(item);
	}
	

	// returns first element or null
	public E poll() {
		
		return messageQueue.poll();
	}


   //
   public E take() {

      E val = super.take();
      sinkObserver.notifySinkWillYield((DataMessage<?>)val);
      return val;
   }

   public void addObserver(SinkObserver<DataMessage<?>> o) {
      sinkObserver = o;
   }

}


