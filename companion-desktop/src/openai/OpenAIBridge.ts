import OpenAI from 'openai';
import { z } from 'zod';
import { RealtimeClient, SessionConfig } from './RealtimeClient';
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
    const apiKey = process.env['OPENAI_API_KEY'];
    if (!apiKey) {
      throw new Error('OPENAI_API_KEY environment variable is required');
    }

    // VideoSDK pattern: Smart glasses optimized instructions
    this.currentInstructions = `You are a smart glasses AI assistant for Vuzix M400. Follow VideoSDK best practices:

CRITICAL HUD CONSTRAINTS:
- Maximum 50 words per response (HUD space limited)
- Always use display_on_hud tool to show text
- Confirm text display with "Displayed: [text]"
- Use simple, actionable language
- Prioritize essential information only

RESPONSE FORMAT:
1. Process user input
2. Generate brief response (≤50 words)
3. Use display_on_hud tool to show text
4. Confirm display completion

CAPABILITIES:
- Visual analysis (describe key elements only)
- Quick information lookup
- Brief guidance and instructions
- Object/text identification

Remember: Every response MUST use display_on_hud tool for HUD delivery.`;

    this.realtimeClient = new RealtimeClient(apiKey);
    this.openaiClient = new OpenAI({
      apiKey: apiKey
    });

    this.setupRealtimeEvents();
    logger.info('🔥 OpenAI Bridge initialized with ENHANCED TRANSCRIPT DEBUG (v2025-07-24-14h)', {
      debugFeatures: ['fullEvent', 'eventKeys', 'transcriptValidation']
    });
  }

  private setupRealtimeEvents(): void {
    this.realtimeClient.on('connected', () => {
      this.isConnectedFlag = true;
      logger.info('Realtime client connected');
    });

    this.realtimeClient.on('disconnected', () => {
      this.isConnectedFlag = false;
      logger.info('Realtime client disconnected');
    });

    this.realtimeClient.on('text_complete', (text: string) => {
      logger.info('🎯 TEXT_COMPLETE received from OpenAI - CRITICAL PATH to WebRTC', { 
        length: text.length,
        preview: text.substring(0, 100) + '...',
        callbackSet: !!this.onTextResponseCallback,
        source: 'direct_text_complete'  // Track this vs tool-based
      });
      
      if (this.onTextResponseCallback) {
        this.onTextResponseCallback(text);
        logger.info('🎯 Direct text response forwarded to WebRTC callback (ORIGINAL PIPELINE)');
      } else {
        logger.error('🚨 NO TEXT RESPONSE CALLBACK SET! Text cannot be sent to M400');
      }
    });

    this.realtimeClient.on('text_delta', (text: string) => {
      // VideoSDK pattern: Stream text to HUD as it arrives (better UX)
      logger.debug('🎯 TEXT_DELTA received - streaming to HUD', { 
        text: text.substring(0, 50) + '...',
        length: text.length 
      });
      
      if (this.onTextResponseCallback) {
        this.onTextResponseCallback(text);
        logger.debug('🎯 Text delta forwarded to WebRTC for streaming display');
      }
    });

    this.realtimeClient.on('audio_complete', (audioPart: any) => {
      if (audioPart.audio) {
        const audioBuffer = Buffer.from(audioPart.audio, 'base64');
        logger.info('Audio response received from OpenAI', { size: audioBuffer.length });
        this.onAudioResponseCallback?.(audioBuffer);
      }
    });

    this.realtimeClient.on('speech_started', () => {
      logger.debug('User speech started');
    });

    this.realtimeClient.on('speech_stopped', () => {
      logger.debug('User speech stopped');
    });

    this.realtimeClient.on('api_error', (error: any) => {
      logger.error('OpenAI API error', { error });
    });

    this.realtimeClient.on('error', (error: Error) => {
      logger.error('Realtime client error', { error });
      this.isConnectedFlag = false;
    });

    // VideoSDK pattern: Handle display_on_hud tool calls
    this.realtimeClient.on('hud_display_request', (data: { text: string; priority: string; call_id: string }) => {
      logger.info('🎯 HUD_DISPLAY_REQUEST received from OpenAI tool - ALTERNATIVE PATH', { 
        text: data.text.substring(0, 50) + '...',
        priority: data.priority,
        call_id: data.call_id,
        source: 'tool_based_display'  // Track this vs direct text_complete
      });
      
      if (this.onTextResponseCallback) {
        this.onTextResponseCallback(data.text);
        logger.info('🎯 Tool-triggered text forwarded to WebRTC (VIDEOSDK ENHANCEMENT PATH)');
      } else {
        logger.error('🚨 NO TEXT RESPONSE CALLBACK for tool-triggered display!');
      }
    });
  }

  async initialize(): Promise<void> {
    try {
      await this.realtimeClient.connect();
      logger.info('OpenAI Bridge initialized successfully');
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

      // Send audio data to realtime API
      this.realtimeClient.sendAudio(audioData);

      // CRITICAL: Commit audio buffer to trigger VAD processing
      // Without this, OpenAI just accumulates audio without processing
      this.realtimeClient.commitAudio();

      logger.debug('Audio data sent and committed to OpenAI Realtime API', { size: audioData.length });

    } catch (error) {
      logger.error('Failed to send audio to OpenAI', { error });
    }
  }

  // VideoSDK pattern: Force reply when VAD fails (community recommendation)
  async forceReply(): Promise<void> {
    try {
      if (!this.isConnectedFlag) {
        logger.warn('Cannot force reply: not connected to OpenAI');
        return;
      }

      // Force OpenAI to generate response with current audio buffer
      (this.realtimeClient as any).createResponse();
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
                detail: 'auto' // Use 'auto' for optimal balance of speed and quality
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

      this.realtimeClient.sendText(text);
      logger.debug('Text sent to OpenAI Realtime API', { text });

    } catch (error) {
      logger.error('Failed to send text to OpenAI', { error });
    }
  }

  updateSystemPrompt(prompt: string): void {
    this.currentInstructions = prompt;

    if (this.isConnectedFlag) {
      this.realtimeClient.updateInstructions(prompt);
    }

    logger.info('System prompt updated', { 
      length: prompt.length,
      preview: prompt.substring(0, 100) + '...'
    });
  }

  updateSessionConfig(config: Partial<SessionConfig>): void {
    if (this.isConnectedFlag) {
      this.realtimeClient.updateSessionConfig(config);
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

  setVoice(voice: 'alloy' | 'echo' | 'fable' | 'onyx' | 'nova' | 'shimmer'): void {
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
    return this.isConnectedFlag && this.realtimeClient.isReady();
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
      const realtimeHealthy = this.realtimeClient.isReady();
      const openaiHealthy = this.openaiClient !== null;
      
      return {
        realtime: realtimeHealthy,
        openai: openaiHealthy,
        overall: realtimeHealthy && openaiHealthy
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
  getSessionConfig(): SessionConfig | null {
    if (this.isConnectedFlag) {
      return this.realtimeClient.getSessionConfig();
    }
    return null;
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