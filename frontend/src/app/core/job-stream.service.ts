import { Injectable, signal } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { environment } from '../../environments/environment';
import { JobProgressEvent } from './job.models';
import { JobStore } from './job-store';

export type ConnectionStatus = 'closed' | 'reconnecting' | 'connected';

interface JobSubscription {
  jobId: string;
  handler: (event: JobProgressEvent) => void;
  active?: StompSubscription;
}

@Injectable({ providedIn: 'root' })
export class JobStreamService {
  private client?: Client;
  private firehose?: StompSubscription;
  private readonly jobSubscriptions = new Map<number, JobSubscription>();
  private nextSubscriptionId = 0;

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
      for (const subscription of this.jobSubscriptions.values()) {
        this.activateJobSubscription(client, subscription);
      }
    };
    client.onWebSocketClose = () => {
      this.firehose = undefined;
      for (const subscription of this.jobSubscriptions.values()) {
        subscription.active = undefined;
      }
      this.status.set(client.active ? 'reconnecting' : 'closed');
    };
    this.status.set('reconnecting');
    client.activate();
  }

  /** Subscribe to one job's topic; returns an unsubscribe function. */
  subscribeJob(jobId: string, handler: (event: JobProgressEvent) => void): () => void {
    const id = this.nextSubscriptionId++;
    const subscription: JobSubscription = { jobId, handler };
    this.jobSubscriptions.set(id, subscription);
    if (this.client?.connected) {
      this.activateJobSubscription(this.client, subscription);
    }
    return () => {
      this.jobSubscriptions.delete(id);
      if (this.client?.connected) {
        subscription.active?.unsubscribe();
      }
      subscription.active = undefined;
    };
  }

  disconnect(): void {
    if (this.client?.connected) {
      this.firehose?.unsubscribe();
      for (const subscription of this.jobSubscriptions.values()) {
        subscription.active?.unsubscribe();
      }
    }
    this.jobSubscriptions.clear();
    void this.client?.deactivate();
    this.client = undefined;
    this.firehose = undefined;
    this.status.set('closed');
  }

  private activateJobSubscription(client: Client, subscription: JobSubscription): void {
    subscription.active = client.subscribe(`/topic/jobs/${subscription.jobId}`, (msg: IMessage) =>
      subscription.handler(JSON.parse(msg.body) as JobProgressEvent),
    );
  }
}
