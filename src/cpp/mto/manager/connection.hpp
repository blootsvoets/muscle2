#ifndef CONNECTION_H
#define CONNECTION_H

#include "messages.hpp"
#include "muscle2/util/async_service.h"

#include <set>
#include <map>
#include <queue>
#include <vector>
#include <exception>

/** Size of a receive data buffer for each connection
 With MTU=1460, this is 4096 packets */
#define MTO_CONNECTION_BUFFER_SIZE 2990080

class PeerCollection;
class PeerConnectionHandler;
class LocalMto;

/**
 * Represents connection between two end points
 */
class Connection : public muscle::net::async_recvlistener, public muscle::net::async_sendlistener, public muscle::net::async_function
{
private:
	static const muscle::util::duration recvTimeout;
	
    /** Local side of the connection */
    muscle::net::ClientSocket *sock;
    
    /** Represents the remote end */
    PeerConnectionHandler *remoteMto;
    
    /** Reusable header with proper adresses and ports */
    Header header;
    
    /** Indicates whether there is a peer on the other side */
    bool hasRemotePeer;
    
    /** How many completion hooks still will call this class */
    int pendingOperations;
    
    /** If the connections should close / is just closing */
    bool closing;
    
    LocalMto *mto;
    
	void receive(void *buffer, size_t sz);
    inline void receive()
	{ receive(new char[MTO_CONNECTION_BUFFER_SIZE], MTO_CONNECTION_BUFFER_SIZE); }
	
	void send(void *data, size_t length, int user_flag);
	
    void tryClose();
    
    size_t closing_timer;
	size_t receiving_timer;
	void *lastReceivedData;
	size_t lastReceivedSize;
	
	enum {
		CONNECT = 1,
		RECEIVE = 2,
		SEND = 3,
		TIMER_CLOSE = 4,
		TIMER_RECEIVE = 5
	};
    /** Close the Connection */
    void close();

public:
    Connection(Header h, muscle::net::ClientSocket* s, PeerConnectionHandler * remoteMto, LocalMto *mto, bool remotePeerConnected);
    
    virtual ~Connection();
    
	/* Close timer */
	virtual void async_execute(size_t code, int flag, void *user_data);
	
	/* Receive */
    virtual bool async_received(size_t code, int user_flag, void *data, void *last_data_ptr, size_t count, int is_final);

    /* Send */
    inline void send(void *data, size_t length) { send(data, length, SEND); }
    virtual void async_sent(size_t code, int user_flag, void *data, size_t len, int is_final);
    
    virtual void async_report_error(size_t code, int user_flag, const muscle::muscle_exception& ex);
    virtual void async_done(size_t code, int flag);
    
    /** Fires when the response for connection request is received */
    void remoteConnected(Header h);

    /** Called when remote peer closed */
    void remoteClosed();
    
    /** Once a peer becomes unavailable, this is called. If this connection uses the peer, it closes. */
    void peerDied(PeerConnectionHandler * handler);
    
    /** Requests replacing one PeerConnectionHandler to another */
    void replacePeer(PeerConnectionHandler* from, PeerConnectionHandler* to);
};


#endif // CONNECTION_H
