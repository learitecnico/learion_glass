import WebSocket from 'ws';
import { EventEmitter } from 'events';
import { Logger } from '../utils/Logger';

const logger = Logger.getInstance();

/**
 * Valid audio formats
 */
export type AudioFormatType = 'pcm16' | 'g711_ulaw' | 'g711_alaw';

/**
 * Audio transcription configuration
 */
export interface AudioTranscriptionType {
  model: 'whisper-1';
}

/**
 * Turn detection configuration for server VAD
 */
export interface TurnDetectionServerVadType {
  type: 'server_vad';
  threshold?: number;
  prefix_padding_ms?: number;
  silence_duration_ms?: number;
}

/**
 * Tool definition for function calling
 */
export interface ToolDefinitionType {
  type?: 'function';
  name: string;
  description: string;
  parameters: { [key: string]: any };
}

/**
 * Session configuration for Realtime API
 */
export interface SessionResourceType {
  model?: string;
  modalities?: string[];
  instructions?: string;
  voice?: 'alloy' | 'ash' | 'ballad' | 'coral' | 'echo' | 'sage' | 'shimmer' | 'verse';
  input_audio_format?: AudioFormatType;
  output_audio_format?: AudioFormatType;
  input_audio_transcription?: AudioTranscriptionType | null;
  turn_detection?: TurnDetectionServerVadType | null;
  tools?: ToolDefinitionType[];
  tool_choice?: 'auto' | 'none' | 'required' | { type: 'function'; name: string };
  temperature?: number;
  max_response_output_tokens?: number | 'inf';
}

/**
 * Item status types
 */
export type ItemStatusType = 'in_progress' | 'completed' | 'incomplete';

/**
 * Content types for items
 */
export interface InputTextContentType {
  type: 'input_text';
  text: string;
}

export interface InputAudioContentType {
  type: 'input_audio';
  audio?: string; // base64-encoded audio data
  transcript?: string | null;
}

export interface TextContentType {
  type: 'text';
  text: string;
}

export interface AudioContentType {
  type: 'audio';
  audio?: string; // base64-encoded audio data
  transcript?: string | null;
}

/**
 * Item types for conversation
 */
export interface SystemItemType {
  previous_item_id?: string | null;
  type: 'message';
  status: ItemStatusType;
  role: 'system';
  content: InputTextContentType[];
}

export interface UserItemType {
  previous_item_id?: string | null;
  type: 'message';
  status: ItemStatusType;
  role: 'user';
  content: (InputTextContentType | InputAudioContentType)[];
}

export interface AssistantItemType {
  previous_item_id?: string | null;
  type: 'message';
  status: ItemStatusType;
  role: 'assistant';
  content: (TextContentType | AudioContentType)[];
}

export interface FunctionCallItemType {
  previous_item_id?: string | null;
  type: 'function_call';
  status: ItemStatusType;
  call_id: string;
  name: string;
  arguments: string;
}

export interface FunctionCallOutputItemType {
  previous_item_id?: string | null;
  type: 'function_call_output';
  status: ItemStatusType;
  call_id: string;
  output: string;
}

export type ItemType = 
  | SystemItemType 
  | UserItemType 
  | AssistantItemType 
  | FunctionCallItemType 
  | FunctionCallOutputItemType;

/**
 * Contains text and audio information about an item
 * Can also be used as a delta
 */
export interface ItemContentDeltaType {
  text?: string;
  audio?: Int16Array;
  arguments?: string;
  transcript?: string;
}

/**
 * Event handler callback type
 */
export type EventHandlerCallbackType = (event: { [key: string]: any }) => void;

/**
 * Base event handler class
 */
class RealtimeEventHandler extends EventEmitter {
  protected eventHandlers: { [key: string]: EventHandlerCallbackType[] };
  protected nextEventHandlers: { [key: string]: EventHandlerCallbackType[] };

  constructor() {
    super();
    this.eventHandlers = {};
    this.nextEventHandlers = {};
  }

  /**
   * Clears all event handlers
   */
  clearEventHandlers(): boolean {
    this.eventHandlers = {};
    this.nextEventHandlers = {};
    return true;
  }

  /**
   * Listen to specific events
   */
  on(eventName: string, callback: EventHandlerCallbackType): EventHandlerCallbackType {
    this.eventHandlers[eventName] = this.eventHandlers[eventName] || [];
    this.eventHandlers[eventName].push(callback);
    return callback;
  }

  /**
   * Listen for the next event of a specified type
   */
  onNext(eventName: string, callback: EventHandlerCallbackType): EventHandlerCallbackType {
    this.nextEventHandlers[eventName] = this.nextEventHandlers[eventName] || [];
    this.nextEventHandlers[eventName].push(callback);
    return callback;
  }

