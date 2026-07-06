import { JobStreamService } from './job-stream.service';
import { JobStore } from './job-store';
import { JobProgressEvent } from './job.models';

// Minimal fake matching the bits of @stomp/stompjs Client we use.
class FakeClient {
  onConnect!: () => void;
  onWebSocketClose!: () => void;
  active = false;
  connected = false;
  autoConnect = true;
  subscriptions: Record<string, (msg: { body: string }) => void> = {};

  activate() {
    this.active = true;
    if (this.autoConnect) {
      this.connectNow();
    }
  }

  connectNow() {
    this.connected = true;
    this.onConnect?.();
  }

  close() {
    this.connected = false;
    this.subscriptions = {};
    this.onWebSocketClose?.();
  }

  deactivate() {
    this.active = false;
    this.connected = false;
    return Promise.resolve();
  }

  subscribe(dest: string, cb: (msg: { body: string }) => void) {
    if (!this.connected) {
      throw new TypeError('There is no underlying STOMP connection');
    }
    this.subscriptions[dest] = cb;
    return { unsubscribe: () => delete this.subscriptions[dest] };
  }

  emit(dest: string, event: JobProgressEvent) {
    this.subscriptions[dest]?.({ body: JSON.stringify(event) });
  }
}

class TestableStream extends JobStreamService {
  fake = new FakeClient();

  protected override createClient() {
    return this.fake as any;
  }
}

describe('JobStreamService', () => {
  let store: JobStore;
  let stream: TestableStream;

  beforeEach(() => {
    store = new JobStore();
    stream = new TestableStream(store);
  });

  it('sets status to connected on connect and applies firehose events to the store', () => {
    stream.connect();
    expect(stream.status()).toBe('connected');
    const event: JobProgressEvent = {
      jobId: 'j1',
      name: 'resize',
      status: 'RUNNING',
      progress: 50,
      workerId: 'app-1',
      timestamp: '2026-06-10T10:00:05Z',
    };
    stream.fake.emit('/topic/jobs', event);
    expect(store.job('j1')()?.progress).toBe(50);
  });

  it('reports reconnecting when the socket closes', () => {
    stream.connect();
    stream.fake.close();
    expect(stream.status()).toBe('reconnecting');
  });
});
