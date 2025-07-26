import OpenAI from 'openai';
import { RealtimeClient, SessionResourceType } from './RealtimeClientOfficial';
import { Logger } from '../utils/Logger';

const logger = Logger.getInstance();

interface VisionAnalysis {
  description: string;
  objects: string[];
  actions?: string[];
  context?: string;
}

export class OpenAIBridge {
  private realtimeClient: RealtimeClient;
  private openaiClient: OpenAI;
  private isConnectedFlag = false;
  private onTextResponseCallback?: (text: string) => void;
  private onAudioResponseCallback?: (audioData: Buffer) => void;
  private currentInstructions: string;

  constructor() {
    // VideoSDK pattern: Smart glasses optimized instructions
    this.currentInstructions = `You are a smart glasses AI assistant for Vuzix M400. Follow VideoSDK best practices:

CRITICAL HUD CONSTRAINTS:
- Maximum 50 words per response (HUD space limited)  
- Use simple, actionable language
- Prioritize essential information only
- Respond directly without confirmation phrases

RESPONSE FORMAT:
1. Process user input
2. Generate brief response (≤50 words)
3. Send text directly to HUD

CAPABILITIES:
- Visual analysis (describe key elements only)
- Quick information lookup
- Brief guidance and instructions
- Object/text identification

Remember: Keep responses concise and immediately useful for heads-up display.`;

    this.setupOpenAIClient();

    logger.info('🔥 OpenAI Bridge initialized with OFFICIAL REALTIME CLIENT + DIRECT API KEY', {
      features: ['official-client', 'direct-api-key', 'enhanced-transcript-handling']
    });
  }

  private setupRealtimeClient(): void {
    // Use API key directly (official pattern from OpenAI docs)
    const apiKey = process.env['OPENAI_API_KEY'];
    if (!apiKey) {
      throw new Error('OPENAI_API_KEY environment variable is required');
    }
    
    this.realtimeClient = new RealtimeClient({
      apiKey: apiKey,
      debug: true
    });

    this.setupRealtimeEvents();
  }

  private setupOpenAIClient(): void {
    const apiKey = process.env['OPENAI_API_KEY'];
    if (!apiKey) {
      throw new Error('OPENAI_API_KEY environment variable is required');
    }

    this.openaiClient = new OpenAI({
      apiKey: apiKey
    });
  }

  private setupRealtimeEvents(): void {
    this.realtimeClient.on('conversation.updated', ({ item, delta }) => {
      if (delta?.transcript) {
        logger.debug('🎯 TEXT_DELTA received from official client', { 
          text: delta.transcript.substring(0, 50) + '...',
          length: delta.transcript.length 
        });
        
        if (this.onTextResponseCallback) {
          this.onTextResponseCallback(delta.transcript);
        }
      }
    });

    this.realtimeClient.on('text_complete', (text: string) => {
      logger.info('🎯 TEXT_COMPLETE received from official RealtimeClient - CRITICAL PATH to WebRTC', { 
        length: text.length,
        preview: text.substring(0, 100) + '...',
        callbackSet: !!this.onTextResponseCallback,
        source: 'official_client_text_complete'
      });
      
      if (this.onTextResponseCallback) {
        this.onTextResponseCallback(text);
        logger.info('🎯 Official client text response forwarded to WebRTC callback');
      } else {
        logger.error('🚨 NO TEXT RESPONSE CALLBACK SET! Text cannot be sent to M400');
      }
    });

    this.realtimeClient.on('text_delta', (text: string) => {
      logger.debug('🎯 TEXT_DELTA received - streaming to HUD', { 
        text: text.substring(0, 50) + '...',
        length: text.length 
      });
      
      if (this.onTextResponseCallback) {
        this.onTextResponseCallback(text);
        logger.debug('🎯 Text delta forwarded to WebRTC for streaming display');
      }
    });

    this.realtimeClient.on('conversation.item.completed', ({ item }) => {
      if (item.type === 'message' && item.role === 'assistant') {
        const textContent = (item as any).content?.find((c: any) => c.type === 'text');
        if (textContent?.text) {
          logger.info('🎯 ASSISTANT MESSAGE COMPLETED', { 
            text: textContent.text.substring(0, 100) + '...'
          });
        }
      }
    });

    this.realtimeClient.on('speech_started', () => {
      logger.debug('User speech started (official client)');
    });

    this.realtimeClient.on('speech_stopped', () => {
      logger.debug('User speech stopped (official client)');
    });

    this.realtimeClient.on('error', (error: any) => {
      logger.error('Official Realtime client error', { error });
      this.isConnectedFlag = false;
    });

    this.realtimeClient.realtime.on('server.connected', () => {
      this.isConnectedFlag = true;
      logger.info('Official Realtime client connected');
    });

    this.realtimeClient.realtime.on('server.disconnected', () => {
      this.isConnectedFlag = false;
      logger.info('Official Realtime client disconnected');
    });
  }

