import winston from 'winston';
import path from 'path';
import fs from 'fs';

export class Logger {
  private static instance: winston.Logger;

  static getInstance(): winston.Logger {
    if (!Logger.instance) {
      Logger.instance = Logger.createLogger();
    }
    return Logger.instance;
  }

  private static createLogger(): winston.Logger {
    // Ensure logs directory exists
    const logsDir = path.join(process.cwd(), 'logs');
    if (!fs.existsSync(logsDir)) {
      fs.mkdirSync(logsDir, { recursive: true });
    }

    const logLevel = process.env['LOG_LEVEL'] ?? 'debug';  // Set to debug for testing
    
    // Create timestamped log file for this session
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
    const sessionLogFile = `./logs/companion-session-${timestamp}.log`;
    const errorLogFile = `./logs/error-session-${timestamp}.log`;
    
    // Keep fallback to original log file if env var is set
    const logFile = process.env['LOG_FILE'] ?? sessionLogFile;

    const logger = winston.createLogger({
      level: logLevel,
      format: winston.format.combine(
        winston.format.timestamp(),
        winston.format.errors({ stack: true }),
        winston.format.json()
      ),
      defaultMeta: { 
        service: 'smart-companion-desktop',
        sessionStart: timestamp
      },
      transports: [
        // Write all logs to timestamped session file
        new winston.transports.File({ 
          filename: logFile,
          maxsize: 10485760, // 10MB for testing sessions
          maxFiles: 10
        }),
        
        // Write errors to separate timestamped file
        new winston.transports.File({ 
          filename: errorLogFile, 
          level: 'error',
          maxsize: 5242880,
          maxFiles: 5
        })
      ]
    });

    // Log session start info
    logger.info('🚀 NEW COMPANION SESSION STARTED', {
      sessionId: timestamp,
      logFile: logFile,
      errorFile: errorLogFile,
      logLevel: logLevel,
      debugMode: logLevel === 'debug'
    });

    return logger;
  }

  static addConsoleTransport(): void {
    const logger = Logger.getInstance();
    
    logger.add(new winston.transports.Console({
      format: winston.format.combine(
        winston.format.colorize(),
        winston.format.simple(),
        winston.format.printf(({ level, message, timestamp, ...meta }) => {
          const metaStr = Object.keys(meta).length ? ` ${JSON.stringify(meta)}` : '';
          return `${timestamp as string} [${level}]: ${message as string}${metaStr}`;
        })
      )
    }));
  }
}

// Add console transport in development
if (process.env['NODE_ENV'] !== 'production') {
  Logger.addConsoleTransport();
}