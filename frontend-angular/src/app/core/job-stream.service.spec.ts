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

  it('subscribes and unsubscribes a per-job topic', () => {
    stream.connect();
    const seen: JobProgressEvent[] = [];
    const sub = stream.subscribeJob('j1', (e) => seen.push(e));
    const event: JobProgressEvent = {
      jobId: 'j1',
      name: 'resize',
      status: 'COMPLETED',
      progress: 100,
      timestamp: '2026-06-10T10:00:09Z',
    };
    stream.fake.emit('/topic/jobs/j1', event);
    expect(seen.length).toBe(1);
    sub();
    expect(stream.fake.subscriptions['/topic/jobs/j1']).toBeUndefined();
  });

  it('queues a per-job subscription until the STOMP connection opens', () => {
    stream.fake.autoConnect = false;
    stream.connect();
    const seen: JobProgressEvent[] = [];
    stream.subscribeJob('j1', (e) => seen.push(e));

    expect(stream.fake.subscriptions['/topic/jobs/j1']).toBeUndefined();
    stream.fake.connectNow();
    stream.fake.emit('/topic/jobs/j1', {
      jobId: 'j1',
      name: 'resize',
      status: 'RUNNING',
      progress: 50,
      timestamp: '2026-06-10T10:00:05Z',
    });

    expect(seen).toHaveLength(1);
  });

  it('restores per-job subscriptions after reconnecting', () => {
    stream.connect();
    const seen: JobProgressEvent[] = [];
    stream.subscribeJob('j1', (e) => seen.push(e));
    const event: JobProgressEvent = {
      jobId: 'j1',
      name: 'resize',
      status: 'RUNNING',
      progress: 50,
      timestamp: '2026-06-10T10:00:05Z',
    };

    stream.fake.emit('/topic/jobs/j1', event);
    stream.fake.close();
    stream.fake.connectNow();
    stream.fake.emit('/topic/jobs/j1', event);

    expect(seen).toHaveLength(2);
  });
});