  async initialize(): Promise<void> {
    try {
      // Setup realtime client first
      this.setupRealtimeClient();
      
      // Update session with our smart glasses configuration
      await this.realtimeClient.updateSession({
        instructions: this.currentInstructions,
        voice: 'alloy',
        input_audio_format: 'pcm16',
        output_audio_format: 'pcm16',
        turn_detection: {
          type: 'server_vad',
          threshold: 0.3,
          prefix_padding_ms: 300,
          silence_duration_ms: 300
        },
        temperature: 0.7,
        modalities: ['text', 'audio']
      });

      await this.realtimeClient.connect();
      logger.info('🔥 OpenAI Bridge initialized successfully with DIRECT API KEY (official pattern)');
    } catch (error) {
      logger.error('Failed to initialize OpenAI Bridge', { error });
      throw error;
    }
  }

  async sendAudio(audioData: Buffer): Promise<void> {
    try {
      if (!this.isConnectedFlag) {
        logger.warn('Cannot send audio: not connected to OpenAI');
        return;
      }

      // Convert Buffer to Int16Array for official client
      const int16Array = new Int16Array(audioData.buffer, audioData.byteOffset, audioData.length / 2);
      
      // Send audio data to realtime API
      this.realtimeClient.appendInputAudio(int16Array);

      logger.debug('Audio data sent to official RealtimeClient', { size: audioData.length });

    } catch (error) {
      logger.error('Failed to send audio to OpenAI', { error });
    }
  }

  // Force reply when VAD fails
  async forceReply(): Promise<void> {
    try {
      if (!this.isConnectedFlag) {
        logger.warn('Cannot force reply: not connected to OpenAI');
        return;
      }

      this.realtimeClient.createResponse();
      logger.info('🎯 FORCE REPLY triggered - asking OpenAI to respond with current buffer');

    } catch (error) {
      logger.error('Failed to force reply', { error });
    }
  }

  async sendImage(imageData: Buffer): Promise<void> {
    try {
      logger.info('Processing image with OpenAI Vision', { size: imageData.length });

      // Convert image buffer to base64
      const imageBase64 = imageData.toString('base64');
      const imageUrl = `data:image/jpeg;base64,${imageBase64}`;

      // Use OpenAI client to analyze the image
      const visionPrompt = `${this.currentInstructions}\n\nAnalyze this image briefly. Describe what you see in 1-2 sentences, focusing on the most important elements. Keep it concise for a heads-up display.`;
      
      const response = await this.openaiClient.chat.completions.create({
        model: 'gpt-4o',
        messages: [{
          role: 'user',
          content: [
            { type: 'text', text: visionPrompt },
            {
              type: 'image_url',
              image_url: {
                url: imageUrl,
                detail: 'auto'
              }
            }
          ]
        }],
        max_tokens: 150,
        temperature: 0.7
      });

      const aiResponse = response.choices[0]?.message?.content;
      if (aiResponse) {
        logger.info('Vision analysis completed', { 
          response: aiResponse.substring(0, 100) + '...'
        });
        this.onTextResponseCallback?.(aiResponse);
      }

    } catch (error) {
      logger.error('Failed to process image with OpenAI', { error });
      this.onTextResponseCallback?.('Sorry, I could not analyze the image at this time.');
    }
  }

