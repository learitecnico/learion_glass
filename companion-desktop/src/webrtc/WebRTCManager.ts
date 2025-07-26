import { 
  RTCPeerConnection, 
  RTCSessionDescription, 
  RTCIceCandidate, 
  RTCDataChannel,
  RTCDataChannelEvent,
  RTCPeerConnectionIceEvent
} from '@roamhq/wrtc';
import { Logger } from '../utils/Logger';

const logger = Logger.getInstance();

export class WebRTCManager {
  private connections = new Map<string, RTCPeerConnection>();
  private dataChannels = new Map<string, RTCDataChannel>();
  private onAudioReceivedCallback?: (audioData: Buffer) => void;
  private onSnapshotReceivedCallback?: (imageData: Buffer) => void;
  private signalingCallback?: (clientId: string, message: any) => void;

  constructor() {
    logger.info('WebRTC Manager initialized (real implementation)');
  }

  async createPeerConnection(clientId: string): Promise<void> {
    try {
      const configuration = {
        iceServers: [
          { urls: 'stun:stun.l.google.com:19302' },
          { urls: 'stun:stun1.l.google.com:19302' },
          { urls: 'stun:stun2.l.google.com:19302' },
          { urls: 'stun:stun.stunprotocol.org:3478' },
          { urls: 'stun:stun3.l.google.com:19302' }
        ],
        iceCandidatePoolSize: 10
      };

      const peerConnection = new RTCPeerConnection(configuration);
      this.connections.set(clientId, peerConnection);

      // Handle incoming data channels from M400
      peerConnection.ondatachannel = (event: RTCDataChannelEvent) => {
        const channel = event.channel;
        this.dataChannels.set(clientId, channel);
        this.setupDataChannelHandlers(clientId, channel);
        logger.info('Data channel received from M400', { 
          clientId, 
          label: channel.label,
          state: channel.readyState 
        });
      };

      // Handle ICE candidates
      peerConnection.onicecandidate = (event: RTCPeerConnectionIceEvent) => {
        if (event.candidate) {
          logger.debug('ICE candidate generated', { clientId });
          // M400 expects ICE candidate in format: { type: "ice_candidate", candidate: "...", sdpMid: "...", sdpMLineIndex: 0 }
          this.sendSignalingMessage(clientId, {
            type: 'ice_candidate',
            candidate: event.candidate.candidate,
            sdpMid: event.candidate.sdpMid,
            sdpMLineIndex: event.candidate.sdpMLineIndex
          });
        } else {
          logger.debug('ICE gathering complete', { clientId });
        }
      };

      // Handle connection state changes
      peerConnection.onconnectionstatechange = () => {
        const state = peerConnection.connectionState;
        logger.info('WebRTC connection state changed', { clientId, state });
        
        if (state === 'connected') {
          logger.info('WebRTC peer connection established successfully', { clientId });
        } else if (state === 'failed' || state === 'disconnected') {
          logger.warn('WebRTC connection lost', { clientId, state });
          this.cleanup(clientId);
        }
      };

      // Handle ICE connection state changes
      peerConnection.oniceconnectionstatechange = () => {
        const iceState = peerConnection.iceConnectionState;
        logger.debug('ICE connection state changed', { clientId, iceState });
        
        if (iceState === 'failed') {
          logger.error('🚨 ICE CONNECTION FAILED! Possible causes:', {
            clientId,
            troubleshooting: [
              'Check if STUN servers are reachable',
              'Verify network/firewall settings',
              'Consider adding TURN servers',
              'Test on real IP instead of localhost'
            ]
          });
        } else if (iceState === 'connected') {
          logger.info('🎉 ICE CONNECTION ESTABLISHED!', { clientId });
        }
      };

      logger.info('Peer connection created successfully', { clientId });

    } catch (error) {
      logger.error('Failed to create peer connection', { clientId, error });
      throw error;
    }
  }

  private setupDataChannelHandlers(clientId: string, channel: RTCDataChannel): void {
    channel.onopen = () => {
      logger.info('Data channel opened - ready to receive audio from M400', { 
        clientId, 
        label: channel.label,
        readyState: channel.readyState
      });
    };

    channel.onclose = () => {
      logger.info('Data channel closed', { clientId, label: channel.label });
      this.dataChannels.delete(clientId);
    };

    channel.onerror = (error: Event) => {
      logger.error('Data channel error', { clientId, label: channel.label, error });
    };

    channel.onmessage = (event: MessageEvent) => {
      this.handleDataChannelMessage(clientId, event.data);
    };

    // Set appropriate binary type for audio data
    channel.binaryType = 'arraybuffer';
  }

