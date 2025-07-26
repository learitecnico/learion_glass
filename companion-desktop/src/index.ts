import 'dotenv/config';
import { createServer } from 'http';
import * as net from 'net';
import express from 'express';
import cors from 'cors';
import { SignalingServer } from './signaling/SignalingServer';
import { WebRTCManager } from './webrtc/WebRTCManager';
import { OpenAIBridge } from './openai/OpenAIBridge';
import { Logger } from './utils/Logger';
import { StunTester } from './utils/StunTester';
import { DiscoveryServer } from './discovery/DiscoveryServer';

const logger = Logger.getInstance();

class CompanionApp {
  private app: express.Application;
  private server: ReturnType<typeof createServer>;
  private signalingServer: SignalingServer;
  private webrtcManager: WebRTCManager;
  private openAIBridge: OpenAIBridge;
  private discoveryServer: DiscoveryServer;

  constructor() {
    this.app = express();
    this.server = createServer(this.app);
    
    // Initialize components
    this.signalingServer = new SignalingServer(this.server);
    this.webrtcManager = new WebRTCManager();
    this.openAIBridge = new OpenAIBridge();
    this.discoveryServer = new DiscoveryServer(parseInt(process.env['SIGNALING_PORT'] ?? '3001'));

    this.setupExpress();
    this.setupRoutes();
    this.connectComponents();
  }

  private setupExpress(): void {
    this.app.use(cors());
    this.app.use(express.json());
    this.app.use(express.static('public'));
  }

