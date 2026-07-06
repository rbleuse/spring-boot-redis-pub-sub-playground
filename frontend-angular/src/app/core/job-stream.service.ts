import { Injectable, signal } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { environment } from '../../environments/environment';
import { JobProgressEvent } from './job.models';
import { JobStore } from './job-store';

export type ConnectionStatus = 'closed' | 'reconnecting' | 'connected';

@Injectable({ providedIn: 'root' })
export class JobStreamService {
  private client?: Client;
  private firehose?: StompSubscription;

  readonly status = signal<ConnectionStatus>('closed');

  constructor(private readonly store: JobStore) {}

  /** Overridable for tests. */
  protected createClient(): Client {
    return new Client({
      brokerURL: environment.wsUrl,
      reconnectDelay: 5000,
    });
  }

  connect(): void {
    if (this.client) {
      return;
    }
    const client = this.createClient();
    this.client = client;
    client.onConnect = () => {
      this.status.set('connected');
      this.firehose = client.subscribe('/topic/jobs', (msg: IMessage) => {
        this.store.apply(JSON.parse(msg.body) as JobProgressEvent);
      });
    };
    client.onWebSocketClose = () => {
      this.firehose = undefined;
      this.status.set(client.active ? 'reconnecting' : 'closed');
    };
    this.status.set('reconnecting');
    client.activate();
  }

  disconnect(): void {
    if (this.client?.connected) {
      this.firehose?.unsubscribe();
    }
    void this.client?.deactivate();
    this.client = undefined;
    this.firehose = undefined;
    this.status.set('closed');
  }
}