  private handleDataChannelMessage(clientId: string, data: any): void {
    try {
      if (typeof data === 'string') {
        // Handle JSON messages from M400
        const message = JSON.parse(data);
        this.handleJsonMessage(clientId, message);
      } else if (data instanceof ArrayBuffer) {
        // Handle binary data (shouldn't happen with current M400 implementation)
        const buffer = Buffer.from(data);
        logger.debug('Binary data received', { clientId, size: buffer.length });
        this.handleBinaryData(clientId, buffer);
      }
    } catch (error) {
      logger.error('Failed to handle data channel message', { clientId, error });
    }
  }

  private handleJsonMessage(clientId: string, message: any): void {
    logger.debug('Received JSON message from M400', { 
      clientId, 
      type: message.type,
      timestamp: message.timestamp 
    });

    switch (message.type) {
      case 'audio_data':
        // Handle audio data from M400 (base64 encoded PCM16)
        this.handleAudioDataMessage(clientId, message);
        break;
      
      case 'snapshot':
        // Handle snapshot data from M400 (base64 encoded JPEG)
        this.handleSnapshotMessage(clientId, message);
        break;

      case 'capture_snapshot':
        logger.info('Snapshot capture command received from M400', { clientId });
        // Could trigger response or acknowledgment if needed
        break;

      case 'display_confirmed':
        // VideoSDK pattern: Handle display confirmation from M400
        this.handleDisplayConfirmation(clientId, message);
        break;

      default:
        logger.warn('Unknown message type from M400', { clientId, type: message.type });
    }
  }

  private handleAudioDataMessage(clientId: string, message: any): void {
    try {
      if (!message.data) {
        logger.warn('Audio message missing data field', { clientId });
        return;
      }

      // Decode base64 audio data from M400
      const audioBuffer = Buffer.from(message.data, 'base64');
      
      logger.debug('Audio data received from M400', { 
        clientId, 
        originalSize: audioBuffer.length,
        format: message.format,
        sampleRate: message.sampleRate,
        channels: message.channels,
        timestamp: message.timestamp
      });

      // Forward to OpenAI bridge
      this.onAudioReceivedCallback?.(audioBuffer);
      
    } catch (error) {
      logger.error('Failed to process audio data from M400', { clientId, error });
    }
  }

  private handleSnapshotMessage(clientId: string, message: any): void {
    try {
      if (!message.data_base64) {
        logger.warn('Snapshot message missing data_base64 field', { clientId });
        return;
      }

      // Decode base64 image data from M400
      const imageBuffer = Buffer.from(message.data_base64, 'base64');
      
      logger.info('Snapshot received from M400', { 
        clientId, 
        size: imageBuffer.length,
        id: message.id,
        mime: message.mime
      });

      // Forward to OpenAI vision processing
      this.onSnapshotReceivedCallback?.(imageBuffer);
      
    } catch (error) {
      logger.error('Failed to process snapshot from M400', { clientId, error });
    }
  }

  private handleBinaryData(clientId: string, data: Buffer): void {
    // This shouldn't be used with current M400 implementation (uses JSON + base64)
    // But keeping for potential future optimization
    logger.debug('Binary data received', { clientId, size: data.length });
    
    if (data.length > 10000) {
      // Likely an image
      this.onSnapshotReceivedCallback?.(data);
    } else {
      // Likely audio data
      this.onAudioReceivedCallback?.(data);
    }
  }

  async handleSignalingMessage(clientId: string, message: any): Promise<void> {
    const peerConnection = this.connections.get(clientId);
    if (!peerConnection) {
      logger.error('No peer connection found for signaling message', { clientId });
      return;
    }

    try {
      logger.debug('Processing signaling message', { clientId, type: message.type });

      switch (message.type) {
        case 'offer':
          await this.handleOffer(clientId, peerConnection, message);
          break;

        case 'ice_candidate':
          await this.handleIceCandidate(peerConnection, message);
          break;

        default:
          logger.warn('Unknown signaling message type', { clientId, type: message.type });
      }
    } catch (error) {
      logger.error('Failed to handle signaling message', { clientId, error });
    }
  }

