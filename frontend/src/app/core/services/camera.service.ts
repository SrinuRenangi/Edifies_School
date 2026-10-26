import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class CameraService {
  private activeStream: MediaStream | null = null;
  private previewObjectUrl: string | null = null;

  /**
   * Requests user media stream with front-facing camera preference and attaches to video element.
   * Audio is strictly excluded per privacy specification.
   */
  async startCamera(videoElement: HTMLVideoElement): Promise<MediaStream> {
    this.stopCamera();

    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      throw new Error('Camera API is not supported by your browser or context is not secure (requires HTTPS or localhost).');
    }

    try {
      const constraints: MediaStreamConstraints = {
        video: {
          facingMode: 'user',
          width: { ideal: 640 },
          height: { ideal: 480 }
        },
        audio: false
      };

      const stream = await navigator.mediaDevices.getUserMedia(constraints);
      this.activeStream = stream;
      videoElement.srcObject = stream;

      await new Promise<void>((resolve) => {
        if (videoElement.readyState >= 2 && videoElement.videoWidth > 0) {
          resolve();
        } else {
          let resolved = false;
          const onReady = () => {
            if (!resolved) {
              resolved = true;
              videoElement.removeEventListener('loadedmetadata', onReady);
              videoElement.removeEventListener('playing', onReady);
              resolve();
            }
          };
          videoElement.addEventListener('loadedmetadata', onReady, { once: true });
          videoElement.addEventListener('playing', onReady, { once: true });
          setTimeout(() => {
            if (!resolved) {
              resolved = true;
              resolve();
            }
          }, 2500);
        }
      });

      try {
        await videoElement.play();
      } catch (playErr) {
        console.warn('Video play warning:', playErr);
      }

      return stream;
    } catch (err: any) {
      if (err.name === 'NotAllowedError' || err.name === 'PermissionDeniedError') {
        throw new Error('Camera access is required to record attendance. Please allow camera permissions in your browser.');
      } else if (err.name === 'NotFoundError' || err.name === 'DevicesNotFoundError') {
        throw new Error('No camera hardware detected on this device.');
      } else if (err.name === 'NotReadableError' || err.name === 'TrackStartError') {
        throw new Error('Camera is currently in use by another application.');
      }
      throw new Error(`Camera error: ${err.message || 'Unable to open camera.'}`);
    }
  }

  /**
   * Captures the current video frame into a JPEG Blob suitable for server upload.
   */
  captureFrame(videoElement: HTMLVideoElement, quality = 0.85): Promise<Blob> {
    return new Promise((resolve, reject) => {
      if (!videoElement.videoWidth || !videoElement.videoHeight) {
        return reject(new Error('Video stream not ready for capture.'));
      }

      const canvas = document.createElement('canvas');
      canvas.width = videoElement.videoWidth;
      canvas.height = videoElement.videoHeight;

      const ctx = canvas.getContext('2d');
      if (!ctx) {
        return reject(new Error('Canvas 2D context unavailable.'));
      }

      ctx.drawImage(videoElement, 0, 0, canvas.width, canvas.height);

      canvas.toBlob((blob) => {
        if (!blob) {
          return reject(new Error('Failed to generate image blob from camera frame.'));
        }
        resolve(blob);
      }, 'image/jpeg', quality);
    });
  }

  /**
   * Creates an object URL for previewing the captured photo blob.
   */
  createPreviewUrl(blob: Blob): string {
    this.revokePreviewUrl();
    this.previewObjectUrl = URL.createObjectURL(blob);
    return this.previewObjectUrl;
  }

  /**
   * Revokes the preview object URL to prevent frontend memory leaks.
   */
  revokePreviewUrl(): void {
    if (this.previewObjectUrl) {
      URL.revokeObjectURL(this.previewObjectUrl);
      this.previewObjectUrl = null;
    }
  }

  /**
   * Explicitly releases all active camera tracks and turns off hardware indicator.
   */
  stopCamera(): void {
    if (this.activeStream) {
      this.activeStream.getTracks().forEach((track) => {
        try {
          track.stop();
        } catch (e) {
          console.warn('Error stopping video track:', e);
        }
      });
      this.activeStream = null;
    }
  }

  /**
   * Comprehensive cleanup on component destruction or kiosk reset.
   */
  cleanup(): void {
    this.stopCamera();
    this.revokePreviewUrl();
  }
}
