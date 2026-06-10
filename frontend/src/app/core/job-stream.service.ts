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
    if (this.client) { return; }
    const client = this.createClient();
    this.client = client;
    client.onConnect = () => {
      this.status.set('connected');
      this.firehose = client.subscribe('/topic/jobs', (msg: IMessage) => {
        this.store.apply(JSON.parse(msg.body) as JobProgressEvent);
      });
    };
    client.onWebSocketClose = () => this.status.set('reconnecting');
    this.status.set('reconnecting');
    client.activate();
  }

  /** Subscribe to one job's topic; returns an unsubscribe function. */
  subscribeJob(jobId: string, handler: (event: JobProgressEvent) => void): () => void {
    const sub = this.client!.subscribe(`/topic/jobs/${jobId}`, (msg: IMessage) =>
      handler(JSON.parse(msg.body) as JobProgressEvent),
    );
    return () => sub.unsubscribe();
  }

  disconnect(): void {
    this.firehose?.unsubscribe();
    void this.client?.deactivate();
    this.client = undefined;
    this.status.set('closed');
  }
}