  /**
   * Turns off event listening for specific events
   */
  off(eventName: string, callback?: EventHandlerCallbackType): boolean {
    const handlers = this.eventHandlers[eventName] || [];
    if (callback) {
      const index = handlers.indexOf(callback);
      if (index === -1) {
        throw new Error(
          `Could not turn off specified event listener for "${eventName}": not found as a listener`
        );
      }
      handlers.splice(index, 1);
    } else {
      delete this.eventHandlers[eventName];
    }
    return true;
  }

  /**
   * Dispatch events to handlers
   */
  protected dispatch(eventName: string, event: any): boolean {
    const handlers = this.eventHandlers[eventName] || [];
    const nextHandlers = this.nextEventHandlers[eventName] || [];
    
    // Execute regular handlers
    for (const handler of handlers) {
      handler(event);
    }
    
    // Execute and clear next handlers
    for (const handler of nextHandlers) {
      handler(event);
    }
    this.nextEventHandlers[eventName] = [];
    
    return true;
  }

  /**
   * Wait for next event of specified type
   */
  waitForNext(eventName: string, timeout?: number): Promise<any> {
    return new Promise((resolve, reject) => {
      let timeoutId: NodeJS.Timeout | null = null;
      
      if (timeout) {
        timeoutId = setTimeout(() => {
          reject(new Error(`Timeout waiting for event: ${eventName}`));
        }, timeout);
      }
      
      this.onNext(eventName, (event) => {
        if (timeoutId) {
          clearTimeout(timeoutId);
        }
        resolve(event);
      });
    });
  }
}

/**
 * RealtimeAPI class for WebSocket communication with OpenAI Realtime API
 */
export class RealtimeAPI extends RealtimeEventHandler {
  private defaultUrl = 'wss://api.openai.com/v1/realtime';
  private url: string;
  private apiKey: string | null;
  private debug: boolean;
  private ws: WebSocket | null = null;

  constructor({ 
    url, 
    apiKey, 
    debug = false 
  }: { 
    url?: string; 
    apiKey?: string; 
    debug?: boolean; 
  } = {}) {
    super();
    this.url = url || this.defaultUrl;
    this.apiKey = apiKey || null;
    this.debug = debug;
  }

  /**
   * Tells us whether or not the WebSocket is connected
   */
  isConnected(): boolean {
    return !!this.ws && this.ws.readyState === WebSocket.OPEN;
  }

  /**
   * Writes WebSocket logs to console
   */
  log(...args: any[]): boolean {
    const date = new Date().toISOString();
    const logs = [`[Websocket/${date}]`].concat(args).map((arg) => {
      if (typeof arg === 'object' && arg !== null) {
        return JSON.stringify(arg, null, 2);
      } else {
        return arg;
      }
    });
    if (this.debug) {
      console.log(...logs);
    }
    return true;
  }

  /**
   * Connects to the Realtime API
   */
  connect(): Promise<boolean> {
    return new Promise((resolve, reject) => {
      const url = this.url;
      const apiKey = this.apiKey;
      
      if (!apiKey) {
        reject(new Error('API key is required to connect'));
        return;
      }
      
      this.log(`Connecting to "${url}"`);
      
      this.ws = new WebSocket(url, {
        headers: {
          'Authorization': `Bearer ${apiKey}`,
          'OpenAI-Beta': 'realtime=v1'
        }
      });

      this.ws.on('open', () => {
        this.log('Connected to server');
        this.dispatch('server.connected', {});
        resolve(true);
      });

      this.ws.on('close', () => {
        this.log('Disconnected from server');
        this.dispatch('server.disconnected', {});
        this.ws = null;
      });

      this.ws.on('error', (error) => {
        this.log('WebSocket error:', error);
        this.dispatch('server.error', { error });
        reject(error);
      });

      this.ws.on('message', (data) => {
        try {
          const event = JSON.parse(data.toString());
          this.log('Received event:', event.type, event);
          this.dispatch(`server.${event.type}`, event);
        } catch (error) {
          this.log('Error parsing message:', error);
        }
      });
    });
  }

  /**
   * Disconnects from the Realtime API
   */
  disconnect(): boolean {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
    return true;
  }

  /**
   * Sends an event to the server
   */
  send(eventName: string, data?: any): boolean {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      throw new Error('Not connected to server');
    }

    const event = {
      event_id: this.generateId(),
      type: eventName,
      ...data
    };

    this.log('Sending event:', event.type, event);
    this.ws.send(JSON.stringify(event));
    this.dispatch(`client.${eventName}`, event);
    