  private async handleOffer(clientId: string, peerConnection: RTCPeerConnection, message: any): Promise<void> {
    try {
      // M400 sends offer as: { type: "offer", sdp: "..." }
      // We need to create RTCSessionDescription from this format
      const offerDescription = {
        type: 'offer',
        sdp: message.sdp
      };
      
      logger.debug('Processing offer from M400', { clientId, sdpLength: message.sdp?.length });
      
      // Set the remote description from M400
      await peerConnection.setRemoteDescription(new RTCSessionDescription(offerDescription));
      logger.debug('Remote description set from M400 offer', { clientId });

      // Create and set local answer
      const answer = await peerConnection.createAnswer();
      await peerConnection.setLocalDescription(answer);
      logger.debug('Local answer created and set', { clientId });

      // Send answer back to M400 via signaling
      // M400 expects answer in format: { type: "answer", sdp: "..." }
      this.sendSignalingMessage(clientId, {
        type: 'answer',
        sdp: answer.sdp
      });

      logger.info('Answer sent to M400', { clientId });
      
    } catch (error) {
      logger.error('Failed to handle offer from M400', { clientId, error });
      throw error;
    }
  }

  private async handleIceCandidate(peerConnection: RTCPeerConnection, message: any): Promise<void> {
    try {
      // M400 sends ICE candidate as: { type: "ice_candidate", candidate: "...", sdpMid: "...", sdpMLineIndex: 0 }
      if (message.candidate) {
        const iceCandidate = {
          candidate: message.candidate,
          sdpMid: message.sdpMid,
          sdpMLineIndex: message.sdpMLineIndex
        };
        
        logger.debug('Processing ICE candidate from M400', { 
          candidate: message.candidate?.substring(0, 50) + '...', 
          sdpMid: message.sdpMid, 
          sdpMLineIndex: message.sdpMLineIndex 
        });
        
        await peerConnection.addIceCandidate(new RTCIceCandidate(iceCandidate));
        logger.debug('ICE candidate added from M400');
      }
    } catch (error) {
      logger.error('Failed to add ICE candidate', { error });
    }
  }

  // VideoSDK pattern: Send text response with confirmation loop
  sendTextResponse(text: string): void {
    logger.info('🎯 SENDING TEXT RESPONSE TO M400', { 
      text: text.substring(0, 100) + '...',
      length: text.length,
      activeChannels: this.getActiveDataChannels()
    });

    const messageId = this.generateMessageId();
    const message = {
      type: 'model_text',
      message_id: messageId,
      conversation_id: 'session-' + Date.now(),
      seq: Math.floor(Math.random() * 1000),
      ts: Date.now(),
      text,
      requires_confirmation: true  // VideoSDK pattern
    };

    // CRITICAL FIX: Send via WebSocket for M400 compatibility
    // M400's WebRTCService expects messages via SignalingClient (WebSocket)
    this.sendViaWebSocket(message);
    
    // Also broadcast via DataChannel for future compatibility
    this.broadcastMessage(message);
    
    // VideoSDK pattern: Wait for confirmation
    this.waitForDisplayConfirmation(messageId, text);
    
    logger.info('🎯 Text response sent via BOTH WebSocket and DataChannel', { 
      messageId,
      length: text.length,
      messageType: message.type 
    });
  }