  private setupRoutes(): void {
    this.app.get('/', (req, res) => {
      res.json({
        name: 'Smart Companion Desktop',
        version: '1.0.0',
        status: 'running',
        debugVersion: 'v2025-07-24-14h-transcript-debug', // 🔥 Confirmation marker
        features: ['enhanced-transcript-debug', 'full-event-logging', 'event-keys-validation'],
        timestamp: new Date().toISOString()
      });
    });

    this.app.get('/health', async (req, res) => {
      const openaiHealth = await this.openAIBridge.checkHealth();
      
      res.json({
        status: 'healthy',
        webrtc: this.webrtcManager.isConnected(),
        openai: openaiHealth,
        signaling: this.signalingServer.getClientCount(),
        timestamp: new Date().toISOString()
      });
    });

    this.app.get('/diagnostic/stun', async (req, res) => {
      try {
        logger.info('🧪 Running STUN diagnostic test...');
        
        const [stunConnectivity, publicIP] = await Promise.all([
          StunTester.testStunConnectivity(),
          StunTester.getPublicIP()
        ]);

        const result = {
          stunConnectivity,
          publicIP,
          timestamp: new Date().toISOString(),
          recommendations: []
        };

        if (!stunConnectivity) {
          result.recommendations.push('STUN servers may be unreachable - check firewall/network settings');
          result.recommendations.push('Consider adding TURN servers for better connectivity');
        }

        if (!publicIP) {
          result.recommendations.push('Could not detect public IP - NAT traversal may fail');
          result.recommendations.push('Try testing on real IP address instead of localhost');
        }

        logger.info('🧪 STUN diagnostic completed', { stunConnectivity, publicIP });
        res.json(result);
        
      } catch (error) {
        logger.error('STUN diagnostic failed:', error);
        res.status(500).json({ 
          error: 'Diagnostic failed', 
          message: error instanceof Error ? error.message : 'Unknown error'
        });
      }
    });

    this.app.post('/prompt', (req, res) => {
      const { prompt } = req.body as { prompt?: string };
      if (!prompt) {
        return res.status(400).json({ error: 'Prompt is required' });
      }

      this.openAIBridge.updateSystemPrompt(prompt);
      logger.info('System prompt updated', { prompt: prompt.substring(0, 100) + '...' });
      
      return res.json({ success: true, message: 'Prompt updated' });
    });

    this.app.get('/prompt', (req, res) => {
      const currentPrompt = this.openAIBridge.getSystemPrompt();
      res.json({ 
        prompt: currentPrompt,
        length: currentPrompt.length 
      });
    });

    this.app.post('/session/config', (req, res) => {
      const { config } = req.body as { config?: any };
      if (!config) {
        return res.status(400).json({ error: 'Configuration is required' });
      }

      this.openAIBridge.updateSessionConfig(config);
      logger.info('Session configuration updated via API', { config });
      
      return res.json({ success: true, message: 'Session configuration updated' });
    });

    this.app.get('/session/config', (req, res) => {
      const config = this.openAIBridge.getSessionConfig();
      res.json({ config });
    });

    this.app.post('/session/voice', (req, res) => {
      const { voice } = req.body as { voice?: string };
      const validVoices = ['alloy', 'echo', 'fable', 'onyx', 'nova', 'shimmer'];
      
      if (!voice || !validVoices.includes(voice)) {
        return res.status(400).json({ 
          error: 'Valid voice is required', 
          validVoices 
        });
      }

      this.openAIBridge.setVoice(voice as any);
      logger.info('Voice updated via API', { voice });
      
      return res.json({ success: true, message: `Voice set to ${voice}` });
    });

    this.app.post('/session/temperature', (req, res) => {
      const { temperature } = req.body as { temperature?: number };
      
      if (typeof temperature !== 'number' || temperature < 0 || temperature > 2) {
        return res.status(400).json({ 
          error: 'Temperature must be a number between 0 and 2' 
        });
      }

      this.openAIBridge.setTemperature(temperature);
      logger.info('Temperature updated via API', { temperature });
      
      return res.json({ success: true, message: `Temperature set to ${temperature}` });
    });

    // Log analysis endpoint for debugging
    this.app.get('/logs/recent', (req, res) => {
      try {
        const fs = require('fs');
        const path = require('path');
        const lines = parseInt(req.query.lines as string) || 100;
        const filter = req.query.filter as string;
        
        const logFile = path.join(process.cwd(), 'logs', 'companion.log');
        
        if (!fs.existsSync(logFile)) {
          return res.json({ logs: [], message: 'Log file not found' });
        }
        
        const logContent = fs.readFileSync(logFile, 'utf8');
        let logLines = logContent.split('\n').filter(line => line.trim()).slice(-lines);
        
        // Apply filter if provided
        if (filter) {
          const filterTerms = filter.split(',').map(term => term.trim().toLowerCase());
          logLines = logLines.filter(line => 
            filterTerms.some(term => line.toLowerCase().includes(term))
          );
        }
        
        const parsedLogs = logLines.map(line => {
          try {
            return JSON.parse(line);
          } catch {
            return { message: line, raw: true };
          }
        });
        
        res.json({ 
          logs: parsedLogs,
          count: parsedLogs.length,
          totalLines: logContent.split('\n').length
        });
        
      } catch (error) {
        logger.error('Failed to read logs', { error });
        res.status(500).json({ error: 'Failed to read logs' });
      }
    });

    // Force reply endpoint (VideoSDK community recommendation for VAD issues)
    this.app.post('/force-reply', async (req, res) => {
      try {
        await this.openAIBridge.forceReply();
        res.json({ success: true, message: 'Force reply triggered' });
      } catch (error) {
        logger.error('Failed to force reply via API', { error });
        res.status(500).json({ error: 'Failed to force reply' });
      }
    });
  }