    return true;
  }

  /**
   * Generates a unique ID for events
   */
  private generateId(): string {
    return `event_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;
  }
}

/**
 * Manages conversation state and items
 */
export class RealtimeConversation {
  private defaultFrequency = 24000; // 24kHz
  private items: ItemType[] = [];
  private itemLookup: { [id: string]: ItemType } = {};
  private queuedSpeechItems: { [id: string]: { audio: Int16Array } } = {};
  private queuedTranscriptItems: { [id: string]: { transcript: string } } = {};

  /**
   * Event processors for different server events
   */
  private EventProcessors: { [key: string]: (event: any) => { item?: ItemType; delta?: ItemContentDeltaType } | null } = {
    'conversation.item.created': (event) => {
      const { item } = event;
      const newItem = JSON.parse(JSON.stringify(item));
      
      if (!this.itemLookup[newItem.id]) {
        this.itemLookup[newItem.id] = newItem;
        this.items.push(newItem);
      }
      
      (newItem as any).formatted = {
        audio: new Int16Array(0),
        text: '',
        transcript: ''
      };
      
      // If we have queued speech, populate audio
      if (this.queuedSpeechItems[newItem.id]) {
        (newItem as any).formatted.audio = this.queuedSpeechItems[newItem.id].audio;
        delete this.queuedSpeechItems[newItem.id];
      }
      
      // Populate formatted text if available
      if (newItem.content) {
        const textContent = newItem.content.filter((c: any) =>
          ['text', 'input_text'].includes(c.type)
        );
        for (const content of textContent) {
          (newItem as any).formatted.text += (content as any).text;
        }
      }
      
      // If we have queued transcript, populate it
      if (this.queuedTranscriptItems[newItem.id]) {
        (newItem as any).formatted.transcript = this.queuedTranscriptItems[newItem.id].transcript;
        delete this.queuedTranscriptItems[newItem.id];
      }
      
      return { item: newItem };
    },

    'conversation.item.input_audio_transcription.completed': (event) => {
      const { item_id, content_index, transcript } = event;
      const item = this.itemLookup[item_id];
      const formattedTranscript = transcript || ' ';
      
      if (!item) {
        this.queuedTranscriptItems[item_id] = { transcript: formattedTranscript };
        return null;
      }
      
      (item.content[content_index] as any).transcript = transcript;
      (item as any).formatted.transcript = formattedTranscript;
      
      return { item, delta: { transcript } };
    },

    'response.audio_transcript.delta': (event) => {
      const { item_id, content_index, delta } = event;
      const item = this.itemLookup[item_id];
      
      if (!item) {
        throw new Error(`response.audio_transcript.delta: Item "${item_id}" not found`);
      }
      
      (item.content[content_index] as any).transcript += delta;
      (item as any).formatted.transcript += delta;
      
      return { item, delta: { transcript: delta } };
    },

    // Add more event processors as needed...
  };

  /**
   * Process server events and update conversation state
   */
  processEvent(event: any): { item?: ItemType; delta?: ItemContentDeltaType } | null {
    const processor = this.EventProcessors[event.type];
    if (processor) {
      return processor(event);
    }
    return null;
  }

  /**
   * Get all conversation items
   */
  getItems(): ItemType[] {
    return [...this.items];
  }

  /**
   * Get item by ID
   */
  getItem(id: string): ItemType | null {
    return this.itemLookup[id] || null;
  }

  /**
   * Clear conversation
   */
  clear(): void {
    this.items = [];
    this.itemLookup = {};
    this.queuedSpeechItems = {};
    this.queuedTranscriptItems = {};
  }
}

/**
 * Main RealtimeClient class combining API and conversation management
 */
export class RealtimeClient extends RealtimeEventHandler {
  public realtime: RealtimeAPI;
  public conversation: RealtimeConversation;
  private sessionConfig: SessionResourceType;
  private sessionCreated = false;
  private tools: { [name: string]: { definition: ToolDefinitionType; handler?: Function } } = {};

  constructor({ 
    apiKey, 
    dangerouslyAllowAPIKeyInBrowser = false,
    url,
    debug = false 
  }: { 
    apiKey?: string; 
    dangerouslyAllowAPIKeyInBrowser?: boolean;
    url?: string;
    debug?: boolean; 
  } = {}) {
    super();
    
    this.realtime = new RealtimeAPI({ url, apiKey, debug });
    this.conversation = new RealtimeConversation();
    
    // Default session configuration
    this.sessionConfig = {
      modalities: ['text', 'audio'],
      instructions: '',
      voice: 'alloy',
      input_audio_format: 'pcm16',
      output_audio_format: 'pcm16',
      input_audio_transcription: null,
      turn_detection: {
        type: 'server_vad',
        threshold: 0.5,
        prefix_padding_ms: 300,
        silence_duration_ms: 200
      },
      tools: [],
      tool_choice: 'auto',
      temperature: 0.8
    };

    this.setupEventHandlers();
  }

  /**
   * Set up event handlers for server events
   */
  private setupEventHandlers(): void {
    // Handle conversation updates
    this.realtime.on('server.conversation.updated', (event) => {
      const result = this.conversation.processEvent(event);
      if (result) {
        this.dispatch('conversation.updated', result);
        if (result.item && result.item.status === 'completed') {
          this.dispatch('conversation.item.completed', { item: result.item });
        }
      }
    });

    // Handle all conversation item events
    [
      'conversation.item.created',
      'conversation.item.input_audio_transcription.completed',
      'conversation.item.input_audio_transcription.failed',
      'response.audio_transcript.delta',
      'response.content_part.added',
      'response.content_part.done'
    ].forEach(eventType => {
      this.realtime.on(`server.${eventType}`, (event) => {
        const result = this.conversation.processEvent(event);
        if (result) {
          this.dispatch('conversation.updated', result);
          
          // Emit specific events based on transcript content
          if (eventType === 'response.content_part.done' && result.item) {
            const item = result.item as any;
            if (item.content && item.content.length > 0) {
              const textContent = item.content.find((c: any) => c.type === 'text');
              if (textContent && textContent.text) {
                logger.info('🎯 TEXT_COMPLETE from official RealtimeClient', { 
                  text: textContent.text.substring(0, 100) + '...'
                });
                this.dispatch('text_complete', textContent.text);
              }
            }
          }
          
          if (eventType === 'response.audio_transcript.delta' && result.delta?.transcript) {
            this.dispatch('text_delta', result.delta.transcript);
          }
        }
      });
    });

    // Handle session events
    this.realtime.on('server.session.created', (event) => {
      this.sessionCreated = true;
      logger.info('🎯 RealtimeClient session created', { session: event.session });
    });

    // Handle speech detection
    this.realtime.on('server.input_audio_buffer.speech_started', () => {
      this.dispatch('speech_started');
    });

    this.realtime.on('server.input_audio_buffer.speech_stopped', () => {
      this.dispatch('speech_stopped');
    });

    // Handle errors
    this.realtime.on('server.error', (event) => {
      this.dispatch('error', event.error);
    });

    // Forward realtime events
    this.realtime.on('server.*', (event) => {
      this.dispatch('realtime.event', {
        time: new Date().toISOString(),
        source: 'server',
        event
      });
    });
  }

  /**
   * Connect to the Realtime API
   */
  async connect(): Promise<boolean> {
    const connected = await this.realtime.connect();
    if (connected) {
      await this.updateSession();
    }
    return connected;
  }

  /**
   * Disconnect from the Realtime API
   */
  disconnect(): boolean {
    return this.realtime.disconnect();
  }

  /**
   * Update session configuration
   */
  updateSession(config: Partial<SessionResourceType> = {}): Promise<boolean> {
    // Merge with existing config
    this.sessionConfig = { ...this.sessionConfig, ...config };
    
    // Add tools from this.tools
    const toolDefinitions = Object.values(this.tools).map(t => t.definition);
    this.sessionConfig.tools = [...(this.sessionConfig.tools || []), ...toolDefinitions];
    
    return new Promise((resolve) => {
      this.realtime.send('session.update', { session: this.sessionConfig });
      
      // Wait for session update confirmation
      this.realtime.onNext('server.session.updated', () => {
        resolve(true);
      });
    });
  }

  /**
   * Send streaming audio data
   */
  appendInputAudio(audioData: Int16Array | ArrayBuffer): void {
    let base64Audio: string;
    
    if (audioData instanceof Int16Array) {
      const buffer = new ArrayBuffer(audioData.length * 2);
      const view = new DataView(buffer);
      for (let i = 0; i < audioData.length; i++) {
        view.setInt16(i * 2, audioData[i], true);
      }
      base64Audio = Buffer.from(buffer).toString('base64');
    } else {
      base64Audio = Buffer.from(audioData).toString('base64');
    }
    
    this.realtime.send('input_audio_buffer.append', { audio: base64Audio });
  }

  /**
   * Create a response (triggers generation)
   */
  createResponse(): void {
    this.realtime.send('response.create');
  }

  /**
   * Send user message content
   */
  sendUserMessageContent(content: (InputTextContentType | InputAudioContentType)[]): void {
    this.realtime.send('conversation.item.create', {
      item: {
        type: 'message',
        role: 'user',
        content
      }
    });
    this.createResponse();
  }

  /**
   * Add a tool with automatic handler
   */
  addTool(definition: ToolDefinitionType, handler?: Function): ToolDefinitionType {
    if (!definition.type) {
      definition.type = 'function';
    }
    
    this.tools[definition.name] = { definition, handler };
    
    // Update session with new tools
    if (this.sessionCreated) {
      this.updateSession();
    }
    
    return definition;
  }

  /**
   * Check if connected
   */
  isConnected(): boolean {
    return this.realtime.isConnected();
  }
}