  private generateMessageId(): string {
    return `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  private sendViaWebSocket(message: any): void {
    // Send to all connected clients via WebSocket
    this.connections.forEach((_, clientId) => {
      logger.info('🎯 Sending message via WebSocket to client', { 
        clientId, 
        messageType: message.type,
        messageId: message.message_id
      });
      
      // Use signaling callback to send via WebSocket
      if (this.signalingCallback) {
        this.signalingCallback(clientId, message);
      } else {
        logger.error('🚨 No signaling callback set - cannot send via WebSocket!');
      }
    });
  }

  private waitForDisplayConfirmation(messageId: string, text: string): void {
    const timeout = setTimeout(() => {
      logger.warn('🚨 DISPLAY CONFIRMATION TIMEOUT', { 
        messageId, 
        text: text.substring(0, 50) + '...',
        timeoutMs: 5000
      });
      // Could implement retry logic here
    }, 5000);

    // Store timeout for cleanup when confirmation received
    this.pendingConfirmations.set(messageId, { timeout, text });
  }

  private pendingConfirmations = new Map<string, { timeout: NodeJS.Timeout; text: string }>();

  // VideoSDK pattern: Handle display confirmation from M400
  private handleDisplayConfirmation(clientId: string, message: any): void {
    const { message_id, timestamp, status } = message;
    
    logger.info('🎯 DISPLAY CONFIRMATION received from M400', { 
      clientId, 
      message_id, 
      status, 
      timestamp 
    });
    
    const pending = this.pendingConfirmations.get(message_id);
    if (pending) {
      clearTimeout(pending.timeout);
      this.pendingConfirmations.delete(message_id);
      
      logger.info('🎉 Display confirmation successful', { 
        message_id, 
        text: pending.text.substring(0, 50) + '...',
        status
      });
    } else {
      logger.warn('🚨 Received confirmation for unknown message', { 
        message_id, 
        clientId 
      });
    }
  }

  // Send audio response back to M400 via DataChannel (future feature)
  sendAudioResponse(audioData: Buffer): void {
    // For future implementation - send audio back to M400
    this.dataChannels.forEach((channel, clientId) => {
      if (channel.readyState === 'open') {
        // For now, just send a notification that audio response is available
        const message = {
          type: 'audio_response_available',
          size: audioData.length,
          ts: Date.now()
        };
        channel.send(JSON.stringify(message));
        logger.debug('Audio response notification sent to M400', { clientId, size: audioData.length });
      }
    });
  }

  private broadcastMessage(message: any): void {
    const messageStr = JSON.stringify(message);
    let sentCount = 0;

    logger.info('🎯 BROADCASTING MESSAGE TO M400', {
      messageType: message.type,
      totalChannels: this.dataChannels.size,
      messageLength: messageStr.length
    });

    this.dataChannels.forEach((channel, clientId) => {
      logger.debug('🎯 Checking DataChannel', { 
        clientId, 
        readyState: channel.readyState,
        label: channel.label 
      });

      if (channel.readyState === 'open') {
        try {
          channel.send(messageStr);
          sentCount++;
          logger.info('🎯 MESSAGE SENT TO M400 successfully', { 
            clientId,
            messageType: message.type,
            textPreview: message.text?.substring(0, 50) + '...'
          });
        } catch (error) {
          logger.error('🚨 FAILED to send message to M400', { clientId, error });
        }
      } else {
        logger.warn('🚨 DataChannel NOT OPEN, cannot send message', { 
          clientId, 
          readyState: channel.readyState,
          label: channel.label
        });
      }
    });

    logger.info('🎯 BROADCAST COMPLETED', { 
      sentCount, 
      totalChannels: this.dataChannels.size,
      messageType: message.type
    });
  }

  private sendSignalingMessage(clientId: string, message: any): void {
    // Integrate with existing SignalingServer
    if (this.signalingCallback) {
      this.signalingCallback(clientId, message);
    } else {
      logger.warn('No signaling callback set - cannot send signaling message', { clientId });
    }
  }

  // Set callback for signaling messages (to be called by SignalingServer)
  setSignalingCallback(callback: (clientId: string, message: any) => void): void {
    this.signalingCallback = callback;
  }

  // Callback setters for audio and snapshot data
  onAudioReceived(callback: (audioData: Buffer) => void): void {
    this.onAudioReceivedCallback = callback;
    logger.debug('Audio received callback set');
  }

  onSnapshotReceived(callback: (imageData: Buffer) => void): void {
    this.onSnapshotReceivedCallback = callback;
    logger.debug('Snapshot received callback set');
  }

  // Status methods
  isConnected(): boolean {
    return Array.from(this.connections.values()).some(
      pc => pc.connectionState === 'connected'
    );
  }

  getConnectionCount(): number {
    return this.connections.size;
  }

  getActiveDataChannels(): number {
    return Array.from(this.dataChannels.values()).filter(
      channel => channel.readyState === 'open'
    ).length;
  }

  // Cleanup methods
  private cleanup(clientId: string): void {
    const channel = this.dataChannels.get(clientId);
    if (channel) {
      channel.close();
      this.dataChannels.delete(clientId);
    }

    const connection = this.connections.get(clientId);
    if (connection) {
      connection.close();
      this.connections.delete(clientId);
    }

    logger.info('WebRTC resources cleaned up', { clientId });
  }

  dispose(): void {
    logger.info('Disposing WebRTC Manager');
    
    // Close all data channels
    this.dataChannels.forEach((channel, clientId) => {
      try {
        channel.close();
      } catch (error) {
        logger.warn('Error closing data channel', { clientId, error });
      }
    });

    // Close all peer connections
    this.connections.forEach((pc, clientId) => {
      try {
        pc.close();
      } catch (error) {
        logger.warn('Error closing peer connection', { clientId, error });
      }
    });
    
    this.dataChannels.clear();
    this.connections.clear();
    
    logger.info('WebRTC Manager disposed successfully');
  }
}