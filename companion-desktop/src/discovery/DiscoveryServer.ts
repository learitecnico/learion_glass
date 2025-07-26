import * as dgram from 'dgram';
import { Logger } from '../utils/Logger';
import * as os from 'os';

const logger = Logger.getInstance();

export class DiscoveryServer {
  private udpServer: dgram.Socket;
  private broadcastInterval: NodeJS.Timeout | null = null;
  private readonly DISCOVERY_PORT = 3002;
  private readonly BROADCAST_INTERVAL = 2000; // 2 seconds

  constructor(private signalingPort: number) {
    this.udpServer = dgram.createSocket('udp4');
    this.setupUdpServer();
  }

  private setupUdpServer(): void {
    this.udpServer.on('listening', () => {
      const address = this.udpServer.address();
      logger.info('Discovery server listening', { port: address.port });
      this.udpServer.setBroadcast(true);
      this.startBroadcasting();
    });

    this.udpServer.on('message', (msg: Buffer, rinfo: dgram.RemoteInfo) => {
      const message = msg.toString();
      logger.debug('Discovery request received', { from: rinfo.address, message });
      
      if (message === 'SMART_COMPANION_DISCOVER') {
        // Respond with our server info
        const response = JSON.stringify({
          type: 'SMART_COMPANION_RESPONSE',
          host: this.getLocalIP(),
          port: this.signalingPort,
          name: os.hostname(),
          timestamp: Date.now()
        });
        
        this.udpServer.send(response, rinfo.port, rinfo.address, (err) => {
          if (err) {
            logger.error('Failed to send discovery response', { error: err });
          } else {
            logger.info('Discovery response sent', { to: rinfo.address });
          }
        });
      }
    });

    this.udpServer.on('error', (err) => {
      logger.error('Discovery server error', { error: err });
    });
  }

  private getLocalIP(): string {
    const interfaces = os.networkInterfaces();
    for (const name of Object.keys(interfaces)) {
      for (const iface of interfaces[name] || []) {
        if (iface.family === 'IPv4' && !iface.internal) {
          // Prioritize common network ranges
          if (iface.address.startsWith('192.168.') || 
              iface.address.startsWith('10.') || 
              iface.address.startsWith('172.')) {
            return iface.address;
          }
        }
      }
    }
    return '127.0.0.1';
  }

  private startBroadcasting(): void {
    const broadcastMessage = JSON.stringify({
      type: 'SMART_COMPANION_ANNOUNCE',
      host: this.getLocalIP(),
      port: this.signalingPort,
      name: os.hostname(),
      timestamp: Date.now()
    });

    this.broadcastInterval = setInterval(() => {
      // Get all network broadcast addresses
      const interfaces = os.networkInterfaces();
      for (const name of Object.keys(interfaces)) {
        for (const iface of interfaces[name] || []) {
          if (iface.family === 'IPv4' && !iface.internal) {
            // Calculate broadcast address
            const broadcastAddr = this.calculateBroadcastAddress(iface.address, iface.netmask);
            
            this.udpServer.send(broadcastMessage, this.DISCOVERY_PORT, broadcastAddr, (err) => {
              if (err) {
                logger.debug('Failed to send broadcast', { error: err.message, address: broadcastAddr });
              }
            });
          }
        }
      }
    }, this.BROADCAST_INTERVAL);
  }

  private calculateBroadcastAddress(ip: string, netmask: string): string {
    const ipParts = ip.split('.').map(Number);
    const maskParts = netmask.split('.').map(Number);
    const broadcastParts = ipParts.map((ipPart, i) => ipPart | (~maskParts[i] & 255));
    return broadcastParts.join('.');
  }

  start(): void {
    this.udpServer.bind(this.DISCOVERY_PORT);
  }

  stop(): void {
    if (this.broadcastInterval) {
      clearInterval(this.broadcastInterval);
      this.broadcastInterval = null;
    }
    this.udpServer.close();
  }
}