  async sendText(text: string): Promise<void> {
    try {
      if (!this.isConnectedFlag) {
        logger.warn('Cannot send text: not connected to OpenAI');
        return;
      }

      this.realtimeClient.sendUserMessageContent([{ type: 'input_text', text }]);
      logger.debug('Text sent to official RealtimeClient', { text });

    } catch (error) {
      logger.error('Failed to send text to OpenAI', { error });
    }
  }

  updateSystemPrompt(prompt: string): void {
    this.currentInstructions = prompt;

    if (this.isConnectedFlag) {
      this.realtimeClient.updateSession({ instructions: prompt });
    }

    logger.info('System prompt updated', { 
      length: prompt.length,
      preview: prompt.substring(0, 100) + '...'
    });
  }

  updateSessionConfig(config: Partial<SessionResourceType>): void {
    if (this.isConnectedFlag) {
      this.realtimeClient.updateSession(config);
      logger.info('Session configuration updated', { config });
    } else {
      logger.warn('Cannot update session config: not connected');
    }
  }

  // Helper methods for specific configurations
  enableAudioMode(): void {
    this.updateSessionConfig({
      modalities: ['text', 'audio'],
      voice: 'alloy'
    });
  }

  enableTextOnlyMode(): void {
    this.updateSessionConfig({
      modalities: ['text']
    });
  }

  setVoice(voice: 'alloy' | 'ash' | 'ballad' | 'coral' | 'echo' | 'sage' | 'shimmer' | 'verse'): void {
    this.updateSessionConfig({
      voice: voice
    });
  }

  setTemperature(temperature: number): void {
    if (temperature < 0 || temperature > 2) {
      logger.warn('Temperature should be between 0 and 2', { temperature });
      return;
    }
    
    this.updateSessionConfig({
      temperature: temperature
    });
  }

  getSystemPrompt(): string {
    return this.currentInstructions;
  }

  onTextResponse(callback: (text: string) => void): void {
    this.onTextResponseCallback = callback;
  }

  onAudioResponse(callback: (audioData: Buffer) => void): void {
    this.onAudioResponseCallback = callback;
  }

  isConnected(): boolean {
    return this.isConnectedFlag && this.realtimeClient && this.realtimeClient.isConnected();
  }

  async disconnect(): Promise<void> {
    logger.info('Disconnecting OpenAI Bridge');
    
    this.realtimeClient.disconnect();
    this.isConnectedFlag = false;
    
    logger.info('OpenAI Bridge disconnected');
  }

  // Health check method
  async checkHealth(): Promise<{
    realtime: boolean;
    openai: boolean;
    overall: boolean;
  }> {
    try {
      const realtimeHealthy = this.realtimeClient && this.realtimeClient.isConnected();
      const openaiHealthy = this.openaiClient !== null;
      
      return {
        realtime: !!realtimeHealthy,
        openai: openaiHealthy,
        overall: !!realtimeHealthy && openaiHealthy
      };
    } catch (error) {
      logger.error('Health check failed', { error });
      return {
        realtime: false,
        openai: false,
        overall: false
      };
    }
  }

  // Get current session configuration
  getSessionConfig(): SessionResourceType | null {
    // Return a basic config since official client doesn't expose this directly
    return {
      instructions: this.currentInstructions,
      voice: 'alloy',
      modalities: ['text', 'audio'],
      input_audio_format: 'pcm16',
      output_audio_format: 'pcm16',
      temperature: 0.7
    };
  }

  // Utility method to create a specialized prompt for different contexts
  createContextualPrompt(context: 'navigation' | 'reading' | 'general' | 'safety'): string {
    const basePrompt = `You are a Smart Glasses AI assistant. Keep responses extremely brief (max 1-2 sentences).`;
    
    const contextPrompts = {
      navigation: `${basePrompt} Focus on directions, locations, and spatial information. Use clear, actionable language.`,
      reading: `${basePrompt} Help with reading text, documents, or signs. Provide summaries and key information.`,
      safety: `${basePrompt} Prioritize safety information. Alert about hazards, warnings, or important safety considerations.`,
      general: `${basePrompt} Provide helpful, concise assistance for everyday tasks and questions.`
    };

    return contextPrompts[context];
  }
}