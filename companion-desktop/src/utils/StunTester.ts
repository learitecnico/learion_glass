import { RTCPeerConnection } from '@roamhq/wrtc';
import { Logger } from './Logger';

const logger = Logger.getInstance();

export class StunTester {
  static async testStunConnectivity(): Promise<boolean> {
    try {
      logger.info('🧪 Testing STUN server connectivity...');
      
      const config = {
        iceServers: [
          { urls: 'stun:stun.l.google.com:19302' },
          { urls: 'stun:stun1.l.google.com:19302' },
          { urls: 'stun:stun2.l.google.com:19302' },
          { urls: 'stun:stun.stunprotocol.org:3478' }
        ]
      };

      const pc = new RTCPeerConnection(config);
      
      return new Promise<boolean>((resolve) => {
        let candidatesFound = 0;
        let timeoutId: NodeJS.Timeout;
        
        pc.onicecandidate = (event) => {
          if (event.candidate) {
            candidatesFound++;
            logger.debug('STUN test - ICE candidate found:', {
              type: event.candidate.type,
              candidate: event.candidate.candidate?.substring(0, 50) + '...'
            });
            
            if (candidatesFound >= 2) {
              clearTimeout(timeoutId);
              pc.close();
              logger.info('✅ STUN connectivity test PASSED', { candidatesFound });
              resolve(true);
            }
          } else {
            // ICE gathering complete
            if (candidatesFound === 0) {
              clearTimeout(timeoutId);
              pc.close();
              logger.error('❌ STUN connectivity test FAILED - No candidates found');
              resolve(false);
            }
          }
        };

        pc.onicegatheringstatechange = () => {
          logger.debug('ICE gathering state:', pc.iceGatheringState);
        };

        // Create a data channel to trigger ICE gathering
        pc.createDataChannel('test-channel');
        
        // Create offer to start ICE gathering
        pc.createOffer().then((offer) => {
          return pc.setLocalDescription(offer);
        }).catch((error) => {
          logger.error('Failed to create offer for STUN test:', error);
          resolve(false);
        });

        // Timeout after 10 seconds
        timeoutId = setTimeout(() => {
          pc.close();
          if (candidatesFound > 0) {
            logger.warn('⚠️ STUN test partial success', { candidatesFound });
            resolve(true);
          } else {
            logger.error('❌ STUN connectivity test TIMED OUT');
            resolve(false);
          }
        }, 10000);
      });
      
    } catch (error) {
      logger.error('STUN connectivity test error:', error);
      return false;
    }
  }

  static async getPublicIP(): Promise<string | null> {
    try {
      logger.info('🌐 Getting public IP via STUN...');
      
      const config = {
        iceServers: [{ urls: 'stun:stun.l.google.com:19302' }]
      };

      const pc = new RTCPeerConnection(config);
      
      return new Promise<string | null>((resolve) => {
        let timeoutId: NodeJS.Timeout;
        
        pc.onicecandidate = (event) => {
          if (event.candidate && event.candidate.type === 'srflx') {
            // Server reflexive candidate contains public IP
            const candidateStr = event.candidate.candidate || '';
            const ipMatch = candidateStr.match(/(\d+\.\d+\.\d+\.\d+)/);
            
            if (ipMatch) {
              const publicIP = ipMatch[1];
              clearTimeout(timeoutId);
              pc.close();
              logger.info('✅ Public IP detected:', { publicIP });
              resolve(publicIP);
            }
          }
        };

        // Create a data channel to trigger ICE gathering
        pc.createDataChannel('ip-test');
        
        // Create offer to start ICE gathering
        pc.createOffer().then((offer) => {
          return pc.setLocalDescription(offer);
        }).catch((error) => {
          logger.error('Failed to get public IP:', error);
          resolve(null);
        });

        // Timeout after 5 seconds
        timeoutId = setTimeout(() => {
          pc.close();
          logger.warn('⚠️ Public IP detection timed out');
          resolve(null);
        }, 5000);
      });
      
    } catch (error) {
      logger.error('Public IP detection error:', error);
      return null;
    }
  }
}