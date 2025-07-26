import { WebSocketServer, WebSocket } from 'ws';
import { IncomingMessage, Server } from 'http';
import { Logger } from '../utils/Logger';

const logger = Logger.getInstance();

export interface SignalingMessage {
  type: 'offer' | 'answer' | 'ice_candidate' | 'join' | 'leave';
  [key: string]: any;
}

export class SignalingServer {
  private wss: WebSocketServer;
  private clients = new Map<string, WebSocket>();
  private onClientConnectedCallback?: (clientId: string) => void;
  private onSignalingMessageCallback?: (clientId: string, message: any) => void;
  private onAudioStreamCallback?: (audioData: Buffer) => void;

  constructor(server: Server) {
    this.wss = new WebSocketServer({
      server,
      path: '/signaling'
    });

    this.setupWebSocketServer();
  }

  private setupWebSocketServer(): void {
    this.wss.on('connection', (ws: WebSocket, request: IncomingMessage) => {
      const clientId = this.generateClientId();
      this.clients.set(clientId, ws);

      logger.info('Client connected to signaling server', { 
        clientId,
        ip: request.socket.remoteAddress,
        userAgent: request.headers['user-agent']
      });

      // Send welcome message
      this.sendToClient(clientId, {
        type: 'welcome',
        clientId,
        timestamp: Date.now()
      });

      // Handle messages
      ws.on('message', (data: Buffer) => {
        try {
          const message: SignalingMessage = JSON.parse(data.toString());
          logger.debug('Signaling message received', { clientId, type: message.type });

          this.handleSignalingMessage(clientId, message);
        } catch (error) {
          logger.error('Failed to parse signaling message', { clientId, error });
          this.sendError(clientId, 'Invalid message format');
        }
      });

      // Handle client disconnect
      ws.on('close', (code: number, reason: Buffer) => {
        logger.info('Client disconnected from signaling server', { 
          clientId, 
          code, 
          reason: reason.toString()
        });
        this.clients.delete(clientId);
      });

      // Handle errors
      ws.on('error', (error: Error) => {
        logger.error('WebSocket error', { clientId, error });
        this.clients.delete(clientId);
      });

      // Notify about new connection
      this.onClientConnectedCallback?.(clientId);
    });

    this.wss.on('error', (error: Error) => {
      logger.error('WebSocket server error', { error });
    });
  }

  private handleSignalingMessage(clientId: string, message: SignalingMessage): void {
    switch (message.type) {
      case 'join':
        logger.info('Client joined room', { clientId, room: message.room });
        this.sendToClient(clientId, {
          type: 'joined',
          room: message.room,
          clientId
        });
        break;

      case 'offer':
      case 'answer':
      case 'ice_candidate':
        // Forward signaling messages to WebRTC manager
        this.onSignalingMessageCallback?.(clientId, message);
        break;

      case 'audio_stream' as any:
        // Handle WebSocket audio streaming (MVP approach)
        this.handleAudioStream(clientId, message);
        break;

      case 'leave':
        logger.info('Client left room', { clientId });
        break;

      default:
        logger.warn('Unknown signaling message type', { clientId, type: message.type });
        this.sendError(clientId, `Unknown message type: ${message.type}`);
    }
  }

  sendToClient(clientId: string, message: any): boolean {
    const client = this.clients.get(clientId);
    if (!client || client.readyState !== WebSocket.OPEN) {
      logger.warn('Cannot send message to client', { clientId, state: client?.readyState });
      return false;
    }

    try {
      client.send(JSON.stringify(message));
      logger.debug('Message sent to client', { clientId, type: message.type });
      return true;
    } catch (error) {
      logger.error('Failed to send message to client', { clientId, error });
      return false;
    }
  }

  private sendError(clientId: string, error: string): void {
    this.sendToClient(clientId, {
      type: 'error',
      error,
      timestamp: Date.now()
    });
  }

  private handleAudioStream(clientId: string, message: any): void {
    try {
      logger.debug('🎵 Audio stream received via WebSocket', { 
        clientId, 
        size: message.data?.length || 0,
        format: message.format,
        sampleRate: message.sampleRate,
        timestamp: message.timestamp
      });

      if (!message.data) {
        logger.warn('Audio stream message missing data field', { clientId });
        return;
      }

      // Decode base64 audio data from M400
      const audioBuffer = Buffer.from(message.data, 'base64');
      
      logger.debug('🎵 Audio data decoded', { 
        clientId, 
        originalSize: audioBuffer.length,
        format: message.format,
        sampleRate: message.sampleRate 
      });

      // Forward to OpenAI Realtime API via callback
      this.onAudioStreamCallback?.(audioBuffer);
      
    } catch (error) {
      logger.error('Failed to process audio stream', { clientId, error });
    }
  }

  broadcast(message: any, excludeClientId?: string): void {
    const messageStr = JSON.stringify(message);
    let sentCount = 0;

    this.clients.forEach((client, clientId) => {
      if (clientId !== excludeClientId && client.readyState === WebSocket.OPEN) {
        try {
          client.send(messageStr);
          sentCount++;
        } catch (error) {
          logger.error('Failed to broadcast to client', { clientId, error });
        }
      }
    });

    logger.debug('Message broadcasted', { sentCount, type: message.type });
  }

  onClientConnected(callback: (clientId: string) => void): void {
    this.onClientConnectedCallback = callback;
  }

  onSignalingMessage(callback: (clientId: string, message: any) => void): void {
    this.onSignalingMessageCallback = callback;
  }

  onAudioStream(callback: (audioData: Buffer) => void): void {
    this.onAudioStreamCallback = callback;
  }

  getClientCount(): number {
    return this.clients.size;
  }

  getConnectedClients(): string[] {
    return Array.from(this.clients.keys());
  }

  private generateClientId(): string {
    return `client_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;
  }

  dispose(): void {
    logger.info('Disposing signaling server');
    
    this.clients.forEach((client, clientId) => {
      client.close(1000, 'Server shutting down');
    });
    
    this.clients.clear();
    this.wss.close();
  }
}