  private connectComponents(): void {
    // Connect WebRTC to OpenAI (fallback method)
    this.webrtcManager.onAudioReceived((audioData: Buffer) => {
      void this.openAIBridge.sendAudio(audioData);
    });

    this.webrtcManager.onSnapshotReceived((imageData: Buffer) => {
      void this.openAIBridge.sendImage(imageData);
    });

    // 🎵 Connect WebSocket Audio Streaming to OpenAI (MVP approach)
    this.signalingServer.onAudioStream((audioData: Buffer) => {
      logger.info('🎵 WebSocket audio received, forwarding to OpenAI Realtime API', { 
        size: audioData.length 
      });
      void this.openAIBridge.sendAudio(audioData);
    });

    // Connect OpenAI responses back to WebRTC
    this.openAIBridge.onTextResponse((text: string) => {
      this.webrtcManager.sendTextResponse(text);
    });

    this.openAIBridge.onAudioResponse((audioData: Buffer) => {
      this.webrtcManager.sendAudioResponse(audioData);
    });

    // Connect signaling to WebRTC
    this.signalingServer.onClientConnected((clientId: string) => {
      logger.info('Device connected', { clientId });
      void this.webrtcManager.createPeerConnection(clientId);
    });

    this.signalingServer.onSignalingMessage((clientId: string, message: any) => {
      void this.webrtcManager.handleSignalingMessage(clientId, message);
    });

    // Connect WebRTC signaling back to SignalingServer (bidirectional)
    this.webrtcManager.setSignalingCallback((clientId: string, message: any) => {
      this.signalingServer.sendToClient(clientId, message);
    });
  }

  async start(): Promise<void> {
    const signalingPort = parseInt(process.env['SIGNALING_PORT'] ?? '3001'); // Fixed default port
    
    try {
      // Initialize OpenAI connection
      await this.openAIBridge.initialize();
      
      // Start discovery server
      this.discoveryServer.start();
      logger.info('Discovery server started for automatic M400 detection');
      
      // Check if port is available before binding
      const isPortAvailable = await this.checkPortAvailable(signalingPort);
      if (!isPortAvailable) {
        logger.error('Port already in use - graceful shutdown in progress', { port: signalingPort });
        await this.waitForPortToBeAvailable(signalingPort, 30000); // Wait up to 30s
      }
      
      // Start server - listen on all interfaces (0.0.0.0) to accept connections from M400
      this.server.listen(signalingPort, '0.0.0.0', () => {
        logger.info('Smart Companion Desktop started', {
          signalingPort,
          nodeEnv: process.env['NODE_ENV'] ?? 'development'
        });
        
        console.log(`🚀 Smart Companion Desktop`);
        console.log(`📡 Signaling Server: ws://localhost:${signalingPort}/signaling`);
        console.log(`🌐 HTTP Server: http://localhost:${signalingPort}`);
        console.log(`📊 Health Check: http://localhost:${signalingPort}/health`);
        console.log(`🔍 Discovery: Broadcasting on UDP port 3002`);
      });

    } catch (error) {
      logger.error('Failed to start application', { error });
      process.exit(1);
    }
  }

  private async checkPortAvailable(port: number): Promise<boolean> {
    return new Promise((resolve) => {
      const server = net.createServer();
      
      server.listen(port, () => {
        server.once('close', () => resolve(true));
        server.close();
      });
      
      server.on('error', () => resolve(false));
    });
  }

  private async waitForPortToBeAvailable(port: number, timeoutMs: number): Promise<void> {
    const startTime = Date.now();
    const checkInterval = 1000; // Check every 1 second
    
    return new Promise((resolve, reject) => {
      const checkPort = async () => {
        const available = await this.checkPortAvailable(port);
        
        if (available) {
          logger.info('Port became available', { port });
          resolve();
          return;
        }
        
        if (Date.now() - startTime > timeoutMs) {
          reject(new Error(`Port ${port} did not become available within ${timeoutMs}ms`));
          return;
        }
        
        logger.info('Waiting for port to become available...', { port, elapsed: Date.now() - startTime });
        setTimeout(checkPort, checkInterval);
      };
      
      checkPort();
    });
  }

  async stop(): Promise<void> {
    logger.info('Shutting down application');
    
    await this.openAIBridge.disconnect();
    this.webrtcManager.dispose();
    this.signalingServer.dispose();
    this.discoveryServer.stop();
    
    this.server.close();
  }
}

// Start the application
const app = new CompanionApp();

// Graceful shutdown
process.on('SIGINT', async () => {
  console.log('\n📴 Received SIGINT, shutting down gracefully...');
  await app.stop();
  process.exit(0);
});

process.on('SIGTERM', async () => {
  console.log('\n📴 Received SIGTERM, shutting down gracefully...');
  await app.stop();
  process.exit(0);
});

// Start the app
void app.start();