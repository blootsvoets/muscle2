/*
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
#ifndef MESSAGES_H
#define MESSAGES_H

#include <cstdlib>
#include <iostream>

/** Message from client to MTO */
struct Request
{
  /** Type of message or header */
  enum Type { 
    Register = 1,         ///< Client informs that it listens on the given srcAddress:srcPort
    Connect = 2,          ///< Client wants to conect from src to dest OR one proxy forward to another the client request for it
    ConnectResponse = 3,  ///< Proxy responds if the Connect suceeded
    Data = 4,             ///< One proxy sends client data to the other proxy 
    Close = 5,            ///< A client closed the connection on one end
    PortRangeInfo = 6,    ///< Proxy tells what ports it owns
    PeerClose = 7         ///< Iddle connection gets closed, don't print an error, just close.
  };
  
  /** Converts the Type value ot it's textual representation */
  static std::string typeToString(Type t);
  
  /** Type of message or header */
  char type; 
  
  /**
   * The source and destination stay unchanged,
   * and are always seen from the 'Connect' point of view
   * (i.e. these are identical on both ends)
   */
  
  unsigned int srcAddress, ///< Source address for the connection
               dstAddress; ///< Destination address for the connection
  unsigned short srcPort,  ///< Source port for the connection
                 dstPort;  ///< Destination port for the connection
                 
  /** Some unused int filed */
  int sessionId;
  
  /** Serializes the Request to an existing char* of size at least of getSize */
  virtual void serialize(char* buf) const;
  
  /** Size, in bytes, of the serialized request */
  static unsigned getSize();
  
  /** Deserializes request from the given buffer */
  static Request deserialize(char * buf);
public:
  Request() : type(0), srcAddress(0), dstAddress(0), srcPort(0), dstPort(0), sessionId(0) {
  }
};


/** 
 * Header exchnaged between MTO's and resonse to client for connect
 */
struct Header : Request
{
  /** Length is the length of data */
  unsigned int length;
  
  /** Size, in bytes, of the serialized header */
  static unsigned getSize();
  
  /** Deserializes the header from the given buffer */
  static Header deserialize(char * buf);
  
  /** Serializes the Header to an existing char* of size at least of getSize */
  void serialize(char* buf) const;
  
  Header(): length(0) {};
  
  /** Constructs the header basing on the given request */
  Header(const Request & r) : Request(r), length(0) {};
};


/**
 * All that's needed to identify a connection. Implements all mandatory methods for (tree and hash) map keys.
 */ 
struct Identifier
{
  unsigned int srcAddress, dstAddress;
  unsigned short srcPort, dstPort;
  
  Identifier() : srcAddress(0), dstAddress(0), srcPort(0), dstPort(0) {};
  Identifier(const Request & r) : srcAddress(r.srcAddress),dstAddress(r.dstAddress), srcPort(r.srcPort), dstPort(r.dstPort){};
  
  bool operator==(const Identifier & other) const;
  
  bool operator<(const Identifier & other) const;
};

/** Hash for storing the Identifiers in a hash map */
std::size_t hash_value(const Identifier & b);


struct MtoHello
{
  /** Port range */
  unsigned short portLow, portHigh;
  
  /** Hop count */
  unsigned short distance;
  
  /** Indicates if other hellos follow */
  bool isLastMtoHello;

  MtoHello() : portLow(0), portHigh(0), distance(0) {};
  
  /** Size, in bytes, of the serialized MtoHello */
  static unsigned getSize();
  
  /** Deserializes the MtoHello from the given buffer */
  static MtoHello deserialize(char * buf);
  
  /** Serializes the MtoHello to an existing char* of size at least of getSize */
  void serialize(char* buf) const;
  
  std::string str() const;
  
  bool operator==(const MtoHello & o);
};

#endif // MESSAGES_H
