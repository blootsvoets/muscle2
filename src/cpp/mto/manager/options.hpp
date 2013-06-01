#ifndef OPTIONS_H
#define OPTIONS_H

#include "../util/option_parser.hpp"
#include "../util/time.h"

#include <string>

/** Default name for the file with options */
#define CONFIG_FILE_NAMEPATH "mto-config.cfg"

/** Default name for the file with topology */
#define TOPOLOGY_FILE_NAMEPATH "mto-topology.cfg"

class Options
{
private:
    uint16_t localPortLow, localPortHigh; ///< Local port range
    muscle::endpoint internalEndpoint;             ///< Address and port for listening to clients
    std::string myName;                              ///< Name as in config file
    bool daemonize;                             ///< If the MTO should go to background
    bool useMPWide;			      ///< use MPWide
    int tcpBufSize;			      ///< TCP Buff size
    muscle::duration sockAutoCloseTimeout;         ///< Iddle time after which sockets are closed (until first access)
    
    std::string topologyFilePath;                    ///< Location of the topology
    
    option_parser opts;
    void setOptions(option_parser& opts);
    
    bool load(int argc, char **argv);
    
public:
    /**
     * Loads options from file and argv
     *
     * Returns if all options are provided and valid
     */
    Options(int argc, char **argv);
    
    void print();
    
    /* Getters */
    
    unsigned short getLocalPortLow() const {return localPortLow;}
    unsigned short getLocalPortHigh() const {return localPortHigh;}
    muscle::endpoint getInternalEndpoint() const {return internalEndpoint;}
    std::string getMyName() const {return myName;}
    bool getDaemonize() const {return daemonize;}
    int getTCPBufSize() const {return tcpBufSize;}
    std::string getTopologyFilePath() const {return topologyFilePath;}
    const muscle::duration& getSockAutoCloseTimeout() const {return sockAutoCloseTimeout;}
    
private:
    bool setLogFile(std::string path);
    bool setLogLvL(std::string f);
    bool setLogMsgType(std::string x);
};

#endif // OPTIONS_H